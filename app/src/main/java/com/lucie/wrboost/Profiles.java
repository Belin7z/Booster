package com.lucie.wrboost;

import android.content.Context;
import android.content.SharedPreferences;

// Perfil de cada jogo. Valor -1 significa "usar a configuração geral".
public final class Profiles {
    private Profiles() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("wr", Context.MODE_PRIVATE);
    }

    public static int rawLevel(Context c, String pkg) {
        return p(c).getInt("gp_level_" + pkg, -1);
    }

    public static int rawDnd(Context c, String pkg) {
        return p(c).getInt("gp_dnd_" + pkg, -1);
    }

    public static int rawClean(Context c, String pkg) {
        return p(c).getInt("gp_clean_" + pkg, -1);
    }

    public static int level(Context c, String pkg) {
        int l = pkg == null ? -1 : rawLevel(c, pkg);
        if (l < 0) return Booster.level(c);
        return Shell.granted() ? l : 0;
    }

    public static boolean dnd(Context c, String pkg) {
        int v = pkg == null ? -1 : rawDnd(c, pkg);
        return v < 0 ? p(c).getBoolean("dnd", true) : v == 1;
    }

    // Limpar ao abrir o jogo.
    public static boolean clean(Context c, String pkg) {
        int v = pkg == null ? -1 : rawClean(c, pkg);
        return v < 0 ? p(c).getBoolean("auto_boost", true) : v == 1;
    }

    public static void setLevel(Context c, String pkg, int v) {
        p(c).edit().putInt("gp_level_" + pkg, v).apply();
    }

    public static void setDnd(Context c, String pkg, int v) {
        p(c).edit().putInt("gp_dnd_" + pkg, v).apply();
    }

    public static void setClean(Context c, String pkg, int v) {
        p(c).edit().putInt("gp_clean_" + pkg, v).apply();
    }

    // Próximo valor no ciclo -1 (geral), 0, 1, ... até max.
    public static int next(int cur, int max) {
        return cur >= max ? -1 : cur + 1;
    }
}
