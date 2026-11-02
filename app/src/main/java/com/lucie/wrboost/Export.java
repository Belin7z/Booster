package com.lucie.wrboost;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Exporta o histórico de sessões em CSV (planilha) ou PDF (leitura/impressão) e compartilha.
public final class Export {
    private Export() {
    }

    private static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }

    public static File csv(Context c) throws Exception {
        List<Reports.Record> all = Reports.all(c);
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder b = new StringBuilder("\uFEFF");
        b.append("jogo;pacote;inicio;duracao_s;nota;fps_medio;fps_alvo;fps_min;fps_max;low1;estabilidade_pct;travadas;cpu_max;cpu_media;gpu_max;bateria_temp_max;ram_max_mb;bateria_ini;bateria_fim;limitacao_termica_s;ping_medio_ms;ping_max_ms;perda_pct\n");
        for (Reports.Record r : all) {
            b.append(q(r.label)).append(';').append(q(r.pkg)).append(';').append(f.format(new Date(r.start))).append(';')
                    .append(r.durSec).append(';').append(r.measured ? r.score() : "").append(';').append(r.fpsAvg).append(';')
                    .append(r.target).append(';').append(r.fpsMin).append(';').append(r.fpsMax).append(';').append(r.low1).append(';')
                    .append(r.stablePct).append(';').append(r.stutters).append(';').append(String.format(Locale.US, "%.1f", r.cpuMax)).append(';')
                    .append(String.format(Locale.US, "%.1f", r.cpuAvg)).append(';').append(String.format(Locale.US, "%.1f", r.gpuMax)).append(';')
                    .append(String.format(Locale.US, "%.1f", r.battTempMax)).append(';').append(r.ramMaxMb).append(';').append(r.battStart).append(';')
                    .append(r.battEnd).append(';').append(r.throttleSec).append(';').append(r.pingAvg).append(';').append(r.pingMax).append(';')
                    .append(r.lossPct).append('\n');
        }
        File out = new File(c.getCacheDir(), "historico.csv");
        Files.write(out.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        return out;
    }

    public static File pdf(Context c) throws Exception {
        List<Reports.Agg> games = Reports.byGame(c);
        List<Reports.Record> all = Reports.all(c);
        final int w = 595, h = 842, left = 40, rowH = 18;
        PdfDocument doc = new PdfDocument();
        Paint title = paint(0xFF0B1220, 20, true), head = paint(0xFFFFFFFF, 10, true), txt = paint(0xFF14203A, 10, false), mut = paint(0xFF5C6B8C, 9, false);
        Paint bar = new Paint();
        bar.setColor(0xFF0B1220);
        Paint zebra = new Paint();
        zebra.setColor(0xFFEEF2FA);
        Paint accent = new Paint();
        accent.setColor(0xFF4DA3FF);
        int page = 0, y = 0;
        PdfDocument.Page pg = null;
        Canvas cv = null;
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yy HH:mm", Locale.US);
        int i = 0;
        int totalRows = games.size() + all.size();
        int row = 0;
        boolean sessionsHeaderDone = false;
        while (row <= totalRows + 2) {
            if (pg == null || y > h - 50) {
                if (pg != null) {
                    cv.drawText("Página " + page, w - 90, h - 24, mut);
                    doc.finishPage(pg);
                }
                pg = doc.startPage(new PdfDocument.PageInfo.Builder(w, h, ++page).create());
                cv = pg.getCanvas();
                cv.drawRect(0, 0, w, 70, bar);
                cv.drawRect(0, 70, w, 74, accent);
                cv.drawText("BOOSTER", left, 34, paint(0xFF4DA3FF, 12, true));
                cv.drawText("Histórico de desempenho", left, 56, paint(0xFFFFFFFF, 18, true));
                cv.drawText(new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date()), w - 110, 56, paint(0xFF8FA0C7, 10, false));
                y = 100;
            }
            if (row == 0) {
                cv.drawText("Resumo por jogo", left, y, title);
                y += 24;
                drawHead(cv, bar, head, left, y, w, new String[]{"Jogo", "Sessões", "Tempo", "FPS médio", "Nota"}, new int[]{0, 250, 310, 390, 470});
                y += rowH;
                if (games.isEmpty()) {
                    cv.drawText("Nenhuma sessão registrada.", left + 6, y + 12, txt);
                    y += rowH;
                }
                row++;
                continue;
            }
            if (i < games.size() && row <= games.size()) {
                Reports.Agg a = games.get(i);
                if (i % 2 == 1) cv.drawRect(left, y, w - left, y + rowH, zebra);
                String[] c2 = {a.label, String.valueOf(a.sessions), Reports.duration(a.totalSec), a.avgFps > 0 ? String.valueOf(a.avgFps) : "-", Reports.grade(a.avgScore)};
                int[] xs = {0, 250, 310, 390, 470};
                for (int k = 0; k < c2.length; k++) cv.drawText(clip(c2[k], k == 0 ? 36 : 14), left + xs[k] + 6, y + 12, txt);
                y += rowH;
                i++;
                row++;
                continue;
            }
            if (!sessionsHeaderDone) {
                y += 20;
                if (y > h - 90) {
                    y = h;
                    continue;
                }
                cv.drawText("Sessões", left, y, title);
                y += 24;
                drawHead(cv, bar, head, left, y, w, new String[]{"Data", "Jogo", "Duração", "FPS", "Nota", "CPU máx", "Ping"}, new int[]{0, 85, 235, 290, 340, 385, 450});
                y += rowH;
                sessionsHeaderDone = true;
                i = 0;
                continue;
            }
            if (i >= all.size()) break;
            Reports.Record r = all.get(i);
            if (i % 2 == 1) cv.drawRect(left, y, w - left, y + rowH, zebra);
            String[] c3 = {f.format(new Date(r.start)), r.label, Reports.duration(r.durSec), r.measured ? r.fpsAvg + "/" + r.target : "-",
                    r.measured ? Reports.grade(r.score()) : "-", r.cpuMax > 0 ? String.format(Locale.US, "%.0f°C", r.cpuMax) : "-",
                    r.pingAvg > 0 ? r.pingAvg + " ms" : "-"};
            int[] xs = {0, 85, 235, 290, 340, 385, 450};
            for (int k = 0; k < c3.length; k++) cv.drawText(clip(c3[k], k == 1 ? 24 : 12), left + xs[k] + 6, y + 12, txt);
            y += rowH;
            i++;
            row++;
        }
        if (pg != null) {
            cv.drawText("Página " + page, w - 90, h - 24, mut);
            doc.finishPage(pg);
        }
        File out = new File(c.getCacheDir(), "historico.pdf");
        try (FileOutputStream os = new FileOutputStream(out)) {
            doc.writeTo(os);
        } finally {
            doc.close();
        }
        return out;
    }

    private static void drawHead(Canvas cv, Paint bar, Paint head, int left, int y, int w, String[] cols, int[] xs) {
        cv.drawRect(left, y, w - left, y + 18, bar);
        for (int k = 0; k < cols.length; k++) cv.drawText(cols[k], left + xs[k] + 6, y + 12, head);
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static Paint paint(int color, float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }

    public static void share(Context c, File f, String mime) {
        Uri uri = Uri.parse("content://" + c.getPackageName() + ".apk/" + f.getName());
        Intent i = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(Intent.createChooser(i, "Exportar histórico").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
