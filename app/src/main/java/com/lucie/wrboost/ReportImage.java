package com.lucie.wrboost;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

// Gera uma imagem do relatório da sessão para compartilhar.
public final class ReportImage {
    private ReportImage() {
    }

    private static Paint paint(int color, float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }

    public static Bitmap render(Reports.Record r) {
        int w = 1080, h = 1500;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, h, 0xFF0B1226, 0xFF05080F, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);

        c.drawText("BOOSTER", 60, 110, paint(0xFF4DA3FF, 38, true));
        c.drawText("Relatório de desempenho", 60, 160, paint(0xFF8D99B8, 30, false));
        c.drawText(r.label, 60, 260, paint(0xFFF2F5FF, 62, true));
        c.drawText(Reports.when(r.start) + "  ·  " + Reports.duration(r.durSec), 60, 312, paint(0xFF8D99B8, 32, false));

        int score = r.score();
        int gc = score < 0 ? 0xFF8D99B8 : score >= 80 ? 0xFF3DDC97 : score >= 65 ? 0xFF4DA3FF : score >= 50 ? 0xFFFFC857 : 0xFFFF6B6B;
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(14);
        ring.setColor(gc);
        c.drawCircle(w - 170, 230, 90, ring);
        Paint gp = paint(gc, 96, true);
        gp.setTextAlign(Paint.Align.CENTER);
        c.drawText(Reports.grade(score), w - 170, 264, gp);

        String[][] cells = {
                {r.measured ? r.fpsAvg + " / " + r.target : "--", "FPS médio"},
                {r.measured ? String.valueOf(r.low1) : "--", "1% low"},
                {r.measured ? r.stablePct + "%" : "--", "Estabilidade"},
                {String.format(Locale.US, "%.0f°C", r.cpuMax), "CPU máx"},
                {r.ramMaxMb + " MB", "RAM máx"},
                {r.battStart + "% → " + r.battEnd + "%", "Bateria"},
        };
        Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
        card.setColor(0xFF141C33);
        Paint val = paint(0xFFF2F5FF, 46, true), lab = paint(0xFF8D99B8, 28, false);
        for (int i = 0; i < cells.length; i++) {
            float x = 60 + (i % 3) * 320, y = 380 + (i / 3) * 190;
            c.drawRoundRect(new RectF(x, y, x + 300, y + 170), 28, 28, card);
            c.drawText(cells[i][0], x + 24, y + 80, val);
            c.drawText(cells[i][1], x + 24, y + 128, lab);
        }

        if (r.measured && r.fpsSeries.length > 1) {
            c.drawText("FPS durante a sessão", 60, 820, paint(0xFFF2F5FF, 36, true));
            RectF box = new RectF(60, 850, w - 60, 1180);
            c.drawRoundRect(box, 28, 28, card);
            int max = Math.max(r.target, r.fpsMax);
            Path line = new Path();
            boolean started = false;
            int n = r.fpsSeries.length;
            for (int i = 0; i < n; i++) {
                if (r.fpsSeries[i] < 0) continue;
                float x = box.left + 30 + (box.width() - 60) * i / (n - 1);
                float y = box.bottom - 30 - (box.height() - 60) * Math.min(1f, r.fpsSeries[i] / (float) max);
                if (!started) {
                    line.moveTo(x, y);
                    started = true;
                } else {
                    line.lineTo(x, y);
                }
            }
            Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
            lp.setStyle(Paint.Style.STROKE);
            lp.setStrokeWidth(5);
            lp.setColor(0xFF4DA3FF);
            lp.setStrokeJoin(Paint.Join.ROUND);
            c.drawPath(line, lp);
        }

        float y = 1250;
        c.drawText("Análise", 60, y, paint(0xFFF2F5FF, 36, true));
        Paint tp = paint(0xFFB8C2DD, 28, false);
        int shown = 0;
        for (String s : Reports.insights(r)) {
            if (shown++ >= 3) break;
            String line = s.length() > 62 ? s.substring(0, 60) + "..." : s;
            y += 50;
            c.drawText("• " + line, 60, y, tp);
        }
        return bmp;
    }

    public static Uri save(Context c, Reports.Record r) throws Exception {
        Bitmap b = render(r);
        File f = new File(c.getCacheDir(), "report.png");
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
        b.recycle();
        return Uri.parse("content://" + c.getPackageName() + ".apk/report.png");
    }

    public static void share(Context c, Reports.Record r) throws Exception {
        Uri u = save(c, r);
        Intent i = new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, u)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(Intent.createChooser(i, "Compartilhar imagem").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
