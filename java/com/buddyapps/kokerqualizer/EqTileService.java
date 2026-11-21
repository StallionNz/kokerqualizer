package com.buddyapps.kokerqualizer;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class EqTileService extends TileService {

    public static void requestRefresh(Context ctx) {
        try {
            requestListeningState(ctx, new ComponentName(ctx, EqTileService.class));
        } catch (Throwable ignored) {}
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        syncTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        EqStore store = EqStore.get(this);
        store.getSettings().eqEnabled = !store.getSettings().eqEnabled;
        store.save();
        EqService.startOrRefresh(this);
        syncTile();
    }

    private void syncTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        EqStore store = EqStore.get(this);
        boolean on = store.getSettings().eqEnabled;
        EqStore.EqPreset p = store.getActivePreset();
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("Kokerqualizer");
        if (Build.VERSION.SDK_INT >= 29) {
            tile.setSubtitle(on ? (p != null ? p.name : "Active") : "Off");
        }
        tile.updateTile();
    }
}
