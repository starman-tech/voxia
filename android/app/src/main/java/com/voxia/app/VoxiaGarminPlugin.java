// ─────────────────────────────────────────────────────────────────────────────
// VoxiaGarminPlugin.java
// Plugin Capacitor qui fait le pont entre :
//   ● Garmin Connect Mobile SDK  (messages watch ↔ phone)
//   ● WebView Voxia (JavaScript via Capacitor)
//
// INSTALLATION :
//   1. Copier dans :  android/app/src/main/java/com/voxia/app/VoxiaGarminPlugin.java
//   2. Ajouter la dépendance dans android/app/build.gradle :
//        implementation 'com.garmin.connectiq:mobile-sdk:2.0.1'
//   3. Enregistrer dans MainActivity.java :
//        add(VoxiaGarminPlugin.class);
// ─────────────────────────────────────────────────────────────────────────────

package com.voxia.app;

import android.content.Context;
import android.util.Log;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener;
import com.garmin.android.connectiq.ConnectIQ.IQConnectType;
import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener;
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.IQMessage;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

@CapacitorPlugin(name = "VoxiaGarmin")
public class VoxiaGarminPlugin extends Plugin implements ConnectIQListener {

    private static final String TAG = "VoxiaGarmin";

    // UUID de l'app Garmin VoxiaWatch (doit correspondre au manifest.xml Garmin)
    private static final String VOXIA_WATCH_APP_ID = "a3b4c5d6-e7f8-0a1b-2c3d-4e5f60718293";

    private ConnectIQ connectIQ;
    private IQDevice  connectedDevice;
    private IQApp     watchApp;
    private boolean   sdkReady = false;

    // ── Initialisation ────────────────────────────────────────────────────────
    @Override
    public void load() {
        Context ctx = getContext();
        connectIQ = ConnectIQ.getInstance(ctx, IQConnectType.WIRELESS);
        connectIQ.initialize(ctx, true, this);
        Log.d(TAG, "ConnectIQ SDK initializing...");
    }

    // ── Méthodes exposées au JavaScript ──────────────────────────────────────

    /**
     * Appelée depuis JS : voxiaGarmin.sendToWatch({cmd: "start"})
     * Envoie un message JSON à la montre Garmin.
     */
    @PluginMethod
    public void sendToWatch(PluginCall call) {
        if (!sdkReady || connectedDevice == null || watchApp == null) {
            call.reject("Garmin non connecté");
            return;
        }

        String cmd = call.getString("cmd", "");
        JSONObject payload = new JSONObject();
        try {
            payload.put("cmd", cmd);
        } catch (JSONException e) {
            call.reject("JSON error: " + e.getMessage());
            return;
        }

        try {
            connectIQ.sendMessage(connectedDevice, watchApp, payload,
                new ConnectIQ.IQSendMessageListener() {
                    @Override
                    public void onMessageStatus(IQDevice d, IQApp app, IQMessageStatus status) {
                        if (status == IQMessageStatus.SUCCESS) {
                            call.resolve();
                        } else {
                            call.reject("Envoi échoué : " + status.name());
                        }
                    }
                });
        } catch (InvalidStateException | ServiceUnavailableException e) {
            call.reject("Erreur envoi : " + e.getMessage());
        }
    }

    /**
     * Appelée depuis JS pour envoyer la transcription vers la montre.
     * voxiaGarmin.sendTranscript({status:"idle", text:"mon texte..."})
     */
    @PluginMethod
    public void sendTranscript(PluginCall call) {
        if (!sdkReady || connectedDevice == null || watchApp == null) {
            call.reject("Garmin non connecté");
            return;
        }

        String status = call.getString("status", "idle");
        String text   = call.getString("text", "");
        int elapsed   = call.getInt("elapsed", 0);

        JSONObject payload = new JSONObject();
        try {
            payload.put("status", status);
            if (!text.isEmpty()) { payload.put("text", text); }
            if (elapsed > 0)     { payload.put("elapsed", elapsed); }
        } catch (JSONException e) {
            call.reject("JSON error"); return;
        }

        try {
            connectIQ.sendMessage(connectedDevice, watchApp, payload,
                (d, app, s) -> {
                    if (s == IQMessageStatus.SUCCESS) { call.resolve(); }
                    else { call.reject("Envoi échoué"); }
                });
        } catch (Exception e) {
            call.reject("Erreur : " + e.getMessage());
        }
    }

