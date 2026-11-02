package com.lucie.wrboost;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class GaugeView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private int pct;
    private String sub = "";

    public GaugeView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(12 * d);
        track.setColor(Theme.TRACK);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(12 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        big.setColor(Theme.TXT);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTextSize(44 * d);
        big.setFakeBoldText(true);
        small.setColor(Theme.MUT);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(14 * d);
    }

    public void set(int pct, String sub) {
        this.pct = pct;
        this.sub = sub;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float pad = track.getStrokeWidth();
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        box.set(cx - s / 2 + pad, cy - s / 2 + pad, cx + s / 2 - pad, cy + s / 2 - pad);
        c.drawArc(box, 135, 270, false, track);
        arc.setColor(pct < 70 ? 0xFF2ECC71 : pct < 85 ? 0xFFF5A623 : 0xFFFF5252);
        arc.setShadowLayer(14, 0, 0, arc.getColor() & 0x00FFFFFF | 0x99000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        c.drawArc(box, 135, 270f * Math.min(100, pct) / 100f, false, arc);
        c.drawText(pct + "%", cx, cy + big.getTextSize() * 0.15f, big);
        c.drawText("RAM em uso", cx, cy - big.getTextSize() * 0.65f, small);
        c.drawText(sub, cx, cy + big.getTextSize() * 0.85f, small);
    }
}
