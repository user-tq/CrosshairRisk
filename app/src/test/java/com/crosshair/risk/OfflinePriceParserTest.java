package com.crosshair.risk;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class OfflinePriceParserTest {
    @Test
    public void parsesSimplePrice() {
        assertEquals("4.114", OfflinePriceParser.parse("4.114"));
    }

    @Test
    public void acceptsSurroundingTextAndWhitespace() {
        assertEquals("0.752", OfflinePriceParser.parse("价格\n0 . 752"));
    }

    @Test
    public void acceptsRepeatedSamePrice() {
        assertEquals("4.114", OfflinePriceParser.parse("4.114\n4.114"));
    }

    @Test
    public void rejectsConflictingPrices() {
        assertNull(OfflinePriceParser.parse("4.114 4.150"));
    }

    @Test
    public void rejectsValuesWithoutDecimalPoint() {
        assertNull(OfflinePriceParser.parse("4114"));
    }
}
