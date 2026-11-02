package com.lucie.wrboost;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Histórico de sessões por jogo, guardado em reports.json.
public final class Reports {
    private static final int MAX = 400;
    public static volatile int version;

    public static final class Record {
        public String pkg, label;
        public long start;
        public int durSec, fpsAvg, fpsMin, fpsMax, low1, stutters, stablePct, hz, target, battStart, battEnd, throttleSec, dropHeatSec, dropRamSec, dropOtherSec;
        public float cpuMax, cpuAvg, gpuMax, battTempMax;
        public int ramMaxMb, ramAvgPct;
        public int[] fpsSeries = new int[0], cpuSeries = new int[0];
        public boolean measured;

        public int score() {
            if (!measured) return -1;
            float t = Math.max(1, target);
            float avgPart = Math.min(100, fpsAvg * 100f / t);
            float lowPart = fpsAvg > 0 ? Math.min(100, low1 * 100f / fpsAvg) : 0;
            float heat = 100 - Math.min(100, throttleSec * 200f / Math.max(1, durSec));
            return Math.round(Math.max(0, Math.min(100, 0.45f * stablePct + 0.25f * avgPart + 0.15f * lowPart + 0.15f * heat)));
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("pkg", pkg).put("label", label).put("start", start).put("dur", durSec).put("fa", fpsAvg)
                    .put("fn", fpsMin).put("fx", fpsMax).put("lo", low1).put("st", stutters).put("sb", stablePct)
                    .put("hz", hz).put("tg", target).put("b0", battStart).put("b1", battEnd).put("th", throttleSec)
                    .put("cx", cpuMax).put("ca", cpuAvg).put("gx", gpuMax).put("bx", battTempMax)
                    .put("dh", dropHeatSec).put("dr", dropRamSec).put("do", dropOtherSec).put("rm", ramMaxMb).put("ra", ramAvgPct).put("ms", measured);
            o.put("fs", arr(fpsSeries)).put("cs", arr(cpuSeries));
            return o;
        }

        static Record fromJson(JSONObject o) {
            Record r = new Record();
            r.pkg = o.optString("pkg");
            r.label = o.optString("label");
            r.start = o.optLong("start");
            r.durSec = o.optInt("dur");
            r.fpsAvg = o.optInt("fa");
            r.fpsMin = o.optInt("fn");
            r.fpsMax = o.optInt("fx");
            r.low1 = o.optInt("lo");
            r.stutters = o.optInt("st");
            r.stablePct = o.optInt("sb");
            r.hz = o.optInt("hz", 60);
            r.target = o.optInt("tg", 60);
            r.battStart = o.optInt("b0");
            r.battEnd = o.optInt("b1");
            r.throttleSec = o.optInt("th");
            r.cpuMax = (float) o.optDouble("cx", 0);
            r.cpuAvg = (float) o.optDouble("ca", 0);
            r.gpuMax = (float) o.optDouble("gx", 0);
            r.battTempMax = (float) o.optDouble("bx", 0);
            r.ramMaxMb = o.optInt("rm");
            r.dropHeatSec = o.optInt("dh");
            r.dropRamSec = o.optInt("dr");
            r.dropOtherSec = o.optInt("do");
            r.ramAvgPct = o.optInt("ra");
            r.measured = o.optBoolean("ms");
            r.fpsSeries = ints(o.optJSONArray("fs"));
            r.cpuSeries = ints(o.optJSONArray("cs"));
            return r;
        }
    }

    // Resumo de todas as sessões de um jogo.
    public static final class Agg {
        public String pkg, label;
        public int sessions, avgScore = -1, bestFps, avgFps;
        public long totalSec, lastPlayed;
        public float cpuMaxAll;
        public Record last;
    }

    private Reports() {
    }

    private static JSONArray arr(int[] a) {
        JSONArray j = new JSONArray();
        for (int v : a) j.put(v);
        return j;
    }

