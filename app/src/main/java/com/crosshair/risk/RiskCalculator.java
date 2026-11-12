package com.crosshair.risk;

import java.math.BigDecimal;

/** Long-position 2R calculation. R is the risk per share from entry to stop. */
public final class RiskCalculator {
    private RiskCalculator() {
    }

    public static Calculation calculate(String stopText, String buyText) {
        if (isBlank(stopText) || isBlank(buyText)) {
            throw new IllegalArgumentException("请先捕获止损价和买入价");
        }

        final BigDecimal stop;
        final BigDecimal buy;
        try {
            stop = new BigDecimal(stopText.trim());
            buy = new BigDecimal(buyText.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("价格格式无效", error);
        }
        if (stop.signum() <= 0 || buy.signum() <= 0) {
            throw new IllegalArgumentException("价格必须大于 0");
        }
        if (stop.compareTo(buy) >= 0) {
            throw new IllegalArgumentException("止损价必须低于买入价（按做多交易计算）");
        }

        int scale = Math.max(stop.scale(), buy.scale());
        BigDecimal risk = buy.subtract(stop);
        BigDecimal profit = risk.multiply(BigDecimal.valueOf(2));
        BigDecimal target = buy.add(profit);
        BigDecimal breakEven = buy.add(risk);
        return new Calculation(
                plain(stop, scale),
                plain(buy, scale),
                plain(risk, scale),
                plain(profit, scale),
                plain(target, scale),
                plain(breakEven, scale)
        );
    }

    private static String plain(BigDecimal value, int scale) {
        return value.setScale(scale).toPlainString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Calculation {
        public final String stop;
        public final String buy;
        public final String risk;
        public final String profit;
        public final String target;
        public final String breakEven;

        Calculation(String stop, String buy, String risk, String profit, String target,
                    String breakEven) {
            this.stop = stop;
            this.buy = buy;
            this.risk = risk;
            this.profit = profit;
            this.target = target;
            this.breakEven = breakEven;
        }
    }
}
