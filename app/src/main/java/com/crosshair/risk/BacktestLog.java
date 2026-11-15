package com.crosshair.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Pure storage and math for backtest trade records. */
final class BacktestLog {
    static final String MODE_COMPUTE = "compute";
    static final String MODE_BACKTEST = "backtest";

    private BacktestLog() {
    }

    static final class Entry {
        final String name;
        final String code;
        final String stop;
        final String buy;
        final String sell;
        final String buyDate;
        final String sellDate;

        Entry(String name, String code, String stop, String buy, String sell,
              String buyDate, String sellDate) {
            this.name = clean(name);
            this.code = clean(code);
            this.stop = clean(stop);
            this.buy = clean(buy);
            this.sell = clean(sell);
            this.buyDate = clean(buyDate);
            this.sellDate = clean(sellDate);
        }

        String r() {
            try {
                return rMultiple(stop, buy, sell);
            } catch (IllegalArgumentException error) {
                return "--";
            }
        }
    }

    static final class Stats {
        final int count;
        final int wins;
        final String totalR;
        final String averageR;
        final int winRate;

        Stats(int count, int wins, String totalR, String averageR, int winRate) {
            this.count = count;
            this.wins = wins;
            this.totalR = totalR;
            this.averageR = averageR;
            this.winRate = winRate;
        }
    }

    static String rMultiple(String stop, String buy, String sell) {
        BigDecimal stopValue = parsePrice(stop, "止损价");
        BigDecimal buyValue = parsePrice(buy, "买入价");
        BigDecimal sellValue = parsePrice(sell, "卖出价");
        BigDecimal risk = buyValue.subtract(stopValue);
        if (risk.signum() <= 0) {
            throw new IllegalArgumentException("止损价需低于买入价");
        }
        BigDecimal value = sellValue.subtract(buyValue)
                .divide(risk, 2, RoundingMode.HALF_UP);
        return signed(value);
    }

    static List<Entry> parse(String raw) {
        List<Entry> entries = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return entries;
        }
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 7) {
                continue;
            }
            Entry entry = new Entry(parts[0], parts[1], parts[2], parts[3], parts[4],
                    parts[5], parts[6]);
            if (entry.stop.isEmpty() || entry.buy.isEmpty() || entry.sell.isEmpty()) {
                continue;
            }
            entries.add(entry);
        }
        return entries;
    }

    static String serialize(List<Entry> entries) {
        StringBuilder builder = new StringBuilder();
        for (Entry entry : entries) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(field(entry.name)).append('|')
                    .append(field(entry.code)).append('|')
                    .append(field(entry.stop)).append('|')
                    .append(field(entry.buy)).append('|')
                    .append(field(entry.sell)).append('|')
                    .append(field(entry.buyDate)).append('|')
                    .append(field(entry.sellDate));
        }
        return builder.toString();
    }

    static List<Entry> filter(List<Entry> entries, String name, String code) {
        List<Entry> selected = new ArrayList<>();
        for (Entry entry : entries) {
            if (matches(entry, name, code)) {
                selected.add(entry);
            }
        }
        return selected;
    }

    static Stats stats(List<Entry> entries, String name, String code) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        int wins = 0;
        for (Entry entry : entries) {
            if (!matches(entry, name, code)) {
                continue;
            }
            String text = entry.r();
            if ("--".equals(text)) {
                continue;
            }
            BigDecimal value = new BigDecimal(text.replace("+", ""));
            total = total.add(value);
            count++;
            if (value.signum() > 0) {
                wins++;
            }
        }
        String average = count == 0 ? "0.00"
                : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                .toPlainString();
        int winRate = count == 0 ? 0 : Math.round(wins * 100f / count);
        return new Stats(count, wins, signed(total), average, winRate);
    }

    static String export(List<Entry> entries, String name, String code) {
        List<Entry> selected = filter(entries, name, code);
        Stats stats = stats(entries, name, code);
        StringBuilder builder = new StringBuilder();
        builder.append("回测记录");
        if (!name.isEmpty() || !code.isEmpty()) {
            builder.append(" · ").append(name);
            if (!name.isEmpty() && !code.isEmpty()) {
                builder.append(' ');
            }
            builder.append(code);
        }
        builder.append('\n');
        if (selected.isEmpty()) {
            builder.append("暂无回测记录");
            return builder.toString();
        }
        builder.append("共 ").append(stats.count).append(" 笔 · 胜率 ")
                .append(stats.winRate).append("% · 总R ").append(stats.totalR)
                .append(" · 平均R ").append(stats.averageR).append("\n\n");
        int index = 1;
        for (Entry entry : selected) {
            builder.append(index++).append(". ");
            if (name.isEmpty() && code.isEmpty()) {
                builder.append(display(entry.name, "未识别")).append(' ')
                        .append(display(entry.code, "未识别")).append("  ");
            }
            builder.append("买入 ").append(display(entry.buyDate, "未记录"))
                    .append("  止损 ").append(entry.stop)
                    .append("  买入 ").append(entry.buy)
                    .append("  卖出 ").append(entry.sell)
                    .append("  R ").append(entry.r())
                    .append("  卖出日 ").append(display(entry.sellDate, "未记录"))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private static boolean matches(Entry entry, String name, String code) {
        boolean hasName = name != null && !name.isEmpty();
        boolean hasCode = code != null && !code.isEmpty();
        if (!hasName && !hasCode) {
            return true;
        }
        if (hasCode && code.equals(entry.code)) {
            return true;
        }
        return hasName && name.equals(entry.name);
    }

    private static BigDecimal parsePrice(String raw, String label) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(label + "未捕获");
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() <= 0) {
                throw new IllegalArgumentException(label + "需大于 0");
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "格式不正确");
        }
    }

    private static String signed(BigDecimal value) {
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return scaled.signum() > 0 ? "+" + scaled.toPlainString() : scaled.toPlainString();
    }

    private static String display(String raw, String fallback) {
        return raw == null || raw.isEmpty() ? fallback : raw;
    }

    private static String field(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("|", " ").replace("\n", " ").trim();
    }

    private static String clean(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
