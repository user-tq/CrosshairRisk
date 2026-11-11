package com.crosshair.risk;

final class CrosshairRegionDetector {
    static final class Region {
        final int left;
        final int top;
        final int right;
        final int bottom;
        final int score;

        Region(int left, int top, int right, int bottom, int score) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.score = score;
        }

        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }
    }

    private CrosshairRegionDetector() {
    }

    static Region find(int[] pixels, int width, int height) {
        if (pixels == null || width <= 0 || height <= 0 || pixels.length != width * height) {
            return null;
        }
        int scanRight = Math.min(width, Math.max(1, width / 5));
        int chartTop = height / 4;
        int chartBottom = height * 9 / 10;
        int minimumRowPixels = Math.max(6, scanRight / 12);
        Region best = null;
        int runStart = -1;

        for (int y = chartTop; y <= chartBottom; y++) {
            boolean matches = y < chartBottom
                    && bluePixelCount(pixels, width, y, scanRight) >= minimumRowPixels;
            if (matches) {
                if (runStart < 0) {
                    runStart = y;
                }
            } else if (runStart >= 0) {
                Region candidate = regionForRun(pixels, width, height, scanRight,
                        runStart, y);
                if (candidate != null && (best == null || candidate.score > best.score)) {
                    best = candidate;
                }
                runStart = -1;
            }
        }
        return best;
    }

    private static int bluePixelCount(int[] pixels, int width, int y, int scanRight) {
        int count = 0;
        int row = y * width;
        for (int x = 0; x < scanRight; x++) {
            if (isLabelBlue(pixels[row + x])) {
                count++;
            }
        }
        return count;
    }

    private static Region regionForRun(int[] pixels, int screenWidth, int screenHeight,
                                       int scanRight, int startY, int endY) {
        int minX = scanRight;
        int minY = endY;
        int maxX = -1;
        int maxY = -1;
        int count = 0;
        for (int y = startY; y < endY; y++) {
            int row = y * screenWidth;
            for (int x = 0; x < scanRight; x++) {
                if (isLabelBlue(pixels[row + x])) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    count++;
                }
            }
        }
        if (maxX < 0) {
            return null;
        }
        int regionWidth = maxX - minX + 1;
        int regionHeight = maxY - minY + 1;
        int minimumWidth = Math.max(20, screenWidth / 40);
        int minimumHeight = Math.max(8, screenHeight / 320);
        int maximumHeight = Math.max(48, screenHeight / 18);
        if (regionWidth < minimumWidth || regionHeight < minimumHeight
                || regionHeight > maximumHeight || regionWidth < regionHeight * 1.2f
                || minX > screenWidth * 0.08f
                || count * 4L < regionWidth * (long) regionHeight) {
            return null;
        }
        return new Region(minX, minY, maxX, maxY, count);
    }

    private static boolean isLabelBlue(int pixel) {
        int alpha = (pixel >> 24) & 0xff;
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = pixel & 0xff;
        return alpha >= 128 && red < 120 && green < 170 && blue > 90
                && blue > red + 30 && blue > green + 15;
    }
}
