package com.lucie.wrboost;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;

// ADB sem fio embutido: o Booster pareia com o próprio celular e roda comandos de shell sem Shizuku.
public final class Adb {
    public static final String ACTION_PAIR = "com.lucie.wrboost.PAIR";
    public static final String KEY_CODE = "code";
    private static final String CHANNEL = "pair";
    private static final int NOTIF = 7;

    private static volatile AdbManager mgr;
    private static volatile String status = "";
    private static volatile int pairPort;
    private static volatile String pairHost = "127.0.0.1";
    private static volatile long lastTry;
    private static boolean initStarted;
    private static AdbMdns mdns;
    private static final AtomicBoolean busy = new AtomicBoolean();

    private Adb() {
    }

    public static synchronized void init(Context c) {
        if (initStarted) return;
        initStarted = true;
        final Context app = c.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                mgr = AdbManager.get(app);
            } catch (Throwable e) {
                status = "Falha ao preparar o ADB: " + e.getMessage();
                initStarted = false;
            }
        });
        t.setDaemon(true);
        t.start();
    }

    public static boolean connected() {
        AdbManager m = mgr;
        try {
            return m != null && m.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean paired(Context c) {
        return c.getSharedPreferences("wr", Context.MODE_PRIVATE).getBoolean("adb_paired", false);
    }

    public static String status() {
        return status;
    }

    public static boolean busy() {
        return busy.get();
    }

    public static void startPairing(Context c) {
        final Context app = c.getApplicationContext();
        init(app);
        stopPairing();
        pairPort = 0;
        status = "Aguardando o código. Abra \"Depuração sem fio\" e toque em \"Parear dispositivo com código\".";
        try {
            mdns = new AdbMdns(app, AdbMdns.SERVICE_TYPE_TLS_PAIRING, (addr, port) -> {
                if (port <= 0) return;
                pairPort = port;
                if (addr != null && addr.getHostAddress() != null) pairHost = addr.getHostAddress();
                notifyCodeRequest(app);
            });
            mdns.start();
        } catch (Throwable e) {
            status = "Não consegui procurar o serviço de pareamento: " + e.getMessage();
        }
    }

    public static void stopPairing() {
        try {
            if (mdns != null) mdns.stop();
        } catch (Throwable ignored) {
        }
        mdns = null;
    }

    // Chamado pela notificação, com o código digitado pelo usuário.
    public static boolean pairWithCode(Context c, String code) {
        busy.set(true);
        try {
            AdbManager m = AdbManager.get(c);
            int port = pairPort;
            if (port <= 0) {
                status = "Serviço de pareamento não encontrado. Toque em \"Iniciar pareamento\" e abra a tela de código.";
                return false;
            }
            boolean ok;
            try {
                ok = m.pair("127.0.0.1", port, code);
            } catch (Exception first) {
                ok = m.pair(pairHost, port, code);
            }
            if (ok) {
                c.getSharedPreferences("wr", Context.MODE_PRIVATE).edit().putBoolean("adb_paired", true).apply();
                status = "Pareado. Conectando…";
                stopPairing();
            } else {
                status = "Código recusado. Confira o código e tente de novo.";
            }
            return ok;
        } catch (Throwable e) {
            status = "Falha no pareamento: " + e.getMessage();
            return false;
        } finally {
            busy.set(false);
        }
    }

    public static void connectAsync(Context c, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastTry < 30000) return;
        if (!busy.compareAndSet(false, true)) return;
        lastTry = now;
        final Context app = c.getApplicationContext();
        Thread t = new Thread(() -> {
            try {
                connectNow(app);
            } finally {
                busy.set(false);
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // Reconecta sozinho quando o celular já foi pareado.
    public static void maybeReconnect(Context c) {
        if (paired(c) && !connected()) connectAsync(c, false);
    }

    private static boolean connectNow(Context c) {
        try {
            AdbManager m = AdbManager.get(c);
            if (m.isConnected()) return true;
            status = "Conectando…";
            boolean ok = m.connectTls(c, 10000);
            status = ok ? "Conectado." : "Não encontrei a Depuração sem fio. Confira se ela está ligada e o Wi-Fi conectado.";
            return ok;
        } catch (AdbPairingRequiredException e) {
            c.getSharedPreferences("wr", Context.MODE_PRIVATE).edit().putBoolean("adb_paired", false).apply();
            status = "É preciso parear de novo.";
            return false;
        } catch (Throwable e) {
            status = "Falha ao conectar: " + e.getMessage();
            return false;
        }
    }

    public static void forget(Context c) {
        try {
            AdbManager m = mgr;
            if (m != null) m.disconnect();
        } catch (Throwable ignored) {
        }
        c.getSharedPreferences("wr", Context.MODE_PRIVATE).edit().putBoolean("adb_paired", false).apply();
        status = "Pareamento removido.";
    }

    // Executa um comando de shell e devolve a saída; null se não conectado ou se estourar o tempo.
    public static String exec(final String cmd, long timeoutMs) {
        final AdbManager m = mgr;
        if (m == null || !m.isConnected()) return null;
        final AdbStream[] holder = new AdbStream[1];
        FutureTask<String> task = new FutureTask<>(() -> {
            AdbStream s = m.openStream("shell:" + cmd);
            holder[0] = s;
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            try (InputStream in = s.openInputStream()) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0 && o.size() < 4_000_000) o.write(b, 0, n);
            } catch (java.io.IOException ignored) {
            }
            try {
                s.close();
            } catch (Exception ignored) {
            }
            return o.toString("UTF-8").replace("\r", "");
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
        try {
            return task.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Throwable e) {
            try {
                if (holder[0] != null) holder[0].close();
            } catch (Exception ignored) {
            }
            task.cancel(true);
            return null;
        }
    }

    private static void notifyCodeRequest(Context c) {
        RemoteInput ri = new RemoteInput.Builder(KEY_CODE).setLabel("Código de 6 dígitos").build();
        Intent i = new Intent(c, PairReceiver.class).setAction(ACTION_PAIR);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action a = new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_bolt), "Digitar código", pi)
                .addRemoteInput(ri).build();
        post(c, new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle("Booster: pareamento")
                .setContentText("Toque em \"Digitar código\" e informe o código da tela de pareamento.")
                .addAction(a)
                .setOnlyAlertOnce(true));
        status = "Serviço de pareamento encontrado. Digite o código na notificação.";
    }

    public static void notifyResult(Context c, String text) {
        post(c, new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle("Booster")
                .setContentText(text)
                .setAutoCancel(true));
    }

    private static void post(Context c, Notification.Builder b) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Pareamento ADB", NotificationManager.IMPORTANCE_HIGH));
        nm.notify(NOTIF, b.build());
    }
}
