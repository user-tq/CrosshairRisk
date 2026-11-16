package com.crosshair.risk;

import java.math.BigDecimal;
import java.util.Locale;

/** Long-position 2R calculation. R is the risk per share from entry to stop. */
public final class RiskCalculator {
    /** 单笔最大亏损占总仓位的默认比例（百分比）。 */
    public static final double DEFAULT_MAX_LOSS_PERCENT = 2.0;

    private RiskCalculator() {
    }

    public static Calculation calculate(String stopText, String buyText) {
        return calculate(stopText, buyText, DEFAULT_MAX_LOSS_PERCENT);
    }

    public static Calculation calculate(String stopText, String buyText,
                                        double maxLossPercent) {
        if (isBlank(stopText) || isBlank(buyText)) {
            throw new IllegalArgumentException("请先捕获止损价和买入价");
        }
        if (maxLossPercent <= 0 || maxLossPercent > 100) {
            throw new IllegalArgumentException("最大亏损比例需在 0~100 之间");
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
        // 仓位占比 = 最大亏损比例 × 买入价 ÷ 每股风险
        double positionPercent = maxLossPercent
                * buy.doubleValue() / risk.doubleValue();
        return new Calculation(
                plain(stop, scale),
                plain(buy, scale),
                plain(risk, scale),
                plain(profit, scale),
                plain(target, scale),
                plain(breakEven, scale),
                String.format(Locale.ROOT, "%.1f", positionPercent)
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
        /** 按最大亏损比例计算出的可用仓位占比（百分比，已格式化）。 */
        public final String positionPercent;

        Calculation(String stop, String buy, String risk, String profit, String target,
                    String breakEven, String positionPercent) {
            this.stop = stop;
            this.buy = buy;
            this.risk = risk;
            this.profit = profit;
            this.target = target;
            this.breakEven = breakEven;
            this.positionPercent = positionPercent;
        }
    }
}
