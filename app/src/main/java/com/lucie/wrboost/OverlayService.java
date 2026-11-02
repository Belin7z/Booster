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
                .setSmallIcon(R.drawable.ic_bolt)
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
        view.setText(OverlayText.build(m, getSharedPreferences("wr", MODE_PRIVATE)));
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
