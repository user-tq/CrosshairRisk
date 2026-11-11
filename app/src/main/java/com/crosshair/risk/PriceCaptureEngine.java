package com.crosshair.risk;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selects a crosshair price from exposed accessibility element metadata. */
public final class PriceCaptureEngine {
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\d.,])([0-9]+(?:\\.[0-9]+)?)(?![\\d.,%])"
    );
    private static final Pattern NUMERIC_ONLY = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");

    private static final String[] CROSSHAIR_WORDS = {
            "crosshair", "cross_price", "crossprice", "cursor_price", "cursorprice",
            "kline_cursor", "klincursor", "十字光标", "光标价", "光标价格"
    };
    private static final String[] EXCLUDED_WORDS = {
            "今开", "昨收", "最高", "最低", "开盘", "收盘", "成交量", "成交额", "振幅",
            "溢价", "换手", "总额", "总量", "量比", "涨幅", "跌幅", "至今", "日期",
            "时间", "volume", "vol", "ma5", "ma10", "ema", "h95", "h97", "l999"
    };

    private PriceCaptureEngine() {
    }

    public static Result capture(List<Node> nodes) {
        List<Candidate> candidates = new ArrayList<>();
        for (Node node : nodes) {
            if (node == null || !node.visibleToUser) {
                continue;
            }
            collect(node, preferText(node), candidates);
        }

        if (candidates.isEmpty()) {
            return Result.failure(Status.NO_PRICE,
                    "页面元素中没有可用的十字光标价格。该通达信版本可能使用 Canvas 自绘。",
                    candidateDiagnostics(candidates));
        }

        List<Candidate> semantic = new ArrayList<>();
        List<Candidate> focused = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.semantic) {
                semantic.add(candidate);
            }
            if (candidate.focused) {
                focused.add(candidate);
            }
        }

        Result semanticResult = uniquePrice(semantic, "十字光标语义元素");
        if (semanticResult != null) {
            return semanticResult.withDiagnostics(candidateDiagnostics(candidates));
        }
        Result focusedResult = uniquePrice(focused, "选中/焦点价格元素");
        if (focusedResult != null) {
            return focusedResult.withDiagnostics(candidateDiagnostics(candidates));
        }

        // Compatibility for versions exposing only one unlabeled numeric TextView.
        List<Candidate> numericOnly = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.numericOnly) {
                numericOnly.add(candidate);
            }
        }
        if (candidates.size() == 1 && numericOnly.size() == 1) {
            Candidate candidate = numericOnly.get(0);
            return Result.success(candidate.price, "唯一数值元素（首次使用请核对）",
                    candidateDiagnostics(candidates));
        }

        return Result.failure(Status.AMBIGUOUS,
                "发现 " + candidates.size() + " 个数值元素，无法可靠区分十字光标价，未采用任何数值。",
                candidateDiagnostics(candidates));
    }

    private static Result uniquePrice(List<Candidate> candidates, String source) {
        if (candidates.isEmpty()) {
            return null;
        }
        Map<String, Candidate> distinct = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            distinct.put(candidate.price, candidate);
        }
        if (distinct.size() != 1) {
            return Result.failure(Status.AMBIGUOUS,
                    "多个候选元素给出不同价格，未自动采用。", candidateDiagnostics(candidates));
        }
        Candidate candidate = distinct.values().iterator().next();
        return Result.success(candidate.price, source, candidateDiagnostics(candidates));
    }

    private static String preferText(Node node) {
        if (node.text != null && !node.text.trim().isEmpty()) {
            return node.text.trim();
        }
        return node.contentDescription == null ? "" : node.contentDescription.trim();
    }

    private static void collect(Node node, String text, List<Candidate> output) {
        if (text.isEmpty()) {
            return;
        }
        String searchable = (text + " " + nullToEmpty(node.viewId)).toLowerCase(Locale.ROOT);
        if (text.contains("/") && text.contains(":")) {
            return;
        }

        boolean semantic = containsAny(searchable, CROSSHAIR_WORDS);
        boolean excluded = containsAny(searchable, EXCLUDED_WORDS)
                || containsAny(nullToEmpty(node.viewId).toLowerCase(Locale.ROOT),
                "open", "high", "low", "close", "volume", "amount", "turnover");
        if (excluded && !semantic) {
            return;
        }

        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1);
            try {
                BigDecimal decimal = new BigDecimal(value);
                if (decimal.signum() <= 0 || decimal.scale() > 6) {
                    continue;
                }
            } catch (NumberFormatException ignored) {
                continue;
            }
            boolean focused = node.selected || node.accessibilityFocused;
            output.add(new Candidate(value, semantic, focused,
                    NUMERIC_ONLY.matcher(text).matches(), describe(node, text)));
        }
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(Node node, String text) {
        return "文本=" + text
                + "；id=" + nullToEmpty(node.viewId)
                + "；类=" + nullToEmpty(node.className)
                + "；区域=" + nullToEmpty(node.bounds);
    }

    public static String diagnostics(List<Node> nodes) {
        List<Candidate> candidates = new ArrayList<>();
        for (Node node : nodes) {
            if (node != null && node.visibleToUser) {
                collect(node, preferText(node), candidates);
            }
        }
        return candidateDiagnostics(candidates);
    }

    private static String candidateDiagnostics(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return "未发现可见数值节点";
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            result.append(result.length() == 0 ? "" : "\n")
                    .append(index + 1)
                    .append(". ")
                    .append(candidate.description);
        }
        return result.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public enum Status { SUCCESS, NO_PRICE, AMBIGUOUS }

    public static final class Result {
        public final Status status;
        public final String price;
        public final String message;
        public final String diagnostics;

        private Result(Status status, String price, String message, String diagnostics) {
            this.status = status;
            this.price = price;
            this.message = message;
            this.diagnostics = diagnostics;
        }

        static Result success(String price, String message, String diagnostics) {
            return new Result(Status.SUCCESS, price, message, diagnostics);
        }

        static Result failure(Status status, String message, String diagnostics) {
            return new Result(status, null, message, diagnostics);
        }

        Result withDiagnostics(String value) {
            return new Result(status, price, message, value);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }

    public static final class Node {
        public final String text;
        public final String contentDescription;
        public final String viewId;
        public final String className;
        public final String bounds;
        public final boolean visibleToUser;
        public final boolean selected;
        public final boolean accessibilityFocused;

        public Node(String text, String contentDescription, String viewId, String className,
                    String bounds, boolean visibleToUser, boolean selected,
                    boolean accessibilityFocused) {
            this.text = text;
            this.contentDescription = contentDescription;
            this.viewId = viewId;
            this.className = className;
            this.bounds = bounds;
            this.visibleToUser = visibleToUser;
            this.selected = selected;
            this.accessibilityFocused = accessibilityFocused;
        }

        public static Node numeric(String text, String viewId) {
            return new Node(text, null, viewId, "android.widget.TextView", "[0,0][80,30]",
                    true, false, false);
        }
    }

    private static final class Candidate {
        final String price;
        final boolean semantic;
        final boolean focused;
        final boolean numericOnly;
        final String description;

        Candidate(String price, boolean semantic, boolean focused, boolean numericOnly,
                  String description) {
            this.price = price;
            this.semantic = semantic;
            this.focused = focused;
            this.numericOnly = numericOnly;
            this.description = description;
        }
    }
}
