package com.perera.fishgame.auth;

import java.util.List;
import java.util.Optional;

/**
 * Where user accounts are kept. {@link AuthService} depends only on this
 * interface, so the simple text file used now can later be replaced by a real
 * database (for example SQLite) without changing the login logic.
 */
public interface UserStore {

    /** Finds a user by name (case-insensitive). */
    Optional<User> find(String username) throws AuthException;

    /** Adds a user. Returns false, and changes nothing, if the name is taken. */
    boolean create(User user) throws AuthException;

    /** A copy of every user, in no particular order. */
    List<User> all() throws AuthException;

    /** Raises the stored best score; a lower score is ignored. */
    void updateBestScore(String username, int score) throws AuthException;
}