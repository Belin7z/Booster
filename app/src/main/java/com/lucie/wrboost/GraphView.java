package com.lucie.wrboost;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

public class GraphView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), fill = new Path();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float[] data = new float[0];
    private String title = "";
    private float lo, hi;
    private final float d;

    public GraphView(Context c, String title, int color, float lo, float hi) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        this.title = title;
        this.lo = lo;
        this.hi = hi;
        line.setColor(color);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        grid.setColor(0xFF243152);
        label.setColor(0xFF8FA0C7);
        label.setTextSize(11 * d);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF141D33);
        g.setCornerRadius(14 * d);
        setBackground(g);
    }

    public void set(float[] data) {
        this.data = data;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 10 * d, top = 22 * d;
        float max = hi;
        for (float v : data) if (!Float.isNaN(v) && v > max) max = v;
        c.drawText(title, pad, 16 * d, label);
        c.drawLine(pad, h - pad, w - pad, h - pad, grid);
        c.drawLine(pad, top, w - pad, top, grid);
        c.drawText(String.valueOf(Math.round(max)), w - pad - 24 * d, 16 * d, label);
        if (data.length < 2) return;
        path.reset();
        fill.reset();
        boolean open = false;
        float lastX = 0, lastY = 0, firstX = 0;
        float span = Math.max(1, data.length - 1);
        for (int i = 0; i < data.length; i++) {
            if (Float.isNaN(data[i])) {
                if (open) closeFill(lastX, h - pad, firstX);
                open = false;
                continue;
            }
            float x = pad + (w - 2 * pad) * i / span;
            float y = h - pad - (h - pad - top) * (data[i] - lo) / Math.max(1, max - lo);
            if (!open) {
                path.moveTo(x, y);
                fill.moveTo(x, h - pad);
                fill.lineTo(x, y);
                firstX = x;
                open = true;
            } else {
                path.lineTo(x, y);
                fill.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
        }
        if (open) closeFill(lastX, h - pad, firstX);
        fillPaint.setShader(new android.graphics.LinearGradient(0, top, 0, h - pad,
                (line.getColor() & 0x00FFFFFF) | 0x55000000, line.getColor() & 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
        c.drawPath(fill, fillPaint);
        c.drawPath(path, line);
        if (open) {
            dot.setColor(line.getColor());
            c.drawCircle(lastX, lastY, 3.5f * d, dot);
        }
    }

    private void closeFill(float x, float base, float firstX) {
        fill.lineTo(x, base);
        fill.lineTo(firstX, base);
        fill.close();
    }
}
