package com.crosshair.risk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_POST_NOTIFICATIONS = 42;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refreshState();
            handler.postDelayed(this, 750);
        }
    };

    private TextView serviceStatus;
    private TextView quote;
    private TextView detail;
    private TextView foregroundPackage;
    private TextView diagnostics;
    private TextView headerOcr;
    private TextView backtestStats;
    private TextView backtestList;
    private Button overlayButton;
    private Button modeButton;
    private EditText maxLossInput;
    private LinearLayout alertList;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(State.PREFS, MODE_PRIVATE);
        buildContent();
    }

    private void buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(16, 19, 24));

        LinearLayout content = Ui.vertical(this);
        int padding = Ui.dp(this, 22);
        content.setPadding(padding, Ui.dp(this, 30), padding, Ui.dp(this, 36));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(Ui.bold(this, "十字光标 2R 助手", 28, Ui.WHITE));
        TextView subtitle = Ui.text(this, "元素 + 本地离线识别 · 无联网权限", 14, Ui.MUTED);
        content.addView(subtitle, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 6, this));

        LinearLayout statusCard = card();
        content.addView(statusCard, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 22, this));
        statusCard.addView(Ui.bold(this, "运行状态", 16, Ui.WHITE));
        serviceStatus = Ui.text(this, "", 14, Ui.MUTED);
        statusCard.addView(serviceStatus, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));
        foregroundPackage = Ui.text(this, "", 12, Ui.MUTED);
        foregroundPackage.setTextIsSelectable(true);
        statusCard.addView(foregroundPackage, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 5, this));

        Button accessibility = Ui.button(this, "1  启用无障碍读取服务", true);
        accessibility.setOnClickListener(view -> openAccessibilitySettings());
        statusCard.addView(accessibility, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 16, this));

        overlayButton = Ui.button(this, "", false);
        overlayButton.setOnClickListener(view -> sendControl(State.ACTION_TOGGLE));
        statusCard.addView(overlayButton, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));

        LinearLayout quoteCard = card();
        content.addView(quoteCard, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 16, this));
        quoteCard.addView(Ui.bold(this, "当前 2R 卖出价", 15, Ui.MUTED));
        quote = Ui.bold(this, "--", 34, Ui.ACCENT);
        quoteCard.addView(quote, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 6, this));
        detail = Ui.text(this, "尚未捕获完整价格", 14, Ui.WHITE);
        detail.setTextIsSelectable(true);
        quoteCard.addView(detail, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 8, this));
        TextView formula = Ui.text(this,
                "公式：卖出价 = 买入价 + 2 × (买入价 − 止损价)\n"
                        + "可用仓位 = 最大亏损% × 买入价 ÷ (买入价 − 止损价)\n"
                        + "仅适用于止损价低于买入价的做多交易。",
                13, Ui.MUTED);
        quoteCard.addView(formula, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 12, this));

        TextView lossLabel = Ui.text(this, "单笔最大亏损占总仓位（%）", 13, Ui.MUTED);
        quoteCard.addView(lossLabel, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 14, this));

        LinearLayout lossRow = new LinearLayout(this);
        lossRow.setGravity(Gravity.CENTER_VERTICAL);
        maxLossInput = new EditText(this);
        maxLossInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        maxLossInput.setSingleLine(true);
        maxLossInput.setTextSize(15);
        maxLossInput.setTextColor(Ui.WHITE);
        maxLossInput.setHint("2");
        maxLossInput.setHintTextColor(Ui.MUTED);
        maxLossInput.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        maxLossInput.setBackground(Ui.outlined(Ui.BUTTON, Color.rgb(43, 53, 62), 10, this));
        maxLossInput.setText(formatMaxLoss());
        lossRow.addView(maxLossInput, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1));

        Button saveLoss = Ui.button(this, "保存", true);
        saveLoss.setTextSize(13);
        saveLoss.setMinHeight(0);
        saveLoss.setMinimumHeight(0);
        saveLoss.setOnClickListener(view -> saveMaxLossPercent());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 76), Ui.dp(this, 42));
        saveParams.leftMargin = Ui.dp(this, 8);
        lossRow.addView(saveLoss, saveParams);
        quoteCard.addView(lossRow, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 8, this));

        Button clear = Ui.button(this, "清空已捕获价格", false);
        clear.setOnClickListener(view -> sendControl(State.ACTION_CLEAR));
        quoteCard.addView(clear, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 12, this));

        LinearLayout alertCard = card();
        content.addView(alertCard, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 16, this));
        alertCard.addView(Ui.bold(this, "价格监控清单", 16, Ui.WHITE));
        alertCard.addView(Ui.text(this,
                "勾选后联网实时监控行情。价格达到目标时发系统通知，提示一次后自动取消勾选。"
                        + "价格从腾讯行情接口获取，每 30 秒轮询一次。",
                12, Ui.MUTED), Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 6, this));

        Button addFromCapture = Ui.button(this, "＋ 从已捕获股票添加", true);
        addFromCapture.setOnClickListener(view -> addAlertFromCapture());
        alertCard.addView(addFromCapture, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 12, this));

        Button addBlank = Ui.button(this, "＋ 新建空条目", false);
        addBlank.setOnClickListener(view -> addBlankAlert());
        alertCard.addView(addBlank, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 8, this));

        alertList = Ui.vertical(this);
        alertCard.addView(alertList, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));

        LinearLayout backtestCard = card();
        content.addView(backtestCard, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 16, this));
        backtestCard.addView(Ui.bold(this, "回测模式", 16, Ui.WHITE));
        backtestCard.addView(Ui.text(this,
                "在浮窗右上角切换“计算 / 回测”。回测中依次捕获止损、买入、卖出，"
                        + "卖出后自动记为一笔，并清空当前价格以便继续下一笔。",
                13, Ui.MUTED), Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 8, this));

        modeButton = Ui.button(this, "", false);
        modeButton.setOnClickListener(view -> sendControl(State.ACTION_TOGGLE_MODE));
        backtestCard.addView(modeButton, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 12, this));

        backtestStats = Ui.text(this, "暂无回测记录", 14, Ui.WHITE);
        backtestStats.setTextIsSelectable(true);
        backtestCard.addView(backtestStats, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));

        Button export = Ui.button(this, "导出记录到剪贴板", true);
        export.setOnClickListener(view -> exportBacktest());
        backtestCard.addView(export, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 12, this));

        Button undo = Ui.button(this, "撤销上一笔", false);
        undo.setOnClickListener(view -> undoBacktest());
        backtestCard.addView(undo, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 8, this));

        Button clearBacktest = Ui.button(this, "清空回测记录", false);
        clearBacktest.setOnClickListener(view -> {
            preferences.edit().remove(State.KEY_BACKTEST_LOG).apply();
            Toast.makeText(this, "回测记录已清空", Toast.LENGTH_SHORT).show();
            refreshState();
        });
        backtestCard.addView(clearBacktest, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 8, this));

        backtestList = Ui.text(this, "暂无回测记录", 12, Ui.MUTED);
        backtestList.setTextIsSelectable(true);
        backtestCard.addView(backtestList, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 12, this));

        LinearLayout helpCard = card();
        content.addView(helpCard, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 16, this));
        helpCard.addView(Ui.bold(this, "使用方法", 16, Ui.WHITE));
        TextView steps = Ui.text(this,
                "1. 启用服务并显示悬浮窗。\n"
                        + "2. 打开通达信，长按 K 线调出十字光标。\n"
                        + "3. 分别点击“捕获止损”和“捕获买入”，浮窗会同时识别股票名称和代码。\n"
                        + "4. 核对两个价格后，查看实时计算的 2R 卖出价和可用仓位。\n"
                        + "5. 点击浮窗右上角“复制”，即可得到包含股票名称、代码、止损价、买入价和买入日期的记录。\n"
                        + "6. 在“当前 2R 卖出价”卡片中设置单笔最大亏损占总仓位百分比（默认 2%），捕获止损与买入后浮窗会显示建议仓位。\n\n"
                        + "若提示无法识别，点击浮窗右下角“诊断”，再回本页查看元素结果。",
                14, Ui.MUTED);
        helpCard.addView(steps, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));

        Button copyDiagnostics = Ui.button(this, "复制完整诊断结果", false);
        copyDiagnostics.setOnClickListener(view -> copyDiagnostics());
        helpCard.addView(copyDiagnostics, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 14, this));

        diagnostics = Ui.text(this, "暂无诊断结果", 12, Ui.MUTED);
        diagnostics.setTextIsSelectable(true);
        helpCard.addView(diagnostics, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 14, this));

        headerOcr = Ui.text(this, "标题区识别：暂无", 12, Ui.MUTED);
        headerOcr.setTextIsSelectable(true);
        helpCard.addView(headerOcr, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 10, this));

        TextView privacy = Ui.text(this,
                "说明：优先读取页面元素；失败时，Android 11 及以上会通过无障碍截图定位左侧蓝色标签和顶部股票标题，并使用 APK 内置模型离线识别价格、股票名称与代码。截图仅在内存中处理，不保存；应用没有联网权限。请避免浮窗遮挡蓝色价格标签。",
                12, Ui.MUTED);
        content.addView(privacy, Ui.params(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 20, this));

        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout card = Ui.vertical(this);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 18),
                Ui.dp(this, 18), Ui.dp(this, 18));
        card.setBackground(Ui.outlined(Ui.CARD, Color.rgb(43, 53, 62), 16, this));
        return card;
    }

    private float maxLossPercent() {
        return preferences.getFloat(State.KEY_MAX_LOSS_PERCENT,
                (float) RiskCalculator.DEFAULT_MAX_LOSS_PERCENT);
    }

    private String formatMaxLoss() {
        String value = String.valueOf(maxLossPercent());
        return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
    }

    private void saveMaxLossPercent() {
        String raw = maxLossInput.getText().toString().trim();
        double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException error) {
            Toast.makeText(this, "请输入 0~100 之间的数字", Toast.LENGTH_SHORT).show();
            return;
        }
        if (value <= 0 || value > 100) {
            Toast.makeText(this, "最大亏损比例需在 0~100 之间", Toast.LENGTH_SHORT).show();
            return;
        }
        preferences.edit().putFloat(State.KEY_MAX_LOSS_PERCENT, (float) value).apply();
        Toast.makeText(this, "已保存：单笔最大亏损 " + formatMaxLoss() + "%", Toast.LENGTH_SHORT)
                .show();
        refreshState();
    }

    // ---------- 价格监控清单 ----------

    private void addAlertFromCapture() {
        String name = preferences.getString(State.KEY_NAME, "");
        String code = preferences.getString(State.KEY_CODE, "");
        if (name.isEmpty() && code.isEmpty()) {
            Toast.makeText(this, "请先在通达信中捕获股票", Toast.LENGTH_SHORT).show();
            return;
        }
        String buy = preferences.getString(State.KEY_BUY, "");
        double defaultPrice;
        try {
            defaultPrice = Double.parseDouble(buy);
        } catch (NumberFormatException error) {
            defaultPrice = 0;
        }
        AlertItem item = new AlertItem(name, code, defaultPrice,
                AlertItem.DIR_ABOVE, false, "");
        editAlertItem(item, true);
    }

    private void addBlankAlert() {
        AlertItem item = new AlertItem("", "", 0, AlertItem.DIR_ABOVE, false, "");
        editAlertItem(item, true);
    }

    private void editAlertItem(AlertItem item, boolean isNew) {
        LinearLayout container = Ui.vertical(this);
        container.setPadding(Ui.dp(this, 16), Ui.dp(this, 10),
                Ui.dp(this, 16), Ui.dp(this, 10));

        EditText nameInput = labeledEdit(container, "股票名称", 180);
        nameInput.setText(item.name);

        EditText codeInput = labeledEdit(container, "股票代码（6 位）", 180);
        codeInput.setText(item.code);

        EditText priceInput = labeledEdit(container, "目标价", 180);
        priceInput.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (item.targetPrice > 0) {
            priceInput.setText(String.valueOf(item.targetPrice));
        }

        LinearLayout dirRow = new LinearLayout(this);
        dirRow.setGravity(Gravity.CENTER_VERTICAL);
        dirRow.addView(Ui.text(this, "触发方向  ", 14, Ui.MUTED));
        CheckBox aboveBox = new CheckBox(this);
        aboveBox.setText("≥ 目标价");
        aboveBox.setTextColor(Ui.WHITE);
        aboveBox.setChecked(AlertItem.DIR_ABOVE.equals(item.direction));
        CheckBox belowBox = new CheckBox(this);
        belowBox.setText("≤ 目标价");
        belowBox.setTextColor(Ui.WHITE);
        belowBox.setChecked(AlertItem.DIR_BELOW.equals(item.direction));
        aboveBox.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) {
                belowBox.setChecked(false);
            }
        });
        belowBox.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked) {
                aboveBox.setChecked(false);
            }
        });
        dirRow.addView(aboveBox);
        dirRow.addView(belowBox);
        container.addView(dirRow);

        LinearLayout enabledRow = new LinearLayout(this);
        enabledRow.setGravity(Gravity.CENTER_VERTICAL);
        enabledRow.addView(Ui.text(this, "勾选监控  ", 14, Ui.MUTED));
        CheckBox enabledBox = new CheckBox(this);
        enabledBox.setText("实时监控（勾选后联网轮询）");
        enabledBox.setTextColor(Ui.WHITE);
        enabledBox.setChecked(item.enabled);
        enabledRow.addView(enabledBox);
        container.addView(enabledRow);

        EditText noteInput = labeledEdit(container, "备注", 180);
        noteInput.setText(item.note);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(isNew ? "新增监控条目" : "编辑监控条目")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newName = nameInput.getText().toString().trim();
                    String newCode = codeInput.getText().toString().trim();
                    double newPrice;
                    try {
                        newPrice = Double.parseDouble(priceInput.getText().toString().trim());
                    } catch (NumberFormatException error) {
                        Toast.makeText(this, "目标价格式无效", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newCode.length() != 6) {
                        Toast.makeText(this, "股票代码须为 6 位", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String newDir = aboveBox.isChecked()
                            ? AlertItem.DIR_ABOVE : AlertItem.DIR_BELOW;
                    String newNote = noteInput.getText().toString().trim();
                    boolean newEnabled = enabledBox.isChecked();
                    AlertItem saved = new AlertItem(
                            item.id, newName, newCode, newPrice, newDir,
                            newEnabled, newNote, item.createdAt);
                    saveAlertItem(saved, isNew);
                    if (newEnabled) {
                        requestNotificationsIfNeeded();
                    }
                    refreshState();
                });
        builder.show();
    }

    private EditText labeledEdit(LinearLayout container, String label, int inputMinHeight) {
        TextView labelView = Ui.text(this, label, 14, Ui.MUTED);
        container.addView(labelView);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(Ui.WHITE);
        input.setPadding(Ui.dp(this, 10), Ui.dp(this, 6),
                Ui.dp(this, 10), Ui.dp(this, 6));
        input.setBackground(Ui.outlined(Ui.BUTTON, Color.rgb(43, 53, 62), 8, this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 4);
        params.bottomMargin = Ui.dp(this, 12);
        container.addView(input, params);
        return input;
    }

    private void saveAlertItem(AlertItem item, boolean isNew) {
        List<AlertItem> items = AlertItem.parse(
                preferences.getString(State.KEY_ALERTS, ""));
        if (isNew) {
            items.add(item);
        } else {
            int index = -1;
            for (int i = 0; i < items.size(); i++) {
                if (item.id.equals(items.get(i).id)) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                items.set(index, item);
            } else {
                items.add(item);
            }
        }
        preferences.edit().putString(State.KEY_ALERTS, AlertItem.serialize(items))
                .apply();
    }

    private void deleteAlertItem(AlertItem item) {
        List<AlertItem> items = AlertItem.parse(
                preferences.getString(State.KEY_ALERTS, ""));
        List<AlertItem> kept = new ArrayList<>();
        for (AlertItem existing : items) {
            if (!existing.id.equals(item.id)) {
                kept.add(existing);
            }
        }
        preferences.edit().putString(State.KEY_ALERTS, AlertItem.serialize(kept))
                .apply();
        refreshState();
    }

    private void renderAlertList() {
        if (alertList == null) {
            return;
        }
        alertList.removeAllViews();
        List<AlertItem> items = AlertItem.parse(
                preferences.getString(State.KEY_ALERTS, ""));
        if (items.isEmpty()) {
            TextView empty = Ui.text(this,
                    "暂无监控条目。先在通达信中捕获股票，再点击“＋ 从已捕获股票添加”。",
                    13, Ui.MUTED);
            alertList.addView(empty);
            return;
        }
        for (AlertItem item : items) {
            alertList.addView(buildAlertRow(item), Ui.params(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 6, this));
        }
    }

    private View buildAlertRow(AlertItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.outlined(Ui.BUTTON, Color.rgb(43, 53, 62), 10, this));
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(item.enabled);
        checkBox.setOnCheckedChangeListener((buttonView, checked) -> {
            saveAlertItem(item.withEnabled(checked), false);
            if (checked) {
                requestNotificationsIfNeeded();
            }
            refreshState();
        });
        row.addView(checkBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout mid = Ui.vertical(this);
        mid.setGravity(Gravity.CENTER_VERTICAL);

        String cond = AlertItem.DIR_ABOVE.equals(item.direction) ? "≥" : "≤";
        TextView title = Ui.text(this,
                item.display() + "   目标 " + cond + " " + item.targetPrice,
                14, item.enabled ? Ui.WHITE : Ui.MUTED);
        mid.addView(title);

        if (!item.note.isEmpty()) {
            TextView note = Ui.text(this, item.note, 12, Ui.MUTED);
            mid.addView(note);
        }

        row.addView(mid, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView editBtn = Ui.text(this, "编辑", 13, Ui.ACCENT);
        editBtn.setPadding(Ui.dp(this, 8), 0, 0, 0);
        editBtn.setOnClickListener(view -> editAlertItem(item, false));
        row.addView(editBtn);

        TextView delBtn = Ui.text(this, "删除", 13, Ui.DANGER);
        delBtn.setPadding(Ui.dp(this, 10), 0, 0, 0);
        delBtn.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle("删除监控")
                .setMessage("确定删除 " + item.display() + " 吗？")
                .setPositiveButton("删除", (d, w) -> deleteAlertItem(item))
                .setNegativeButton("取消", null)
                .show());
        row.addView(delBtn);

        row.setOnClickListener(view -> editAlertItem(item, false));
        return row;
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_POST_NOTIFICATIONS);
            }
        }
        AlertMonitor.ensureChannel(this);
    }

    private void copyDiagnostics() {
        String value = preferences.getString(State.KEY_DIAGNOSTICS, "");
        String headerText = preferences.getString(State.KEY_HEADER_TEXT, "");
        if (value.isEmpty() && headerText.isEmpty()) {
            Toast.makeText(this, "请先在通达信十字光标页面执行诊断", Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder report = new StringBuilder();
        if (!value.isEmpty()) {
            report.append(value);
        }
        if (!headerText.isEmpty()) {
            if (report.length() > 0) {
                report.append("\n\n");
            }
            report.append("标题区 OCR 原文：\n").append(headerText);
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("通达信页面元素诊断", report.toString()));
        Toast.makeText(this, "完整诊断结果已复制", Toast.LENGTH_SHORT).show();
    }

    private void exportBacktest() {
        List<BacktestLog.Entry> entries = BacktestLog.parse(
                preferences.getString(State.KEY_BACKTEST_LOG, ""));
        String name = preferences.getString(State.KEY_NAME, "");
        String code = preferences.getString(State.KEY_CODE, "");
        String text = BacktestLog.export(entries, name, code);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("回测记录", text));
        Toast.makeText(this, "回测记录已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    private void undoBacktest() {
        List<BacktestLog.Entry> entries = BacktestLog.parse(
                preferences.getString(State.KEY_BACKTEST_LOG, ""));
        if (entries.isEmpty()) {
            Toast.makeText(this, "暂无回测记录", Toast.LENGTH_SHORT).show();
            return;
        }
        entries.remove(entries.size() - 1);
        preferences.edit().putString(State.KEY_BACKTEST_LOG,
                BacktestLog.serialize(entries)).apply();
        Toast.makeText(this, "已撤销上一笔", Toast.LENGTH_SHORT).show();
        refreshState();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException error) {
            Toast.makeText(this, "无法打开系统无障碍设置，请手动进入系统设置", Toast.LENGTH_LONG).show();
        }
    }

    private void sendControl(String action) {
        Intent intent = new Intent(this, ControlReceiver.class);
        intent.setAction(action);
        sendBroadcast(intent);
        handler.postDelayed(this::refreshState, 150);
    }

    private boolean isAccessibilityEnabled() {
        ComponentName expected = new ComponentName(this, CrosshairAccessibilityService.class);
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        for (String component : enabled.split(":")) {
            ComponentName name = ComponentName.unflattenFromString(component);
            if (expected.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void refreshState() {
        if (serviceStatus == null) {
            return;
        }
        boolean enabled = isAccessibilityEnabled();
        boolean running = CrosshairAccessibilityService.isRunning();
        serviceStatus.setText(enabled
                ? (running ? "● 服务已连接，可以捕获" : "● 服务已授权，等待系统连接")
                : "○ 尚未启用无障碍服务");
        serviceStatus.setTextColor(enabled ? Ui.ACCENT : Ui.DANGER);

        boolean visible = preferences.getBoolean(State.KEY_VISIBLE, true);
        overlayButton.setText(visible ? "2  隐藏悬浮窗" : "2  显示悬浮窗");

        String stop = preferences.getString(State.KEY_STOP, "");
        String buy = preferences.getString(State.KEY_BUY, "");
        String name = preferences.getString(State.KEY_NAME, "");
        String code = preferences.getString(State.KEY_CODE, "");
        String message = preferences.getString(State.KEY_MESSAGE, "尚未捕获完整价格");
        String packageName = preferences.getString(State.KEY_PACKAGE, "");
        String stockLine = name.isEmpty() && code.isEmpty()
                ? "" : name + (name.isEmpty() || code.isEmpty() ? "" : " ") + code + "\n";
        foregroundPackage.setText(packageName.isEmpty()
                ? "前台应用：等待识别" : "最近前台包名：" + packageName);

        if (!stop.isEmpty() && !buy.isEmpty()) {
            try {
                RiskCalculator.Calculation result =
                        RiskCalculator.calculate(stop, buy, maxLossPercent());
                quote.setText(result.target);
                quote.setTextColor(Ui.ACCENT);
                boolean overAllIn = Double.parseDouble(result.positionPercent) > 100;
                String positionLine = "可用仓位 " + result.positionPercent + "%"
                        + (overAllIn
                        ? "（超出可用资金）"
                        : "（单笔最大亏损 " + formatMaxLoss() + "%）");
                detail.setText(stockLine + "止损 " + result.stop + "  ·  买入 " + result.buy
                        + "\n每股风险 " + result.risk + "  ·  2R 收益 " + result.profit
                        + "\n" + positionLine
                        + "\n" + message);
            } catch (IllegalArgumentException error) {
                quote.setText("--");
                quote.setTextColor(Ui.DANGER);
                detail.setText(stockLine + "止损 " + stop + "  ·  买入 " + buy
                        + "\n" + error.getMessage());
            }
        } else {
            quote.setText("--");
            quote.setTextColor(Ui.ACCENT);
            detail.setText(stockLine + (stop.isEmpty() ? "止损 --" : "止损 " + stop)
                    + "  ·  " + (buy.isEmpty() ? "买入 --" : "买入 " + buy)
                    + "\n" + message);
        }

        String diagnosticText = preferences.getString(State.KEY_DIAGNOSTICS, "");
        diagnostics.setText(diagnosticText.isEmpty() ? "暂无诊断结果" : diagnosticText);
        String headerText = preferences.getString(State.KEY_HEADER_TEXT, "");
        headerOcr.setText(headerText.isEmpty()
                ? "标题区识别：暂无" : "标题区识别原文：\n" + headerText);

        boolean backtestMode = BacktestLog.MODE_BACKTEST.equals(
                preferences.getString(State.KEY_MODE, BacktestLog.MODE_COMPUTE));
        modeButton.setText(backtestMode
                ? "当前：回测模式（点按切回计算）" : "当前：计算模式（点按切回回测）");
        List<BacktestLog.Entry> entries = BacktestLog.parse(
                preferences.getString(State.KEY_BACKTEST_LOG, ""));
        BacktestLog.Stats stats = BacktestLog.stats(entries, name, code);
        String scope = name.isEmpty() && code.isEmpty() ? "全部记录" : "当前股票";
        backtestStats.setText(scope + " · 已记录 " + stats.count + " 笔 · 胜率 "
                + stats.winRate + "% · 总R " + stats.totalR
                + " · 平均R " + stats.averageR);
        backtestList.setText(entries.isEmpty()
                ? "暂无回测记录" : BacktestLog.export(entries, name, code));

        renderAlertList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
}
