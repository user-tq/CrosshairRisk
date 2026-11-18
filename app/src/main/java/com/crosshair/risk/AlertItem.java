package com.crosshair.risk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 价格监控条目，持久化到 SharedPreferences。 */
final class AlertItem {
    static final String DIR_ABOVE = "above";
    static final String DIR_BELOW = "below";

    final String id;
    final String name;
    final String code;
    final double targetPrice;
    /** "above" = 当前价 ≥ 目标价触发；"below" = 当前价 ≤ 目标价触发。 */
    final String direction;
    final boolean enabled;
    final String note;
    final long createdAt;

    AlertItem(String name, String code, double targetPrice, String direction,
              boolean enabled, String note) {
        this(UUID.randomUUID().toString(), name, code, targetPrice, direction,
                enabled, note, System.currentTimeMillis());
    }

    AlertItem(String id, String name, String code, double targetPrice,
              String direction, boolean enabled, String note, long createdAt) {
        this.id = id;
        this.name = clean(name);
        this.code = clean(code);
        this.targetPrice = targetPrice;
        this.direction = DIR_ABOVE.equals(direction) ? DIR_ABOVE : DIR_BELOW;
        this.enabled = enabled;
        this.note = clean(note);
        this.createdAt = createdAt;
    }

    AlertItem withEnabled(boolean enabled) {
        return new AlertItem(id, name, code, targetPrice, direction,
                enabled, note, createdAt);
    }

    AlertItem withPrice(double price) {
        return new AlertItem(id, name, code, price, direction,
                enabled, note, createdAt);
    }

    AlertItem withDirection(String dir) {
        return new AlertItem(id, name, code, targetPrice, dir,
                enabled, note, createdAt);
    }

    AlertItem withNote(String note) {
        return new AlertItem(id, name, code, targetPrice, direction,
                enabled, note, createdAt);
    }

    String display() {
        return (name.isEmpty() ? code : name + (code.isEmpty() ? "" : " " + code));
    }

    /** 代码 → 腾讯行情前缀。
     * 6/9/5 → sh（沪市），0/1/2/3 → sz（深市），4/8 → bj（北交所）。
     * ETF 159xxx（深）、51xxxx（沪）已按首数字正确归入。
     */
    String marketSymbol() {
        if (code == null || code.length() == 0) {
            return "";
        }
        char first = code.charAt(0);
        if (first == '6' || first == '5' || first == '9') {
            return "sh" + code;
        }
        if (first == '0' || first == '1' || first == '2' || first == '3') {
            return "sz" + code;
        }
        if (first == '4' || first == '8') {
            return "bj" + code;
        }
        return "sz" + code;
    }

    boolean triggers(double currentPrice) {
        if (currentPrice <= 0) {
            return false;
        }
        if (DIR_ABOVE.equals(direction)) {
            return currentPrice >= targetPrice;
        }
        return currentPrice <= targetPrice;
    }

    // ---------- 持久化 ----------

    /** 序列化格式：id|name|code|targetPrice|direction|enabled|note|createdAt */
    static String serialize(List<AlertItem> items) {
        StringBuilder builder = new StringBuilder();
        for (AlertItem item : items) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(field(item.id)).append('|')
                    .append(field(item.name)).append('|')
                    .append(field(item.code)).append('|')
                    .append(item.targetPrice).append('|')
                    .append(item.direction).append('|')
                    .append(item.enabled).append('|')
                    .append(field(item.note)).append('|')
                    .append(item.createdAt);
        }
        return builder.toString();
    }

    static List<AlertItem> parse(String raw) {
        List<AlertItem> items = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return items;
        }
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 8) {
                continue;
            }
            double price;
            try {
                price = Double.parseDouble(parts[3]);
            } catch (NumberFormatException error) {
                continue;
            }
            long created;
            try {
                created = Long.parseLong(parts[7]);
            } catch (NumberFormatException error) {
                created = 0;
            }
            items.add(new AlertItem(
                    parts[0], parts[1], parts[2], price,
                    parts[4], Boolean.parseBoolean(parts[5]), parts[6], created));
        }
        return items;
    }

    static AlertItem findById(List<AlertItem> items, String id) {
        if (id == null) {
            return null;
        }
        for (AlertItem item : items) {
            if (id.equals(item.id)) {
                return item;
            }
        }
        return null;
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
