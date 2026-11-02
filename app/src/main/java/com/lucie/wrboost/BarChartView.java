package com.lucie.wrboost;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

// Gráfico de barras: uma barra por dia, com o valor em cima e o dia embaixo.
public class BarChartView extends View {
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint();
    private final RectF r = new RectF();
    private final float d;
    private float[] values = new float[0];
    private String[] top = new String[0], bottom = new String[0];
    private String title = "";

    public BarChartView(Context c, String title) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        this.title = title;
        txt.setTextAlign(Paint.Align.CENTER);
        grid.setColor(Theme.GRID);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Theme.CARD);
        g.setCornerRadius(14 * d);
        g.setStroke((int) Math.max(1, d), Theme.LINE);
        setBackground(g);
    }

    public void set(float[] values, String[] topLabels, String[] bottomLabels) {
        this.values = values;
        this.top = topLabels;
        this.bottom = bottomLabels;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 12 * d;
        txt.setTextAlign(Paint.Align.LEFT);
        txt.setColor(Theme.MUT);
        txt.setTextSize(11 * d);
        c.drawText(title, pad, 18 * d, txt);
        txt.setTextAlign(Paint.Align.CENTER);
        int n = values.length;
        if (n == 0) return;
        float max = 0;
        for (float v : values) max = Math.max(max, v);
        if (max <= 0) max = 1;
        float baseY = h - 22 * d, topY = 40 * d;
        c.drawLine(pad, baseY, w - pad, baseY, grid);
        float slot = (w - 2 * pad) / n, bw = Math.min(slot * 0.62f, 26 * d);
        for (int i = 0; i < n; i++) {
            float cx = pad + slot * i + slot / 2;
            float bh = (baseY - topY) * values[i] / max;
            boolean today = i == n - 1;
            bar.setColor(values[i] <= 0 ? Theme.TRACK : today ? Theme.ACC : Theme.BAR);
            r.set(cx - bw / 2, values[i] <= 0 ? baseY - 3 * d : baseY - Math.max(bh, 3 * d), cx + bw / 2, baseY);
            c.drawRoundRect(r, 5 * d, 5 * d, bar);
            txt.setTextSize(9 * d);
            txt.setColor(today ? Theme.TXT : Theme.MUT);
            if (i < bottom.length) c.drawText(bottom[i], cx, h - 7 * d, txt);
            if (i < top.length && !top[i].isEmpty() && n <= 10) {
                txt.setColor(Theme.TXT);
                c.drawText(top[i], cx, r.top - 4 * d, txt);
            }
        }
    }
}
