package com.lucie.wrboost;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Reinicia o monitor depois que o celular liga ou o app é atualizado.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        android.content.SharedPreferences p = c.getSharedPreferences("wr", Context.MODE_PRIVATE);
        if (!p.getBoolean("boot", true) || !p.getBoolean("auto", true)) return;
        try {
            c.startForegroundService(new Intent(c, OverlayService.class));
        } catch (Exception ignored) {
        }
    }
}
