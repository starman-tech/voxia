package com.voxia.app;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.Window;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    public static boolean nightMode = false;
    private static MainActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        registerPlugin(NightModePlugin.class);
    }

    public static void setNightMode(boolean enabled) {
        nightMode = enabled;
        if (instance != null) {
            instance.runOnUiThread(() -> {
                instance.applySystemUI();
                // Double appel après délai pour contrer le re-show Android
                instance.getWindow().getDecorView().postDelayed(
                    () -> instance.applySystemUI(), 300
                );
            });
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applySystemUI();
    }

    public void applySystemUI() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(!nightMode);
            WindowInsetsController c = window.getInsetsController();
            if (c != null) {
                if (nightMode) {
                    c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                } else {
                    c.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    window.setDecorFitsSystemWindows(true);
                }
            }
        } else {
            View v = window.getDecorView();
            if (nightMode) {
                v.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                );
            } else {
                v.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                );
            }
        }
    }
}