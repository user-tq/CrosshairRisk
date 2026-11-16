package com.crosshair.risk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class RiskCalculatorTest {
    @Test
    public void calculatesScreenshotEtfExample() {
        RiskCalculator.Calculation result = RiskCalculator.calculate("0.752", "0.770");

        assertEquals("0.018", result.risk);
        assertEquals("0.036", result.profit);
        assertEquals("0.806", result.target);
        assertEquals("0.788", result.breakEven);
    }

    @Test
    public void keepsStockPricePrecision() {
        RiskCalculator.Calculation result = RiskCalculator.calculate("9.50", "10.00");

        assertEquals("0.50", result.risk);
        assertEquals("1.00", result.profit);
        assertEquals("11.00", result.target);
        assertEquals("10.50", result.breakEven);
    }

    @Test
    public void keepsGreatestInputScale() {
        RiskCalculator.Calculation result = RiskCalculator.calculate("0.75", "0.770");

        assertEquals("0.020", result.risk);
        assertEquals("0.810", result.target);
        assertEquals("0.790", result.breakEven);
    }

    @Test
    public void rejectsStopAtOrAboveEntry() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("0.770", "0.770"));
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("0.800", "0.770"));
    }

    @Test
    public void defaultsToTwoPercentMaxLoss() {
        RiskCalculator.Calculation result = RiskCalculator.calculate("9.50", "10.00");

        // 2% * 10.00 / 0.50 = 40.0
        assertEquals("40.0", result.positionPercent);
    }

    @Test
    public void computesPositionPercentFromMaxLoss() {
        RiskCalculator.Calculation result = RiskCalculator.calculate("9.50", "10.00", 1.0);

        // 1% * 10.00 / 0.50 = 20.0
        assertEquals("20.0", result.positionPercent);
    }

    @Test
    public void computesPositionPercentForEtf() {
        RiskCalculator.Calculation result = RiskCalculator.calculate("0.752", "0.770", 2.0);

        // 2% * 0.770 / 0.018 = 85.55... -> 85.6
        assertEquals("85.6", result.positionPercent);
    }

    @Test
    public void reportsPositionAboveAllInWhenRiskIsTiny() {
        RiskCalculator.Calculation result = RiskCalculator.calculate("9.99", "10.00", 2.0);

        // 2% * 10.00 / 0.01 = 2000.0
        assertEquals("2000.0", result.positionPercent);
    }

    @Test
    public void rejectsInvalidMaxLossPercent() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("9.50", "10.00", 0));
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("9.50", "10.00", -1));
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("9.50", "10.00", 101));
    }

    @Test
    public void rejectsMissingInvalidAndNonPositivePrices() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("", "0.770"));
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("abc", "0.770"));
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("0", "0.770"));
        assertThrows(IllegalArgumentException.class,
                () -> RiskCalculator.calculate("0.752", "-0.770"));
    }
}
