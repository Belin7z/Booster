package com.lucie.wrboost;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class MonitorTile extends TileService {
    @Override
    public void onStartListening() {
        paint(OverlayService.running);
    }

    @Override
    public void onClick() {
        if (OverlayService.running) {
            stopService(new Intent(this, OverlayService.class));
            paint(false);
        } else if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE));
            } else {
                startActivityAndCollapse(i);
            }
        } else {
            getSharedPreferences("wr", MODE_PRIVATE).edit().putBoolean("overlay", true).apply();
            startForegroundService(new Intent(this, OverlayService.class));
            paint(true);
        }
    }

    private void paint(boolean on) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_bolt));
        t.setLabel("Booster");
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
