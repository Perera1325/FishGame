package com.perera.fishgame.model;

import java.awt.image.BufferedImage;

/**
 * One round of the Fish Game: a picture of fish and the number of fish in it.
 *
 * <p>This is a plain data class (a "model"). It has no knowledge of the web
 * service that produced it or of the GUI that will display it, which keeps
 * coupling low.
 *
 * <p>Author: Vinod Perera (own work; idea of image + solution taken from the
 * unit's example code by Marc Conrad).
 */
public class Game {

    private final BufferedImage image;
    private final int solution;

    public Game(BufferedImage image, int solution) {
        if (image == null) {
            throw new IllegalArgumentException("image must not be null");
        }
        this.image = image;
        this.solution = solution;
    }

    /** The picture to show to the player. */
    public BufferedImage getImage() {
        return image;
    }

    /** The correct number of fish. */
    public int getSolution() {
        return solution;
    }
}