package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Optional;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

final class TwitchLogin extends TwitchIdentityProvider {
    private final String validateUrl;

    TwitchLogin(String clientId, String baseUrl) {
        super(clientId, null, null, baseUrl);
        validateUrl = baseUrl + "/validate";
    }

    // twitch4j counts every 4xx as an invalid token (TwitchIdentityProvider.java:57-58), so a rate
    // limit (429) or a proxy asking for its own login (407) would end the session. Twitch answers
    // an invalid token with 401: https://dev.twitch.tv/docs/authentication/validate-tokens/
    // TwitchChat asks this provider too before it reconnects anonymously (TwitchChat.java:351-352).
    @Override
    public Optional<Boolean> isCredentialValid(OAuth2Credential credential) {
        Request request =
                new Request.Builder()
                        .url(validateUrl)
                        .header("Authorization", "OAuth " + credential.getAccessToken())
                        .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return Optional.of(true);
            }
            return response.code() == HttpURLConnection.HTTP_UNAUTHORIZED
                    ? Optional.of(false)
                    : Optional.empty();
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    // The device code flow makes this a public client: the refresh request carries a client_id and
    // no client secret, and Twitch answers with a new refresh token that replaces the one used
    // here, which expires 30 days after it was generated:
    // https://dev.twitch.tv/docs/authentication/refresh-tokens/
    // The parameters travel in the body because the token must not appear in a URL that HTTP
    // logging could keep.
    Renewal renew(OAuth2Credential credential) {
        String refreshToken = credential.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            // Nothing is left to renew with, so only a new authorisation brings the chat back.
            return Renewal.REFUSED;
        }
        Request request =
                new Request.Builder()
                        .url(tokenUrl)
                        .post(
                                new FormBody.Builder()
                                        .add("grant_type", "refresh_token")
                                        .add("refresh_token", refreshToken)
                                        .add("client_id", clientId)
                                        .build())
                        .build();
        try (Response response = httpClient.newCall(request).execute()) {
            // Twitch answers a spent or invalid refresh token with 400 "Invalid refresh token";
            // every other code, a 429 or a 5xx included, says nothing about the token, so the bot
            // asks again after Bot.RENEWAL_RETRY_DELAY:
            // https://dev.twitch.tv/docs/authentication/refresh-tokens/
            int code = response.code();
            if (code == HttpURLConnection.HTTP_BAD_REQUEST
                    || code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return Renewal.REFUSED;
            }
            if (!response.isSuccessful() || response.body() == null) {
                return Renewal.UNKNOWN;
            }
            JsonObject answer = JsonParser.parseString(response.body().string()).getAsJsonObject();
            if (!answer.has("access_token")) {
                return Renewal.UNKNOWN;
            }
            return Renewal.renewed(
                    new OAuth2Credential(
                            getProviderName(),
                            answer.get("access_token").getAsString(),
                            answer.has("refresh_token")
                                    ? answer.get("refresh_token").getAsString()
                                    : null,
                            null,
                            null,
                            answer.has("expires_in") ? answer.get("expires_in").getAsInt() : null,
                            null));
        } catch (IOException | RuntimeException exception) {
            return Renewal.UNKNOWN;
        }
    }
}
