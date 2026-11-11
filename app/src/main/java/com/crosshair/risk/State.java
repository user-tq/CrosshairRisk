package com.crosshair.risk;

final class State {
    static final String PREFS = "crosshair_risk_state";
    static final String KEY_VISIBLE = "overlay_visible";
    static final String KEY_STOP = "stop_price";
    static final String KEY_BUY = "buy_price";
    static final String KEY_MESSAGE = "last_message";
    static final String KEY_PACKAGE = "foreground_package";
    static final String KEY_DIAGNOSTICS = "diagnostics";

    static final String ACTION_TOGGLE = "com.crosshair.risk.action.TOGGLE";
    static final String ACTION_CLEAR = "com.crosshair.risk.action.CLEAR";

    private State() {
    }
}
