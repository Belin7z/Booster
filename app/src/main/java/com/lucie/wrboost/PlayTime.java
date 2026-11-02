package com.lucie.wrboost;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Tempo de jogo por dia e por jogo, lido do histórico de uso do Android (vale mesmo sem abrir pelo Booster).
// O Android guarda os eventos por poucos dias, então os totais diários ficam salvos em playtime.json.
public final class PlayTime {
    private static final int SCAN_DAYS = 7;
    private static final SimpleDateFormat KEY = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private PlayTime() {
    }

    public static synchronized String key(long t) {
        return KEY.format(new java.util.Date(t));
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "playtime.json");
    }

    // dia (yyyy-MM-dd) -> pacote -> milissegundos
    public static synchronized Map<String, Map<String, Long>> load(Context c) {
        Map<String, Map<String, Long>> out = new HashMap<>();
        try {
            File f = file(c);
            if (!f.exists()) return out;
            JSONObject j = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (Iterator<String> d = j.keys(); d.hasNext(); ) {
                String day = d.next();
                JSONObject o = j.getJSONObject(day);
                Map<String, Long> m = new HashMap<>();
                for (Iterator<String> p = o.keys(); p.hasNext(); ) {
                    String pkg = p.next();
                    m.put(pkg, o.getLong(pkg));
                }
                out.put(day, m);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static long midnightAfter(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    private static void addInterval(Map<String, Map<String, Long>> out, String pkg, long start, long end) {
        while (start < end) {
            long seg = Math.min(end, midnightAfter(start));
            String day = key(start);
            Map<String, Long> m = out.get(day);
            if (m == null) out.put(day, m = new HashMap<>());
            Long old = m.get(pkg);
            m.put(pkg, (old == null ? 0 : old) + (seg - start));
            start = seg;
        }
    }

    // Reescaneia os últimos dias e junta ao arquivo, sempre mantendo o maior valor de cada dia.
    public static synchronized void update(Context c) {
        if (!Games.hasUsageAccess(c)) return;
        try {
            UsageStatsManager um = c.getSystemService(UsageStatsManager.class);
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.DAY_OF_MONTH, -(SCAN_DAYS - 1));
            long now = System.currentTimeMillis();
            UsageEvents ev = um.queryEvents(cal.getTimeInMillis(), now);

            Map<String, Map<String, Long>> fresh = new HashMap<>();
            Map<String, Set<String>> open = new HashMap<>();
            Map<String, Long> openAt = new HashMap<>();
            UsageEvents.Event e = new UsageEvents.Event();
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                String pkg = e.getPackageName();
                long ts = e.getTimeStamp();
                int type = e.getEventType();
                if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE) {
                    for (Map.Entry<String, Long> en : openAt.entrySet()) {
                        if (Games.isGame(c, en.getKey())) addInterval(fresh, en.getKey(), en.getValue(), ts);
                    }
                    openAt.clear();
                    open.clear();
                } else if (type == UsageEvents.Event.ACTIVITY_RESUMED) {
                    Set<String> s = open.get(pkg);
                    if (s == null) open.put(pkg, s = new HashSet<>());
                    s.add(String.valueOf(e.getClassName()));
                    if (!openAt.containsKey(pkg)) openAt.put(pkg, ts);
                } else if (type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED) {
                    Set<String> s = open.get(pkg);
                    if (s != null) s.remove(String.valueOf(e.getClassName()));
                    if ((s == null || s.isEmpty()) && openAt.containsKey(pkg)) {
                        long st = openAt.remove(pkg);
                        if (Games.isGame(c, pkg)) addInterval(fresh, pkg, st, ts);
                    }
                }
            }
            for (Map.Entry<String, Long> en : openAt.entrySet()) {
                if (Games.isGame(c, en.getKey())) addInterval(fresh, en.getKey(), en.getValue(), now);
            }

            Map<String, Map<String, Long>> all = load(c);
            for (Map.Entry<String, Map<String, Long>> d : fresh.entrySet()) {
                Map<String, Long> m = all.get(d.getKey());
                if (m == null) all.put(d.getKey(), m = new HashMap<>());
                for (Map.Entry<String, Long> p : d.getValue().entrySet()) {
                    Long old = m.get(p.getKey());
                    if (old == null || p.getValue() > old) m.put(p.getKey(), p.getValue());
                }
            }
            JSONObject out = new JSONObject();
            for (Map.Entry<String, Map<String, Long>> d : all.entrySet()) {
                JSONObject o = new JSONObject();
                for (Map.Entry<String, Long> p : d.getValue().entrySet()) o.put(p.getKey(), p.getValue());
                out.put(d.getKey(), o);
            }
            Files.write(file(c).toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    public static long dayTotal(Map<String, Map<String, Long>> all, String day) {
        Map<String, Long> m = all.get(day);
        long s = 0;
        if (m != null) for (long v : m.values()) s += v;
        return s;
    }

    // "1 h 20 min", "35 min" ou "-".
    public static String fmt(long ms) {
        long min = Math.round(ms / 60000.0);
        if (min <= 0) return ms > 0 ? "<1 min" : "-";
        return min >= 60 ? (min / 60) + " h " + (min % 60 == 0 ? "" : (min % 60) + " min") : min + " min";
    }

    // "1h20" curto para rótulos de gráfico.
    public static String fmtShort(long ms) {
        long min = Math.round(ms / 60000.0);
        if (min <= 0) return "";
        return min >= 60 ? (min / 60) + "h" + (min % 60 == 0 ? "" : String.format(Locale.US, "%02d", min % 60)) : min + "m";
    }
}
