package com.lucie.wrboost;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Bolha flutuante durante o jogo que abre um painel com atalhos, sem sair do jogo. */
public class GamePanel {
    private static final int BG = 0xF50A0F1C, CARD = 0xFF141C33, LINE = 0xFF222E4D, TXT = 0xFFF2F5FF, MUT = 0xFF8D99B8;
    private static final String[] DEFAULT_APPS = {"com.whatsapp", "org.telegram.messenger", "com.discord",
            "com.google.android.youtube", "com.spotify.music", "com.android.chrome", "com.instagram.android"};

    private final Context c;
    private final WindowManager wm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final float d;
    private View bubble, panel;
    private WindowManager.LayoutParams bubbleLp;
    private TextView stats, tvDnd, tvClean;
    private boolean cleaning;
    private String sig = "";
    private static volatile boolean recording;
    private TextView tvRec, tvFps, tvCpu, tvRam;
    private android.widget.SeekBar brBar;
    private android.widget.SeekBar volBar;

    private SharedPreferences prefs() {
        return c.getSharedPreferences("wr", Context.MODE_PRIVATE);
    }

    private int bubbleSize() {
        int[] dp = {36, 46, 56};
        return (int) (dp[Math.max(0, Math.min(2, prefs().getInt("bubble_size", 1)))] * d);
    }

    private float bubbleAlpha() {
        float[] a = {0.4f, 0.7f, 1f};
        return a[Math.max(0, Math.min(2, prefs().getInt("bubble_alpha", 1)))];
    }

    private int screenW() {
        return wm.getCurrentWindowMetrics().getBounds().width();
    }

    private boolean rightSide() {
        return prefs().getInt("bubble_side", 0) == 1;
    }

    private String currentSig() {
        return prefs().getInt("bubble_size", 1) + "-" + prefs().getInt("bubble_alpha", 1) + "-" + screenW();
    }

    public GamePanel(Context c) {
        this.c = c;
        wm = c.getSystemService(WindowManager.class);
        d = c.getResources().getDisplayMetrics().density;
    }

    public static List<String> apps(Context c) {
        SharedPreferences p = c.getSharedPreferences("wr", Context.MODE_PRIVATE);
        Set<String> set = p.getStringSet("panel_apps", null);
        List<String> out = new ArrayList<>();
        PackageManager pm = c.getPackageManager();
        if (set != null) {
            out.addAll(set);
        } else {
            for (String s : DEFAULT_APPS) if (pm.getLaunchIntentForPackage(s) != null) out.add(s);
        }
        return out;
    }

    /** Chamado a cada segundo pelo serviço. */
    public void tick() {
        SharedPreferences p = c.getSharedPreferences("wr", Context.MODE_PRIVATE);
        boolean want = p.getBoolean("panel", true) && android.provider.Settings.canDrawOverlays(c)
                && (Monitor.sessionActive() || Monitor.activeGame() != null);
        if (bubble != null && !sig.equals(currentSig())) removeBubble();
        if (want && bubble == null) addBubble();
        if (!want) {
            closePanel();
            removeBubble();
        }
        if (panel != null) refreshPanel();
    }

