package com.voxia.app;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "NightMode")
public class NightModePlugin extends Plugin {

    @PluginMethod
    public void enable(PluginCall call) {
        MainActivity.setNightMode(true);
        call.resolve();
    }

    @PluginMethod
    public void disable(PluginCall call) {
        MainActivity.setNightMode(false);
        call.resolve();
    }
}