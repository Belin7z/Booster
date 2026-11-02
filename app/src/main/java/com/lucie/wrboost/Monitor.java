package com.lucie.wrboost;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

// Amostrador central: RAM, bateria, temperatura de CPU/GPU e FPS, uma vez por segundo.
public final class Monitor {
    public static final class Sample {
        public long t;
        public SystemInfo sys;
        public float cpu = Float.NaN, gpu = Float.NaN;
        public String cpuSrc = "";
        public int fps = -1;
        public float low1 = Float.NaN;
        public int stutters;
    }

    private static final String ZONES_CMD = "for z in /sys/class/thermal/thermal_zone*; do "
            + "echo \"$(cat $z/type 2>/dev/null) $(cat $z/temp 2>/dev/null)\"; done";

    public static volatile Sample latest;
    private static final ArrayDeque<Sample> history = new ArrayDeque<>();
    private static final FpsTracker tracker = new FpsTracker();
    private static Context app;
    private static int refs;
    private static int generation;
    private static boolean directThermal = true;
    private static String layer;
    private static long layerCheck;
    private static int tick;
    private static Parsers.Temps lastService = new Parsers.Temps();

    private static boolean sessionOn;
    private static long sStart;
    private static long fpsSum, fpsN, ramMax, throttleSec;
    private static int fpsMin = Integer.MAX_VALUE, stutterBase;
    private static float cpuMax, battMax;
    private static float lowSum;
    private static int lowN;

    private Monitor() {
    }

    public static synchronized void acquire(Context c) {
        app = c.getApplicationContext();
        if (refs++ == 0) {
            final int g = ++generation;
            Thread t = new Thread(() -> loop(g));
            t.setDaemon(true);
            t.start();
        }
    }

    public static synchronized void release() {
        if (--refs <= 0) {
            refs = 0;
            generation++;
        }
    }

    private static synchronized boolean alive(int g) {
        return g == generation;
    }

    private static void loop(int g) {
        while (alive(g)) {
            long t0 = System.currentTimeMillis();
            try {
                publish(take());
            } catch (Throwable ignored) {
            }
            long wait = 1000 - (System.currentTimeMillis() - t0);
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private static Sample take() {
        Sample s = new Sample();
        s.t = System.currentTimeMillis();
        s.sys = SystemInfo.read(app);
        tick++;

        Parsers.Temps tm = readZones();
        if ((Float.isNaN(tm.cpu) || Float.isNaN(tm.gpu)) && Shell.granted()) {
            if (tick % 5 == 1) lastService = Parsers.parseThermalService(Shell.exec("dumpsys thermalservice", 2500));
            if (Float.isNaN(tm.cpu)) {
                tm.cpu = lastService.cpu;
                tm.cpuSrc = lastService.cpuSrc;
            }
            if (Float.isNaN(tm.gpu)) tm.gpu = lastService.gpu;
        }
        s.cpu = tm.cpu;
        s.gpu = tm.gpu;
        s.cpuSrc = tm.cpuSrc;

        readFps(s);
        return s;
    }

    private static Parsers.Temps readZones() {
        if (directThermal) {
            StringBuilder sb = new StringBuilder();
            File[] zs = new File("/sys/class/thermal").listFiles((d, n) -> n.startsWith("thermal_zone"));
            if (zs != null) {
                for (File z : zs) {
                    String type = readLine(new File(z, "type")), temp = readLine(new File(z, "temp"));
                    if (type != null && temp != null) sb.append(type).append(' ').append(temp).append('\n');
                }
            }
            Parsers.Temps t = Parsers.parseZones(sb.toString());
            if (!Float.isNaN(t.cpu)) return t;
            if (sb.length() == 0) directThermal = false;
        }
        if (Shell.granted()) return Parsers.parseZones(Shell.exec(ZONES_CMD, 2000));
        return new Parsers.Temps();
    }

    private static String readLine(File f) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            return r.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    private static void readFps(Sample s) {
        String pkg = Booster.gamePackage(app);
        if (pkg == null || !Shell.granted()) {
            tracker.reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (layer == null) {
            if (now - layerCheck < 3000) return;
            layerCheck = now;
            layer = Parsers.findLayer(Shell.exec("dumpsys SurfaceFlinger --list", 3000), pkg);
            if (layer == null) {
                tracker.reset();
                return;
            }
        }
        java.util.List<Long> frames = Parsers.parseLatency(Shell.exec("dumpsys SurfaceFlinger --latency '" + layer + "'", 2000));
        if (frames.isEmpty()) {
            layer = null;
            return;
        }
        tracker.add(frames);
        s.fps = tracker.fps(System.nanoTime());
        s.low1 = tracker.low1();
        s.stutters = tracker.stutters;
    }

    private static synchronized void publish(Sample s) {
        latest = s;
        history.addLast(s);
        while (history.size() > 120) history.pollFirst();
        if (sessionOn) {
            long used = s.sys.ramTotal - s.sys.ramAvail;
            ramMax = Math.max(ramMax, used);
            if (!Float.isNaN(s.cpu)) cpuMax = Math.max(cpuMax, s.cpu);
            battMax = Math.max(battMax, s.sys.tempC);
            if (s.sys.thermalStatus >= 2) throttleSec++;
            if (s.fps > 0) {
                fpsSum += s.fps;
                fpsN++;
                fpsMin = Math.min(fpsMin, s.fps);
                if (!Float.isNaN(s.low1)) {
                    lowSum += s.low1;
                    lowN++;
                }
            }
        }
    }

    public static synchronized float[] series(int kind) {
        List<Float> l = new ArrayList<>();
        for (Sample s : history) {
            float v = kind == 0 ? (s.fps < 0 ? Float.NaN : s.fps) : kind == 1 ? s.cpu : s.sys.ramUsedPct();
            l.add(v);
        }
        float[] a = new float[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    public static synchronized void startSession() {
        sessionOn = true;
        sStart = System.currentTimeMillis();
        fpsSum = fpsN = ramMax = throttleSec = 0;
        fpsMin = Integer.MAX_VALUE;
        cpuMax = battMax = lowSum = 0;
        lowN = 0;
        stutterBase = tracker.stutters;
    }

    public static synchronized boolean sessionActive() {
        return sessionOn;
    }

    public static synchronized String endSession() {
        if (!sessionOn) return null;
        sessionOn = false;
        long sec = (System.currentTimeMillis() - sStart) / 1000;
        StringBuilder b = new StringBuilder();
        b.append("Duração: ").append(sec / 60).append(" min ").append(sec % 60).append(" s\n");
        if (fpsN > 0) {
            b.append("FPS médio: ").append(fpsSum / fpsN).append(" · mínimo: ").append(fpsMin);
            if (lowN > 0) b.append(" · 1% low médio: ").append(Math.round(lowSum / lowN));
            b.append("\nTravadas (quadros > 50 ms): ").append(Math.max(0, tracker.stutters - stutterBase)).append('\n');
        } else {
            b.append("FPS: não medido (Shizuku inativo ou jogo não detectado)\n");
        }
        if (cpuMax > 0) b.append("CPU máx: ").append(String.format(java.util.Locale.US, "%.1f", cpuMax)).append("°C\n");
        b.append("Bateria máx: ").append(String.format(java.util.Locale.US, "%.1f", battMax)).append("°C\n");
        b.append("RAM máx em uso: ").append(SystemInfo.gb(ramMax)).append(" GB\n");
        b.append("Tempo com limitação térmica: ").append(throttleSec).append(" s");
        return b.toString();
    }
}