    public void destroy() {
        closePanel();
        removeBubble();
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r * d);
        return g;
    }

    private void addBubble() {
        ImageView v = new ImageView(c);
        v.setImageResource(R.drawable.ic_bolt);
        v.setColorFilter(0xFF04101F);
        int pad = (int) (10 * d);
        v.setPadding(pad, pad, pad, pad);
        GradientDrawable g = round(acc(), 24);
        g.setShape(GradientDrawable.OVAL);
        g.setStroke((int) (2 * d), 0x55FFFFFF);
        v.setBackground(g);
        v.setAlpha(bubbleAlpha());
        sig = currentSig();
        final int size = bubbleSize();
        bubbleLp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences p = c.getSharedPreferences("wr", Context.MODE_PRIVATE);
        bubbleLp.x = rightSide() ? screenW() - size : 0;
        bubbleLp.y = p.getInt("bubble_y", (int) (120 * d));
        bubbleLp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        v.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            int ox, oy;
            boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX();
                        sy = e.getRawY();
                        ox = bubbleLp.x;
                        oy = bubbleLp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                        if (Math.abs(dx) > 8 * d || Math.abs(dy) > 8 * d) moved = true;
                        if (moved) {
                            bubbleLp.x = Math.max(0, (int) (ox + dx));
                            bubbleLp.y = Math.max(0, (int) (oy + dy));
                            try {
                                wm.updateViewLayout(bubble, bubbleLp);
                            } catch (Exception ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            openPanel();
                        } else {
                            boolean right = bubbleLp.x + size / 2 > screenW() / 2;
                            bubbleLp.x = right ? screenW() - size : 0;
                            c.getSharedPreferences("wr", Context.MODE_PRIVATE).edit().putInt("bubble_side", right ? 1 : 0).apply();
                            try {
                                wm.updateViewLayout(bubble, bubbleLp);
                            } catch (Exception ignored) {
                            }
                            c.getSharedPreferences("wr", Context.MODE_PRIVATE).edit().putInt("bubble_y", bubbleLp.y).apply();
                        }
                        return true;
                }
                return false;
            }
        });
        try {
            wm.addView(v, bubbleLp);
            bubble = v;
        } catch (Exception ignored) {
        }
    }

    private void removeBubble() {
        if (bubble != null) {
            try {
                wm.removeView(bubble);
            } catch (Exception ignored) {
            }
            bubble = null;
        }
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private int acc() {
        return Theme.ACCENTS[Theme.accentIndex(c)];
    }

    // Botão em bloco: ícone em cima, texto embaixo. O principal vem preenchido com a cor de destaque.
    private TextView tile(String label, int icon, boolean primary) {
        TextView t = text(label, 12, primary ? 0xFF04101F : TXT, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = round(primary ? acc() : CARD, 14);
        if (!primary) g.setStroke((int) d, LINE);
        t.setBackground(g);
        t.setPadding((int) (6 * d), (int) (12 * d), (int) (6 * d), (int) (10 * d));
        android.graphics.drawable.Drawable dr = c.getDrawable(icon).mutate();
        dr.setTint(primary ? 0xFF04101F : acc());
        int s = (int) (22 * d);
        dr.setBounds(0, 0, s, s);
        t.setCompoundDrawables(null, dr, null, null);
        t.setCompoundDrawablePadding((int) (6 * d));
        t.setSingleLine(true);
        return t;
    }

    private LinearLayout chip(String label, TextView[] out, int idx) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, (int) (8 * d), 0, (int) (8 * d));
        l.setBackground(round(CARD, 12));
        TextView v = text("--", 17, TXT, true);
        v.setGravity(Gravity.CENTER);
        TextView n = text(label, 10, MUT, false);
        n.setGravity(Gravity.CENTER);
        l.addView(v);
        l.addView(n);
        out[idx] = v;
        return l;
    }

    private LinearLayout slider(String label, android.widget.SeekBar bar) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, (int) (10 * d), 0, 0);
        r.addView(text(label, 12, MUT, false), new LinearLayout.LayoutParams((int) (52 * d), ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.getProgressDrawable().setTint(acc());
        bar.getThumb().setTint(acc());
        r.addView(bar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return r;
    }

    private void openPanel() {
        if (panel != null) return;
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(0x80000000);
        root.setOnClickListener(v -> closePanel());

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = round(BG, 22);
        bg.setStroke((int) d, LINE);
        box.setBackground(bg);
        int pad = (int) (16 * d);
        box.setPadding(pad, pad, pad, pad);
        box.setOnClickListener(v -> {
        });

        String game = Monitor.activeGame();
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.ic_bolt);
        logo.setColorFilter(0xFF04101F);
        int lp0 = (int) (7 * d);
        logo.setPadding(lp0, lp0, lp0, lp0);
        logo.setBackground(round(acc(), 10));
        head.addView(logo, new LinearLayout.LayoutParams((int) (32 * d), (int) (32 * d)));
        LinearLayout names = new LinearLayout(c);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding((int) (10 * d), 0, 0, 0);
        TextView title = text(game == null ? "Booster" : Games.label(c, game), 15, TXT, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(title);
        names.addView(text("Booster", 11, MUT, false));
        head.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView x = new ImageView(c);
        x.setImageResource(R.drawable.ic_close);
        x.setColorFilter(MUT);
        int xp = (int) (4 * d);
        x.setPadding(xp, xp, xp, xp);
        x.setOnClickListener(v -> closePanel());
        head.addView(x, new LinearLayout.LayoutParams((int) (32 * d), (int) (32 * d)));
        box.addView(head);

        LinearLayout chips = new LinearLayout(c);
        chips.setPadding(0, (int) (14 * d), 0, 0);
        TextView[] cv = new TextView[3];
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cp.setMargins((int) (3 * d), 0, (int) (3 * d), 0);
        chips.addView(chip("FPS", cv, 0), cp);
        chips.addView(chip("CPU", cv, 1), cp);
        chips.addView(chip("RAM", cv, 2), cp);
        tvFps = cv[0];
        tvCpu = cv[1];
        tvRam = cv[2];
        box.addView(chips);

        stats = text("", 11, 0xFFFFC857, false);
        stats.setPadding((int) (3 * d), (int) (6 * d), 0, 0);
        stats.setVisibility(View.GONE);
        box.addView(stats);

        GridLayout acts = new GridLayout(c);
        acts.setColumnCount(2);
        acts.setPadding(0, (int) (12 * d), 0, 0);
        tvClean = tile("Liberar RAM", R.drawable.ic_memory, true);
        tvClean.setOnClickListener(v -> clean());
        tvDnd = tile("Não perturbe", R.drawable.ic_shield, false);
        tvDnd.setOnClickListener(v -> toggleDnd());
        TextView shot = tile("Captura", R.drawable.ic_overlay, false);
        shot.setOnClickListener(v -> screenshot());
        tvRec = tile("Gravar tela", R.drawable.ic_play, false);
        tvRec.setOnClickListener(v -> toggleRecord());
        for (TextView t : new TextView[]{tvClean, tvDnd, shot, tvRec}) {
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            gp.width = 0;
            gp.setMargins((int) (3 * d), (int) (3 * d), (int) (3 * d), (int) (3 * d));
            acts.addView(t, gp);
        }
        box.addView(acts);

        final android.media.AudioManager am = c.getSystemService(android.media.AudioManager.class);
        volBar = new android.widget.SeekBar(c);
        volBar.setMax(am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
        volBar.setProgress(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        volBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) {
                if (user) am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, p, 0);
            }
        });
        box.addView(slider("Volume", volBar));

        brBar = new android.widget.SeekBar(c);
        brBar.setMax(255);
        brBar.setProgress(currentBrightness());
        brBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override
            public void onProgressChanged(android.widget.SeekBar s, int p, boolean user) {
                if (user) setBrightness(Math.max(2, p));
            }
        });
        box.addView(slider("Brilho", brBar));

        PackageManager pm = c.getPackageManager();
        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(4);
        int n = 0;
        for (String pkg : apps(c)) {
            if (pm.getLaunchIntentForPackage(pkg) == null) continue;
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding((int) (4 * d), (int) (4 * d), (int) (4 * d), (int) (6 * d));
            ImageView ic = new ImageView(c);
            try {
                ic.setImageDrawable(pm.getApplicationIcon(pkg));
            } catch (Exception ignored) {
            }
            cell.addView(ic, new LinearLayout.LayoutParams((int) (40 * d), (int) (40 * d)));
            TextView nm = text(Games.label(c, pkg), 10, MUT, false);
            nm.setSingleLine(true);
            nm.setGravity(Gravity.CENTER);
            nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            nm.setMaxWidth((int) (62 * d));
            cell.addView(nm);
            cell.setOnClickListener(v -> launch(pkg));
            grid.addView(cell);
            n++;
        }
        if (n > 0) {
            TextView hdr = text("APPS RÁPIDOS", 10, MUT, true);
            hdr.setLetterSpacing(0.14f);
            hdr.setPadding((int) (3 * d), (int) (16 * d), 0, (int) (4 * d));
            box.addView(hdr);
            box.addView(grid);
        }

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams((int) (320 * d), ViewGroup.LayoutParams.WRAP_CONTENT,
                (rightSide() ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        bp.setMargins((int) (rightSide() ? 0 : 56 * d), 0, (int) (rightSide() ? 56 * d : 0), 0);
        android.widget.ScrollView sv = new android.widget.ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(box);
        root.addView(sv, bp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try {
            wm.addView(root, lp);
            panel = root;
            refreshPanel();
            root.setAlpha(0f);
            root.animate().alpha(1f).setDuration(180).start();
            sv.setTranslationX((rightSide() ? 1 : -1) * 40 * d);
            sv.animate().translationX(0).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        } catch (Exception ignored) {
        }
    }

    private abstract static class SimpleSeek implements android.widget.SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(android.widget.SeekBar s) {
        }

        @Override
        public void onStopTrackingTouch(android.widget.SeekBar s) {
        }
    }
    private void closePanel() {
        if (panel != null) {
            try {
                wm.removeView(panel);
            } catch (Exception ignored) {
            }
            panel = null;
        }
    }

    private void refreshPanel() {
        Monitor.Sample m = Monitor.latest;
        if (m != null && tvFps != null) {
            tvFps.setText((m.fps >= 0 ? String.valueOf(m.fps) : "--") + "/" + m.hz);
            tvFps.setTextColor(m.fps < 0 ? TXT : m.fps >= m.hz * 0.9f ? 0xFF2ECC71 : m.fps >= m.hz * 0.65f ? 0xFFF5A623 : 0xFFFF5252);
            boolean cpuOk = !Float.isNaN(m.cpu);
            tvCpu.setText(cpuOk ? Math.round(m.cpu) + "°" : "--");
            tvCpu.setTextColor(!cpuOk ? TXT : m.cpu >= 45 ? 0xFFFF5252 : m.cpu >= 40 ? 0xFFF5A623 : 0xFF2ECC71);
            int ram = m.sys != null ? m.sys.ramUsedPct() : -1;
            tvRam.setText(ram >= 0 ? ram + "%" : "--");
            tvRam.setTextColor(ram < 0 ? TXT : ram >= 85 ? 0xFFFF5252 : ram >= 70 ? 0xFFF5A623 : 0xFF2ECC71);
            if (stats != null) {
                stats.setVisibility(m.cause != null ? View.VISIBLE : View.GONE);
                if (m.cause != null) stats.setText("Queda de FPS: " + m.cause);
            }
        }
        if (tvRec != null) tvRec.setText(recording ? "Parar gravação" : "Gravar tela");
        if (tvDnd != null) {
            boolean on = dndOn();
            tvDnd.setText(on ? "Silencioso: ligado" : "Não perturbe");
            tvDnd.setTextColor(on ? acc() : TXT);
        }
    }
    private boolean dndOn() {
        try {
            return c.getSystemService(NotificationManager.class).getCurrentInterruptionFilter()
                    != NotificationManager.INTERRUPTION_FILTER_ALL;
        } catch (Exception e) {
            return false;
        }
    }

    private void toggleDnd() {
        if (!Booster.dndAllowed(c)) {
            Toast.makeText(c, "Permita o acesso ao Não perturbe no Booster", Toast.LENGTH_SHORT).show();
            return;
        }
        if (dndOn()) Booster.restoreDnd(c);
        else Booster.enableDnd(c);
        h.postDelayed(this::refreshPanel, 400);
    }

    private void clean() {
        if (cleaning) return;
        cleaning = true;
        tvClean.setText("Limpando...");
        final String game = Monitor.activeGame();
        new Thread(() -> {
            Booster.Result r = Booster.boost(c, Profiles.level(c, game), game);
            h.post(() -> {
                cleaning = false;
                if (tvClean != null) tvClean.setText("Liberar RAM");
                Toast.makeText(c, r.forced + " apps fechados, " + (r.freedBytes / 1048576) + " MB liberados", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private int currentBrightness() {
        try {
            return android.provider.Settings.System.getInt(c.getContentResolver(), "screen_brightness");
        } catch (Exception e) {
            return 0;
        }
    }

    private void setBrightness(int next) {
        if (android.provider.Settings.System.canWrite(c)) {
            android.provider.Settings.System.putInt(c.getContentResolver(), "screen_brightness_mode", 0);
            android.provider.Settings.System.putInt(c.getContentResolver(), "screen_brightness", next);
        } else if (Shell.granted()) {
            final int v = next;
            new Thread(() -> Shell.exec("settings put system screen_brightness_mode 0; settings put system screen_brightness " + v, 3000)).start();
        } else {
            Toast.makeText(c, "Brilho precisa do modo avançado conectado", Toast.LENGTH_SHORT).show();
        }
    }

    private void screenshot() {
        if (!Shell.granted()) {
            Toast.makeText(c, "Captura precisa do modo avançado conectado", Toast.LENGTH_SHORT).show();
            return;
        }
        closePanel();
        new Thread(() -> {
            try {
                Thread.sleep(350);
            } catch (InterruptedException ignored) {
            }
            Shell.exec("input keyevent 120", 3000);
            h.post(() -> Toast.makeText(c, "Captura salva na galeria", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void toggleRecord() {
        if (!Shell.granted()) {
            Toast.makeText(c, "Gravação precisa do modo avançado conectado", Toast.LENGTH_SHORT).show();
            return;
        }
        if (recording) {
            new Thread(() -> Shell.exec("pkill -2 screenrecord", 3000)).start();
            return;
        }
        recording = true;
        closePanel();
        final String file = "/sdcard/Movies/Booster_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".mp4";
        Toast.makeText(c, "Gravando (até 3 min). Abra o painel para parar.", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            Shell.exec("mkdir -p /sdcard/Movies; screenrecord --time-limit 180 --bit-rate 10000000 " + file, 200_000);
            recording = false;
            h.post(() -> Toast.makeText(c, "Gravação salva em Movies", Toast.LENGTH_LONG).show());
        }).start();
    }

    private void launch(String pkg) {
        closePanel();
        final Intent in = Booster.launchIntent(c, pkg);
        if (in == null) return;
        in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!pkg.equals(c.getPackageName()) && Shell.granted()) {
            final android.content.ComponentName cn = in.getComponent();
            if (cn != null) {
                // Tenta abrir em janela flutuante para o jogo continuar visível.
                new Thread(() -> {
                    String out = Shell.exec("am start --windowingMode 5 -n " + cn.flattenToShortString(), 4000);
                    if (out == null || out.contains("Error") || out.contains("Exception")) {
                        h.post(() -> c.startActivity(in));
                    }
                }).start();
                return;
            }
        }
        c.startActivity(in);
    }
}
