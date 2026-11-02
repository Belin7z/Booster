package com.lucie.wrboost;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Map;

// Compara duas sessões de um jogo (tabela com o vencedor de cada métrica) e hoje × ontem.
public final class CompareDialog {
    private CompareDialog() {
    }

    private static AlertDialog.Builder builder(Activity a) {
        return new AlertDialog.Builder(a, Theme.isLight()
                ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert);
    }

    public static void pick(Activity a, String label, List<Reports.Record> rs) {
        if (rs.size() < 2) {
            android.widget.Toast.makeText(a, "Precisa de pelo menos 2 sessões.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[rs.size()];
        for (int i = 0; i < names.length; i++) {
            Reports.Record r = rs.get(i);
            names[i] = Reports.when(r.start) + " · " + Reports.duration(r.durSec) + (r.measured ? " · " + r.fpsAvg + " FPS" : "");
        }
        final int[] sel = {0, 1};
        builder(a).setTitle("Sessão A").setSingleChoiceItems(names, 0, (d, w) -> {
            sel[0] = w;
            d.dismiss();
            builder(a).setTitle("Sessão B").setSingleChoiceItems(names, Math.min(1, names.length - 1), (d2, w2) -> {
                d2.dismiss();
                show(a, label, rs.get(sel[0]), rs.get(w2));
            }).setNegativeButton("Cancelar", null).show();
        }).setNegativeButton("Cancelar", null).show();
    }

    private static TextView cell(Activity a, String s, int color, boolean bold, float weight) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        t.setPadding(0, (int) (5 * a.getResources().getDisplayMetrics().density), 0, (int) (5 * a.getResources().getDisplayMetrics().density));
        return t;
    }

    // higher: true = maior é melhor. Retorna 1 se A vence, 2 se B vence, 0 se empata.
    private static void row(Activity a, LinearLayout l, String name, double x, double y, String fx, String fy, boolean higher) {
        LinearLayout r = new LinearLayout(a);
        int win = x == y ? 0 : ((x > y) == higher ? 1 : 2);
        r.addView(cell(a, name, Theme.MUT, false, 1.1f));
        r.addView(cell(a, fx, win == 1 ? 0xFF3DDC97 : Theme.TXT, win == 1, 1f));
        r.addView(cell(a, fy, win == 2 ? 0xFF3DDC97 : Theme.TXT, win == 2, 1f));
        l.addView(r);
    }

    private static void show(Activity a, String label, Reports.Record x, Reports.Record y) {
        float d = a.getResources().getDisplayMetrics().density;
        ScrollView sv = new ScrollView(a);
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding((int) (16 * d), (int) (16 * d), (int) (16 * d), (int) (16 * d));
        sv.addView(l);

        TextView t = new TextView(a);
        t.setText("Comparar sessões · " + label);
        t.setTextColor(Theme.TXT);
        t.setTextSize(17);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        l.addView(t);

        LinearLayout h = new LinearLayout(a);
        h.addView(cell(a, "", Theme.MUT, false, 1.1f));
        h.addView(cell(a, "A · " + Reports.when(x.start), Theme.ACC, true, 1f));
        h.addView(cell(a, "B · " + Reports.when(y.start), 0xFFFF9F43, true, 1f));
        l.addView(h);

        Locale us = Locale.US;
        row(a, l, "Duração", x.durSec, y.durSec, Reports.duration(x.durSec), Reports.duration(y.durSec), true);
        if (x.measured && y.measured) {
            row(a, l, "Nota", x.score(), y.score(), x.score() + " (" + Reports.grade(x.score()) + ")", y.score() + " (" + Reports.grade(y.score()) + ")", true);
            row(a, l, "FPS médio", x.fpsAvg, y.fpsAvg, "" + x.fpsAvg, "" + y.fpsAvg, true);
            row(a, l, "FPS mínimo", x.fpsMin, y.fpsMin, "" + x.fpsMin, "" + y.fpsMin, true);
            row(a, l, "1% low", x.low1, y.low1, "" + x.low1, "" + y.low1, true);
            row(a, l, "Estabilidade", x.stablePct, y.stablePct, x.stablePct + "%", y.stablePct + "%", true);
            row(a, l, "Travadas", x.stutters, y.stutters, "" + x.stutters, "" + y.stutters, false);
        }
        row(a, l, "CPU máx", x.cpuMax, y.cpuMax, String.format(us, "%.0f°C", x.cpuMax), String.format(us, "%.0f°C", y.cpuMax), false);
        row(a, l, "GPU máx", x.gpuMax, y.gpuMax, String.format(us, "%.0f°C", x.gpuMax), String.format(us, "%.0f°C", y.gpuMax), false);
        row(a, l, "RAM máx", x.ramMaxMb, y.ramMaxMb, x.ramMaxMb + " MB", y.ramMaxMb + " MB", false);
        row(a, l, "Limitação térmica", x.throttleSec, y.throttleSec, Reports.duration(x.throttleSec), Reports.duration(y.throttleSec), false);
        if (x.pingAvg > 0 && y.pingAvg > 0) {
            row(a, l, "Ping médio", x.pingAvg, y.pingAvg, x.pingAvg + " ms", y.pingAvg + " ms", false);
            row(a, l, "Perda de pacotes", x.lossPct, y.lossPct, x.lossPct + "%", y.lossPct + "%", false);
        }

        if (x.measured || y.measured) {
            CompareView cv = new CompareView(a);
            cv.set(toF(x.fpsSeries), toF(y.fpsSeries), Math.max(Math.max(x.target, y.target), Math.max(x.fpsMax, y.fpsMax)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) (170 * d));
            lp.topMargin = (int) (12 * d);
            l.addView(cv, lp);
        }

        TextView day = new TextView(a);
        day.setTextColor(Theme.MUT);
        day.setTextSize(12);
        day.setPadding(0, (int) (12 * d), 0, 0);
        day.setText(days(a));
        l.addView(day);

        AlertDialog dlg = builder(a).setView(sv).setPositiveButton("Fechar", null).create();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.BG);
        bg.setCornerRadius(18 * d);
        dlg.getWindow().setBackgroundDrawable(bg);
        dlg.show();
    }

    private static float[] toF(int[] a) {
        float[] f = new float[a.length];
        for (int i = 0; i < a.length; i++) f[i] = a[i];
        return f;
    }

    // Tempo jogado hoje × ontem, a partir do registro de uso.
    private static String days(Activity a) {
        Map<String, Map<String, Long>> all = PlayTime.load(a);
        long now = System.currentTimeMillis();
        long today = PlayTime.dayTotal(all, PlayTime.key(now));
        long yest = PlayTime.dayTotal(all, PlayTime.key(now - 86400_000L));
        String diff = today == yest ? "igual" : today > yest ? "+" + PlayTime.fmtShort(today - yest) : "-" + PlayTime.fmtShort(yest - today);
        return "Tempo de jogo: hoje " + PlayTime.fmtShort(today) + " × ontem " + PlayTime.fmtShort(yest) + " (" + diff + ")";
    }
}
