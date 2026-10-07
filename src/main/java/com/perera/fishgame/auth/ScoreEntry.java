package com.perera.fishgame.auth;

/** One line of the leaderboard. Deliberately has no password hash. */
public record ScoreEntry(String username, int bestScore) {
}