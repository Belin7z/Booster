package com.lucie.wrboost;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.Locale;

// Monta o texto do monitor flutuante conforme os itens escolhidos nas configurações.
public final class OverlayText {
    public static final int OK = 0xFF2ECC71, WARN = 0xFFF5A623, BAD = 0xFFFF5252, LABEL = 0xFF9AA9CC, WHITE = 0xFFF2F5FF;

    private OverlayText() {
    }

    // 0 = acompanhar a taxa atual da tela.
    public static int fpsTarget(SharedPreferences p, Monitor.Sample m) {
        String g = Monitor.activeGame();
        int gt = g == null ? 0 : p.getInt("gt_" + g, 0);
        if (gt > 0) return gt;
        int t = p.getInt("fps_target", 0);
        return t > 0 ? t : m.hz;
    }

    public static int fpsColor(int fps, int target) {
        if (fps < 0) return WHITE;
        float r = fps / (float) Math.max(1, target);
        return fps >= 55 && r >= 0.45f || r >= 0.9f ? OK : fps >= 40 ? WARN : BAD;
    }

    public static int tempColor(float c, float warn, float bad) {
        return Float.isNaN(c) ? WHITE : c >= bad ? BAD : c >= warn ? WARN : OK;
    }

    public static CharSequence build(Monitor.Sample m, SharedPreferences p) {
        Locale us = Locale.US;
        SystemInfo s = m.sys;
        boolean horizontal = p.getBoolean("ov_h", false);
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (p.getBoolean("ov_fps", true)) {
            int target = fpsTarget(p, m);
            item(b, "FPS", (m.fps >= 0 ? String.valueOf(m.fps) : "--") + " / " + target, fpsColor(m.fps, target), horizontal);
        }
        if (p.getBoolean("ov_low", false)) {
            item(b, "1%", Float.isNaN(m.low1) ? "--" : String.valueOf(Math.round(m.low1)), WHITE, horizontal);
        }
        if (p.getBoolean("ov_cpu", true)) {
            item(b, "CPU", Float.isNaN(m.cpu) ? "--" : String.format(us, "%.0f°C", m.cpu), tempColor(m.cpu, 43, 50), horizontal);
        }
        if (p.getBoolean("ov_gpu", true)) {
            item(b, "GPU", Float.isNaN(m.gpu) ? "--" : String.format(us, "%.0f°C", m.gpu), tempColor(m.gpu, 43, 50), horizontal);
        }
        if (p.getBoolean("ov_ram", true)) {
            item(b, "RAM", SystemInfo.gb(s.ramTotal - s.ramAvail) + "/" + SystemInfo.gb(s.ramTotal) + " GB",
                    s.ramUsedPct() >= 90 ? BAD : s.ramUsedPct() >= 80 ? WARN : WHITE, horizontal);
        }
        if (p.getBoolean("ov_bat", false)) {
            item(b, "BAT", s.battPct + "%" + (s.charging ? " +" : "") + " " + String.format(us, "%.0f°C", s.tempC),
                    tempColor(s.tempC, 38, 43), horizontal);
        }
        if (p.getBoolean("ov_ping", false)) {
            int pg = Monitor.pingMs;
            item(b, "PING", pg < 0 ? "--" : pg + " ms" + (Monitor.lossPct > 0 ? " · " + Monitor.lossPct + "%" : ""),
                    pg < 0 ? WHITE : pg >= 120 ? BAD : pg >= 60 ? WARN : OK, horizontal);
        }
        if (b.length() == 0) b.append("Nenhum item selecionado");
        return b;
    }

    private static void item(SpannableStringBuilder b, String label, String value, int color, boolean horizontal) {
        if (b.length() > 0) b.append(horizontal ? "    " : "\n");
        int a = b.length();
        b.append(label).append(' ');
        b.setSpan(new ForegroundColorSpan(LABEL), a, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new RelativeSizeSpan(0.82f), a, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int v = b.length();
        b.append(value);
        b.setSpan(new ForegroundColorSpan(color), v, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new StyleSpan(Typeface.BOLD), v, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
