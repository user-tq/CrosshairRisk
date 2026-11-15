package com.crosshair.risk;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class OfflineScreenshotRecognizerTest {
    private static final int BLUE = 0xff245a96;
    private static final int WHITE = 0xffffffff;
    private static final int BLACK = 0xff000000;

    @Test
    public void binarizesWhiteTextOnBlueBackground() {
        int width = 40;
        int height = 20;
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = BLUE;
        }
        // Draw a white "text" rectangle in the middle
        for (int y = 5; y <= 14; y++) {
            for (int x = 8; x <= 30; x++) {
                pixels[y * width + x] = WHITE;
            }
        }

        int[] bounds = OfflineScreenshotRecognizer.binarizeAndFindTextBounds(
                pixels, width, height);

        assertArrayEquals(new int[]{8, 5, 30, 14}, bounds);
        // Text pixels should be black
        assertEquals(BLACK, pixels[5 * width + 8]);
        assertEquals(BLACK, pixels[14 * width + 30]);
        // Background should be white
        assertEquals(0xffffffff, pixels[0]);
        assertEquals(0xffffffff, pixels[19 * width + 39]);
    }

    @Test
    public void returnsNullWhenNoTextPixels() {
        int width = 20;
        int height = 10;
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = BLUE;
        }

        int[] bounds = OfflineScreenshotRecognizer.binarizeAndFindTextBounds(
                pixels, width, height);

        assertNull(bounds);
        // All pixels should be white (background)
        assertEquals(0xffffffff, pixels[0]);
        assertEquals(0xffffffff, pixels[width * height - 1]);
    }

    @Test
    public void rejectsDimPixelsAsBackground() {
        int width = 10;
        int height = 5;
        int[] pixels = new int[width * height];
        // Dim gray (luminance ~80, below threshold 140)
        int dim = 0xff505050;
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = dim;
        }

        int[] bounds = OfflineScreenshotRecognizer.binarizeAndFindTextBounds(
                pixels, width, height);

        assertNull(bounds);
    }
}
