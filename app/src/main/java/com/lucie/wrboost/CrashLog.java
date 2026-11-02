package com.lucie.wrboost;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Registro local de falhas e travamentos, para achar a causa de problemas sem depender de adivinhação.
public final class CrashLog {
    private static final int MAX_BYTES = 100_000;
    private static String lastMsg = "";
    private static long lastAt;

    private CrashLog() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "crash.log");
    }

    private static String now() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(new Date());
    }

    public static void install(final Context c) {
        final Context app = c.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log(app, "FALHA em " + t.getName(), stack(e));
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    public static String stack(Throwable e) {
        StringWriter w = new StringWriter();
        e.printStackTrace(new PrintWriter(w));
        return w.toString();
    }

    // Mensagens iguais em sequência rápida são gravadas só uma vez.
    public static synchronized void log(Context c, String kind, String msg) {
        long t = System.currentTimeMillis();
        String key = kind + msg;
        if (key.equals(lastMsg) && t - lastAt < 60_000) return;
        lastMsg = key;
        lastAt = t;
        try {
            File f = file(c);
            String old = f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
            String all = old + "[" + now() + "] " + kind + "\n" + msg.trim() + "\n\n";
            if (all.length() > MAX_BYTES) all = all.substring(all.length() - MAX_BYTES);
            Files.write(f.toPath(), all.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    public static synchronized String read(Context c) {
        try {
            File f = file(c);
            return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
        } catch (Throwable t) {
            return "";
        }
    }

    public static synchronized void clear(Context c) {
        file(c).delete();
    }

    public static int count(Context c) {
        String s = read(c);
        int n = 0, i = 0;
        while ((i = s.indexOf("\n[", i)) >= 0) {
            n++;
            i += 2;
        }
        return s.startsWith("[") ? n + 1 : n;
    }

    // O Android guarda por que o processo anterior morreu (ANR, falha, falta de memória); registramos isso uma vez.
    public static void scanExits(Context c) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            SharedPreferences p = c.getSharedPreferences("wr", Context.MODE_PRIVATE);
            long seen = p.getLong("exit_seen", 0);
            long newest = seen;
            ActivityManager am = c.getSystemService(ActivityManager.class);
            List<ApplicationExitInfo> l = am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 8);
            for (ApplicationExitInfo e : l) {
                if (e.getTimestamp() <= seen) continue;
                newest = Math.max(newest, e.getTimestamp());
                String kind;
                switch (e.getReason()) {
                    case ApplicationExitInfo.REASON_ANR: kind = "ANR (não está respondendo)"; break;
                    case ApplicationExitInfo.REASON_CRASH: kind = "Falha do app"; break;
                    case ApplicationExitInfo.REASON_CRASH_NATIVE: kind = "Falha nativa"; break;
                    case ApplicationExitInfo.REASON_LOW_MEMORY: kind = "Fechado por pouca memória"; break;
                    case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: kind = "Fechado por uso excessivo"; break;
                    default: continue;
                }
                StringBuilder b = new StringBuilder();
                b.append("Quando: ").append(new SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new Date(e.getTimestamp())));
                if (e.getDescription() != null) b.append("\nMotivo: ").append(e.getDescription());
                if (e.getReason() == ApplicationExitInfo.REASON_ANR) {
                    try (InputStream in = e.getTraceInputStream()) {
                        if (in != null) {
                            byte[] buf = new byte[4000];
                            int n = in.read(buf);
                            if (n > 0) b.append("\nTrecho do traço:\n").append(new String(buf, 0, n, StandardCharsets.UTF_8));
                        }
                    } catch (Exception ignored) {
                    }
                }
                log(c, kind, b.toString());
            }
            if (newest > seen) p.edit().putLong("exit_seen", newest).apply();
        } catch (Throwable ignored) {
        }
    }

    public static String deviceInfo(Context c) {
        return "Booster " + Updater.installedName(c) + " (" + Updater.installedCode(c) + ")\n"
                + Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Modo avançado: " + (Adb.connected() ? "ADB" : Shell.shizukuGranted() ? "Shizuku" : "desligado");
    }

    public static String report(Context c) {
        String log = read(c);
        return "Relatório do Booster\n" + deviceInfo(c) + "\n\n" + Diag.summary(c) + "\n\nRegistro:\n" + (log.isEmpty() ? "(vazio)" : log);
    }
}
