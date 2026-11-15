package com.crosshair.risk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class BacktestLogTest {

    @Test
    public void computesPositiveRMultiple() {
        assertEquals("+1.80", BacktestLog.rMultiple("1.00", "2.00", "3.80"));
        assertEquals("+2.00", BacktestLog.rMultiple("0.752", "0.770", "0.806"));
    }

    @Test
    public void computesNegativeRMultiple() {
        assertEquals("-0.50", BacktestLog.rMultiple("1.00", "2.00", "1.50"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsStopAboveBuy() {
        BacktestLog.rMultiple("2.00", "1.00", "3.00");
    }

    @Test
    public void parsesAndSerializesRecords() {
        List<BacktestLog.Entry> entries = Arrays.asList(
                entry("沪深300ETF华泰柏瑞", "510300", "0.752", "0.770", "0.806"),
                entry("沪深300ETF华泰柏瑞", "510300", "1.00", "2.00", "1.50"));

        String raw = BacktestLog.serialize(entries);
        List<BacktestLog.Entry> parsed = BacktestLog.parse(raw);

        assertEquals(2, parsed.size());
        assertEquals("510300", parsed.get(0).code);
        assertEquals("0.752", parsed.get(0).stop);
        assertEquals("+2.00", parsed.get(0).r());
        assertEquals("-0.50", parsed.get(1).r());
    }

    @Test
    public void ignoresMalformedLines() {
        assertTrue(BacktestLog.parse("bad|line").isEmpty());
    }

    @Test
    public void aggregatesStatsForMatchingStock() {
        List<BacktestLog.Entry> entries = Arrays.asList(
                entry("沪深300ETF华泰柏瑞", "510300", "1.00", "2.00", "4.00"),
                entry("沪深300ETF华泰柏瑞", "510300", "1.00", "2.00", "1.50"),
                entry("海天味业", "603288", "10.00", "11.00", "13.00"));

        BacktestLog.Stats stats = BacktestLog.stats(entries, "沪深300ETF华泰柏瑞", "510300");

        assertEquals(2, stats.count);
        assertEquals(1, stats.wins);
        assertEquals(50, stats.winRate);
        assertEquals("+1.50", stats.totalR);
        assertEquals("0.75", stats.averageR);
    }

    @Test
    public void exportContainsHeaderAndTrades() {
        List<BacktestLog.Entry> entries = Arrays.asList(
                entry("海天味业", "603288", "10.00", "11.00", "13.00"));

        String text = BacktestLog.export(entries, "海天味业", "603288");

        assertTrue(text.contains("回测记录 · 海天味业 603288"));
        assertTrue(text.contains("共 1 笔"));
        assertTrue(text.contains("卖出 13.00"));
        assertTrue(text.contains("R +2.00"));
    }

    @Test
    public void exportsEveryStockWhenNoFilter() {
        List<BacktestLog.Entry> entries = Arrays.asList(
                entry("海天味业", "603288", "10.00", "11.00", "13.00"),
                entry("沪深300ETF华泰柏瑞", "510300", "1.00", "2.00", "4.00"));

        String text = BacktestLog.export(entries, "", "");

        assertTrue(text.contains("海天味业"));
        assertTrue(text.contains("510300"));
        assertTrue(text.contains("共 2 笔"));
    }

    private static BacktestLog.Entry entry(String name, String code, String stop,
                                           String buy, String sell) {
        return new BacktestLog.Entry(name, code, stop, buy, sell,
                "2026-11-10", "2026-11-11");
    }
}
