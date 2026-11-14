package io.github.brainage04.simpletwitchchat.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;

/**
 * What Twitch answered a refresh-token request: a new login, a refusal of the token, or no answer
 * that decides either way.
 */
record Renewal(Outcome outcome, OAuth2Credential credential) {
    enum Outcome {
        RENEWED,
        /** Twitch refused the refresh token, or there is none left: only a new login helps. */
        REFUSED,
        /** Twitch could not be asked or did not say: retried after Bot.RENEWAL_RETRY_DELAY. */
        UNKNOWN
    }

    static final Renewal REFUSED = new Renewal(Outcome.REFUSED, null);
    static final Renewal UNKNOWN = new Renewal(Outcome.UNKNOWN, null);

    static Renewal renewed(OAuth2Credential credential) {
        return new Renewal(Outcome.RENEWED, credential);
    }
}
