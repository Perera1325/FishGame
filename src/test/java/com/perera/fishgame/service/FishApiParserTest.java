package com.perera.fishgame.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.perera.fishgame.model.Game;

class FishApiParserTest {

    private static String tinyPngBase64() throws IOException {
        BufferedImage img = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void parsesValidResponse() throws Exception {
        Game g = FishApiParser.parse(tinyPngBase64() + ",7");
        assertEquals(7, g.getSolution());
        assertEquals(4, g.getImage().getWidth());
        assertEquals(3, g.getImage().getHeight());
    }

    @Test
    void toleratesWhitespaceAroundResponse() throws Exception {
        Game g = FishApiParser.parse("  " + tinyPngBase64() + ",2\n");
        assertEquals(2, g.getSolution());
    }

    @Test
    void toleratesAnExtraTrailingField() throws Exception {
        Game g = FishApiParser.parse(tinyPngBase64() + ",6,extra");
        assertEquals(6, g.getSolution());
    }

    @Test
    void skipsAFieldBeforeTheImage() throws Exception {
        Game g = FishApiParser.parse("data:image/png;base64," + tinyPngBase64() + ",9");
        assertEquals(9, g.getSolution());
    }

    @Test
    void rejectsNullAndEmpty() {
        assertThrows(GameProviderException.class, () -> FishApiParser.parse(null));
        assertThrows(GameProviderException.class, () -> FishApiParser.parse("   "));
    }

    @Test
    void rejectsWrongNumberOfFields() {
        assertThrows(GameProviderException.class, () -> FishApiParser.parse("onlyonefield"));
        assertThrows(GameProviderException.class, () -> FishApiParser.parse("a,b,c"));
    }

    @Test
    void rejectsBadBase64() {
        assertThrows(GameProviderException.class, () -> FishApiParser.parse("!!!notbase64!!!,3"));
    }

    @Test
    void rejectsDataThatIsNotAnImage() {
        String notAnImage = Base64.getEncoder().encodeToString("hello".getBytes());
        assertThrows(GameProviderException.class, () -> FishApiParser.parse(notAnImage + ",3"));
    }

    @Test
    void rejectsNonNumericSolution() throws Exception {
        String csv = tinyPngBase64() + ",abc";
        assertThrows(GameProviderException.class, () -> FishApiParser.parse(csv));
    }
}