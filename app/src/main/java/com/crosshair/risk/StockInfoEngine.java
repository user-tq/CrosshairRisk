package com.crosshair.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts the stock name and six digit code from page elements or OCR text. */
public final class StockInfoEngine {
    private static final Pattern CODE_PATTERN = Pattern.compile("(?<!\\d)([0-9]{6})(?!\\d)");
    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern DATE_OR_TIME = Pattern.compile(
            "\\d{4}\\s*[/\\-.]\\s*\\d{1,2}\\s*[/\\-.]\\s*\\d{1,2}|\\d{1,2}:\\d{2}");
    private static final Pattern BOUNDS = Pattern.compile(
            "\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]");
    private static final String[] NAME_EXCLUSIONS = {
            "指数", "今开", "昨收", "最高", "最低", "成交", "量比", "振幅", "溢价",
            "换手", "总额", "总量", "涨幅", "跌幅", "时间", "日期", "盘后", "均价",
            "买一", "卖一", "五档", "自选", "板块", "公告", "研报", "诊断", "复制"
    };
    private static final String CODE_FIRST_DIGITS = "0134568";

    private StockInfoEngine() {
    }

    public static Result find(List<PriceCaptureEngine.Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new Result(null, null);
        }
        List<Entry> entries = new ArrayList<>();
        int screenWidth = 0;
        int screenHeight = 0;
        for (PriceCaptureEngine.Node node : nodes) {
            if (node == null || !node.visibleToUser) {
                continue;
            }
            String text = clean(node.text);
            if (text.isEmpty()) {
                text = clean(node.contentDescription);
            }
            if (text.isEmpty()) {
                continue;
            }
            int[] bounds = parseBounds(node.bounds);
            if (bounds != null) {
                screenWidth = Math.max(screenWidth, bounds[2]);
                screenHeight = Math.max(screenHeight, bounds[3]);
            }
            entries.add(new Entry(text, node.viewId, bounds));
        }
        if (screenWidth <= 0) {
            screenWidth = 1080;
        }
        if (screenHeight <= 0) {
            screenHeight = 2400;
        }
        String code = chooseCode(entries, screenHeight);
        String name = chooseName(entries, screenWidth, screenHeight, code);
        return new Result(name, code);
    }

    public static Result fromText(String text) {
        if (text == null) {
            return new Result(null, null);
        }
        String code = null;
        String name = null;
        int nameScore = Integer.MIN_VALUE;
        for (String rawLine : text.split("[\\r\\n]+")) {
            String line = clean(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            if (code == null) {
                Matcher matcher = CODE_PATTERN.matcher(line);
                while (matcher.find()) {
                    if (isStockCode(matcher.group(1))) {
                        code = matcher.group(1);
                        break;
                    }
                }
            }
            if (isNameCandidate(line)) {
                int score = nameScore(line);
                if (score > nameScore) {
                    nameScore = score;
                    name = line;
                }
            }
        }
        if (name != null && code != null && name.contains(code)) {
            name = clean(name.replace(code, ""));
        }
        if (name != null && (name.length() < 2 || !CJK.matcher(name).find())) {
            name = null;
        }
        return new Result(name, code);
    }

    private static int nameScore(String name) {
        int score = 0;
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.contains("ETF") || upper.contains("LOF") || upper.contains("REIT")
                || name.contains("基金") || name.contains("转债")) {
            score += 3;
        }
        if (name.length() >= 4) {
            score += 1;
        }
        if (name.length() <= 3) {
            score -= 3;
        }
        if (name.matches(".*\\d.*")) {
            score += 2;
        }
        return score;
    }

    private static String chooseCode(List<Entry> entries, int screenHeight) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        boolean ambiguous = false;
        for (Entry entry : entries) {
            Matcher matcher = CODE_PATTERN.matcher(entry.text);
            while (matcher.find()) {
                String value = matcher.group(1);
                if (!isStockCode(value)) {
                    continue;
                }
                int score = 0;
                if (entry.top < screenHeight * 0.35f) {
                    score += 6;
                }
                String id = lower(entry.viewId);
                if (id.contains("code") || id.contains("symbol")) {
                    score += 5;
                }
                if (entry.text.equals(value)) {
                    score += 2;
                }
                if (score > bestScore) {
                    best = value;
                    bestScore = score;
                    ambiguous = false;
                } else if (score == bestScore && !value.equals(best)) {
                    ambiguous = true;
                }
            }
        }
        return ambiguous ? null : best;
    }

    private static String chooseName(List<Entry> entries, int screenWidth, int screenHeight,
                                     String code) {
        Entry codeEntry = null;
        if (code != null) {
            for (Entry entry : entries) {
                if (entry.text.contains(code)) {
                    codeEntry = entry;
                    break;
                }
            }
        }
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Entry entry : entries) {
            String name = clean(entry.text);
            if (!isNameCandidate(name)) {
                continue;
            }
            int score = 0;
            if (entry.top < screenHeight * 0.22f) {
                score += 5;
            } else if (entry.top < screenHeight * 0.4f) {
                score += 1;
            } else {
                continue;
            }
            String id = lower(entry.viewId);
            if (id.contains("name") || id.contains("title") || id.contains("stock")) {
                score += 4;
            }
            String upper = name.toUpperCase(Locale.ROOT);
            if (upper.contains("ETF") || upper.contains("LOF") || upper.contains("REIT")
                    || name.contains("基金") || name.contains("转债")) {
                score += 3;
            }
            int center = (entry.left + entry.right) / 2;
            if (center > screenWidth * 0.25f && center < screenWidth * 0.75f) {
                score += 2;
            }
            if (name.length() <= 14) {
                score += 1;
            }
            if (codeEntry != null && entry != codeEntry && entry.bottom <= codeEntry.top
                    && codeEntry.top - entry.bottom <= Math.max(screenHeight / 30, entry.height() * 3)
                    && entry.left < codeEntry.right && entry.right > codeEntry.left) {
                score += 6;
            }
            if (score > bestScore) {
                bestScore = score;
                best = name;
            }
        }
        return best;
    }

    private static boolean isNameCandidate(String value) {
        if (value == null) {
            return false;
        }
        String name = value.trim();
        if (name.length() < 2 || name.length() > 24) {
            return false;
        }
        if (!CJK.matcher(name).find()) {
            return false;
        }
        if (DATE_OR_TIME.matcher(name).find()) {
            return false;
        }
        if (CODE_PATTERN.matcher(name).find()) {
            return false;
        }
        if (name.contains(":") || name.contains("：") || name.contains("%")
                || name.contains("万") || name.contains("亿") || name.contains("元")
                || name.contains(">") || name.contains("<")) {
            return false;
        }
        for (String excluded : NAME_EXCLUSIONS) {
            if (name.contains(excluded)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStockCode(String value) {
        return value != null && value.length() == 6
                && CODE_FIRST_DIGITS.indexOf(value.charAt(0)) >= 0;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').trim();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static int[] parseBounds(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = BOUNDS.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new int[]{
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(4))
            };
        } catch (NumberFormatException error) {
            return null;
        }
    }

    public static final class Result {
        public final String name;
        public final String code;

        Result(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }

    private static final class Entry {
        final String text;
        final String viewId;
        final int left;
        final int top;
        final int right;
        final int bottom;

        Entry(String text, String viewId, int[] bounds) {
            this.text = text;
            this.viewId = viewId;
            if (bounds == null) {
                left = 0;
                top = 0;
                right = 0;
                bottom = 0;
            } else {
                left = bounds[0];
                top = bounds[1];
                right = bounds[2];
                bottom = bounds[3];
            }
        }

        int height() {
            return Math.max(1, bottom - top);
        }
    }
}
