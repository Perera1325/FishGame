package com.perera.fishgame.auth;

/**
 * A registered player. Only the password <em>hash</em> is ever stored, never
 * the password itself.
 *
 * @param username     the name as typed at registration
 * @param passwordHash salted PBKDF2 hash, see {@link PasswordHasher}
 * @param bestScore    the player's highest score so far
 */
public record User(String username, String passwordHash, int bestScore) {
}