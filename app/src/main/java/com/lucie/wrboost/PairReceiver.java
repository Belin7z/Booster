package com.lucie.wrboost;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class PairReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Bundle r = RemoteInput.getResultsFromIntent(i);
        if (r == null) return;
        CharSequence code = r.getCharSequence(Adb.KEY_CODE);
        if (code == null) return;
        final String digits = code.toString().replaceAll("\\D", "");
        final Context app = c.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                boolean ok = Adb.pairWithCode(app, digits);
                Adb.notifyResult(app, ok ? "Pareado. Conectando ao Booster…" : Adb.status());
                if (ok) Adb.connectAsync(app, true);
            } finally {
                pr.finish();
            }
        }).start();
    }
}
