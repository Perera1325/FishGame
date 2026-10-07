package com.perera.fishgame;

import javax.swing.SwingUtilities;

import com.perera.fishgame.engine.GameEngine;
import com.perera.fishgame.service.FishApiClient;
import com.perera.fishgame.ui.GameWindow;

/**
 * Application entry point. It is the only place that knows which concrete
 * classes are used: it creates the real web client, gives it to the engine
 * (dependency injection) and gives the engine to the window.
 */
public class Main {

    public static void main(String[] args) {
        // Swing windows must be created on the Event Dispatch Thread.
        SwingUtilities.invokeLater(() -> {
            GameEngine engine = new GameEngine(new FishApiClient());
            GameWindow window = new GameWindow(engine);
            window.setVisible(true);
            window.startGame();
        });
    }
}