    private static int[] ints(JSONArray j) {
        if (j == null) return new int[0];
        int[] a = new int[j.length()];
        for (int i = 0; i < a.length; i++) a[i] = j.optInt(i);
        return a;
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "reports.json");
    }

    // Mais recentes primeiro.
    public static synchronized List<Record> all(Context c) {
        List<Record> out = new ArrayList<>();
        try {
            File f = file(c);
            if (!f.exists()) return out;
            JSONArray j = new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (int i = j.length() - 1; i >= 0; i--) out.add(Record.fromJson(j.getJSONObject(i)));
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void write(Context c, List<Record> newestFirst) throws Exception {
        JSONArray j = new JSONArray();
        for (int i = Math.min(newestFirst.size(), MAX) - 1; i >= 0; i--) j.put(newestFirst.get(i).toJson());
        Files.write(file(c).toPath(), j.toString().getBytes(StandardCharsets.UTF_8));
        version++;
    }

    public static synchronized void add(Context c, Record r) {
        try {
            List<Record> l = all(c);
            l.add(0, r);
            write(c, l);
        } catch (Exception ignored) {
        }
    }

    public static synchronized void clear(Context c, String pkg) {
        try {
            List<Record> keep = new ArrayList<>();
            if (pkg != null) for (Record r : all(c)) if (!r.pkg.equals(pkg)) keep.add(r);
            write(c, keep);
        } catch (Exception ignored) {
        }
    }

    public static List<Agg> byGame(Context c) {
        Map<String, Agg> m = new LinkedHashMap<>();
        Map<String, long[]> acc = new LinkedHashMap<>();
        for (Record r : all(c)) {
            Agg a = m.get(r.pkg);
            if (a == null) {
                a = new Agg();
                a.pkg = r.pkg;
                a.label = r.label;
                a.last = r;
                a.lastPlayed = r.start;
                m.put(r.pkg, a);
                acc.put(r.pkg, new long[4]);
            }
            long[] s = acc.get(r.pkg);
            a.sessions++;
            a.totalSec += r.durSec;
            a.cpuMaxAll = Math.max(a.cpuMaxAll, r.cpuMax);
            if (r.measured) {
                s[0] += (long) r.fpsAvg * r.durSec;
                s[1] += r.durSec;
                s[2] += r.score();
                s[3]++;
                a.bestFps = Math.max(a.bestFps, r.fpsAvg);
            }
        }
        for (Agg a : m.values()) {
            long[] s = acc.get(a.pkg);
            if (s[1] > 0) a.avgFps = (int) (s[0] / s[1]);
            if (s[3] > 0) a.avgScore = (int) (s[2] / s[3]);
        }
        return new ArrayList<>(m.values());
    }

    public static List<Record> forGame(Context c, String pkg) {
        List<Record> out = new ArrayList<>();
        for (Record r : all(c)) if (r.pkg.equals(pkg)) out.add(r);
        return out;
    }

    public static String grade(int score) {
        return score < 0 ? "-" : score >= 90 ? "S" : score >= 80 ? "A" : score >= 65 ? "B" : score >= 50 ? "C" : "D";
    }

    public static String duration(long sec) {
        long h = sec / 3600, m = sec % 3600 / 60;
        return h > 0 ? h + " h " + m + " min" : m > 0 ? m + " min " + sec % 60 + " s" : sec + " s";
    }

    public static String when(long t) {
        return new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR")).format(new Date(t));
    }

    public static List<String> insights(Record r) {
        List<String> l = new ArrayList<>();
        if (!r.measured) {
            l.add("FPS não medido nesta sessão. Ative o modo avançado (ADB embutido) para medir.");
        } else {
            if (r.stablePct < 60) l.add("Só " + r.stablePct + "% do tempo ficou perto de " + r.target + " FPS. Reduza efeitos, sombras ou a taxa de quadros do jogo.");
            if (r.fpsAvg > 0 && r.low1 * 100 / r.fpsAvg < 60) l.add("Muita oscilação (1% low baixo). Um FPS fixo menor costuma ficar mais fluido.");
            if (r.stutters > 20) l.add(r.stutters + " travadas (quadros acima de 50 ms).");
            if (r.stablePct >= 90) l.add("Desempenho muito estável nesta sessão.");
        }
        if (r.throttleSec > 30) l.add("O aparelho limitou o desempenho por calor durante " + duration(r.throttleSec) + ".");
        if (r.cpuMax >= 50) l.add("CPU chegou a " + String.format(Locale.US, "%.0f", r.cpuMax) + "°C. Tire a capa e evite jogar carregando.");
        if (r.ramAvgPct >= 85) l.add("RAM ficou perto do limite (" + r.ramAvgPct + "%). Use Liberar RAM antes de jogar.");
        if (r.battStart > r.battEnd && r.durSec >= 300) {
            l.add("Bateria caiu " + (r.battStart - r.battEnd) + "% em " + duration(r.durSec) + " (cerca de "
                    + Math.round((r.battStart - r.battEnd) * 3600f / r.durSec) + "% por hora).");
        }
        if (r.battTempMax >= 42) l.add("A bateria chegou a " + String.format(Locale.US, "%.0f", r.battTempMax) + "°C. Evite jogar carregando.");
        int drops = r.dropHeatSec + r.dropRamSec + r.dropOtherSec;
        if (drops >= 15) {
            if (r.dropHeatSec * 2 >= drops) l.add("Maior parte das quedas de FPS (" + duration(drops) + ") coincidiu com calor alto.");
            else if (r.dropRamSec * 2 >= drops) l.add("Maior parte das quedas de FPS (" + duration(drops) + ") coincidiu com pouca RAM livre.");
            else l.add("Quedas de FPS por " + duration(drops) + " sem calor nem falta de RAM: provavelmente o limite do jogo ou do gráfico escolhido.");
        }
        if (l.isEmpty()) l.add("Sem problemas encontrados.");
        return l;
    }

    // Compara a sessão com a média das anteriores do mesmo jogo.
    public static String compare(List<Record> newestFirst) {
        if (newestFirst.size() < 2 || !newestFirst.get(0).measured) return null;
        Record r = newestFirst.get(0);
        long fps = 0, score = 0;
        int n = 0;
        for (int i = 1; i < newestFirst.size(); i++) {
            Record x = newestFirst.get(i);
            if (!x.measured) continue;
            fps += x.fpsAvg;
            score += x.score();
            n++;
        }
        if (n == 0 || fps == 0) return null;
        float avgFps = fps / (float) n;
        int diff = Math.round((r.fpsAvg - avgFps) * 100f / avgFps);
        int sd = r.score() - Math.round(score / (float) n);
        String fp = Math.abs(diff) < 3 ? "FPS na média das suas últimas partidas" : "FPS " + Math.abs(diff) + "% " + (diff > 0 ? "acima" : "abaixo") + " da sua média";
        return fp + " · nota " + (sd == 0 ? "igual" : (sd > 0 ? "+" : "") + sd + " pontos") + " (base: " + n + (n == 1 ? " sessão" : " sessões") + ")";
    }

    public static String text(Record r) {
        Locale us = Locale.US;
        StringBuilder b = new StringBuilder();
        b.append("Booster - ").append(r.label).append('\n').append(when(r.start)).append(" · ").append(duration(r.durSec)).append('\n');
        if (r.measured) {
            b.append("Nota ").append(grade(r.score())).append(" (").append(r.score()).append("/100)\n");
            b.append("FPS médio ").append(r.fpsAvg).append(" / ").append(r.target).append(" · mín ").append(r.fpsMin)
                    .append(" · máx ").append(r.fpsMax).append(" · 1% low ").append(r.low1).append('\n');
            b.append("Estabilidade ").append(r.stablePct).append("% · travadas ").append(r.stutters).append('\n');
        }
        b.append("CPU máx ").append(String.format(us, "%.0f", r.cpuMax)).append("°C · GPU máx ")
                .append(String.format(us, "%.0f", r.gpuMax)).append("°C\n");
        b.append("RAM máx ").append(r.ramMaxMb).append(" MB · bateria ").append(r.battStart).append("% → ").append(r.battEnd).append('%');
        return b.toString();
    }
}
