package com.lucie.wrboost;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

// Shell com permissões de ADB via Shizuku (o usuário inicia o Shizuku por Depuração sem fio; sem root).
public final class Shell {
    public static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";
    private static Process proc;
    private static BufferedWriter out;
    private static final LinkedBlockingQueue<String> q = new LinkedBlockingQueue<>();
    private static long counter = 0;

    private Shell() {
    }

    public static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(SHIZUKU_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean running() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean granted() {
        try {
            return running() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void request(int code) {
        try {
            Shizuku.requestPermission(code);
        } catch (Throwable ignored) {
        }
    }

    private static void ensure() throws Exception {
        if (proc != null && proc.isAlive()) return;
        Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        m.setAccessible(true);
        proc = (Process) m.invoke(null, new Object[]{new String[]{"sh"}, null, null});
        out = new BufferedWriter(new OutputStreamWriter(proc.getOutputStream()));
        final Process p = proc;
        daemon(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) q.offer(l);
            } catch (Exception ignored) {
            }
        });
        daemon(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                while (r.readLine() != null) ;
            } catch (Exception ignored) {
            }
        });
    }

    private static void daemon(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();
    }

    private static void reset() {
        try {
            if (proc != null) proc.destroy();
        } catch (Throwable ignored) {
        }
        proc = null;
        out = null;
    }

    // Executa e devolve a saída; null se não houver acesso ou se estourar o tempo.
    public static synchronized String exec(String cmd, long timeoutMs) {
        if (!granted()) return null;
        try {
            ensure();
            q.clear();
            String end = "__END" + (++counter) + "__";
            out.write(cmd + "\necho " + end + "\n");
            out.flush();
            StringBuilder sb = new StringBuilder();
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (true) {
                long left = deadline - System.currentTimeMillis();
                String l = left <= 0 ? null : q.poll(left, TimeUnit.MILLISECONDS);
                if (l == null) {
                    reset();
                    return null;
                }
                if (l.equals(end)) return sb.toString();
                sb.append(l).append('\n');
            }
        } catch (Throwable t) {
            reset();
            return null;
        }
    }
}