    /**
     * Retourne l'état de la connexion Garmin.
     */
    @PluginMethod
    public void getStatus(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("connected", sdkReady && connectedDevice != null);
        ret.put("deviceName", connectedDevice != null
                ? connectedDevice.getFriendlyName() : "");
        call.resolve(ret);
    }

    // ── ConnectIQListener ─────────────────────────────────────────────────────
    @Override
    public void onInitializeError(ConnectIQ.IQSdkErrorStatus errStatus) {
        Log.e(TAG, "SDK init error: " + errStatus.name());
        sdkReady = false;
        notifyGarminStatus(false, "Erreur SDK : " + errStatus.name());
    }

    @Override
    public void onSdkReady() {
        Log.d(TAG, "SDK ready");
        sdkReady = true;
        findFirstDevice();
    }

    @Override
    public void onSdkShutDown() {
        sdkReady = false;
        connectedDevice = null;
        notifyGarminStatus(false, "SDK arrêté");
    }

    // ── Recherche de la montre ────────────────────────────────────────────────
    private void findFirstDevice() {
        try {
            List<IQDevice> devices = connectIQ.getKnownDevices();
            if (devices == null || devices.isEmpty()) {
                notifyGarminStatus(false, "Aucune montre détectée");
                return;
            }
            for (IQDevice device : devices) {
                registerDevice(device);
                break; // Prend la première (Quatix 7 en général)
            }
        } catch (InvalidStateException | ServiceUnavailableException e) {
            Log.e(TAG, "findDevices: " + e.getMessage());
        }
    }

    private void registerDevice(IQDevice device) {
        try {
            connectIQ.registerForDeviceEvents(device, (dev, status) -> {
                Log.d(TAG, "Device " + dev.getFriendlyName() + " → " + status.name());
                if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                    connectedDevice = dev;
                    registerWatchApp(dev);
                    notifyGarminStatus(true, dev.getFriendlyName());
                } else {
                    if (connectedDevice != null &&
                        connectedDevice.getDeviceIdentifier() == dev.getDeviceIdentifier()) {
                        connectedDevice = null;
                        watchApp = null;
                    }
                    notifyGarminStatus(false, "Déconnecté");
                }
            });

            // Vérification état initial
            IQDevice.IQDeviceStatus st = connectIQ.getDeviceStatus(device);
            if (st == IQDevice.IQDeviceStatus.CONNECTED) {
                connectedDevice = device;
                registerWatchApp(device);
                notifyGarminStatus(true, device.getFriendlyName());
            }
        } catch (Exception e) {
            Log.e(TAG, "registerDevice: " + e.getMessage());
        }
    }

    private void registerWatchApp(IQDevice device) {
        try {
            watchApp = new IQApp(VOXIA_WATCH_APP_ID);
            connectIQ.registerForAppEvents(device, watchApp, (dev, app, messages, status) -> {
                // Message reçu de la montre → forwarder au JS
                if (messages != null) {
                    for (Object msg : messages) {
                        handleWatchMessage(msg);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "registerWatchApp: " + e.getMessage());
        }
    }

    // ── Réception message montre → JS ─────────────────────────────────────────
    private void handleWatchMessage(Object rawMsg) {
        try {
            JSObject event = new JSObject();
            if (rawMsg instanceof JSONObject) {
                JSONObject json = (JSONObject) rawMsg;
                String cmd = json.optString("cmd", "");
                event.put("cmd", cmd);
                event.put("raw", json.toString());
            } else {
                event.put("raw", rawMsg.toString());
            }
            // Notifie le JavaScript Voxia
            notifyListeners("garminMessage", event);
        } catch (Exception e) {
            Log.e(TAG, "handleWatchMessage: " + e.getMessage());
        }
    }

    // Notifie JS de l'état de connexion Garmin
    private void notifyGarminStatus(boolean connected, String deviceName) {
        JSObject event = new JSObject();
        event.put("connected", connected);
        event.put("deviceName", deviceName);
        notifyListeners("garminStatus", event);
    }

    public void onDestroy() {
        if (connectIQ != null) {
            try { connectIQ.shutdown(getContext()); }
            catch (Exception e) { /* ignore */ }
        }
    }
}
