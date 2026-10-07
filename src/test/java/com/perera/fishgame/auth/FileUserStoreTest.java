package com.perera.fishgame.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileUserStoreTest {

    private Path file;

    @BeforeEach
    void setUp() throws Exception {
        Path dir = Files.createTempDirectory("fishgame-test");
        file = dir.resolve("users.db");
        file.toFile().deleteOnExit();
        dir.toFile().deleteOnExit();
    }

    @Test
    void createdUserCanBeFoundIgnoringCase() throws Exception {
        FileUserStore store = new FileUserStore(file);
        assertTrue(store.create(new User("Vinod", "hash1", 0)));
        assertEquals("Vinod", store.find("vinod").orElseThrow().username());
        assertFalse(store.find("someoneelse").isPresent());
    }

    @Test
    void duplicateNamesAreRefusedEvenWithDifferentCase() throws Exception {
        FileUserStore store = new FileUserStore(file);
        assertTrue(store.create(new User("Vinod", "hash1", 0)));
        assertFalse(store.create(new User("VINOD", "hash2", 0)));
        assertEquals("hash1", store.find("vinod").orElseThrow().passwordHash());
    }

    @Test
    void usersSurviveRestart() throws Exception {
        FileUserStore first = new FileUserStore(file);
        first.create(new User("Vinod", "hash1", 0));
        first.updateBestScore("Vinod", 7);

        FileUserStore second = new FileUserStore(file);
        User loaded = second.find("Vinod").orElseThrow();
        assertEquals("hash1", loaded.passwordHash());
        assertEquals(7, loaded.bestScore());
    }

    @Test
    void bestScoreOnlyGoesUp() throws Exception {
        FileUserStore store = new FileUserStore(file);
        store.create(new User("Vinod", "h", 0));
        store.updateBestScore("Vinod", 5);
        store.updateBestScore("Vinod", 3);
        assertEquals(5, store.find("Vinod").orElseThrow().bestScore());
    }

    @Test
    void unknownUserCannotGetAScore() throws Exception {
        FileUserStore store = new FileUserStore(file);
        assertThrows(AuthException.class, () -> store.updateBestScore("ghost", 1));
    }

    @Test
    void namesThatCouldBreakTheFileFormatAreRefused() throws Exception {
        FileUserStore store = new FileUserStore(file);
        assertThrows(AuthException.class, () -> store.create(new User("a\tb", "h", 0)));
        assertThrows(AuthException.class, () -> store.create(new User("a\nb", "h", 0)));
    }

    @Test
    void damagedFileIsReportedNotIgnored() throws Exception {
        Files.writeString(file, "only-one-field\n");
        assertThrows(AuthException.class, () -> new FileUserStore(file));
    }

    @Test
    void allReturnsEveryStoredUser() throws Exception {
        FileUserStore store = new FileUserStore(file);
        store.create(new User("Vinod", "h1", 3));
        store.create(new User("Kasun", "h2", 7));
        assertEquals(2, store.all().size());
        assertEquals(2, new FileUserStore(file).all().size()); // survives a restart
    }
}