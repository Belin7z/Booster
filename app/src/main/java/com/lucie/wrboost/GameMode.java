package com.lucie.wrboost;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

// Modo jogo completo: aplica brilho e taxa de tela do perfil do jogo e desfaz tudo ao sair.
public final class GameMode {
    private GameMode() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("wr", Context.MODE_PRIVATE);
    }

    private static void putSystem(Context c, String key, int v) {
        if (Settings.System.canWrite(c)) Settings.System.putInt(c.getContentResolver(), key, v);
        else Shell.exec("settings put system " + key + " " + v, 3000);
    }

    // Roda fora da thread principal.
    public static void apply(Context c, String pkg) {
        SharedPreferences sp = p(c);
        int bright = Profiles.rawBright(c, pkg), hz = Profiles.rawHz(c, pkg);
        try {
            if (bright > 0 && (Settings.System.canWrite(c) || Shell.granted())) {
                if (!sp.contains("gm_bright")) {
                    int cur = Settings.System.getInt(c.getContentResolver(), "screen_brightness", 128);
                    int mode = Settings.System.getInt(c.getContentResolver(), "screen_brightness_mode", 0);
                    sp.edit().putInt("gm_bright", cur).putInt("gm_bmode", mode).apply();
                }
                putSystem(c, "screen_brightness_mode", 0);
                putSystem(c, "screen_brightness", Math.max(1, Math.min(255, bright * 255 / 100)));
            }
            if (hz > 0 && Shell.granted()) {
                if (!sp.contains("gm_min")) {
                    String mn = Shell.exec("settings get system min_refresh_rate", 2000);
                    String pk = Shell.exec("settings get system peak_refresh_rate", 2000);
                    sp.edit().putString("gm_min", mn == null ? "null" : mn.trim()).putString("gm_peak", pk == null ? "null" : pk.trim()).apply();
                }
                Shell.exec("settings put system min_refresh_rate " + hz + ".0; settings put system peak_refresh_rate " + hz + ".0", 3000);
            }
        } catch (Throwable t) {
            CrashLog.log(c, "Modo jogo", CrashLog.stack(t));
        }
    }

    // Trava a taxa da tela em hz para esfriar o aparelho; restore() devolve ao normal.
    public static void cool(Context c, int hz) {
        if (!Shell.granted() || hz <= 0) return;
        SharedPreferences sp = p(c);
        if (!sp.contains("gm_min")) {
            String mn = Shell.exec("settings get system min_refresh_rate", 2000);
            String pk = Shell.exec("settings get system peak_refresh_rate", 2000);
            sp.edit().putString("gm_min", mn == null ? "null" : mn.trim()).putString("gm_peak", pk == null ? "null" : pk.trim()).apply();
        }
        Shell.exec("settings put system min_refresh_rate " + hz + ".0; settings put system peak_refresh_rate " + hz + ".0", 3000);
    }

    // Devolve brilho, taxa de tela e Não perturbe ao que estavam antes do jogo.
    public static void restore(Context c) {
        SharedPreferences sp = p(c);
        try {
            if (sp.contains("gm_bright")) {
                putSystem(c, "screen_brightness", sp.getInt("gm_bright", 128));
                putSystem(c, "screen_brightness_mode", sp.getInt("gm_bmode", 0));
                sp.edit().remove("gm_bright").remove("gm_bmode").apply();
            }
            if (sp.contains("gm_min")) {
                restoreKey("min_refresh_rate", sp.getString("gm_min", "null"));
                restoreKey("peak_refresh_rate", sp.getString("gm_peak", "null"));
                sp.edit().remove("gm_min").remove("gm_peak").apply();
            }
            Booster.restoreDnd(c);
        } catch (Throwable t) {
            CrashLog.log(c, "Modo jogo (restaurar)", CrashLog.stack(t));
        }
    }

    private static void restoreKey(String key, String old) {
        if (old == null || old.isEmpty() || old.equals("null")) Shell.exec("settings delete system " + key, 2000);
        else if (old.matches("[0-9.]+")) Shell.exec("settings put system " + key + " " + old, 2000);
    }

    public static boolean active(Context c) {
        SharedPreferences sp = p(c);
        return sp.contains("gm_bright") || sp.contains("gm_min");
    }
}
