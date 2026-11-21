package com.buddyapps.kokerqualizer;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;

public class BluetoothAndBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        EqStore store = EqStore.get(context);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (store.getSettings().startOnBoot) {
                EqService.startOrRefresh(context);
            }
            return;
        }

        if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
            int sid = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            EqEngine.get(context).onExternalSessionOpened(sid);
            return;
        }

        if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
            int sid = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0);
            EqEngine.get(context).onExternalSessionClosed(sid);
            return;
        }

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            BluetoothDevice dev = EqService.extractBtDevice(intent);
            if (dev != null) forwardBt(context, EqService.ACTION_BT_CONNECTED, dev);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            BluetoothDevice dev = EqService.extractBtDevice(intent);
            if (dev != null) forwardBt(context, EqService.ACTION_BT_DISCONNECTED, dev);
        } else if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1);
            BluetoothDevice dev = EqService.extractBtDevice(intent);
            if (dev != null) {
                if (state == BluetoothA2dp.STATE_CONNECTED) {
                    forwardBt(context, EqService.ACTION_BT_CONNECTED, dev);
                } else if (state == BluetoothA2dp.STATE_DISCONNECTED) {
                    forwardBt(context, EqService.ACTION_BT_DISCONNECTED, dev);
                }
            }
        }
    }

    private void forwardBt(Context context, String serviceAction, BluetoothDevice dev) {
        EqStore store = EqStore.get(context);
        EqStore.BtDeviceRule rule = store.findBtRule(dev.getAddress());
        if (rule == null || !rule.autoStart) return;

        Intent svc = new Intent(context, EqService.class);
        svc.setAction(serviceAction);
        svc.putExtra(EqService.EXTRA_BT_ADDRESS, dev.getAddress());
        try {
            svc.putExtra(EqService.EXTRA_BT_NAME, dev.getName());
        } catch (SecurityException ignored) {}

        try {
            context.startForegroundService(svc);
        } catch (Throwable t) {
            // Android 14+ background FGS restriction fallback: apply rule directly without crashing
            if (EqService.ACTION_BT_CONNECTED.equals(serviceAction)) {
                EqService.applyBtRuleDirect(context, rule, true);
            } else {
                String phoneId = store.getSettings().phonePresetId;
                if (phoneId != null && store.findPreset(phoneId) != null) {
                    store.getSettings().activePresetId = phoneId;
                    store.save();
                    EqEngine.get(context).setAbFlatCompare(false);
                    EqEngine.get(context).applyCurrent();
                    KokerWidgetProvider.updateAll(context);
                    EqTileService.requestRefresh(context);
                }
            }
        }
    }
}
