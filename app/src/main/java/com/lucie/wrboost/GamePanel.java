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
    private static final int BG = 0xF00A0F1C, CARD = 0xFF141C33, TXT = 0xFFF2F5FF, MUT = 0xFF8D99B8, ACC = 0xFF4DA3FF;
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
        GradientDrawable g = round(ACC, 24);
        g.setShape(GradientDrawable.OVAL);
        g.setStroke((int) (2 * d), 0x55FFFFFF);
        v.setBackground(g);
        v.setAlpha(0.7f);
        int size = (int) (46 * d);
        bubbleLp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences p = c.getSharedPreferences("wr", Context.MODE_PRIVATE);
        bubbleLp.x = 0;
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
                            bubbleLp.x = 0;
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

    private TextView action(String label, int icon) {
        TextView t = text(label, 13, TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(CARD, 12));
        t.setPadding((int) (8 * d), (int) (12 * d), (int) (8 * d), (int) (12 * d));
        android.graphics.drawable.Drawable dr = c.getDrawable(icon).mutate();
        dr.setTint(ACC);
        int s = (int) (18 * d);
        dr.setBounds(0, 0, s, s);
        t.setCompoundDrawables(dr, null, null, null);
        t.setCompoundDrawablePadding((int) (6 * d));
        return t;
    }

    private void openPanel() {
        if (panel != null) return;
        FrameLayout root = new FrameLayout(c);
        root.setBackgroundColor(0x66000000);
        root.setOnClickListener(v -> closePanel());

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(round(BG, 18));
        int pad = (int) (14 * d);
        box.setPadding(pad, pad, pad, pad);
        box.setOnClickListener(v -> {
        });

        String game = Monitor.activeGame();
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(game == null ? "Booster" : Games.label(c, game), 16, TXT, true);
        title.setSingleLine(true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView x = new ImageView(c);
        x.setImageResource(R.drawable.ic_close);
        x.setColorFilter(MUT);
        x.setOnClickListener(v -> closePanel());
        head.addView(x, new LinearLayout.LayoutParams((int) (24 * d), (int) (24 * d)));
        box.addView(head);

        stats = text("", 13, MUT, false);
        stats.setPadding(0, (int) (6 * d), 0, (int) (10 * d));
        box.addView(stats);

        LinearLayout row = new LinearLayout(c);
        tvClean = action("Liberar RAM", R.drawable.ic_memory);
        tvClean.setOnClickListener(v -> clean());
        tvDnd = action("Não perturbe", R.drawable.ic_shield);
        tvDnd.setOnClickListener(v -> toggleDnd());
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp1.setMargins(0, 0, (int) (4 * d), 0);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp2.setMargins((int) (4 * d), 0, 0, 0);
        row.addView(tvClean, lp1);
        row.addView(tvDnd, lp2);
        box.addView(row);

        TextView hdr = text("APPS RÁPIDOS", 11, MUT, true);
        hdr.setLetterSpacing(0.12f);
        hdr.setPadding(0, (int) (14 * d), 0, (int) (6 * d));
        box.addView(hdr);

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(4);
        PackageManager pm = c.getPackageManager();
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
            cell.addView(ic, new LinearLayout.LayoutParams((int) (42 * d), (int) (42 * d)));
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
        if (n == 0) {
            box.addView(text("Escolha os apps do painel em Configurações > Painel no jogo.", 12, MUT, false));
        } else {
            box.addView(grid);
        }
        TextView open = text("Abrir Booster", 13, ACC, true);
        open.setGravity(Gravity.CENTER);
        open.setPadding(0, (int) (12 * d), 0, 0);
        open.setOnClickListener(v -> launch(c.getPackageName()));
        box.addView(open);

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams((int) (310 * d), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        bp.leftMargin = (int) (56 * d);
        root.addView(box, bp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        try {
            wm.addView(root, lp);
            panel = root;
            refreshPanel();
        } catch (Exception ignored) {
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
        if (m != null && stats != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(m.fps >= 0 ? m.fps : "--").append(" / ").append(m.hz).append(" FPS");
            if (!Float.isNaN(m.cpu)) sb.append("   CPU ").append(Math.round(m.cpu)).append("°C");
            if (m.sys != null) sb.append("   RAM ").append(m.sys.ramUsedPct()).append("%");
            stats.setText(sb);
        }
        if (tvDnd != null) {
            boolean on = dndOn();
            tvDnd.setText(on ? "Não perturbe: ligado" : "Não perturbe");
            tvDnd.setTextColor(on ? ACC : TXT);
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
            Booster.Result r = Booster.boost(c, Booster.level(c), game);
            h.post(() -> {
                cleaning = false;
                if (tvClean != null) tvClean.setText("Liberar RAM");
                Toast.makeText(c, r.forced + " apps fechados, " + (r.freedBytes / 1048576) + " MB liberados", Toast.LENGTH_SHORT).show();
            });
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
