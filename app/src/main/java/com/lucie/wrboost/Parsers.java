package com.lucie.wrboost;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Parsers sem dependência de Android, para poderem ser testados na JVM.
public final class Parsers {
    private Parsers() {
    }

    public static final class Temps {
        public float cpu = Float.NaN, gpu = Float.NaN;
        public String cpuSrc = "";
    }

    private static final String[] EXCLUDE = {"batt", "bat", "charg", "usb", "skin", "modem", "wifi", "flash", "ufs",
            "xo", "quiet", "ambient", "board", "camera", "lcd", "case", "disp", "pa", "wlan", "nfc", "pmic", "cam"};
    private static final String[] CPU_KEYS = {"cpu", "soc", "big", "mid", "little", "core", "ap_", "tsens", "mtk", "apu"};

    private static boolean has(String s, String[] keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    // Aceita milésimos de grau (padrão do kernel) ou graus.
    static float normalize(float v) {
        if (Math.abs(v) >= 1000) v /= 1000f;
        return (v >= 5 && v <= 125) ? v : Float.NaN;
    }

    // Cada linha: "<tipo> <valor>".
    public static Temps parseZones(String text) {
        Temps t = new Temps();
        if (text == null) return t;
        for (String line : text.split("\n")) {
            line = line.trim();
            int sp = line.lastIndexOf(' ');
            if (sp <= 0) continue;
            String type = line.substring(0, sp).trim().toLowerCase(Locale.ROOT);
            float v;
            try {
                v = normalize(Float.parseFloat(line.substring(sp + 1)));
            } catch (NumberFormatException e) {
                continue;
            }
            if (Float.isNaN(v)) continue;
            if (type.contains("gpu")) {
                if (Float.isNaN(t.gpu) || v > t.gpu) t.gpu = v;
            } else if (has(type, CPU_KEYS) && !excluded(type)) {
                if (Float.isNaN(t.cpu) || v > t.cpu) {
                    t.cpu = v;
                    t.cpuSrc = type;
                }
            }
        }
        return t;
    }

    private static boolean excluded(String type) {
        for (String k : EXCLUDE) {
            if (type.equals(k) || type.startsWith(k + "-") || type.startsWith(k + "_")) return true;
        }
        return type.contains("batt") || type.contains("charg") || type.contains("skin") || type.contains("modem")
                || type.contains("usb");
    }

    private static final Pattern THERMAL = Pattern.compile("mValue=([\\d.]+), mType=(\\d+), mName=([^,}]+)");

    // Tipos do HAL: 0 = CPU, 1 = GPU.
    public static Temps parseThermalService(String text) {
        Temps t = new Temps();
        if (text == null) return t;
        Matcher m = THERMAL.matcher(text);
        while (m.find()) {
            float v = normalize(Float.parseFloat(m.group(1)));
            if (Float.isNaN(v)) continue;
            int type = Integer.parseInt(m.group(2));
            if (type == 0 && (Float.isNaN(t.cpu) || v > t.cpu)) {
                t.cpu = v;
                t.cpuSrc = m.group(3).trim();
            } else if (type == 1 && (Float.isNaN(t.gpu) || v > t.gpu)) {
                t.gpu = v;
            }
        }
        return t;
    }

    // Todas as camadas candidatas do pacote, da mais provável (SurfaceView BLAST) para a menos.
    public static List<String> findLayers(String list, String pkg) {
        List<String> blast = new ArrayList<>(), sv = new ArrayList<>(), other = new ArrayList<>();
        if (list == null || pkg == null) return blast;
        for (String raw : list.split("\n")) {
            String l = raw.trim();
            if (!l.contains(pkg) || l.contains("'")) continue;
            if (l.startsWith("Background for") || l.startsWith("Bounds for") || l.startsWith("ActivityRecord")
                    || l.startsWith("Task=") || l.startsWith("WindowToken")) continue;
            if (l.startsWith("SurfaceView")) (l.contains("BLAST") ? blast : sv).add(l);
            else other.add(l);
        }
        blast.addAll(sv);
        blast.addAll(other);
        return blast;
    }

    public static String findLayer(String list, String pkg) {
        if (list == null || pkg == null) return null;
        String surfaceBlast = null, surface = null, any = null;
        for (String raw : list.split("\n")) {
            String l = raw.trim();
            if (!l.contains(pkg) || l.contains("'")) continue;
            if (l.startsWith("Background for") || l.startsWith("Bounds for") || l.startsWith("ActivityRecord")
                    || l.startsWith("Task=") || l.startsWith("WindowToken")) continue;
            if (l.startsWith("SurfaceView")) {
                if (l.contains("BLAST")) {
                    if (surfaceBlast == null) surfaceBlast = l;
                } else if (surface == null) surface = l;
            } else if (any == null) any = l;
        }
        return surfaceBlast != null ? surfaceBlast : surface != null ? surface : any;
    }

    // Linhas 2+ da saída: "desiredPresent actualPresent frameReady". Usa actualPresent.
    public static List<Long> parseLatency(String text) {
        List<Long> out = new ArrayList<>();
        if (text == null) return out;
        String[] lines = text.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String[] p = lines[i].trim().split("\\s+");
            if (p.length < 3) continue;
            try {
                long ts = Long.parseLong(p[1]);
                if (ts > 0 && ts != Long.MAX_VALUE) out.add(ts);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static boolean safePackage(String p) {
        return p != null && p.matches("[A-Za-z0-9_.]+");
    }
}
