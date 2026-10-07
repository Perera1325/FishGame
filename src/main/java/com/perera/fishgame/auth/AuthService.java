package com.perera.fishgame.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Virtual identity: registration, login, sessions and score saving.
 *
 * <p>Security measures (for the video):
 * <ul>
 * <li>Passwords are never stored, only salted PBKDF2 hashes
 * ({@link PasswordHasher}). The password array is wiped after use.</li>
 * <li>Input rules: username 3-20 letters, digits or underscores; password 8-128
 * characters.</li>
 * <li>A failed login gives one generic message, whether the user exists or not,
 * and a dummy hash is checked for unknown users so the response time does not
 * reveal which usernames exist.</li>
 * <li>Brute-force protection: after 5 wrong passwords a username is locked for
 * 5 minutes.</li>
 * <li>Login returns a {@link Session} with a random 256-bit token that expires
 * (the desktop equivalent of a session cookie). Saving a score needs a valid
 * token, so identity decides who may change what.</li>
 * </ul>
 *
 * <p>The clock is injected so that expiry and lockout can be unit tested
 * without waiting.
 */
public class AuthService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    public static final Duration DEFAULT_SESSION_LIFETIME = Duration.ofHours(8);

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,20}");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 128;
    private static final String BAD_CREDENTIALS = "Invalid username or password.";

    private static final class FailedLogins {
        int count;
        Instant lockedUntil;
    }

    private final UserStore store;
    private final PasswordHasher hasher;
    private final Clock clock;
    private final Duration sessionLifetime;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<String, FailedLogins> failures = new HashMap<>();
    private String dummyHash;

    public AuthService(UserStore store) {
        this(store, new PasswordHasher(), Clock.systemUTC(), DEFAULT_SESSION_LIFETIME);
    }

    public AuthService(UserStore store, PasswordHasher hasher, Clock clock,
            Duration sessionLifetime) {
        this.store = store;
        this.hasher = hasher;
        this.clock = clock;
        this.sessionLifetime = sessionLifetime;
    }

    /** Creates a new account. The password array is wiped before returning. */
    public synchronized void register(String username, char[] password) throws AuthException {
        try {
            String name = username == null ? "" : username.trim();
            if (!USERNAME.matcher(name).matches()) {
                throw new AuthException(
                        "Username must be 3-20 letters, numbers or underscores.");
            }
            if (password == null || password.length < MIN_PASSWORD
                    || password.length > MAX_PASSWORD) {
                throw new AuthException("Password must be 8-128 characters long.");
            }
            if (sameText(password, name)) {
                throw new AuthException("Password must not be the same as the username.");
            }
            if (!store.create(new User(name, hasher.hash(password), 0))) {
                throw new AuthException("That username is already taken.");
            }
        } finally {
            wipe(password);
        }
    }

    /**
     * Checks the credentials and starts a session. The password array is wiped
     * before returning.
     */
    public synchronized Session login(String username, char[] password) throws AuthException {
        try {
            String name = username == null ? "" : username.trim();
            char[] pw = password == null ? new char[0] : password;
            if (!USERNAME.matcher(name).matches()) {
                hasher.verify(pw, dummyHash()); // same effort as a real check
                throw new AuthException(BAD_CREDENTIALS);
            }
            String key = name.toLowerCase(Locale.ROOT);
            Instant now = clock.instant();

            FailedLogins failed = failures.get(key);
            if (failed != null && failed.lockedUntil != null
                    && now.isBefore(failed.lockedUntil)) {
                throw new AuthException(
                        "Too many failed attempts. Please try again in a few minutes.");
            }

            Optional<User> user = store.find(name);
            boolean correct;
            if (user.isPresent()) {
                correct = hasher.verify(pw, user.get().passwordHash());
            } else {
                hasher.verify(pw, dummyHash());
                correct = false;
            }
            if (!correct) {
                recordFailure(key, now);
                throw new AuthException(BAD_CREDENTIALS);
            }
            failures.remove(key);
            return startSession(user.get().username(), now);
        } finally {
            wipe(password);
        }
    }

    /** The session for this token, if it exists and has not expired. */
    public synchronized Optional<Session> session(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired(clock.instant())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    /** Ends the session; the token stops working immediately. */
    public synchronized void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** The logged-in player's best score. */
    public synchronized int bestScore(String token) throws AuthException {
        Session session = requireSession(token);
        return store.find(session.getUsername()).map(User::bestScore).orElse(0);
    }

    /** Saves a finished game's score if it is a new personal best. */
    public synchronized void recordScore(String token, int score) throws AuthException {
        Session session = requireSession(token);
        store.updateBestScore(session.getUsername(), score);
    }

    // ------------------------------------------------------------------

    private Session requireSession(String token) throws AuthException {
        return session(token).orElseThrow(
                () -> new AuthException("Your session has expired. Please log in again."));
    }

    private Session startSession(String username, Instant now) {
        byte[] bytes = new byte[32]; // 256 bits of randomness
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Session session = new Session(token, username, now.plus(sessionLifetime));
        sessions.put(token, session);
        return session;
    }

    private void recordFailure(String key, Instant now) {
        FailedLogins failed = failures.computeIfAbsent(key, k -> new FailedLogins());
        if (failed.lockedUntil != null && !now.isBefore(failed.lockedUntil)) {
            failed.lockedUntil = null; // an old lock has run out: start counting again
            failed.count = 0;
        }
        failed.count++;
        if (failed.count >= MAX_FAILED_ATTEMPTS) {
            failed.lockedUntil = now.plus(LOCK_DURATION);
            failed.count = 0;
        }
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = hasher.hash("not-a-real-password".toCharArray());
        }
        return dummyHash;
    }

    private static boolean sameText(char[] password, String name) {
        if (password.length != name.length()) {
            return false;
        }
        for (int i = 0; i < password.length; i++) {
            if (Character.toLowerCase(password[i]) != Character.toLowerCase(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static void wipe(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}