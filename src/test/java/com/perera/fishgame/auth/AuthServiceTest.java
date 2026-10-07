package com.perera.fishgame.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AuthService is tested with an in-memory store and a clock we control, so the
 * tests are fast and need no files or waiting. This works because the service
 * depends on the UserStore interface and receives its Clock (low coupling).
 */
class AuthServiceTest {

    private static class InMemoryStore implements UserStore {
        final Map<String, User> users = new HashMap<>();

        @Override
        public Optional<User> find(String name) {
            return Optional.ofNullable(users.get(name.toLowerCase(Locale.ROOT)));
        }

        @Override
        public boolean create(User user) {
            return users.putIfAbsent(user.username().toLowerCase(Locale.ROOT), user) == null;
        }

        @Override
        public List<User> all() {
            return new ArrayList<>(users.values());
        }

        @Override
        public void updateBestScore(String name, int score) {
            User u = users.get(name.toLowerCase(Locale.ROOT));
            if (u != null && score > u.bestScore()) {
                users.put(name.toLowerCase(Locale.ROOT),
                        new User(u.username(), u.passwordHash(), score));
            }
        }
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private InMemoryStore store;
    private MutableClock clock;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        clock = new MutableClock();
        auth = new AuthService(store, new PasswordHasher(1000), clock, Duration.ofHours(1));
    }

    private static char[] pw(String s) {
        return s.toCharArray();
    }

    @Test
    void registerThenLoginGivesASession() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        Session s = auth.login("vinod", pw("goodpassword1"));
        assertEquals("Vinod", s.getUsername());
        assertTrue(auth.session(s.getToken()).isPresent());
    }

    @Test
    void storedPasswordIsAHashNotThePassword() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        String stored = store.find("Vinod").orElseThrow().passwordHash();
        assertFalse(stored.contains("goodpassword1"));
    }

    @Test
    void weakOrInvalidRegistrationsAreRefused() {
        assertThrows(AuthException.class, () -> auth.register("ab", pw("goodpassword1")));
        assertThrows(AuthException.class, () -> auth.register("bad name!", pw("goodpassword1")));
        assertThrows(AuthException.class, () -> auth.register("Vinod", pw("short")));
        assertThrows(AuthException.class, () -> auth.register("Vinod", pw("vinod")));
        assertThrows(AuthException.class, () -> auth.register(null, pw("goodpassword1")));
        assertThrows(AuthException.class, () -> auth.register("Vinod", null));
    }

    @Test
    void duplicateUsernameIsRefused() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        assertThrows(AuthException.class, () -> auth.register("vinod", pw("otherpassword2")));
    }

    @Test
    void passwordArrayIsWipedAfterUse() throws Exception {
        char[] password = pw("goodpassword1");
        auth.register("Vinod", password);
        for (char c : password) {
            assertEquals('\0', c);
        }
        char[] again = pw("goodpassword1");
        auth.login("Vinod", again);
        for (char c : again) {
            assertEquals('\0', c);
        }
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameMessage() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        AuthException wrong = assertThrows(AuthException.class,
                () -> auth.login("Vinod", pw("badpassword99")));
        AuthException unknown = assertThrows(AuthException.class,
                () -> auth.login("Nobody", pw("badpassword99")));
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void accountLocksAfterTooManyFailuresEvenForTheRightPassword() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            assertThrows(AuthException.class, () -> auth.login("Vinod", pw("badpassword99")));
        }
        AuthException locked = assertThrows(AuthException.class,
                () -> auth.login("Vinod", pw("goodpassword1")));
        assertTrue(locked.getMessage().contains("Too many"));
    }

    @Test
    void lockEndsAfterTheLockDuration() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            assertThrows(AuthException.class, () -> auth.login("Vinod", pw("badpassword99")));
        }
        clock.advance(AuthService.LOCK_DURATION.plusSeconds(1));
        assertTrue(auth.login("Vinod", pw("goodpassword1")) != null);
    }

    @Test
    void successfulLoginResetsTheFailureCount() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThrows(AuthException.class, () -> auth.login("Vinod", pw("badpassword99")));
        }
        auth.login("Vinod", pw("goodpassword1"));
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThrows(AuthException.class, () -> auth.login("Vinod", pw("badpassword99")));
        }
        assertTrue(auth.login("Vinod", pw("goodpassword1")) != null); // still not locked
    }

    @Test
    void sessionExpires() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        Session s = auth.login("Vinod", pw("goodpassword1"));
        clock.advance(Duration.ofMinutes(59));
        assertTrue(auth.session(s.getToken()).isPresent());
        clock.advance(Duration.ofMinutes(2));
        assertFalse(auth.session(s.getToken()).isPresent());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        Session s = auth.login("Vinod", pw("goodpassword1"));
        auth.logout(s.getToken());
        assertFalse(auth.session(s.getToken()).isPresent());
    }

    @Test
    void eachLoginGetsADifferentToken() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        Session a = auth.login("Vinod", pw("goodpassword1"));
        Session b = auth.login("Vinod", pw("goodpassword1"));
        assertNotEquals(a.getToken(), b.getToken());
    }

    @Test
    void scoresAreSavedForTheSessionOwnerOnly() throws Exception {
        auth.register("Vinod", pw("goodpassword1"));
        Session s = auth.login("Vinod", pw("goodpassword1"));
        auth.recordScore(s.getToken(), 4);
        auth.recordScore(s.getToken(), 2);
        assertEquals(4, auth.bestScore(s.getToken()));
    }

    @Test
    void scoreCannotBeSavedWithoutAValidSession() throws Exception {
        assertThrows(AuthException.class, () -> auth.recordScore("made-up-token", 5));
        assertThrows(AuthException.class, () -> auth.recordScore(null, 5));
        auth.register("Vinod", pw("goodpassword1"));
        Session s = auth.login("Vinod", pw("goodpassword1"));
        clock.advance(Duration.ofHours(2));
        assertThrows(AuthException.class, () -> auth.recordScore(s.getToken(), 5));
    }

    private Session registerAndLogin(String name, int score) throws Exception {
        auth.register(name, pw("goodpassword1"));
        Session s = auth.login(name, pw("goodpassword1"));
        auth.recordScore(s.getToken(), score);
        return s;
    }

    @Test
    void leaderboardIsSortedBestFirstAndHidesZeroScores() throws Exception {
        Session a = registerAndLogin("Alice", 3);
        registerAndLogin("Bob", 9);
        registerAndLogin("Carol", 0);
        List<ScoreEntry> board = auth.leaderboard(a.getToken(), 10);
        assertEquals(2, board.size());
        assertEquals("Bob", board.get(0).username());
        assertEquals(9, board.get(0).bestScore());
        assertEquals("Alice", board.get(1).username());
    }

    @Test
    void leaderboardTiesAreBrokenByName() throws Exception {
        Session z = registerAndLogin("Zed", 5);
        registerAndLogin("Amy", 5);
        List<ScoreEntry> board = auth.leaderboard(z.getToken(), 10);
        assertEquals("Amy", board.get(0).username());
        assertEquals("Zed", board.get(1).username());
    }

    @Test
    void leaderboardRespectsTheLimit() throws Exception {
        Session s = registerAndLogin("User1", 1);
        registerAndLogin("User2", 2);
        registerAndLogin("User3", 3);
        assertEquals(2, auth.leaderboard(s.getToken(), 2).size());
    }

    @Test
    void leaderboardNeedsAValidSession() throws Exception {
        assertThrows(AuthException.class, () -> auth.leaderboard("made-up-token", 10));
        assertThrows(AuthException.class, () -> auth.leaderboard(null, 10));
    }
}