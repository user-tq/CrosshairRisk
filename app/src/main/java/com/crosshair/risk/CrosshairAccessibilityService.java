package com.crosshair.risk;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CrosshairAccessibilityService extends AccessibilityService {
    private static volatile CrosshairAccessibilityService instance;
    private static volatile boolean running;

    private SharedPreferences preferences;
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private View overlayView;
    private LinearLayout overlayBody;
    private TextView stopValue;
    private TextView buyValue;
    private TextView targetValue;
    private TextView riskValue;
    private TextView statusValue;
    private boolean collapsed;
    private boolean captureInFlight;
    private OfflineScreenshotRecognizer offlineRecognizer;
    private String lastForegroundPackage = "";

    public static CrosshairAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return running;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        running = true;
        preferences = getSharedPreferences(State.PREFS, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        applyOverlayVisibility();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }
        String packageName = event.getPackageName().toString();
        if (getPackageName().equals(packageName) || packageName.equals(lastForegroundPackage)) {
            return;
        }
        lastForegroundPackage = packageName;
        if (preferences != null) {
            preferences.edit().putString(State.KEY_PACKAGE, packageName).apply();
        }
    }

    @Override
    public void onInterrupt() {
        // No continuous capture is performed, so there is nothing to interrupt.
    }

    public void applyOverlayVisibility() {
        if (preferences == null || windowManager == null) {
            return;
        }
        if (preferences.getBoolean(State.KEY_VISIBLE, true)) {
            showOverlay();
        } else {
            removeOverlay();
        }
    }

    public void refreshFromPreferences() {
        if (overlayView != null) {
            renderPrices();
        }
    }

    private void showOverlay() {
        if (overlayView != null) {
            return;
        }
        buildOverlay();
        try {
            windowManager.addView(overlayView, overlayParams);
            renderPrices();
        } catch (RuntimeException error) {
            overlayView = null;
            preferences.edit().putString(State.KEY_MESSAGE,
                    "悬浮窗创建失败，请重新启用无障碍服务").apply();
        }
    }

    private void removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (IllegalArgumentException ignored) {
                // The window may already have been removed by the system.
            }
            overlayView = null;
        }
    }

    private void buildOverlay() {
        LinearLayout container = Ui.vertical(this);
        container.setPadding(Ui.dp(this, 10), Ui.dp(this, 7),
                Ui.dp(this, 10), Ui.dp(this, 9));
        container.setBackground(Ui.outlined(Color.argb(185, 20, 26, 32),
                Color.argb(150, 67, 83, 91), 16, this));
        container.setElevation(Ui.dp(this, 10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 34)));

        TextView title = Ui.bold(this, "2R 卖出价助手", 14, Ui.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView collapse = Ui.bold(this, collapsed ? "＋" : "—", 20, Ui.MUTED);
        collapse.setGravity(Gravity.CENTER);
        collapse.setContentDescription(collapsed ? "展开悬浮窗" : "收起悬浮窗");
        header.addView(collapse, new LinearLayout.LayoutParams(Ui.dp(this, 36),
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView close = Ui.bold(this, "×", 22, Ui.MUTED);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("隐藏悬浮窗");
        header.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 34),
                ViewGroup.LayoutParams.MATCH_PARENT));

        overlayBody = Ui.vertical(this);
        container.addView(overlayBody, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        overlayBody.setVisibility(collapsed ? View.GONE : View.VISIBLE);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        overlayBody.addView(buttons, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 8, this));

        Button captureStop = Ui.button(this, "捕获止损价", false);
        Button captureBuy = Ui.button(this, "捕获买入价", true);
        for (Button button : new Button[]{captureStop, captureBuy}) {
            button.setTextSize(12);
            button.setMinHeight(0);
            button.setMinimumHeight(0);
            button.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        }
        buttons.addView(captureStop, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1));
        LinearLayout.LayoutParams buyParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1);
        buyParams.leftMargin = Ui.dp(this, 6);
        buttons.addView(captureBuy, buyParams);

        LinearLayout prices = new LinearLayout(this);
        prices.setGravity(Gravity.CENTER_VERTICAL);
        overlayBody.addView(prices, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 14, this));

        LinearLayout stopColumn = Ui.vertical(this);
        prices.addView(stopColumn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        stopColumn.addView(Ui.text(this, "止损价", 11, Ui.MUTED));
        stopValue = Ui.bold(this, "--", 18, Ui.WHITE);
        stopColumn.addView(stopValue);

        LinearLayout buyColumn = Ui.vertical(this);
        buyColumn.setGravity(Gravity.END);
        prices.addView(buyColumn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        buyColumn.addView(Ui.text(this, "买入价", 11, Ui.MUTED));
        buyValue = Ui.bold(this, "--", 18, Ui.WHITE);
        buyColumn.addView(buyValue);

        LinearLayout targetCard = Ui.vertical(this);
        targetCard.setGravity(Gravity.CENTER_HORIZONTAL);
        targetCard.setPadding(Ui.dp(this, 8), Ui.dp(this, 8),
                Ui.dp(this, 8), Ui.dp(this, 8));
        targetCard.setBackground(Ui.rounded(Color.argb(190, 25, 54, 51), 12, this));
        overlayBody.addView(targetCard, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));
        targetCard.addView(Ui.bold(this, "2 倍盈亏比卖出价", 12, Ui.ACCENT));
        targetValue = Ui.bold(this, "--", 28, Ui.ACCENT);
        targetValue.setTextIsSelectable(true);
        targetCard.addView(targetValue);
        riskValue = Ui.text(this, "等待止损价和买入价", 11, Ui.MUTED);
        targetCard.addView(riskValue, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 3, this));

        statusValue = Ui.text(this, "请先在通达信中调出十字光标", 11, Ui.MUTED);
        statusValue.setTextIsSelectable(true);
        overlayBody.addView(statusValue, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 7, this));

        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        overlayBody.addView(tools, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 4, this));
        TextView diagnose = Ui.text(this, "诊断页面", 11, Ui.ACCENT);
        diagnose.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        tools.addView(diagnose, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView clear = Ui.text(this, "清空", 11, Ui.MUTED);
        clear.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6));
        tools.addView(clear, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        captureStop.setOnClickListener(view -> captureInto(true));
        captureBuy.setOnClickListener(view -> captureInto(false));
        diagnose.setOnClickListener(view -> diagnoseCurrentPage());
        clear.setOnClickListener(view -> {
            preferences.edit().remove(State.KEY_STOP).remove(State.KEY_BUY)
                    .putString(State.KEY_MESSAGE, "价格已清空").apply();
            renderPrices();
        });
        collapse.setOnClickListener(view -> {
            collapsed = !collapsed;
            overlayBody.setVisibility(collapsed ? View.GONE : View.VISIBLE);
            collapse.setText(collapsed ? "＋" : "—");
            collapse.setContentDescription(collapsed ? "展开悬浮窗" : "收起悬浮窗");
        });
        close.setOnClickListener(view -> {
            preferences.edit().putBoolean(State.KEY_VISIBLE, false).apply();
            removeOverlay();
        });
        enableDragging(header);

        int width = Math.min(Ui.dp(this, 240),
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 20));
        overlayParams = new WindowManager.LayoutParams(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = Ui.dp(this, 12);
        overlayParams.y = Ui.dp(this, 150);
        overlayView = container;
    }

    private void enableDragging(View handle) {
        final float[] initialTouch = new float[2];
        final int[] initialPosition = new int[2];
        handle.setOnTouchListener((view, event) -> {
            if (overlayParams == null || overlayView == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouch[0] = event.getRawX();
                    initialTouch[1] = event.getRawY();
                    initialPosition[0] = overlayParams.x;
                    initialPosition[1] = overlayParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    overlayParams.x = initialPosition[0]
                            + Math.round(event.getRawX() - initialTouch[0]);
                    overlayParams.y = initialPosition[1]
                            + Math.round(event.getRawY() - initialTouch[1]);
                    windowManager.updateViewLayout(overlayView, overlayParams);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void captureInto(boolean stop) {
        if (captureInFlight) {
            saveMessage("正在识别，请稍候");
            renderPrices();
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            saveMessage("读取失败：未取得前台页面，请保持通达信在前台");
            renderPrices();
            return;
        }
        try {
            String packageName = root.getPackageName() == null
                    ? lastForegroundPackage : root.getPackageName().toString();
            if (!isTongDaXin(packageName)) {
                saveMessage("当前应用未识别为通达信：" + packageName + "，未读取价格");
                renderPrices();
                return;
            }
            List<PriceCaptureEngine.Node> nodes = snapshot(root);
            PriceCaptureEngine.Result result = PriceCaptureEngine.capture(nodes);
            saveDiagnostics(packageName, nodes.size(), result.diagnostics);
            if (result.isSuccess()) {
                applyCapturedPrice(stop, result.price, "页面元素");
                renderPrices();
                return;
            }
            startOfflineCapture(stop);
        } catch (RuntimeException error) {
            startOfflineCapture(stop);
        } finally {
            root.recycle();
        }
    }

    private void startOfflineCapture(boolean stop) {
        if (Build.VERSION.SDK_INT < 30) {
            saveMessage("元素未识别；离线截图识别需要 Android 11 及以上");
            renderPrices();
            return;
        }
        try {
            if (offlineRecognizer == null) {
                offlineRecognizer = new OfflineScreenshotRecognizer(this);
            }
        } catch (RuntimeException error) {
            saveMessage("离线识别模型启动失败，请重新安装应用后再试");
            renderPrices();
            return;
        }
        captureInFlight = true;
        saveMessage("正在本地离线识别蓝色标签…");
        renderPrices();
        offlineRecognizer.capture(new OfflineScreenshotRecognizer.Callback() {
            @Override
            public void onPrice(String price) {
                captureInFlight = false;
                applyCapturedPrice(stop, price, "离线图像识别");
                renderPrices();
            }

            @Override
            public void onError(String message) {
                captureInFlight = false;
                saveMessage(message);
                renderPrices();
            }
        });
    }

    private void applyCapturedPrice(boolean stop, String price, String source) {
        String key = stop ? State.KEY_STOP : State.KEY_BUY;
        String label = stop ? "止损价" : "买入价";
        preferences.edit().putString(key, price)
                .putString(State.KEY_MESSAGE,
                        label + "已捕获 " + price + " · " + source)
                .apply();
    }

    private void diagnoseCurrentPage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            saveMessage("诊断失败：未取得前台页面");
            renderPrices();
            return;
        }
        try {
            String packageName = root.getPackageName() == null
                    ? "未知" : root.getPackageName().toString();
            List<PriceCaptureEngine.Node> nodes = snapshot(root);
            saveDiagnostics(packageName, nodes.size(), PriceCaptureEngine.diagnostics(nodes));
            saveMessage("诊断完成，请回主界面查看结果");
        } catch (RuntimeException error) {
            saveMessage("诊断失败：系统未允许读取该页面");
        } finally {
            root.recycle();
        }
        renderPrices();
    }

    private List<PriceCaptureEngine.Node> snapshot(AccessibilityNodeInfo root) {
        List<PriceCaptureEngine.Node> nodes = new ArrayList<>();
        appendNode(root, nodes, 0);
        return nodes;
    }

    private void appendNode(AccessibilityNodeInfo node, List<PriceCaptureEngine.Node> output,
                            int depth) {
        if (node == null || depth > 80 || output.size() >= 4000) {
            return;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        output.add(new PriceCaptureEngine.Node(
                string(node.getText()),
                string(node.getContentDescription()),
                safeViewId(node),
                string(node.getClassName()),
                bounds.toShortString(),
                node.isVisibleToUser(),
                node.isSelected(),
                node.isAccessibilityFocused()
        ));
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                try {
                    appendNode(child, output, depth + 1);
                } finally {
                    child.recycle();
                }
            }
        }
    }

    private String safeViewId(AccessibilityNodeInfo node) {
        try {
            return node.getViewIdResourceName();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void saveDiagnostics(String packageName, int nodeCount, String details) {
        preferences.edit().putString(State.KEY_PACKAGE, packageName)
                .putString(State.KEY_DIAGNOSTICS,
                        "前台包名：" + packageName + "\n遍历节点：" + nodeCount
                                + "（含无文本容器）\n" + details)
                .apply();
    }

    private boolean isTongDaXin(String packageName) {
        if (packageName == null) {
            return false;
        }
        String value = packageName.toLowerCase(Locale.ROOT);
        return value.contains("tdx") || value.contains("tongdaxin");
    }

    private void saveMessage(String message) {
        preferences.edit().putString(State.KEY_MESSAGE, message).apply();
    }

    private void renderPrices() {
        if (stopValue == null) {
            return;
        }
        String stop = preferences.getString(State.KEY_STOP, "");
        String buy = preferences.getString(State.KEY_BUY, "");
        String message = preferences.getString(State.KEY_MESSAGE, "等待捕获");
        stopValue.setText(stop.isEmpty() ? "--" : stop);
        buyValue.setText(buy.isEmpty() ? "--" : buy);

        if (!stop.isEmpty() && !buy.isEmpty()) {
            try {
                RiskCalculator.Calculation result = RiskCalculator.calculate(stop, buy);
                targetValue.setText(result.target);
                targetValue.setTextColor(Ui.ACCENT);
                riskValue.setText("每股风险 " + result.risk + "  ·  2R 收益 " + result.profit);
                riskValue.setTextColor(Ui.MUTED);
            } catch (IllegalArgumentException error) {
                targetValue.setText("--");
                targetValue.setTextColor(Ui.DANGER);
                riskValue.setText(error.getMessage());
                riskValue.setTextColor(Ui.DANGER);
            }
        } else {
            targetValue.setText("--");
            targetValue.setTextColor(Ui.ACCENT);
            riskValue.setText("等待止损价和买入价");
            riskValue.setTextColor(Ui.MUTED);
        }
        statusValue.setText(message);
        statusValue.setTextColor(message.contains("失败") || message.contains("无法")
                || message.contains("未识别") ? Ui.DANGER : Ui.MUTED);
    }

    private static String string(CharSequence value) {
        return value == null ? null : value.toString();
    }

    @Override
    public void onDestroy() {
        captureInFlight = false;
        if (offlineRecognizer != null) {
            offlineRecognizer.close();
            offlineRecognizer = null;
        }
        removeOverlay();
        running = false;
        instance = null;
        super.onDestroy();
    }
}
