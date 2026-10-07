package com.perera.fishgame;

import java.nio.file.Path;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import com.perera.fishgame.auth.AuthException;
import com.perera.fishgame.auth.AuthService;
import com.perera.fishgame.auth.FileUserStore;
import com.perera.fishgame.auth.Session;
import com.perera.fishgame.engine.GameEngine;
import com.perera.fishgame.service.FishApiClient;
import com.perera.fishgame.ui.GameWindow;
import com.perera.fishgame.ui.LoginWindow;

/**
 * Application entry point. It is the only place that knows which concrete
 * classes are used, and it wires them together (dependency injection):
 * user file -> authentication service -> login window -> game window, with the
 * real Fish API client inside the game engine.
 */
public class Main {

    /** Accounts are kept here (ignored by Git: it contains password hashes). */
    private static final Path USER_FILE = Path.of("fishgame-users.db");

    public static void main(String[] args) {
        // Swing windows must be created on the Event Dispatch Thread.
        SwingUtilities.invokeLater(Main::showLogin);
    }

    private static void showLogin() {
        try {
            AuthService auth = new AuthService(new FileUserStore(USER_FILE));
            new LoginWindow(auth, session -> startGame(auth, session)).setVisible(true);
        } catch (AuthException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Cannot start",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void startGame(AuthService auth, Session session) {
        int best = 0;
        try {
            best = auth.bestScore(session.getToken());
        } catch (AuthException e) {
            // Could not read the best score: start from zero.
        }
        GameEngine engine = new GameEngine(new FishApiClient());
        GameWindow window = new GameWindow(engine, session.getUsername(), best,
                score -> saveScore(auth, session, score));
        window.setVisible(true);
        window.startGame();
    }

    private static void saveScore(AuthService auth, Session session, int score) {
        try {
            auth.recordScore(session.getToken(), score);
        } catch (AuthException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Score not saved",
                    JOptionPane.WARNING_MESSAGE);
        }
    }
}