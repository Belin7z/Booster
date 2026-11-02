package com.lucie.wrboost;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

// Duas séries de FPS sobrepostas (A em destaque, B em laranja) na mesma escala.
public class CompareView extends View {
    private final Paint a = new Paint(Paint.ANTI_ALIAS_FLAG), b = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(), label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;
    private float[] sa = new float[0], sb = new float[0];
    private float max = 60;

    public CompareView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[]{a, b}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2 * d);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        a.setColor(Theme.ACC);
        b.setColor(0xFFFF9F43);
        grid.setColor(Theme.GRID);
        label.setColor(Theme.MUT);
        label.setTextSize(11 * d);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Theme.CARD);
        g.setCornerRadius(14 * d);
        setBackground(g);
    }

    public void set(float[] a, float[] b, float max) {
        this.sa = a;
        this.sb = b;
        this.max = Math.max(1, max);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 10 * d, top = 24 * d;
        c.drawText("FPS: A (azul) × B (laranja)", pad, 16 * d, label);
        c.drawLine(pad, h - pad, w - pad, h - pad, grid);
        c.drawLine(pad, top, w - pad, top, grid);
        draw(c, sa, a, w, h, pad, top);
        draw(c, sb, b, w, h, pad, top);
    }

    private void draw(Canvas c, float[] s, Paint p, float w, float h, float pad, float top) {
        if (s.length < 2) return;
        path.reset();
        boolean open = false;
        for (int i = 0; i < s.length; i++) {
            if (Float.isNaN(s[i])) {
                open = false;
                continue;
            }
            float x = pad + (w - 2 * pad) * i / (s.length - 1);
            float y = h - pad - (h - pad - top) * Math.min(1, s[i] / max);
            if (!open) {
                path.moveTo(x, y);
                open = true;
            } else path.lineTo(x, y);
        }
        c.drawPath(path, p);
    }
}
