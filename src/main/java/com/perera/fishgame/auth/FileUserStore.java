package com.perera.fishgame.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps users in a small tab-separated text file: {@code username, hash,
 * bestScore} per line. It needs no extra library, which keeps the project easy
 * to build, and the hashes are the only secret it contains. The file is written
 * to a temporary file first and then moved over the old one, so a crash cannot
 * leave a half-written user file.
 *
 * <p>For a real system a database would be the better choice (concurrent
 * access, indexes, backups); that is why this class sits behind the
 * {@link UserStore} interface.
 */
public final class FileUserStore implements UserStore {

    private final Path file;
    private final Map<String, User> users = new LinkedHashMap<>(); // key: lower-case name

    public FileUserStore(Path file) throws AuthException {
        this.file = file;
        load();
    }

    @Override
    public synchronized Optional<User> find(String username) {
        return Optional.ofNullable(users.get(key(username)));
    }

    @Override
    public synchronized boolean create(User user) throws AuthException {
        String name = user.username();
        if (name.contains("\t") || name.contains("\n") || name.contains("\r")) {
            throw new AuthException("Invalid username.");
        }
        String k = key(name);
        if (users.containsKey(k)) {
            return false;
        }
        users.put(k, user);
        try {
            save();
        } catch (AuthException e) {
            users.remove(k); // keep memory and file in step
            throw e;
        }
        return true;
    }

    @Override
    public synchronized List<User> all() {
        return new ArrayList<>(users.values());
    }

    @Override
    public synchronized void updateBestScore(String username, int score) throws AuthException {
        User user = users.get(key(username));
        if (user == null) {
            throw new AuthException("Unknown user.");
        }
        if (score <= user.bestScore()) {
            return;
        }
        users.put(key(username), new User(user.username(), user.passwordHash(), score));
        save();
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private void load() throws AuthException {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] p = line.split("\t", -1);
                if (p.length != 3) {
                    throw new AuthException("The user file is damaged.");
                }
                users.put(key(p[0]), new User(p[0], p[1], Integer.parseInt(p[2])));
            }
        } catch (IOException | NumberFormatException e) {
            throw new AuthException("Could not read the user file.", e);
        }
    }

    private void save() throws AuthException {
        List<String> lines = new ArrayList<>();
        for (User u : users.values()) {
            lines.add(u.username() + "\t" + u.passwordHash() + "\t" + u.bestScore());
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new AuthException("Could not save the user file.", e);
        }
    }
}