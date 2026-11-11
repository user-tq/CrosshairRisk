package com.crosshair.risk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

public final class ControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        SharedPreferences preferences = context.getSharedPreferences(State.PREFS, Context.MODE_PRIVATE);
        CrosshairAccessibilityService service = CrosshairAccessibilityService.getInstance();

        if (State.ACTION_CLEAR.equals(action)) {
            preferences.edit()
                    .remove(State.KEY_STOP)
                    .remove(State.KEY_BUY)
                    .putString(State.KEY_MESSAGE, "价格已清空")
                    .apply();
            if (service != null) {
                service.refreshFromPreferences();
            }
            return;
        }

        if (State.ACTION_TOGGLE.equals(action)) {
            boolean show = !preferences.getBoolean(State.KEY_VISIBLE, true);
            preferences.edit().putBoolean(State.KEY_VISIBLE, show).apply();
            if (service == null) {
                Toast.makeText(context, "请先在系统设置中启用本应用的无障碍服务", Toast.LENGTH_LONG).show();
            } else {
                service.applyOverlayVisibility();
            }
        }
    }
}
