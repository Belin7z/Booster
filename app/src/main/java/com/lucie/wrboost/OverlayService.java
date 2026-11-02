package com.lucie.wrboost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayService extends Service {
    public static final String ACTION_STOP = "com.lucie.wrboost.STOP";
    private static final String CHANNEL = "monitor";
    public static boolean running;
    private boolean acquired;

    private WindowManager wm;
    private TextView view;
    private GamePanel panel;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int ticks;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            if (panel == null) panel = new GamePanel(OverlayService.this);
            panel.tick();
            if (++ticks % 3 == 0) Diag.sample();
            if (ticks % 30 == 0) {
                final android.content.Context app = getApplicationContext();
                new Thread(() -> {
                    if (StatsWidget.has(app)) StatsWidget.refresh(app);
                    Updater.autoCheck(app);
                }).start();
            }
            h.postDelayed(this, 1000);
        }
    };

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Quem usou startForegroundService precisa chamar startForeground logo, senão o Android acusa "não responde".
        startForegroundCompat();
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!acquired) {
            // Sem partida em andamento, desfaz o que o modo jogo possa ter deixado (ex.: o app foi encerrado em jogo).
            if (!Monitor.sessionActive()) {
                final android.content.Context app = getApplicationContext();
                new Thread(() -> GameMode.restore(app)).start();
            }
            Monitor.acquire(this);
            acquired = true;
        }
        removeOverlay();
        if (Settings.canDrawOverlays(this) && getSharedPreferences("wr", MODE_PRIVATE).getBoolean("overlay", true)) addOverlay();
        h.removeCallbacks(tick);
        h.post(tick);
        running = true;
        return getSharedPreferences("wr", MODE_PRIVATE).getBoolean("auto", true) ? START_STICKY : START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Monitor de jogo", NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle("Booster")
                .setContentText("Monitor e detecção de jogos ativos")
                .addAction(new Notification.Action.Builder(null, "Parar", pi).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private void addOverlay() {
        wm = getSystemService(WindowManager.class);
        float d = getResources().getDisplayMetrics().density;
        view = new TextView(this);
        view.setTextColor(0xFFF2F5FF);
        view.setTextSize(12);
        view.setLineSpacing(0, 1.05f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xC70A0F1C);
        bg.setCornerRadius(10 * d);
        bg.setStroke((int) Math.max(1, d), 0x33FFFFFF);
        view.setBackground(bg);
        int p = (int) (8 * d);
        view.setPadding(p, (int) (5 * d), p, (int) (5 * d));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        int pos = getSharedPreferences("wr", MODE_PRIVATE).getInt("pos", 0);
        lp.gravity = Gravity.TOP | (pos == 1 ? Gravity.CENTER_HORIZONTAL : pos == 2 ? Gravity.END : Gravity.START);
        lp.x = pos == 1 ? 0 : (int) (48 * d);
        lp.y = (int) (4 * d);
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        wm.addView(view, lp);
    }

    private void update() {
        if (view == null) return;
        Monitor.Sample m = Monitor.latest;
        if (m == null) return;
        android.content.SharedPreferences p = getSharedPreferences("wr", MODE_PRIVATE);
        boolean show = !p.getBoolean("ov_only_game", false) || Monitor.sessionActive() || Monitor.activeGame() != null;
        view.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
        if (show) view.setText(OverlayText.build(m, p));
    }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacks(tick);
        if (acquired) {
            Monitor.release();
            acquired = false;
        }
        if (panel != null) panel.destroy();
        removeOverlay();
        super.onDestroy();
    }

    private void removeOverlay() {
        if (view != null && wm != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
            }
        }
        view = null;
    }
}
