package com.lucie.wrboost;

import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

// Ícone de configurações rápidas que libera a RAM com um toque.
public class CleanTile extends TileService {
    @Override
    public void onStartListening() {
        paint("Liberar RAM");
    }

    @Override
    public void onClick() {
        paint("Limpando...");
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            Adb.init(app);
            Booster.Result r = Booster.boost(app, Booster.level(app), Monitor.activeGame());
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                Toast.makeText(app, r.forced + " apps fechados, " + (r.freedBytes / 1048576) + " MB liberados", Toast.LENGTH_SHORT).show();
                paint("Liberar RAM");
            });
        }).start();
    }

    private void paint(String label) {
        Tile t = getQsTile();
        if (t == null) return;
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_memory));
        t.setLabel(label);
        t.setState(Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
