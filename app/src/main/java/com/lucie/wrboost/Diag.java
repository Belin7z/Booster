package com.lucie.wrboost;

import android.content.Context;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;

import java.util.Locale;

// Mede quanto o próprio Booster consome, para um otimizador não pesar no celular.
public final class Diag {
    private static long lastCpu, lastWall;
    private static float cpuPct;
    private static long pssKb;
    private static long pssAt;

    private Diag() {
    }

    // Chame de tempos em tempos; calcula o uso de CPU desde a última chamada.
    public static synchronized void sample() {
        long cpu = Process.getElapsedCpuTime(), wall = SystemClock.elapsedRealtime();
        if (lastWall != 0 && wall > lastWall) cpuPct = 100f * (cpu - lastCpu) / (wall - lastWall);
        lastCpu = cpu;
        lastWall = wall;
        if (wall - pssAt > 10_000) {
            pssAt = wall;
            Debug.MemoryInfo mi = new Debug.MemoryInfo();
            Debug.getMemoryInfo(mi);
            pssKb = mi.getTotalPss();
        }
    }

    public static synchronized float cpuPct() {
        return cpuPct;
    }

    public static synchronized long ramMb() {
        return pssKb / 1024;
    }

    public static synchronized String summary(Context c) {
        Locale us = Locale.US;
        Monitor.Sample m = Monitor.latest;
        StringBuilder b = new StringBuilder();
        b.append("CPU do Booster: ").append(String.format(us, "%.1f%%", cpuPct)).append(" de um núcleo\n");
        b.append("RAM do Booster: ").append(pssKb / 1024).append(" MB\n");
        b.append("Threads: ").append(Thread.activeCount()).append('\n');
        b.append("Ciclo do monitor: ").append(Monitor.takeMs).append(" ms (a cada 1000 ms)\n");
        b.append("Rede: ").append(Monitor.pingMs < 0 ? "sem resposta" : Monitor.pingMs + " ms").append(" · perda ").append(Monitor.lossPct).append('%');
        if (m != null) b.append("\nRAM do sistema: ").append(m.sys.ramUsedPct()).append("% em uso");
        return b.toString();
    }
}
