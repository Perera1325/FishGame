package com.perera.fishgame.auth;

/**
 * Thrown when registration, login or a session check fails. The message is
 * safe to show to the user (it never reveals, for example, whether a username
 * exists when a login fails).
 */
public class AuthException extends Exception {

    private static final long serialVersionUID = 1L;

    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}