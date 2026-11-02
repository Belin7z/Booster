package com.lucie.wrboost;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;

public final class FpsTracker {
    private static final long SEC = 1_000_000_000L;
    private final ArrayDeque<Long> frames = new ArrayDeque<>();
    private long last = 0;
    public int stutters = 0;

    public void reset() {
        frames.clear();
        last = 0;
    }

    public void add(List<Long> ts) {
        for (long t : ts) {
            if (t <= last) continue;
            if (last != 0) {
                long dt = t - last;
                if (dt > 50_000_000L && dt < SEC) stutters++;
            }
            frames.addLast(t);
            last = t;
        }
        while (!frames.isEmpty() && last - frames.peekFirst() > 30 * SEC) frames.pollFirst();
    }

    // Quadros apresentados no último segundo; 0 se o jogo parou de desenhar.
    public int fps(long nowNs) {
        if (last == 0 || nowNs - last > SEC * 3 / 2) return 0;
        int n = 0;
        long first = 0;
        for (long t : frames) {
            if (t > last - SEC) {
                if (n == 0) first = t;
                n++;
            }
        }
        return n < 2 ? n : (int) Math.round((n - 1) * 1e9 / (last - first));
    }

    // FPS do pior 1% de quadros dos últimos 30 s; NaN se houver poucos dados.
    public float low1() {
        if (frames.size() < 60) return Float.NaN;
        Long[] a = frames.toArray(new Long[0]);
        double[] dt = new double[a.length - 1];
        for (int i = 1; i < a.length; i++) dt[i - 1] = (a[i] - a[i - 1]) / 1e6;
        Arrays.sort(dt);
        double p99 = dt[Math.min(dt.length - 1, (int) Math.ceil(dt.length * 0.99) - 1)];
        return p99 <= 0 ? Float.NaN : (float) (1000.0 / p99);
    }
}
