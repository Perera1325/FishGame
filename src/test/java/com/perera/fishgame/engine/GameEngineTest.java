package com.perera.fishgame.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.perera.fishgame.model.Game;
import com.perera.fishgame.service.GameProvider;
import com.perera.fishgame.service.GameProviderException;

/**
 * The engine is tested with a fake provider: no internet needed. This works
 * only because the engine depends on the GameProvider interface (low coupling).
 */
class GameEngineTest {

    /** Always returns a game whose answer is 5; can be told to fail. */
    private static class FakeProvider implements GameProvider {
        boolean failing = false;

        @Override
        public Game nextGame() throws GameProviderException {
            if (failing) {
                throw new GameProviderException("service down");
            }
            return new Game(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), 5);
        }
    }

    /** Records every event it receives, in order. */
    private static class Recorder implements GameListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onRoundStarted(Game game) {
            events.add("round");
        }

        @Override
        public void onScoreChanged(int s) {
            events.add("score=" + s);
        }

        @Override
        public void onLivesChanged(int l) {
            events.add("lives=" + l);
        }

        @Override
        public void onGameOver(int f) {
            events.add("over=" + f);
        }
    }

    private FakeProvider provider;
    private GameEngine engine;

    @BeforeEach
    void setUp() {
        provider = new FakeProvider();
        engine = new GameEngine(provider);
    }

    @Test
    void startsWithZeroScoreAndFullLives() {
        assertEquals(0, engine.getScore());
        assertEquals(GameEngine.STARTING_LIVES, engine.getLives());
        assertFalse(engine.isGameOver());
    }

    @Test
    void correctAnswerIncreasesScore() throws Exception {
        engine.nextRound();
        assertEquals(AnswerResult.CORRECT, engine.submitAnswer(5));
        assertEquals(1, engine.getScore());
        assertEquals(GameEngine.STARTING_LIVES, engine.getLives());
    }

    @Test
    void wrongAnswerCostsALifeButNotScore() throws Exception {
        engine.nextRound();
        assertEquals(AnswerResult.WRONG, engine.submitAnswer(9));
        assertEquals(0, engine.getScore());
        assertEquals(GameEngine.STARTING_LIVES - 1, engine.getLives());
    }

    @Test
    void lastWrongAnswerEndsTheGame() throws Exception {
        engine.nextRound();
        engine.submitAnswer(0);
        engine.submitAnswer(0);
        assertEquals(AnswerResult.GAME_OVER, engine.submitAnswer(0));
        assertTrue(engine.isGameOver());
    }

    @Test
    void cannotAnswerOrContinueAfterGameOver() throws Exception {
        engine.nextRound();
        for (int i = 0; i < GameEngine.STARTING_LIVES; i++) {
            engine.submitAnswer(0);
        }
        assertThrows(IllegalStateException.class, () -> engine.submitAnswer(5));
        assertThrows(IllegalStateException.class, () -> engine.nextRound());
    }

    @Test
    void cannotAnswerBeforeARoundIsLoaded() {
        assertThrows(IllegalStateException.class, () -> engine.submitAnswer(5));
    }

    @Test
    void providerFailureIsReportedAndStateIsKept() throws Exception {
        engine.nextRound();
        engine.submitAnswer(5);
        provider.failing = true;
        assertThrows(GameProviderException.class, () -> engine.nextRound());
        assertEquals(1, engine.getScore());
        assertEquals(5, engine.getCurrentGame().getSolution());
    }

    @Test
    void restartResetsEverything() throws Exception {
        engine.nextRound();
        engine.submitAnswer(5);
        engine.submitAnswer(0);
        engine.restart();
        assertEquals(0, engine.getScore());
        assertEquals(GameEngine.STARTING_LIVES, engine.getLives());
        assertEquals(null, engine.getCurrentGame());
    }

    @Test
    void listenersReceiveEventsInOrder() throws Exception {
        Recorder rec = new Recorder();
        engine.addListener(rec);
        engine.nextRound();
        engine.submitAnswer(5);
        engine.submitAnswer(0);
        engine.submitAnswer(0);
        engine.submitAnswer(0);
        assertEquals(List.of("round", "score=1", "lives=2", "lives=1", "lives=0", "over=1"),
                rec.events);
    }

    @Test
    void removedListenerGetsNoMoreEvents() throws Exception {
        Recorder rec = new Recorder();
        engine.addListener(rec);
        engine.removeListener(rec);
        engine.nextRound();
        assertTrue(rec.events.isEmpty());
    }

    @Test
    void nullProviderIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new GameEngine(null));
    }
}