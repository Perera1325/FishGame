package com.perera.fishgame.auth;

import java.time.Instant;

/**
 * Proof that a user has logged in. In a web application this is what a session
 * cookie represents: a random, unguessable token that the client presents
 * instead of the password, and that stops working after a time limit. Here the
 * token is held in memory by the desktop application.
 */
public final class Session {

    private final String token;
    private final String username;
    private final Instant expiresAt;

    public Session(String token, String username, Instant expiresAt) {
        this.token = token;
        this.username = username;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}