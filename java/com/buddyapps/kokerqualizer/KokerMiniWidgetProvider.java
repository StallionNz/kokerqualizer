package com.buddyapps.kokerqualizer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * 1x1 Compact Neon Green Orb Home Widget (#40):
 * Tap to toggle EQ power; displays active status & custom monogram letter.
 */
public class KokerMiniWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_MINI_TOGGLE = "com.buddyapps.kokerqualizer.MINI_WIDGET_TOGGLE";

    public static void updateAll(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, KokerMiniWidgetProvider.class));
            if (ids != null && ids.length > 0) {
                for (int id : ids) updateSingle(ctx, mgr, id);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateSingle(context, appWidgetManager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_MINI_TOGGLE.equals(intent.getAction())) {
            EqStore store = EqStore.get(context);
            store.getSettings().eqEnabled = !store.getSettings().eqEnabled;
            store.save();
            EqService.startOrRefresh(context);
            updateAll(context);
            KokerWidgetProvider.updateAll(context);
        }
    }

    private static void updateSingle(Context ctx, AppWidgetManager mgr, int widgetId) {
        EqStore store = EqStore.get(ctx);
        EqStore.Settings s = store.getSettings();
        boolean on = s.eqEnabled;
        String letter = EqStore.firstCodePoint(s.orbLetter, "K");

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_koker_mini);
        rv.setTextViewText(R.id.mini_letter, letter);
        rv.setTextColor(R.id.mini_letter, on ? EqStore.DEFAULT_ACCENT : 0xFF1F8F0C);
        rv.setTextViewText(R.id.mini_state, on ? "ON" : "OFF");
        rv.setTextColor(R.id.mini_state, on ? EqStore.DEFAULT_ACCENT : 0xFF64748B);

        Intent i = new Intent(ctx, KokerMiniWidgetProvider.class);
        i.setAction(ACTION_MINI_TOGGLE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 21, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.mini_root, pi);
        mgr.updateAppWidget(widgetId, rv);
    }
}
