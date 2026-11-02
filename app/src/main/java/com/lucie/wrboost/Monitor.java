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
        public int hz = 60;
        public String cause;
        public float trend = Float.NaN;
    }

    public static int refreshRate(Context c) {
        try {
            android.view.Display d = c.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
            return Math.max(1, Math.round(d.getRefreshRate()));
        } catch (Throwable t) {
            return 60;
        }
    }

    // Taxas que a tela suporta (ex.: 60, 90, 120), em ordem crescente.
    public static int[] supportedRates(Context c) {
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        try {
            android.view.Display d = c.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
            for (android.view.Display.Mode m : d.getSupportedModes()) set.add(Math.round(m.getRefreshRate()));
        } catch (Throwable ignored) {
        }
        if (set.isEmpty()) set.add(refreshRate(c));
        int[] a = new int[set.size()];
        int i = 0;
        for (int v : set) a[i++] = v;
        return a;
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
    private static long fpsSum, fpsN, ramMax, throttleSec, stableSec, ramPctSum, ticksN;
    private static int fpsMin = Integer.MAX_VALUE, fpsMaxS, stutterBase, battStart, lastBatt, hzSeen;
    private static float cpuMax, gpuMax, battMax, cpuSum;
    private static long cpuN;
    private static int dropHeat, dropRam, dropOther;
    private static float lowSum;
    private static int lowN;
    private static final List<Integer> fpsSer = new ArrayList<>(), cpuSer = new ArrayList<>();

    public static volatile long takeMs;
    public static volatile int pingMs = -1, lossPct;
    private static volatile boolean pinging;
    private static long pingSum, pingN, lossSum;
    private static int pingMaxS, badNet;
    private static long lastNetAlert, lastPredict;
    private static final ArrayDeque<float[]> trendBuf = new ArrayDeque<>();

    private static volatile String gamePkg, fgGame, layerPkg;
    private static int awayTicks;

    private Monitor() {
    }

    public static synchronized void acquire(Context c) {
        app = c.getApplicationContext();
        Adb.init(app);
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
            } catch (Throwable e) {
                CrashLog.log(app, "Monitor", CrashLog.stack(e));
            }
            takeMs = System.currentTimeMillis() - t0;
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
        Adb.maybeReconnect(app);
        s.hz = refreshRate(app);
        autoDetect();
        if (tick % 20 == 3 && !Games.hasUsageAccess(app) && Shell.granted()) new Thread(() -> Games.grantUsageViaShell(app)).start();
        autoClean(s);
        if (tick % 300 == 7 && Games.hasUsageAccess(app)) new Thread(() -> PlayTime.update(app)).start();

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

        s.trend = trend(s);
        if (tick % (activeGame() != null ? 6 : 30) == 2) probeNet();
        readFps(s);
        s.cause = cause(s);
        heatAlert(s);
        return s;
    }

    // Variação da temperatura da CPU em °C por minuto, pela média dos últimos ~90 s.
    private static float trend(Sample s) {
        if (Float.isNaN(s.cpu)) return Float.NaN;
        trendBuf.addLast(new float[]{s.t / 1000f, s.cpu});
        while (!trendBuf.isEmpty() && s.t / 1000f - trendBuf.peekFirst()[0] > 90) trendBuf.pollFirst();
        int n = trendBuf.size();
        if (n < 30) return Float.NaN;
        double sx = 0, sy = 0, sxy = 0, sxx = 0;
        float t0 = trendBuf.peekFirst()[0];
        for (float[] p : trendBuf) {
            double x = p[0] - t0;
            sx += x;
            sy += p[1];
            sxy += x * p[1];
            sxx += x * x;
        }
        double den = n * sxx - sx * sx;
        return den == 0 ? Float.NaN : (float) (60.0 * (n * sxy - sx * sy) / den);
    }

    // Mede o ping com conexões TCP curtas, fora da thread do monitor.
    private static void probeNet() {
        if (pinging) return;
        pinging = true;
        new Thread(() -> {
            try {
                int ok = 0, sum = 0, max = 0;
                for (int i = 0; i < 4; i++) {
                    long t0 = System.nanoTime();
                    try (java.net.Socket so = new java.net.Socket()) {
                        so.connect(new java.net.InetSocketAddress("1.1.1.1", 443), 1200);
                        int ms = (int) ((System.nanoTime() - t0) / 1_000_000);
                        ok++;
                        sum += ms;
                        max = Math.max(max, ms);
                    } catch (Exception ignored) {
                    }
                    try {
                        Thread.sleep(80);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                pingMs = ok == 0 ? -1 : sum / ok;
                lossPct = (4 - ok) * 25;
                synchronized (Monitor.class) {
                    if (sessionOn) {
                        lossSum += lossPct;
                        pingN++;
                        if (ok > 0) pingSum += pingMs;
                        pingMaxS = Math.max(pingMaxS, max);
                    }
                }
                netAlert();
            } finally {
                pinging = false;
            }
        }).start();
    }

    private static void netAlert() {
        if (activeGame() == null || !prefs().getBoolean("net_alert", true)) return;
        boolean bad = pingMs < 0 || pingMs >= 120 || lossPct >= 25;
        badNet = bad ? badNet + 1 : 0;
        long now = System.currentTimeMillis();
        if (badNet >= 2 && now - lastNetAlert > 180_000) {
            lastNetAlert = now;
            notifyInfo("Rede instável no jogo", (pingMs < 0 ? "Sem resposta da internet" : "Ping " + pingMs + " ms, perda " + lossPct + "%")
                    + ". Chegue mais perto do roteador ou troque a rede.");
        }
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

    // Jogo cujo FPS está sendo medido agora: o da sessão ou o que está em primeiro plano.
    public static String activeGame() {
        String g = gamePkg;
        return g != null ? g : fgGame;
    }

    private static android.content.SharedPreferences prefs() {
        return app.getSharedPreferences("wr", Context.MODE_PRIVATE);
    }

    private static void autoDetect() {
        if (tick % 3 != 1) return;
        String fg = Games.foreground(app);
        if (fg == null) return;
        boolean isGame = Games.isGame(app, fg);
        fgGame = isGame ? fg : null;
        boolean auto = prefs().getBoolean("auto", true);
        if (isGame) {
            awayTicks = 0;
            if (sessionOn && gamePkg == null) gamePkg = fg;
            if (auto && (!sessionOn || !fg.equals(gamePkg))) {
                if (sessionOn) endSession();
                beginAuto(fg);
            }
        } else if (sessionOn && !fg.equals(app.getPackageName())) {
            awayTicks += 3;
            if (auto && awayTicks >= 30) {
                endSession();
                Booster.restoreDnd(app);
            }
        }
    }

    private static long lastClean;
    private static volatile boolean cleaning;

    // Limpeza automática quando a RAM passa do limite escolhido, só com um jogo em primeiro plano.
    private static void autoClean(Sample s) {
        int pct = prefs().getInt("auto_clean_pct", 0);
        String g = fgGame;
        long now = System.currentTimeMillis();
        if (pct <= 0 || g == null || cleaning || s.sys.ramUsedPct() < pct || now - lastClean < 180_000) return;
        lastClean = now;
        cleaning = true;
        new Thread(() -> {
            try {
                Booster.boost(app, Profiles.level(app, g), g);
            } finally {
                cleaning = false;
            }
        }).start();
    }

    private static void beginAuto(String pkg) {
        startSession(pkg);
        new Thread(() -> {
            if (Profiles.clean(app, pkg)) {
                Booster.Result r = Booster.boost(app, Profiles.level(app, pkg), pkg);
                Booster.gameModePerformance(pkg);
                notifyInfo("Booster otimizou o jogo", r.forced + " apps fechados, " + (r.freedBytes / 1048576) + " MB liberados");
            }
            if (Profiles.dnd(app, pkg)) Booster.enableDnd(app);
            GameMode.apply(app, pkg);
        }).start();
    }

    private static long lastAlert;

    private static void notifyInfo(String title, String text) {
        try {
            android.app.NotificationManager nm = app.getSystemService(android.app.NotificationManager.class);
            nm.createNotificationChannel(new android.app.NotificationChannel("alerts", "Alertas do jogo", android.app.NotificationManager.IMPORTANCE_HIGH));
            nm.notify(2, new android.app.Notification.Builder(app, "alerts").setSmallIcon(R.drawable.ic_bolt)
                    .setContentTitle(title).setContentText(text).setTimeoutAfter(8000).setAutoCancel(true).build());
        } catch (Exception ignored) {
        }
    }

    // Resumo ao sair do jogo, em uma notificação que fica até ser dispensada.
    private static void notifySummary(Reports.Record r, long sec) {
        java.util.Locale us = java.util.Locale.US;
        StringBuilder b = new StringBuilder();
        if (r.measured) b.append("FPS médio ").append(r.fpsAvg).append(" / ").append(r.target).append(" · nota ").append(Reports.grade(r.score())).append('\n');
        if (r.cpuMax > 0) b.append("CPU máx ").append(String.format(us, "%.0f", r.cpuMax)).append("°C · ");
        b.append("bateria máx ").append(String.format(us, "%.0f", r.battTempMax)).append("°C\n");
        b.append("RAM máx ").append(String.format(us, "%.1f", r.ramMaxMb / 1024f)).append(" GB · bateria ").append(r.battEnd - r.battStart).append("%");
        if (r.pingAvg > 0) b.append("\nPing médio ").append(r.pingAvg).append(" ms, perda ").append(r.lossPct).append('%');
        try {
            android.app.NotificationManager nm = app.getSystemService(android.app.NotificationManager.class);
            nm.createNotificationChannel(new android.app.NotificationChannel("summary", "Resumo da partida", android.app.NotificationManager.IMPORTANCE_DEFAULT));
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(app, 3, new android.content.Intent(app, MainActivity.class),
                    android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            nm.notify(3, new android.app.Notification.Builder(app, "summary").setSmallIcon(R.drawable.ic_bolt)
                    .setContentTitle(r.label + " · " + (sec / 60) + " min").setContentText(b.toString().replace('\n', ' '))
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(b.toString()))
                    .setContentIntent(pi).setAutoCancel(true).build());
        } catch (Exception ignored) {
        }
    }

    public static int heatLimit(Context c) {
        return c.getSharedPreferences("wr", Context.MODE_PRIVATE).getInt("heat_alert", 46);
    }

    // Avisa na tela quando CPU ou bateria passam do limite, no máximo a cada 2 minutos.
    private static void heatAlert(Sample s) {
        int lim = heatLimit(app);
        if (lim <= 0 || activeGame() == null) return;
        long now = System.currentTimeMillis();
        if (prefs().getBoolean("heat_predict", true) && !Float.isNaN(s.trend) && !Float.isNaN(s.cpu) && s.cpu < lim
                && s.trend >= 0.6f && (lim - s.cpu) / s.trend <= 4 && now - lastPredict > 180_000) {
            lastPredict = now;
            notifyInfo("Esquentando rápido: CPU em " + Math.round(s.cpu) + "°C (+" + String.format(java.util.Locale.US, "%.1f", s.trend) + "°C/min)",
                    "Deve chegar a " + lim + "°C em cerca de " + Math.max(1, Math.round((lim - s.cpu) / s.trend)) + " min. Reduza gráficos ou sombras agora para manter o FPS.");
        }
        if (now - lastAlert < 120_000) return;
        if (!Float.isNaN(s.cpu) && s.cpu >= lim) {
            lastAlert = now;
            notifyInfo("Aparelho quente: CPU em " + Math.round(s.cpu) + "°C", "Baixe o gráfico do jogo, tire a capa ou faça uma pausa curta.");
        } else if (s.sys.tempC >= 42) {
            lastAlert = now;
            notifyInfo("Bateria quente: " + Math.round(s.sys.tempC) + "°C", "Evite jogar carregando para não perder desempenho.");
        }
    }

    // Causa provável de uma queda de FPS no instante atual, ou null.
    public static String cause(Sample s) {
        if (s.fps <= 0) return null;
        int target = OverlayText.fpsTarget(prefs(), s);
        if (s.fps >= 0.85f * target) return null;
        int lim = heatLimit(app);
        if (s.sys.thermalStatus >= 2 || (lim > 0 && !Float.isNaN(s.cpu) && s.cpu >= lim)) return "calor";
        if (s.sys.ramUsedPct() >= 88) return "pouca RAM livre";
        if (s.sys.battPct <= 15 && !s.sys.charging) return "bateria baixa";
        return "limite do jogo";
    }

    // Motivo de o FPS estar vazio, mostrado no monitor para facilitar o diagnóstico.
    public static volatile String fpsNote = "";

    private static void readFps(Sample s) {
        String pkg = activeGame();
        if (pkg == null) {
            fpsNote = "sem jogo detectado";
            tracker.reset();
            return;
        }
        if (!Shell.granted()) {
            fpsNote = "conecte o modo avançado";
            tracker.reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (!pkg.equals(layerPkg)) {
            layerPkg = pkg;
            layer = null;
            tracker.reset();
        }
        java.util.List<Long> frames = null;
        if (layer != null) {
            frames = Parsers.parseLatency(Shell.exec("dumpsys SurfaceFlinger --latency '" + layer + "'", 2000));
            if (frames.isEmpty()) layer = null;
        }
        if (layer == null) {
            if (now - layerCheck < 2000) return;
            layerCheck = now;
            java.util.List<String> cands = Parsers.findLayers(Shell.exec("dumpsys SurfaceFlinger --list", 3000), pkg);
            if (cands.isEmpty()) {
                fpsNote = "camada do jogo não encontrada";
                tracker.reset();
                return;
            }
            for (int i = 0; i < Math.min(4, cands.size()); i++) {
                java.util.List<Long> f = Parsers.parseLatency(Shell.exec("dumpsys SurfaceFlinger --latency '" + cands.get(i) + "'", 2000));
                if (f.size() > 2) {
                    layer = cands.get(i);
                    frames = f;
                    break;
                }
            }
            if (layer == null) {
                fpsNote = "sistema não informou os quadros";
                return;
            }
        }
        fpsNote = "";
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
            ramPctSum += s.sys.ramUsedPct();
            ticksN++;
            lastBatt = s.sys.battPct;
            hzSeen = s.hz;
            if (!Float.isNaN(s.cpu)) {
                cpuMax = Math.max(cpuMax, s.cpu);
                cpuSum += s.cpu;
                cpuN++;
            }
            if (!Float.isNaN(s.gpu)) gpuMax = Math.max(gpuMax, s.gpu);
            battMax = Math.max(battMax, s.sys.tempC);
            if (s.sys.thermalStatus >= 2) throttleSec++;
            fpsSer.add(s.fps);
            if (s.cause != null) {
                if (s.cause.equals("calor")) dropHeat++;
                else if (s.cause.equals("pouca RAM livre")) dropRam++;
                else dropOther++;
            }
            cpuSer.add(Float.isNaN(s.cpu) ? -1 : Math.round(s.cpu * 10));
            if (s.fps > 0) {
                fpsSum += s.fps;
                fpsN++;
                fpsMin = Math.min(fpsMin, s.fps);
                fpsMaxS = Math.max(fpsMaxS, s.fps);
                if (s.fps >= 0.9f * OverlayText.fpsTarget(prefs(), s)) stableSec++;
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

    public static synchronized void startSession(String pkg) {
        sessionOn = true;
        sStart = System.currentTimeMillis();
        fpsSum = fpsN = ramMax = throttleSec = stableSec = ramPctSum = ticksN = cpuN = 0;
        fpsMin = Integer.MAX_VALUE;
        fpsMaxS = 0;
        cpuMax = gpuMax = battMax = lowSum = cpuSum = 0;
        lowN = 0;
        dropHeat = dropRam = dropOther = 0;
        cpuSer.clear();
        fpsSer.clear();
        pingSum = pingN = lossSum = 0;
        pingMaxS = 0;
        gamePkg = pkg;
        awayTicks = 0;
        Sample l = latest;
        battStart = lastBatt = l == null ? 0 : l.sys.battPct;
        stutterBase = tracker.stutters;
    }

    public static synchronized boolean sessionActive() {
        return sessionOn;
    }

    private static int[] shrink(List<Integer> src, int max) {
        int n = src.size();
        if (n == 0) return new int[0];
        int out = Math.min(n, max);
        int[] a = new int[out];
        for (int i = 0; i < out; i++) {
            int from = (int) ((long) i * n / out), to = Math.max(from + 1, (int) ((long) (i + 1) * n / out));
            long sum = 0;
            int c = 0;
            for (int k = from; k < to && k < n; k++) {
                if (src.get(k) >= 0) {
                    sum += src.get(k);
                    c++;
                }
            }
            a[i] = c == 0 ? -1 : (int) (sum / c);
        }
        return a;
    }

    public static synchronized String endSession() {
        if (!sessionOn) return null;
        sessionOn = false;
        long sec = (System.currentTimeMillis() - sStart) / 1000;
        java.util.Locale us = java.util.Locale.US;

        Reports.Record r = new Reports.Record();
        r.pkg = gamePkg == null ? "desconhecido" : gamePkg;
        r.label = gamePkg == null ? "Jogo não identificado" : Games.label(app, gamePkg);
        r.start = sStart;
        r.durSec = (int) sec;
        r.measured = fpsN > 0;
        if (r.measured) {
            r.fpsAvg = (int) (fpsSum / fpsN);
            r.fpsMin = fpsMin;
            r.fpsMax = fpsMaxS;
            r.low1 = lowN > 0 ? Math.round(lowSum / lowN) : r.fpsAvg;
            r.stablePct = (int) (stableSec * 100 / fpsN);
        }
        r.stutters = Math.max(0, tracker.stutters - stutterBase);
        r.hz = hzSeen > 0 ? hzSeen : 60;
        int pt = gamePkg == null ? 0 : prefs().getInt("gt_" + gamePkg, 0);
        if (pt == 0) pt = prefs().getInt("fps_target", 0);
        r.target = pt > 0 ? pt : r.hz;
        r.battStart = battStart;
        r.battEnd = lastBatt;
        r.throttleSec = (int) throttleSec;
        r.dropHeatSec = dropHeat;
        r.dropRamSec = dropRam;
        r.dropOtherSec = dropOther;
        r.cpuMax = cpuMax;
        r.cpuAvg = cpuN > 0 ? cpuSum / cpuN : 0;
        r.gpuMax = gpuMax;
        r.battTempMax = battMax;
        r.ramMaxMb = (int) (ramMax / 1048576);
        r.ramAvgPct = ticksN > 0 ? (int) (ramPctSum / ticksN) : 0;
        r.pingAvg = pingN > 0 ? (int) (pingSum / pingN) : 0;
        r.pingMax = pingMaxS;
        r.lossPct = pingN > 0 ? (int) (lossSum / pingN) : 0;
        r.fpsSeries = shrink(fpsSer, 90);
        r.cpuSeries = shrink(cpuSer, 90);
        if (sec >= 20) Reports.add(app, r);
        gamePkg = null;

        StringBuilder b = new StringBuilder();
        b.append(r.label).append('\n');
        b.append("Duração: ").append(sec / 60).append(" min ").append(sec % 60).append(" s\n");
        if (r.measured) {
            b.append("FPS médio: ").append(r.fpsAvg).append(" · mínimo: ").append(r.fpsMin);
            if (lowN > 0) b.append(" · 1% low médio: ").append(r.low1);
            b.append("\nTravadas (quadros > 50 ms): ").append(r.stutters).append('\n');
        } else {
            b.append("FPS: não medido (modo avançado inativo ou jogo não detectado)\n");
        }
        if (cpuMax > 0) b.append("CPU máx: ").append(String.format(us, "%.1f", cpuMax)).append("°C\n");
        b.append("Bateria máx: ").append(String.format(us, "%.1f", battMax)).append("°C\n");
        b.append("RAM máx em uso: ").append(SystemInfo.gb(ramMax)).append(" GB\n");
        b.append("Tempo com limitação térmica: ").append(throttleSec).append(" s");
        String sum = b.toString();
        prefs().edit().putString("last", sum).apply();
        final Context a = app;
        new Thread(() -> GameMode.restore(a)).start();
        if (sec >= 20 && prefs().getBoolean("post_summary", true)) notifySummary(r, sec);
        return sum;
    }
}
