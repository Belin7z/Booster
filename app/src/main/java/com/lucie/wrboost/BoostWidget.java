package com.lucie.wrboost;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.Toast;

// Widget da tela inicial: um toque libera a RAM e mostra o uso atual.
public class BoostWidget extends AppWidgetProvider {
    static final String ACTION = "com.lucie.wrboost.WIDGET_BOOST";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, views(c, null));
    }

    static RemoteViews views(Context c, String status) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_boost);
        SystemInfo s = SystemInfo.read(c);
        v.setTextViewText(R.id.w_title, status != null ? status : "Liberar RAM");
        v.setTextViewText(R.id.w_sub, "RAM em uso " + s.ramUsedPct() + "%  ·  " + SystemInfo.gb(s.ramAvail) + " GB livres");
        Intent i = new Intent(c, BoostWidget.class).setAction(ACTION);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }

    static void refresh(Context c, String status) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, BoostWidget.class));
        for (int id : ids) m.updateAppWidget(id, views(c, status));
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (!ACTION.equals(i.getAction())) return;
        final Context app = c.getApplicationContext();
        final BroadcastReceiver.PendingResult pr = goAsync();
        refresh(app, "Limpando...");
        new Thread(() -> {
            try {
                Adb.init(app);
                Booster.Result r = Booster.boost(app, Booster.level(app), Monitor.activeGame());
                String msg = r.forced + " apps fechados, " + (r.freedBytes / 1048576) + " MB liberados";
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(app, msg, Toast.LENGTH_SHORT).show());
                refresh(app, "Liberar RAM");
            } finally {
                pr.finish();
            }
        }).start();
    }
}
