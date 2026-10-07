package com.perera.fishgame;

import com.perera.fishgame.model.Game;
import com.perera.fishgame.service.FishApiClient;
import com.perera.fishgame.service.GameProviderException;

/**
 * Stage 1 smoke test: fetches one game from the real Fish API and prints what
 * came back. Run it to check your internet connection and the API format.
 * It is replaced by the GUI start-up in a later stage.
 */
public class Main {

    public static void main(String[] args) {
        try {
            Game game = new FishApiClient().nextGame();
            System.out.println("Fetched a game from the Fish API.");
            System.out.println("Image size: " + game.getImage().getWidth()
                    + " x " + game.getImage().getHeight());
            System.out.println("Number of fish (solution): " + game.getSolution());
        } catch (GameProviderException e) {
            System.err.println("Could not get a game: " + e.getMessage());
        }
    }
}