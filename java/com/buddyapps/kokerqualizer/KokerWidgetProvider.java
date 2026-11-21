package com.buddyapps.kokerqualizer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class KokerWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TOGGLE = "com.buddyapps.kokerqualizer.WIDGET_TOGGLE";
    public static final String ACTION_PREV = "com.buddyapps.kokerqualizer.WIDGET_PREV";
    public static final String ACTION_NEXT = "com.buddyapps.kokerqualizer.WIDGET_NEXT";

    public static void updateAll(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, KokerWidgetProvider.class));
            if (ids != null && ids.length > 0) {
                for (int id : ids) {
                    updateSingle(ctx, mgr, id);
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            updateSingle(context, appWidgetManager, id);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null || intent.getAction() == null) return;
        String a = intent.getAction();
        EqStore store = EqStore.get(context);
        if (ACTION_TOGGLE.equals(a)) {
            store.getSettings().eqEnabled = !store.getSettings().eqEnabled;
            store.save();
            EqService.startOrRefresh(context);
            updateAll(context);
        } else if (ACTION_PREV.equals(a)) {
            store.cyclePreset(-1);
            EqEngine.get(context).setAbFlatCompare(false);
            EqService.startOrRefresh(context);
            updateAll(context);
        } else if (ACTION_NEXT.equals(a)) {
            store.cyclePreset(1);
            EqEngine.get(context).setAbFlatCompare(false);
            EqService.startOrRefresh(context);
            updateAll(context);
        }
    }

    private static void updateSingle(Context ctx, AppWidgetManager mgr, int widgetId) {
        EqStore store = EqStore.get(ctx);
        boolean on = store.getSettings().eqEnabled;
        EqStore.EqPreset p = store.getActivePreset();
        int accent = p != null ? p.accentColor : 0xFF39FF14;

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_koker);
        rv.setTextViewText(R.id.widget_preset, p != null ? p.name : "Flat");
        rv.setTextViewText(R.id.widget_power, on ? "● ON" : "○ OFF");
        rv.setTextColor(R.id.widget_power, on ? accent : 0xFF64748B);

        Intent openMain = new Intent(ctx, MainActivity.class);
        openMain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        rv.setOnClickPendingIntent(R.id.widget_preset,
                PendingIntent.getActivity(ctx, 10, openMain, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        rv.setOnClickPendingIntent(R.id.widget_title,
                PendingIntent.getActivity(ctx, 11, openMain, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent openCar = new Intent(ctx, CarModeActivity.class);
        openCar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        rv.setOnClickPendingIntent(R.id.widget_car,
                PendingIntent.getActivity(ctx, 12, openCar, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        rv.setOnClickPendingIntent(R.id.widget_power, broadcastPi(ctx, ACTION_TOGGLE, 13));
        rv.setOnClickPendingIntent(R.id.widget_prev, broadcastPi(ctx, ACTION_PREV, 14));
        rv.setOnClickPendingIntent(R.id.widget_next, broadcastPi(ctx, ACTION_NEXT, 15));

        mgr.updateAppWidget(widgetId, rv);
    }

    private static PendingIntent broadcastPi(Context ctx, String action, int req) {
        Intent i = new Intent(ctx, KokerWidgetProvider.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
