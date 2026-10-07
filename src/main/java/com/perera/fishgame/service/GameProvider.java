package com.perera.fishgame.service;

import com.perera.fishgame.model.Game;

/**
 * Anything that can supply Fish Game rounds.
 *
 * <p>The engine depends only on this interface (dependency inversion), so the
 * real web service can be replaced by a fake one in unit tests, or by another
 * source in future, without changing the engine.
 */
public interface GameProvider {

    /**
     * Gets the next game.
     *
     * @throws GameProviderException if no game could be obtained
     */
    Game nextGame() throws GameProviderException;
}