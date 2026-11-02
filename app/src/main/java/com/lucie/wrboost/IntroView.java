package com.lucie.wrboost;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

// Abertura: rajadas de velocidade saindo do centro, logo com onda de choque, nome e assinatura do criador.
public class IntroView extends View {
    public static final long TOTAL = 2700;

    private static final int STREAKS = 56;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] ang = new float[STREAKS], seed = new float[STREAKS], len = new float[STREAKS];
    private final Drawable bolt;
    private ValueAnimator anim;
    private Runnable done;
    private long t;

    public IntroView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        Random r = new Random(7);
        for (int i = 0; i < STREAKS; i++) {
            ang[i] = (float) (i * 2 * Math.PI / STREAKS + (r.nextFloat() - 0.5f) * 0.08f);
            seed[i] = r.nextFloat();
            len[i] = 0.6f + r.nextFloat() * 0.8f;
        }
        bolt = c.getDrawable(R.drawable.ic_bolt).mutate();
        bolt.setTint(Theme.ONACC);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void start(Runnable onDone) {
        done = onDone;
        anim = ValueAnimator.ofFloat(0, TOTAL);
        anim.setDuration(TOTAL);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (long) (float) a.getAnimatedValue();
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                finish();
            }
        });
        anim.start();
    }

    // Termina na hora (toque para pular) e avisa uma única vez.
    public void finish() {
        Runnable r = done;
        done = null;
        if (anim != null) {
            ValueAnimator a = anim;
            anim = null;
            a.removeAllListeners();
            a.cancel();
        }
        if (r != null) r.run();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float out(float v) {
        v = 1f - clamp(v);
        return 1f - v * v * v;
    }

    private static float overshoot(float v) {
        v = clamp(v) - 1f;
        return v * v * (3.2f * v + 2.2f) + 1f;
    }

    private static int alpha(int color, float a) {
        return (Math.round(255 * clamp(a)) << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h * 0.40f, maxR = (float) Math.hypot(w, h) / 2f;
        c.drawColor(Theme.BG);

        float fadeOut = 1f - clamp((t - 2150f) / 450f);
        float glow = out(t / 1100f);
        float gr = d * (110 + 150 * glow);
        p.setShader(new RadialGradient(cx, cy, gr, alpha(Theme.ACC, 0.42f * glow * fadeOut), alpha(Theme.ACC, 0f), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, gr, p);
        p.setShader(null);

        // rajadas de velocidade
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2 * d);
        float warp = clamp(t / 500f) * fadeOut;
        for (int i = 0; i < STREAKS; i++) {
            float ph = ((t / 1100f) + seed[i]) % 1f;
            float r0 = (0.10f + 0.85f * ph * ph) * maxR;
            float r1 = r0 + (0.03f + 0.22f * ph) * maxR * len[i];
            float a = (float) Math.sin(Math.PI * ph) * warp * 0.75f;
            if (a <= 0.01f) continue;
            p.setColor(alpha(i % 4 == 0 ? Theme.TXT : Theme.ACC, a));
            float cs = (float) Math.cos(ang[i]), sn = (float) Math.sin(ang[i]);
            c.drawLine(cx + cs * r0, cy + sn * r0, cx + cs * r1, cy + sn * r1, p);
        }

        // onda de choque
        float q = (t - 650f) / 800f;
        if (q > 0f && q < 1f) {
            p.setStrokeWidth((1 + 4 * (1 - q)) * d);
            p.setColor(alpha(Theme.ACC, (1 - q) * 0.9f));
            c.drawCircle(cx, cy, d * 52 + out(q) * maxR * 0.7f, p);
            if (q > 0.18f) {
                float q2 = (q - 0.18f) / 0.82f;
                p.setStrokeWidth(2 * d);
                p.setColor(alpha(Theme.TXT, (1 - q2) * 0.45f));
                c.drawCircle(cx, cy, d * 52 + out(q2) * maxR * 0.55f, p);
            }
        }

        // logo
        float sc = overshoot((t - 150f) / 750f);
        float la = clamp((t - 150f) / 300f) * fadeOut;
        if (sc > 0f) {
            float half = 48 * d * sc;
            rect.set(cx - half, cy - half, cx + half, cy + half);
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Theme.ACC, 0.35f * la));
            c.drawRoundRect(rect.left - 6 * d, rect.top - 6 * d, rect.right + 6 * d, rect.bottom + 6 * d, 34 * d * sc, 34 * d * sc, p);
            p.setColor(alpha(Theme.ACC, la));
            c.drawRoundRect(rect, 28 * d * sc, 28 * d * sc, p);
            int in = (int) (22 * d * sc);
            bolt.setBounds((int) rect.left + in, (int) rect.top + in, (int) rect.right - in, (int) rect.bottom - in);
            bolt.setAlpha((int) (255 * la));
            bolt.draw(c);
        }

        // nome
        float na = out((t - 800f) / 650f) * fadeOut;
        if (na > 0f) {
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            text.setTextSize(32 * d);
            text.setLetterSpacing(0.55f - 0.37f * out((t - 800f) / 900f));
            text.setColor(alpha(Theme.TXT, na));
            c.drawText("BOOSTER", cx, cy + 48 * d + 58 * d + (1 - na) * 16 * d, text);
        }
        float ta = out((t - 1250f) / 600f) * fadeOut;
        if (ta > 0f) {
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(13 * d);
            text.setLetterSpacing(0.04f);
            text.setColor(alpha(Theme.MUT, ta));
            c.drawText("Desempenho máximo para jogar sem travar", cx, cy + 48 * d + 92 * d, text);
        }

        // barra de "carregamento"
        float bp = out((t - 1400f) / 800f);
        if (bp > 0f) {
            float bw = 140 * d, by = cy + 48 * d + 116 * d;
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Theme.TRACK, fadeOut));
            c.drawRoundRect(cx - bw / 2, by, cx + bw / 2, by + 4 * d, 2 * d, 2 * d, p);
            p.setColor(alpha(Theme.ACC, fadeOut));
            c.drawRoundRect(cx - bw / 2, by, cx - bw / 2 + bw * bp, by + 4 * d, 2 * d, 2 * d, p);
        }

        // assinatura
        float fa = out((t - 1550f) / 600f) * fadeOut;
        if (fa > 0f) {
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(11 * d);
            text.setLetterSpacing(0.2f);
            text.setColor(alpha(Theme.MUT, fa));
            c.drawText("CRIADO POR", cx, h - 62 * d, text);
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            text.setTextSize(17 * d);
            text.setLetterSpacing(0.1f);
            text.setColor(alpha(Theme.ACC, fa));
            c.drawText("Belin7z", cx, h - 38 * d, text);
        }
    }
}
