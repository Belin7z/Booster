package com.lucie.wrboost;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Biblioteca de jogos: detecção automática, lista manual e descoberta do app em primeiro plano.
public final class Games {
    public static final class Game {
        public String pkg, label;
        public boolean manual;
    }

    private static final String ADDED = "games_added", HIDDEN = "games_hidden";

    // Reforço para jogos que nem sempre se declaram como jogo na loja.
    private static final String[] KNOWN = {
            "com.riotgames.league.wildrift", "com.tencent.ig", "com.pubg.imobile", "com.activision.callofduty.shooter",
            "com.dts.freefireth", "com.dts.freefiremax", "com.miHoYo.GenshinImpact", "com.HoYoverse.hkrpgoversea",
            "com.mobile.legends", "com.supercell.", "com.garena.game.", "com.roblox.client", "com.mojang.minecraftpe",
            "com.kiloo.subwaysurf", "com.epicgames.", "com.netease.", "com.levelinfinite.", "com.proximabeta."};

    private static final Pattern ACTIVITY = Pattern.compile("u\\d+\\s+([\\w.]+)/");
    private static long fgQuery;
    private static String fgUsage;
    private static long cacheAt;
    private static Set<String> cache = new HashSet<>();

    private Games() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("wr", Context.MODE_PRIVATE);
    }

    @SuppressWarnings("deprecation")
    private static boolean looksLikeGame(ApplicationInfo ai) {
        if ((ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0 || ai.category == ApplicationInfo.CATEGORY_GAME) return true;
        for (String k : KNOWN) if (k.endsWith(".") ? ai.packageName.startsWith(k) : ai.packageName.equals(k)) return true;
        return false;
    }

    public static synchronized List<Game> list(Context c) {
        PackageManager pm = c.getPackageManager();
        SharedPreferences p = prefs(c);
        Set<String> added = p.getStringSet(ADDED, new HashSet<>());
        Set<String> hidden = p.getStringSet(HIDDEN, new HashSet<>());
        List<Game> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            String pkg = ai.packageName;
            if (pkg.equals(c.getPackageName()) || pm.getLaunchIntentForPackage(pkg) == null) continue;
            boolean man = added.contains(pkg);
            boolean auto = (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 && looksLikeGame(ai) && !hidden.contains(pkg);
            if (!man && !auto) continue;
            Game g = new Game();
            g.pkg = pkg;
            g.label = String.valueOf(pm.getApplicationLabel(ai));
            g.manual = man;
            out.add(g);
            names.add(pkg);
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        cache = names;
        cacheAt = System.currentTimeMillis();
        return out;
    }

    public static synchronized boolean isGame(Context c, String pkg) {
        if (pkg == null) return false;
        if (System.currentTimeMillis() - cacheAt > 60_000) list(c);
        return cache.contains(pkg);
    }

    // Apps abertos pelo usuário que ainda não estão na lista, para o botão "Adicionar jogo".
    public static List<Game> candidates(Context c) {
        PackageManager pm = c.getPackageManager();
        Set<String> have = new HashSet<>();
        for (Game g : list(c)) have.add(g.pkg);
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<Game> out = new ArrayList<>();
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (have.contains(pkg) || pkg.equals(c.getPackageName())) continue;
            Game g = new Game();
            g.pkg = pkg;
            g.label = String.valueOf(ri.loadLabel(pm));
            out.add(g);
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public static synchronized void add(Context c, String pkg) {
        SharedPreferences p = prefs(c);
        Set<String> added = new HashSet<>(p.getStringSet(ADDED, new HashSet<>()));
        Set<String> hidden = new HashSet<>(p.getStringSet(HIDDEN, new HashSet<>()));
        added.add(pkg);
        hidden.remove(pkg);
        p.edit().putStringSet(ADDED, added).putStringSet(HIDDEN, hidden).apply();
        cacheAt = 0;
    }

    public static synchronized void remove(Context c, String pkg) {
        SharedPreferences p = prefs(c);
        Set<String> added = new HashSet<>(p.getStringSet(ADDED, new HashSet<>()));
        Set<String> hidden = new HashSet<>(p.getStringSet(HIDDEN, new HashSet<>()));
        added.remove(pkg);
        hidden.add(pkg);
        p.edit().putStringSet(ADDED, added).putStringSet(HIDDEN, hidden).apply();
        cacheAt = 0;
    }

    public static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    public static boolean hasUsageAccess(Context c) {
        try {
            AppOpsManager ops = c.getSystemService(AppOpsManager.class);
            return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), c.getPackageName())
                    == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return false;
        }
    }

    // Concede o acesso de uso pelo ADB/Shizuku, sem passar pela tela bloqueada do sistema. Devolve true se ficou permitido.
    public static boolean grantUsageViaShell(Context c) {
        if (hasUsageAccess(c)) return true;
        if (!Shell.granted()) return false;
        String pkg = c.getPackageName();
        Shell.exec("appops set " + pkg + " GET_USAGE_STATS allow; appops set " + pkg + " android:get_usage_stats allow", 4000);
        return hasUsageAccess(c);
    }

    // Pacote do app em primeiro plano. Usa o acesso de uso (leve) e, sem ele, o ADB embutido.
    public static String foreground(Context c) {
        if (hasUsageAccess(c)) {
            try {
                UsageStatsManager um = c.getSystemService(UsageStatsManager.class);
                long now = System.currentTimeMillis();
                long from = fgQuery == 0 ? now - 2 * 3600_000L : fgQuery;
                UsageEvents ev = um.queryEvents(from, now);
                UsageEvents.Event e = new UsageEvents.Event();
                while (ev.hasNextEvent()) {
                    ev.getNextEvent(e);
                    if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) fgUsage = e.getPackageName();
                    else if (e.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED && e.getPackageName().equals(fgUsage)) fgUsage = null;
                }
                fgQuery = now;
                if (fgUsage != null) return fgUsage;
            } catch (Throwable ignored) {
            }
        }
        if (Shell.granted()) {
            String out = Shell.exec("dumpsys activity activities | grep -m2 -E 'topResumedActivity|mResumedActivity'", 2500);
            if (out != null) {
                Matcher m = ACTIVITY.matcher(out);
                if (m.find()) return m.group(1);
            }
        }
        return null;
    }
}
