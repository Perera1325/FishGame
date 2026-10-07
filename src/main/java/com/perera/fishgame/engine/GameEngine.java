package com.perera.fishgame.engine;

import java.util.ArrayList;
import java.util.List;

import com.perera.fishgame.model.Game;
import com.perera.fishgame.service.GameProvider;
import com.perera.fishgame.service.GameProviderException;

/**
 * The rules of the game: current round, score and lives.
 *
 * <p>Design notes (for the video):
 * <ul>
 * <li><b>Low coupling:</b> the engine receives a {@link GameProvider} through its
 * constructor (dependency injection). It never creates the web client itself and
 * has no Swing/GUI code.</li>
 * <li><b>High cohesion:</b> it only handles game rules. Fetching data is in
 * {@code service}, data format in {@code FishApiParser}, display will be in
 * the GUI package.</li>
 * <li><b>Events:</b> state changes are announced to {@link GameListener}s.</li>
 * </ul>
 *
 * <p>Author: Vinod Perera. (The unit's example engine only had score and
 * checkSolution; lives, events and injection are my own additions.)
 */
public class GameEngine {

    public static final int STARTING_LIVES = 3;

    private final GameProvider provider;
    private final List<GameListener> listeners = new ArrayList<>();

    private Game current;
    private int score;
    private int lives = STARTING_LIVES;

    public GameEngine(GameProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        this.provider = provider;
    }

    public void addListener(GameListener listener) {
        listeners.add(listener);
    }

    public void removeListener(GameListener listener) {
        listeners.remove(listener);
    }

    /**
     * Loads the next round.
     *
     * @throws GameProviderException if the game service failed; the previous
     *                               round (if any) is kept
     * @throws IllegalStateException if the game is already over
     */
    public Game nextRound() throws GameProviderException {
        if (isGameOver()) {
            throw new IllegalStateException("Game is over; call restart() first");
        }
        Game game = provider.nextGame();
        current = game;
        listeners.forEach(l -> l.onRoundStarted(game));
        return game;
    }

    /**
     * Checks the player's answer against the current round.
     *
     * @throws IllegalStateException if there is no round or the game is over
     */
    public AnswerResult submitAnswer(int answer) {
        requireRoundInProgress();
        if (answer == current.getSolution()) {
            score++;
            listeners.forEach(l -> l.onScoreChanged(score));
            return AnswerResult.CORRECT;
        }
        return loseLife();
    }

    /**
     * The player ran out of time for this round. It counts as a wrong answer:
     * one life is lost. The timer itself lives in the GUI; the engine only
     * applies the rule, so it stays free of any Swing code.
     *
     * @return {@link AnswerResult#WRONG}, or {@link AnswerResult#GAME_OVER} if
     *         that was the last life
     * @throws IllegalStateException if there is no round or the game is over
     */
    public AnswerResult timeOut() {
        requireRoundInProgress();
        return loseLife();
    }

    private void requireRoundInProgress() {
        if (current == null) {
            throw new IllegalStateException("No round in progress; call nextRound() first");
        }
        if (isGameOver()) {
            throw new IllegalStateException("Game is over; call restart() first");
        }
    }

    private AnswerResult loseLife() {
        lives--;
        listeners.forEach(l -> l.onLivesChanged(lives));
        if (lives == 0) {
            listeners.forEach(l -> l.onGameOver(score));
            return AnswerResult.GAME_OVER;
        }
        return AnswerResult.WRONG;
    }

    /** Starts again with zero score and full lives. No round is loaded yet. */
    public void restart() {
        score = 0;
        lives = STARTING_LIVES;
        current = null;
        listeners.forEach(l -> {
            l.onScoreChanged(score);
            l.onLivesChanged(lives);
        });
    }

    public int getScore() {
        return score;
    }

    public int getLives() {
        return lives;
    }

    public boolean isGameOver() {
        return lives <= 0;
    }

    public Game getCurrentGame() {
        return current;
    }
}