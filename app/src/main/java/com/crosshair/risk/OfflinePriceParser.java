package com.crosshair.risk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OfflinePriceParser {
    private static final Pattern PRICE = Pattern.compile(
            "(?<![\\d.])\\d+\\.\\d{1,6}(?![\\d.])");

    private OfflinePriceParser() {
    }

    static String parse(String rawText) {
        if (rawText == null) {
            return null;
        }
        String normalized = rawText.replace('，', '.').replace(',', '.')
                .replace('。', '.').replaceAll("\\s+", " ")
                .replaceAll("\\s*\\.\\s*", ".");
        Matcher matcher = PRICE.matcher(normalized);
        String price = null;
        while (matcher.find()) {
            String candidate = matcher.group();
            if (price != null && !price.equals(candidate)) {
                return null;
            }
            price = candidate;
        }
        return price;
    }
}
