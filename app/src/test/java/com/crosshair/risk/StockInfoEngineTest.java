package com.crosshair.risk;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class StockInfoEngineTest {
    @Test
    public void findsFundNameAndCodeFromHeaderNodes() {
        PriceCaptureEngine.Result ignored = null;
        StockInfoEngine.Result result = StockInfoEngine.find(Arrays.asList(
                node("沪深300ETF华泰柏瑞", "[300,140][700,180]"),
                node("510300 融通 L1", "[380,185][500,210]"),
                node("跟踪指数：沪深300 4075.71 16.60 0.41%", "[20,420][860,460]"),
                node("4.150", "[20,230][220,320]"),
                node("2026/11/10/13:30", "[20,600][280,630]")
        ));

        assertEquals("沪深300ETF华泰柏瑞", result.name);
        assertEquals("510300", result.code);
    }

    @Test
    public void ignoresTrackingIndexRowWhenChoosingName() {
        StockInfoEngine.Result result = StockInfoEngine.find(Arrays.asList(
                node("跟踪指数：沪深300", "[20,420][860,460]"),
                node("510300", "[380,185][500,210]")
        ));

        assertNull(result.name);
        assertEquals("510300", result.code);
    }

    @Test
    public void rejectsSixDigitValuesOutsideStockCodeRange() {
        StockInfoEngine.Result result = StockInfoEngine.find(Collections.singletonList(
                node("202611", "[20,100][200,130]")
        ));

        assertNull(result.code);
    }

    @Test
    public void parsesNameAndCodeFromOfflineOcrText() {
        StockInfoEngine.Result result = StockInfoEngine.fromText(
                "沪深300ETF华泰柏瑞\n510300 融通 L1\n4.150 +0.015 0.36%");

        assertEquals("沪深300ETF华泰柏瑞", result.name);
        assertEquals("510300", result.code);
    }

    @Test
    public void prefersFundNameOverShortStatusTextInOcrText() {
        StockInfoEngine.Result result = StockInfoEngine.fromText(
                "小红书\n01:09\n沪深300ETF华泰柏瑞\n510300 融通 L1");

        assertEquals("沪深300ETF华泰柏瑞", result.name);
        assertEquals("510300", result.code);
    }

    @Test
    public void keepsNameWithoutCodeWhenOcrMissesDigits() {
        StockInfoEngine.Result result = StockInfoEngine.fromText("沪深300ETF华泰柏瑞");

        assertEquals("沪深300ETF华泰柏瑞", result.name);
        assertNull(result.code);
    }

    private static PriceCaptureEngine.Node node(String text, String bounds) {
        return new PriceCaptureEngine.Node(text, null, null, "android.widget.TextView",
                bounds, true, false, false);
    }
}
