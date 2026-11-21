package com.buddyapps.kokerqualizer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class EqService extends Service {
    private static final String TAG = "KokerEqService";
    public static final String CHANNEL_ID = "kokerqualizer_dsp";
    public static final int NOTIF_ID = 1042;

    public static final String ACTION_START = "com.buddyapps.kokerqualizer.START";
    public static final String ACTION_REFRESH = "com.buddyapps.kokerqualizer.REFRESH";
    public static final String ACTION_TOGGLE_POWER = "com.buddyapps.kokerqualizer.TOGGLE_POWER";
    public static final String ACTION_PREV_PRESET = "com.buddyapps.kokerqualizer.PREV_PRESET";
    public static final String ACTION_NEXT_PRESET = "com.buddyapps.kokerqualizer.NEXT_PRESET";
    public static final String ACTION_BT_CONNECTED = "com.buddyapps.kokerqualizer.BT_CONNECTED";
    public static final String ACTION_BT_DISCONNECTED = "com.buddyapps.kokerqualizer.BT_DISCONNECTED";
    public static final String ACTION_STATE_BROADCAST = "com.buddyapps.kokerqualizer.STATE_CHANGED";

    public static final String EXTRA_BT_ADDRESS = "bt_address";
    public static final String EXTRA_BT_NAME = "bt_name";

    private static String lastBtConnectAddr = null;
    private static long lastBtConnectMs = 0;
    private static String lastBtDisconnectAddr = null;
    private static long lastBtDisconnectMs = 0;

    private BroadcastReceiver dynamicReceiver;
    private AudioDeviceCallback audioDeviceCallback;
    private BubbleOverlayManager bubbleManager;
    private boolean isForeground = false;
    private boolean wiredPluggedLast = false;

    public static void startOrRefresh(Context ctx) {
        Intent i = new Intent(ctx, EqService.class);
        i.setAction(ACTION_REFRESH);
        try {
            ctx.startForegroundService(i);
        } catch (Throwable t) {
            Log.w(TAG, "startForegroundService blocked: " + t.getMessage());
            EqEngine.get(ctx).applyCurrent();
            KokerWidgetProvider.updateAll(ctx);
            EqTileService.requestRefresh(ctx);
        }
    }

    public static void applyBtRuleDirect(Context ctx, EqStore.BtDeviceRule rule, boolean launchCar) {
        if (rule == null) return;
        EqStore store = EqStore.get(ctx);
        store.getSettings().eqEnabled = true;
        if (rule.presetId != null && store.findPreset(rule.presetId) != null) {
            store.getSettings().activePresetId = rule.presetId;
        }
        store.save();
        EqEngine.get(ctx).setAbFlatCompare(false);
        EqEngine.get(ctx).applyCurrent();

        if (rule.setVolume) {
            try {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int target = Math.round((rule.volumePercent / 100f) * max);
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
                }
            } catch (Throwable ignored) {}
        }

        if (launchCar && rule.autoCarMode) {
            try {
                Intent car = new Intent(ctx, CarModeActivity.class);
                car.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                ctx.startActivity(car);
            } catch (Throwable ignored) {}
        }

        KokerWidgetProvider.updateAll(ctx);
        EqTileService.requestRefresh(ctx);
        Intent b = new Intent(ACTION_STATE_BROADCAST);
        b.setPackage(ctx.getPackageName());
        ctx.sendBroadcast(b);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        bubbleManager = new BubbleOverlayManager(this);
        registerDynamicReceivers();
        registerWiredAudioCallback();
    }

    private void registerWiredAudioCallback() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        wiredPluggedLast = hasWiredOrUsbOutput(am);
        audioDeviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                checkWiredStateChange(am);
            }
            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                checkWiredStateChange(am);
            }
        };
        am.registerAudioDeviceCallback(audioDeviceCallback, new Handler(Looper.getMainLooper()));
    }

    private boolean hasWiredOrUsbOutput(AudioManager am) {
        try {
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || t == AudioDeviceInfo.TYPE_USB_HEADSET
                        || t == AudioDeviceInfo.TYPE_USB_DEVICE) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean hasBluetoothOutput(AudioManager am) {
        try {
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || t == AudioDeviceInfo.TYPE_BLE_HEADSET
                        || t == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void checkWiredStateChange(AudioManager am) {
        boolean nowWired = hasWiredOrUsbOutput(am);
        if (nowWired == wiredPluggedLast) return;
        wiredPluggedLast = nowWired;

        EqStore store = EqStore.get(this);
        EqStore.Settings s = store.getSettings();
        if (!s.wiredAutoSwitch) return;

        if (nowWired) {
            if (s.wiredPresetId != null && store.findPreset(s.wiredPresetId) != null) {
                s.eqEnabled = true;
                s.activePresetId = s.wiredPresetId;
                store.save();
                EqEngine.get(this).setAbFlatCompare(false);
                EqEngine.get(this).applyCurrent();
                updateNotificationAndUi();
            }
        } else if (!hasBluetoothOutput(am)) {
            if (s.phonePresetId != null && store.findPreset(s.phonePresetId) != null) {
                s.activePresetId = s.phonePresetId;
                store.save();
                EqEngine.get(this).setAbFlatCompare(false);
                EqEngine.get(this).applyCurrent();
                updateNotificationAndUi();
            }
        }
    }

    private void registerDynamicReceivers() {
        dynamicReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String a = intent.getAction();
                if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(a)) {
                    int sid = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
                    EqEngine.get(context).onExternalSessionOpened(sid);
                    updateNotificationAndUi();
                } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(a)) {
                    int sid = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
                    EqEngine.get(context).onExternalSessionClosed(sid);
                    updateNotificationAndUi();
                } else if ("android.media.VOLUME_CHANGED_ACTION".equals(a)) {
                    if (EqStore.get(context).getSettings().autoLoudness) {
                        EqEngine.get(context).applyCurrent();
                        updateNotificationAndUi();
                    }
                } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) {
                    BluetoothDevice dev = extractBtDevice(intent);
                    if (dev != null) handleBtConnected(dev.getAddress(), safeDevName(dev));
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) {
                    BluetoothDevice dev = extractBtDevice(intent);
                    if (dev != null) handleBtDisconnected(dev.getAddress());
                } else if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(a)) {
                    int state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1);
                    BluetoothDevice dev = extractBtDevice(intent);
                    if (dev != null) {
                        if (state == BluetoothA2dp.STATE_CONNECTED) {
                            handleBtConnected(dev.getAddress(), safeDevName(dev));
                        } else if (state == BluetoothA2dp.STATE_DISCONNECTED) {
                            handleBtDisconnected(dev.getAddress());
                        }
                    }
                }
            }
        };

        IntentFilter f = new IntentFilter();
        f.addAction("android.media.VOLUME_CHANGED_ACTION");
        f.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        f.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dynamicReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(dynamicReceiver, f);
        }
    }

    @SuppressWarnings("deprecation")
    public static BluetoothDevice extractBtDevice(Intent intent) {
        if (intent == null) return null;
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        }
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }

    private String safeDevName(BluetoothDevice dev) {
        try {
            String n = dev.getName();
            return n != null ? n : "Bluetooth Device";
        } catch (SecurityException e) {
            return "Bluetooth Device";
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean fgOk = ensureForeground();
        EqStore store = EqStore.get(this);
        String action = intent != null ? intent.getAction() : ACTION_START;

        if (ACTION_TOGGLE_POWER.equals(action)) {
            store.getSettings().eqEnabled = !store.getSettings().eqEnabled;
            store.save();
            EqEngine.get(this).applyCurrent();
        } else if (ACTION_PREV_PRESET.equals(action)) {
            store.cyclePreset(-1);
            EqEngine.get(this).setAbFlatCompare(false);
            EqEngine.get(this).applyCurrent();
        } else if (ACTION_NEXT_PRESET.equals(action)) {
            store.cyclePreset(1);
            EqEngine.get(this).setAbFlatCompare(false);
            EqEngine.get(this).applyCurrent();
        } else if (ACTION_BT_CONNECTED.equals(action) && intent != null) {
            handleBtConnected(
                    intent.getStringExtra(EXTRA_BT_ADDRESS),
                    intent.getStringExtra(EXTRA_BT_NAME));
        } else if (ACTION_BT_DISCONNECTED.equals(action) && intent != null) {
            handleBtDisconnected(intent.getStringExtra(EXTRA_BT_ADDRESS));
        } else {
            EqEngine.get(this).applyCurrent();
        }

        updateNotificationAndUi();
        if (!fgOk) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void handleBtConnected(String address, String name) {
        if (address == null) return;
        long now = System.currentTimeMillis();
        if (address.equalsIgnoreCase(lastBtConnectAddr) && (now - lastBtConnectMs) < 3500) {
            return;
        }
        lastBtConnectAddr = address;
        lastBtConnectMs = now;

        EqStore store = EqStore.get(this);
        EqStore.BtDeviceRule rule = store.findBtRule(address);
        if (rule == null || !rule.autoStart) return;

        applyBtRuleDirect(this, rule, true);
        if (store.getSettings().bubbleAutoOnBt && BubbleOverlayManager.canDraw(this)) {
            store.getSettings().bubbleEnabled = true;
            store.save();
        }
        updateNotificationAndUi();
    }

    private void handleBtDisconnected(String address) {
        if (address == null) return;
        long now = System.currentTimeMillis();
        if (address.equalsIgnoreCase(lastBtDisconnectAddr) && (now - lastBtDisconnectMs) < 3500) {
            return;
        }
        lastBtDisconnectAddr = address;
        lastBtDisconnectMs = now;

        EqStore store = EqStore.get(this);
        EqStore.BtDeviceRule rule = store.findBtRule(address);
        if (rule == null || !rule.autoStart) return;

        String phoneId = store.getSettings().phonePresetId;
        if (phoneId != null && store.findPreset(phoneId) != null) {
            store.getSettings().activePresetId = phoneId;
            store.save();
            EqEngine.get(this).setAbFlatCompare(false);
            EqEngine.get(this).applyCurrent();
            updateNotificationAndUi();
        }
    }

    private boolean ensureForeground() {
        Notification n = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, n);
            }
            isForeground = true;
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "startForeground failed: " + t.getMessage());
            return isForeground;
        }
    }

    private void updateNotificationAndUi() {
        if (bubbleManager != null) {
            bubbleManager.syncVisibility();
        }
        if (isForeground) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                try { nm.notify(NOTIF_ID, buildNotification()); } catch (Throwable ignored) {}
            }
        }
        KokerWidgetProvider.updateAll(this);
        EqTileService.requestRefresh(this);
        Intent b = new Intent(ACTION_STATE_BROADCAST);
        b.setPackage(getPackageName());
        sendBroadcast(b);
    }

    private Notification buildNotification() {
        EqStore store = EqStore.get(this);
        EqStore.Settings s = store.getSettings();
        EqStore.EqPreset p = store.getActivePreset();
        String presetName = p != null ? p.name : "Flat";
        String status = EqEngine.get(this).getStatusText();

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent prevPi = serviceActionPi(ACTION_PREV_PRESET, 2);
        PendingIntent togglePi = serviceActionPi(ACTION_TOGGLE_POWER, 3);
        PendingIntent nextPi = serviceActionPi(ACTION_NEXT_PRESET, 4);

        Notification.Builder nb = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("Kokerqualizer • " + presetName)
                .setContentText(s.eqEnabled ? status : "Bypassed — Tap to resume")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "◀ Preset", prevPi).build())
                .addAction(new Notification.Action.Builder(null, s.eqEnabled ? "Bypass" : "Enable", togglePi).build())
                .addAction(new Notification.Action.Builder(null, "Preset ▶", nextPi).build());

        return nb.build();
    }

    private PendingIntent serviceActionPi(String action, int reqCode) {
        Intent i = new Intent(this, EqService.class);
        i.setAction(action);
        return PendingIntent.getService(this, reqCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Kokerqualizer Audio Engine",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Active equalizer controls and background DSP status");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (bubbleManager != null) {
            bubbleManager.onScreenConfigChanged();
        }
    }

    @Override
    public void onDestroy() {
        isForeground = false;
        if (bubbleManager != null) {
            bubbleManager.hide();
        }
        if (dynamicReceiver != null) {
            try { unregisterReceiver(dynamicReceiver); } catch (Throwable ignored) {}
        }
        if (audioDeviceCallback != null) {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                try { am.unregisterAudioDeviceCallback(audioDeviceCallback); } catch (Throwable ignored) {}
            }
        }
        EqEngine.get(this).stopVisualizer();
        EqEngine.get(this).releaseDsp();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
