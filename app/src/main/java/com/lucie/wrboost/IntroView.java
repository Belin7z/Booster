package com.lucie.wrboost;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

// Abertura: o Booster suga a energia ao redor, vibra cada vez mais forte, explode em pulso e revela o nome.
public class IntroView extends View {
    public static final long TOTAL = 3500;

    private static final long CHARGE = 2150;   // fim da sucção
    private static final long BURST = 2400;    // fim do clímax
    private static final int N = 110;
    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] ang = new float[N], dist = new float[N], spd = new float[N], size = new float[N], off = new float[N];
    private final Drawable bolt;
    private ValueAnimator anim;
    private Runnable done;
    private long t;

    public IntroView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        Random r = new Random(11);
        for (int i = 0; i < N; i++) {
            ang[i] = r.nextFloat() * 6.2832f;
            dist[i] = 0.35f + r.nextFloat() * 0.75f;
            spd[i] = 0.7f + r.nextFloat() * 0.6f;
            size[i] = 1f + r.nextFloat() * 2.2f;
            off[i] = r.nextFloat();
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

    private static int alpha(int color, float a) {
        return (Math.round(255 * clamp(a)) << 24) | (color & 0xFFFFFF);
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h * 0.42f;
        float reach = (float) Math.hypot(w, h) * 0.55f;
        int acc = Theme.ACC;
        cv.drawColor(Theme.BG);

        float charge = clamp(t / (float) CHARGE);
        float e = charge * charge;                       // energia acumulada
        float burst = clamp((t - CHARGE) / (float) (BURST - CHARGE));
        float after = clamp((t - CHARGE) / 1000f);
        boolean boom = t >= CHARGE;

        // Halo de energia que cresce com a carga e estoura no clímax.
        float haloR = (110 + 150 * e + (boom ? 380 * out(burst) : 0)) * d;
        float haloA = boom ? 1f - clamp((t - CHARGE) / 1100f) * 0.75f : 0.15f + 0.55f * e;
        p.setShader(new RadialGradient(cx, cy, haloR, new int[]{alpha(acc, haloA), alpha(acc, haloA * 0.25f), 0}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, haloR, p);
        p.setShader(null);

        // Anéis contraindo em direção ao logo (a energia sendo puxada).
        if (!boom) {
            p.setStyle(Paint.Style.STROKE);
            for (int k = 0; k < 4; k++) {
                float ph = ((t / 700f) + k * 0.25f) % 1f;
                float r = (1f - ph) * reach * 0.8f + 50 * d;
                p.setStrokeWidth(1.6f * d);
                p.setColor(alpha(acc, (0.05f + 0.3f * e) * (1f - Math.abs(ph - 0.5f) * 2f)));
                cv.drawCircle(cx, cy, r, p);
            }
        }

        // Partículas em espiral sendo sugadas para dentro, com rastro.
        if (!boom) {
            p.setStyle(Paint.Style.FILL);
            float speed = 0.35f + 1.6f * e;
            for (int i = 0; i < N; i++) {
                float life = (off[i] + t / 1000f * speed * spd[i] * 0.55f) % 1f;
                float k = 1f - life;                       // 1 = longe, 0 = no centro
                float r = k * k * dist[i] * reach + 34 * d * k;
                float a0 = ang[i] + life * (2.6f + 3.2f * e) * (i % 2 == 0 ? 1 : -0.6f);
                float a1 = a0 - 0.16f * (0.4f + e);
                float x = cx + (float) Math.cos(a0) * r, y = cy + (float) Math.sin(a0) * r;
                float tailR = r + (0.06f + 0.1f * e) * reach * k;
                float tx = cx + (float) Math.cos(a1) * tailR, ty = cy + (float) Math.sin(a1) * tailR;
                float fade = clamp(life * 6f) * clamp(k * 4f + 0.15f) * clamp(t / 350f);
                p.setStrokeWidth(size[i] * d * (0.6f + 0.6f * life));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(alpha(i % 5 == 0 ? Theme.TXT : acc, fade * (0.45f + 0.5f * e)));
                cv.drawLine(tx, ty, x, y, p);
            }
        }

        // Onda de choque e faíscas no clímax.
        if (boom) {
            float sw = out(burst * 1.3f + after * 0.4f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth((7 - 5 * clamp(after * 1.4f)) * d);
            p.setColor(alpha(acc, 1f - clamp(after * 1.2f)));
            cv.drawCircle(cx, cy, (50 + 520 * sw) * d, p);
            p.setStrokeWidth(2f * d);
            p.setColor(alpha(Theme.TXT, 0.8f * (1f - clamp(after * 1.6f))));
            cv.drawCircle(cx, cy, (40 + 330 * sw) * d, p);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < N; i += 2) {
                float r = (60 + dist[i] * 520 * out(after * 1.1f)) * d;
                float s = size[i] * d * (1f - clamp(after * 1.3f));
                p.setColor(alpha(i % 4 == 0 ? Theme.TXT : acc, 1f - clamp(after * 1.3f)));
                cv.drawCircle(cx + (float) Math.cos(ang[i]) * r, cy + (float) Math.sin(ang[i]) * r, s * 1.4f, p);
            }
        }

        // Logo: tremor crescente, cresce e brilha com a energia; no clímax dá um tranco e assenta.
        float base = 78 * d;
        float scale = 0.82f + 0.3f * e;
        float shakeAmp = (boom ? 0f : (0.5f + 6.5f * e * e)) * d;
        float sx = (float) Math.sin(t * 0.09f) * shakeAmp + (float) Math.sin(t * 0.23f) * shakeAmp * 0.5f;
        float sy = (float) Math.cos(t * 0.11f) * shakeAmp * 0.8f;
        if (boom) {
            float kick = (float) Math.exp(-after * 5f) * (float) Math.cos(after * 22f);
            scale = 1.0f + 0.22f * kick;
        }
        float lr = base * scale * clamp(t / 300f);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx + sx, cy + sy, lr * 1.7f, new int[]{alpha(acc, 0.55f * (0.3f + e)), 0}, null, Shader.TileMode.CLAMP));
        cv.drawCircle(cx + sx, cy + sy, lr * 1.7f, p);
        p.setShader(null);
        p.setColor(boom ? blend(acc, 0xFFFFFFFF, (float) Math.exp(-after * 6f)) : blend(acc, 0xFFFFFFFF, e * 0.55f));
        cv.drawCircle(cx + sx, cy + sy, lr, p);
        int ib = (int) (lr * 0.58f);
        bolt.setBounds((int) (cx + sx) - ib, (int) (cy + sy) - ib, (int) (cx + sx) + ib, (int) (cy + sy) + ib);
        bolt.draw(cv);

        // Clarão branco rápido no instante da explosão.
        if (boom && after < 0.35f) {
            cv.drawColor(alpha(0xFFFFFF, 0.55f * (1f - after / 0.35f)));
        }

        // Nome, slogan, barra de carga e assinatura.
        float title = out((t - CHARGE - 80) / 700f);
        if (title > 0f) {
            float ty = cy + base * 1.9f + (1f - title) * 18 * d;
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            text.setTextSize(34 * d);
            text.setLetterSpacing(0.3f - 0.14f * title);
            text.setColor(alpha(Theme.TXT, title));
            cv.drawText("BOOSTER", cx, ty, text);
            float sub = out((t - CHARGE - 350) / 700f);
            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(12.5f * d);
            text.setLetterSpacing(0.22f);
            text.setColor(alpha(Theme.MUT, sub));
            cv.drawText("MAIS DESEMPENHO. ZERO TRAVAS.", cx, ty + 28 * d, text);
        }

        // Barra de energia: acompanha a carga do logo.
        float bw = 150 * d, by = h - 96 * d;
        float barA = clamp(t / 400f) * (1f - clamp((t - BURST) / 600f));
        if (barA > 0f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(alpha(Theme.TRACK, barA));
            cv.drawRoundRect(cx - bw / 2, by, cx + bw / 2, by + 3 * d, 2 * d, 2 * d, p);
            p.setColor(alpha(acc, barA));
            cv.drawRoundRect(cx - bw / 2, by, cx - bw / 2 + bw * (boom ? 1f : charge), by + 3 * d, 2 * d, 2 * d, p);
        }

        float cr = clamp((t - BURST - 100) / 600f);
        if (cr > 0f) {
            text.setTypeface(Typeface.DEFAULT);
            text.setLetterSpacing(0.25f);
            text.setTextSize(9.5f * d);
            text.setColor(alpha(Theme.MUT, cr));
            cv.drawText("CRIADO POR", cx, h - 62 * d, text);
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            text.setTextSize(16 * d);
            text.setLetterSpacing(0.08f);
            text.setColor(alpha(acc, cr));
            cv.drawText("Belin7z", cx, h - 38 * d, text);
        }
    }

    private static int blend(int a, int b, float k) {
        k = clamp(k);
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
