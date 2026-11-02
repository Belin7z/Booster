package com.lucie.wrboost;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Booster {
    public static final String[] GAME_PACKAGES = {
            "com.riotgames.league.wildrift",
            "com.riotgames.league.wildrifttw",
            "com.riotgames.league.wildriftvn"
    };

    private static final String[] PROTECTED_PREFIXES = {
            "com.android.", "com.google.android.gms", "com.google.android.gsf",
            "com.miui.", "com.xiaomi.", "android", "com.mediatek", "com.lucie.wrboost"
    };

    // Apps de mensagem ficam de fora do modo agressivo para não perder notificações.
    private static final Set<String> KEEP_ALWAYS = new HashSet<>(Arrays.asList(
            Shell.SHIZUKU_PKG, "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.discord",
            "com.google.android.dialer", "com.google.android.apps.messaging"));

    public static final class Result {
        public int targeted;
        public int forced;
        public long freedBytes;
    }

    public static String gamePackage(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String g : GAME_PACKAGES) {
            if (pm.getLaunchIntentForPackage(g) != null) return g;
        }
        return null;
    }

    public static Result boost(Context c, boolean aggressive) {
        PackageManager pm = c.getPackageManager();
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        Set<String> keep = protectedPackages(c);

        ActivityManager.MemoryInfo before = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(before);

        Result r = new Result();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_STOPPED) != 0) continue;
            if (keep.contains(ai.packageName) || startsWithAny(ai.packageName)) continue;
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updated = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            if (system && !updated) continue;
            am.killBackgroundProcesses(ai.packageName);
            r.targeted++;
        }

        if (aggressive && Shell.granted()) r.forced = forceStopThirdParty(keep);

        try {
            Thread.sleep(1500);
        } catch (InterruptedException ignored) {
        }
        ActivityManager.MemoryInfo after = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(after);
        r.freedBytes = after.availMem - before.availMem;
        return r;
    }

    private static int forceStopThirdParty(Set<String> keep) {
        String list = Shell.exec("pm list packages -3", 4000);
        if (list == null) return 0;
        StringBuilder cmd = new StringBuilder();
        int n = 0;
        for (String line : list.split("\n")) {
            line = line.trim();
            if (!line.startsWith("package:")) continue;
            String p = line.substring(8);
            if (!Parsers.safePackage(p) || keep.contains(p) || KEEP_ALWAYS.contains(p) || startsWithAny(p)) continue;
            cmd.append("am force-stop ").append(p).append('\n');
            n++;
        }
        if (n > 0) Shell.exec(cmd.toString(), 15000);
        return n;
    }

    // Game Mode "performance" do Android 12+, definido via shell.
    public static void gameModePerformance(String pkg) {
        if (Parsers.safePackage(pkg) && Shell.granted()) Shell.exec("cmd game mode performance " + pkg, 3000);
    }

    private static Set<String> protectedPackages(Context c) {
        Set<String> keep = new HashSet<>(Arrays.asList(GAME_PACKAGES));
        PackageManager pm = c.getPackageManager();

        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = pm.queryIntentActivities(home, 0);
        for (ResolveInfo ri : homes) keep.add(ri.activityInfo.packageName);

        String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (ime != null && ime.contains("/")) keep.add(ime.substring(0, ime.indexOf('/')));
        return keep;
    }

    private static boolean startsWithAny(String pkg) {
        for (String p : PROTECTED_PREFIXES) if (pkg.startsWith(p)) return true;
        return false;
    }

    public static Intent gameLaunchIntent(Context c) {
        String g = gamePackage(c);
        return g == null ? null : c.getPackageManager().getLaunchIntentForPackage(g);
    }
}
