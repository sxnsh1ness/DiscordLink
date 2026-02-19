package io.github.sxnsh1ness.discordlink.sessions;

import lombok.Getter;
import lombok.Setter;

public class TwoFASession {
    @Getter
    private final String code;
    @Getter
    private final long expiresAt;
    @Setter
    private boolean verified;

    public TwoFASession(String code, long expiresAt) {
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public boolean isVerified() {
        return verified;
    }
}
