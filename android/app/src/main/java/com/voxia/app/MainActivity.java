package com.voxia.app;

import com.getcapacitor.BridgeActivity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
public class MainActivity extends BridgeActivity {}
// Colle ces deux méthodes dans ta MainActivity
@Override
protected void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) applySystemUI();
}

void applySystemUI() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            // Masque STATUS BAR + NAV BAR si le flag nightMode est actif
            // Sinon restaure
            boolean night = getBridge().getWebView()
                .evaluateJavascriptSync("String(window.__nightMode||false)").equals("true");
            if (night) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.systemBars());
            }
        }
    } else {
        View v = getWindow().getDecorView();
        v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
}