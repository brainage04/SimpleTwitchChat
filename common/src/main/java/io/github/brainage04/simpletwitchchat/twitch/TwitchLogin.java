package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Optional;
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
}
