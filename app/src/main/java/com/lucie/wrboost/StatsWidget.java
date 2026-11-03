package com.lucie.wrboost;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.Locale;

// Widget com RAM em uso, temperatura da CPU e tempo jogado hoje. Toque abre o app.
public class StatsWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        final Context app = c.getApplicationContext();
        final BroadcastReceiver.PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                refresh(app);
            } finally {
                pr.finish();
            }
        }).start();
    }

    static boolean has(Context c) {
        return AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, StatsWidget.class)).length > 0;
    }

    // Lê o uso de arquivos: chamar fora da thread principal.
    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, StatsWidget.class));
        if (ids.length == 0) return;
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_stats);
        SystemInfo si = SystemInfo.read(c);
        v.setTextViewText(R.id.s_ram, si.ramUsedPct() + "%");
        v.setTextViewText(R.id.s_free, SystemInfo.gb(si.ramAvail) + " GB livres");
        Monitor.Sample s = Monitor.latest;
        v.setTextViewText(R.id.s_temp, s != null && !Float.isNaN(s.cpu) ? String.format(Locale.US, "%.0f°", s.cpu) : "--");
        long today = 0;
        try {
            today = PlayTime.dayTotal(PlayTime.load(c), PlayTime.key(System.currentTimeMillis()));
        } catch (Exception ignored) {
        }
        v.setTextViewText(R.id.s_today, PlayTime.fmtShort(today));
        v.setOnClickPendingIntent(R.id.s_root, PendingIntent.getActivity(c, 6, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        for (int id : ids) m.updateAppWidget(id, v);
    }
}
