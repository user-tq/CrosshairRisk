package com.crosshair.risk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class CrosshairRegionDetectorTest {
    private static final int BACKGROUND = 0xff101010;
    private static final int LABEL_BLUE = 0xff245a96;

    @Test
    public void findsDenseBlueLabelAtLeftEdge() {
        int[] pixels = pixels(240, 600);
        rectangle(pixels, 240, 4, 280, 40, 305, LABEL_BLUE);

        CrosshairRegionDetector.Region region = CrosshairRegionDetector.find(pixels, 240, 600);

        assertNotNull(region);
        assertEquals(4, region.left);
        assertEquals(40, region.right);
        assertEquals(280, region.top);
        assertEquals(305, region.bottom);
    }

    @Test
    public void ignoresThinBlueChartLine() {
        int[] pixels = pixels(240, 600);
        rectangle(pixels, 240, 0, 280, 45, 282, LABEL_BLUE);

        assertNull(CrosshairRegionDetector.find(pixels, 240, 600));
    }

    @Test
    public void ignoresLabelAboveChartArea() {
        int[] pixels = pixels(240, 600);
        rectangle(pixels, 240, 4, 90, 40, 115, LABEL_BLUE);

        assertNull(CrosshairRegionDetector.find(pixels, 240, 600));
    }

    @Test
    public void ignoresCyanVolumeBars() {
        int[] pixels = pixels(240, 600);
        rectangle(pixels, 240, 4, 280, 40, 305, 0xff18c8e0);

        assertNull(CrosshairRegionDetector.find(pixels, 240, 600));
    }

    private static int[] pixels(int width, int height) {
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, BACKGROUND);
        return pixels;
    }

    private static void rectangle(int[] pixels, int width, int left, int top,
                                  int right, int bottom, int color) {
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                pixels[y * width + x] = color;
            }
        }
    }
}
