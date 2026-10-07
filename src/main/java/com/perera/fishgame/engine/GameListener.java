package com.perera.fishgame.engine;

/**
 * Event-driven programming: the engine does not know about the GUI. Instead it
 * <em>fires events</em> to any registered listeners (Observer pattern), and the
 * GUI (added in a later stage) reacts to them. Default methods let a listener
 * handle only the events it cares about.
 */
public interface GameListener {

    /** A new round has been loaded. */
    default void onRoundStarted(com.perera.fishgame.model.Game game) {
    }

    /** The score changed. */
    default void onScoreChanged(int newScore) {
    }

    /** The number of lives changed. */
    default void onLivesChanged(int livesLeft) {
    }

    /** The last life was lost. */
    default void onGameOver(int finalScore) {
    }
}