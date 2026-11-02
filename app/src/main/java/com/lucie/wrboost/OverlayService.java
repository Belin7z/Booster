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
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            update();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundCompat();
        if (!acquired) {
            Monitor.acquire(this);
            acquired = true;
        }
        removeOverlay();
        if (Settings.canDrawOverlays(this) && getSharedPreferences("wr", MODE_PRIVATE).getBoolean("overlay", true)) addOverlay();
        h.removeCallbacks(tick);
        h.post(tick);
        running = true;
        return START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Monitor de jogo", NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent pi = PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Booster")
                .setContentText("Monitor de RAM e temperatura ativo")
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
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(11);
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xB3000000);
        bg.setCornerRadius(8 * d);
        view.setBackground(bg);
        int p = (int) (6 * d);
        view.setPadding(p, p / 2, p, p / 2);

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
        SystemInfo s = m.sys;
        java.util.Locale us = java.util.Locale.US;
        StringBuilder t = new StringBuilder();
        if (m.fps >= 0) {
            t.append("FPS ").append(m.fps);
            if (!Float.isNaN(m.low1)) t.append(" (1% ").append(Math.round(m.low1)).append(")");
            t.append("\n");
        }
        t.append("CPU ").append(Float.isNaN(m.cpu) ? "--" : String.format(us, "%.0f°", m.cpu));
        if (!Float.isNaN(m.gpu)) t.append(" GPU ").append(String.format(us, "%.0f°", m.gpu));
        t.append("  BAT ").append(String.format(us, "%.0f°", s.tempC)).append("\n");
        t.append("RAM ").append(SystemInfo.gb(s.ramTotal - s.ramAvail)).append("/").append(SystemInfo.gb(s.ramTotal))
                .append("G  ").append(s.battPct).append("%").append(s.charging ? "⚡" : "");
        view.setText(t.toString());
        float hot = Float.isNaN(m.cpu) ? s.tempC : Math.max(m.cpu - 8, s.tempC);
        boolean lowFps = m.fps >= 0 && m.fps < 45;
        view.setTextColor(hot >= 43 || s.thermalStatus >= 2 || lowFps ? 0xFFFF5252 : hot >= 39 ? 0xFFF5A623 : 0xFF2ECC71);
    }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacks(tick);
        if (acquired) {
            Monitor.release();
            acquired = false;
        }
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
