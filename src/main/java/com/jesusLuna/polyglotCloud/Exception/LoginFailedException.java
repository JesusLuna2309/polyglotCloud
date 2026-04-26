package com.jesusLuna.polyglotCloud.Exception;

import java.time.Instant;

public class LoginFailedException extends BusinessRuleException {

    private final Integer remainingAttempts;
    private final Instant lockedUntil;
    private final boolean accountLocked;
    private final boolean accountDisabled;

    public LoginFailedException(String message, Integer remainingAttempts, Instant lockedUntil, boolean accountLocked, boolean accountDisabled) {
        super(message);
        this.remainingAttempts = remainingAttempts;
        this.lockedUntil = lockedUntil;
        this.accountLocked = accountLocked;
        this.accountDisabled = accountDisabled;
    }

    public LoginFailedException(String message, Integer remainingAttempts) {
        this(message, remainingAttempts, null, false, false);
    }

    public Integer getRemainingAttempts() {
        return remainingAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public boolean isAccountDisabled() {
        return accountDisabled;
    }
}
