package com.perera.fishgame.service;

/**
 * Thrown when a game cannot be obtained (network down, timeout, bad data...).
 * It is a checked exception so callers are forced to decide what happens when
 * the external service fails, instead of crashing with a NullPointerException.
 */
public class GameProviderException extends Exception {

    private static final long serialVersionUID = 1L;

    public GameProviderException(String message) {
        super(message);
    }

    public GameProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}