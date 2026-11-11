package com.crosshair.risk;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class PriceCaptureEngineTest {
    @Test
    public void prefersCrosshairIdOverOtherVisiblePrices() {
        List<PriceCaptureEngine.Node> nodes = Arrays.asList(
                PriceCaptureEngine.Node.numeric("0.770", "com.tdx.AndroidNew:id/open_price"),
                PriceCaptureEngine.Node.numeric("0.752", "com.tdx.AndroidNew:id/crosshair_price"),
                PriceCaptureEngine.Node.numeric("0.774", "com.tdx.AndroidNew:id/close_price")
        );

        PriceCaptureEngine.Result result = PriceCaptureEngine.capture(nodes);

        assertTrue(result.isSuccess());
        assertEquals("0.752", result.price);
        assertEquals("十字光标语义元素", result.message);
    }

    @Test
    public void readsChineseCrosshairDescription() {
        PriceCaptureEngine.Node node = new PriceCaptureEngine.Node(
                null, "十字光标价：0.752", null, "android.view.View", "[10,20][90,50]",
                true, false, false);

        PriceCaptureEngine.Result result = PriceCaptureEngine.capture(Collections.singletonList(node));

        assertTrue(result.isSuccess());
        assertEquals("0.752", result.price);
    }

    @Test
    public void usesUniqueSelectedPriceWhenNoSemanticIdExists() {
        PriceCaptureEngine.Node other = PriceCaptureEngine.Node.numeric("0.770", null);
        PriceCaptureEngine.Node selected = new PriceCaptureEngine.Node(
                "0.752", null, null, "android.widget.TextView", "[0,0][80,30]",
                true, true, false);

        PriceCaptureEngine.Result result = PriceCaptureEngine.capture(Arrays.asList(other, selected));

        assertTrue(result.isSuccess());
        assertEquals("0.752", result.price);
    }

    @Test
    public void rejectsAmbiguousNumbersInsteadOfGuessing() {
        PriceCaptureEngine.Result result = PriceCaptureEngine.capture(Arrays.asList(
                PriceCaptureEngine.Node.numeric("0.752", null),
                PriceCaptureEngine.Node.numeric("0.770", null)
        ));

        assertFalse(result.isSuccess());
        assertEquals(PriceCaptureEngine.Status.AMBIGUOUS, result.status);
        assertNull(result.price);
    }

    @Test
    public void supportsSingleUnlabeledNumericCompatibilityCase() {
        PriceCaptureEngine.Result result = PriceCaptureEngine.capture(Collections.singletonList(
                PriceCaptureEngine.Node.numeric("0.752", null)
        ));

        assertTrue(result.isSuccess());
        assertEquals("0.752", result.price);
        assertTrue(result.message.contains("核对"));
    }

    @Test
    public void ignoresLabeledQuoteVolumeAndDateValues() {
        PriceCaptureEngine.Result result = PriceCaptureEngine.capture(Arrays.asList(
                PriceCaptureEngine.Node.numeric("最低 0.752", null),
                PriceCaptureEngine.Node.numeric("成交量：3237", null),
                PriceCaptureEngine.Node.numeric("2026/11/10/10:10", null)
        ));

        assertFalse(result.isSuccess());
        assertEquals(PriceCaptureEngine.Status.NO_PRICE, result.status);
    }

    @Test
    public void rejectsDifferentSemanticValues() {
        PriceCaptureEngine.Result result = PriceCaptureEngine.capture(Arrays.asList(
                PriceCaptureEngine.Node.numeric("0.752", "crosshair_price"),
                PriceCaptureEngine.Node.numeric("0.753", "cursor_price")
        ));

        assertFalse(result.isSuccess());
        assertEquals(PriceCaptureEngine.Status.AMBIGUOUS, result.status);
    }

    @Test
    public void ignoresInvisibleCrosshairNode() {
        PriceCaptureEngine.Node invisible = new PriceCaptureEngine.Node(
                "0.752", null, "crosshair_price", "android.widget.TextView", "[0,0][80,30]",
                false, false, false);

        PriceCaptureEngine.Result result = PriceCaptureEngine.capture(Collections.singletonList(invisible));

        assertFalse(result.isSuccess());
        assertEquals(PriceCaptureEngine.Status.NO_PRICE, result.status);
    }

    @Test
    public void diagnosticsIncludeValuesBeyondTheOldTwentyNodeLimit() {
        List<PriceCaptureEngine.Node> nodes = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            nodes.add(PriceCaptureEngine.Node.numeric("1." + (100 + index), null));
        }
        nodes.add(PriceCaptureEngine.Node.numeric("4.114", null));

        String diagnostics = PriceCaptureEngine.diagnostics(nodes);

        assertTrue(diagnostics.contains("25. 文本=4.114"));
    }
}
