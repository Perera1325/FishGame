package com.perera.fishgame.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import com.perera.fishgame.model.Game;

/**
 * Turns the Fish API's CSV response ("base64Image,solution") into a
 * {@link Game}. It is kept separate from the HTTP code (high cohesion): this
 * class only knows the data format, so it can be tested without a network.
 *
 * <p>The parser is deliberately tolerant (robustness principle): the response
 * may contain more than two comma-separated parts, so it looks for the first
 * part that decodes to an image and then takes the first number after it as the
 * solution. Anything else is ignored.
 */
public final class FishApiParser {

    private FishApiParser() {
    }

    /**
     * @param csv raw response body, e.g. {@code "iVBORw0K...,4"}
     * @throws GameProviderException if the text is not in the expected format
     */
    public static Game parse(String csv) throws GameProviderException {
        if (csv == null || csv.isBlank()) {
            throw new GameProviderException("Empty response from game service");
        }
        String[] parts = csv.trim().split(",");
        if (parts.length < 2) {
            throw new GameProviderException(
                    "Unexpected response format: expected at least 2 fields but got "
                            + parts.length);
        }
        for (int i = 0; i < parts.length - 1; i++) {
            BufferedImage image = tryDecodeImage(parts[i].trim());
            if (image == null) {
                continue; // not the image part, keep looking
            }
            for (int j = i + 1; j < parts.length; j++) {
                Integer solution = tryParseInt(parts[j].trim());
                if (solution != null) {
                    return new Game(image, solution);
                }
            }
            throw new GameProviderException("Response contained an image but no solution");
        }
        throw new GameProviderException("Response did not contain a readable image");
    }

    /** Returns the decoded image, or null if the text is not a Base64 image. */
    private static BufferedImage tryDecodeImage(String text) {
        try {
            byte[] bytes = Base64.getDecoder().decode(text);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IllegalArgumentException | IOException e) {
            return null;
        }
    }

    /** Returns the number, or null if the text is not an integer. */
    private static Integer tryParseInt(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}