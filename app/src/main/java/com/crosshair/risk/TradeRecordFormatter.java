package com.crosshair.risk;

/** Formats the captured trade information for the clipboard. */
final class TradeRecordFormatter {
    private TradeRecordFormatter() {
    }

    static String format(String stop, String buy, String name, String code, String date) {
        return "止损价：" + value(stop, "未捕获") + "\n"
                + "买入价：" + value(buy, "未捕获") + "\n"
                + "股票名称：" + value(name, "未识别") + "\n"
                + "股票代码：" + value(code, "未识别") + "\n"
                + "买入日期：" + value(date, "未记录");
    }

    private static String value(String raw, String fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        return raw.trim();
    }
}
