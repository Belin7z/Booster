package com.lucie.wrboost;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

// Atualizações vêm da última release do repositório Belin7z/Booster (APK + latest.json).
public final class Updater {
    private static final String BASE = "https://github.com/Belin7z/Booster/releases/latest/download/";
    public static final String ACTION_RESULT = "com.lucie.wrboost.INSTALL_RESULT";

    public interface Progress {
        void onProgress(int percent);
    }

    public static final class Info {
        public int code;
        public String name = "", notes = "", apk = "", sha256 = "";
    }

    private Updater() {
    }

    public static int installedCode(Context c) {
        try {
            return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    public static String installedName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    // Devolve a versão mais nova ou null se já estiver atualizado.
    public static Info check(Context c) throws Exception {
        HttpURLConnection h = open(BASE + "latest.json");
        try {
            if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
            JSONObject j = new JSONObject(readAll(h.getInputStream()));
            Info i = new Info();
            i.code = j.getInt("versionCode");
            i.name = j.optString("versionName", "");
            i.notes = j.optString("notes", "");
            i.apk = j.getString("apk");
            i.sha256 = j.optString("sha256", "").toLowerCase();
            if (!i.apk.matches("[A-Za-z0-9._-]+\\.apk")) throw new Exception("arquivo inválido");
            return i.code > installedCode(c) ? i : null;
        } finally {
            h.disconnect();
        }
    }

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("wr", Context.MODE_PRIVATE);
    }

    // Já baixada e conferida (hash) no cache, pronta para instalar.
    public static boolean ready(Context c, Info info) {
        return prefs(c).getInt("dl_code", 0) == info.code && new java.io.File(c.getCacheDir(), "update.apk").exists();
    }

    // Atualização já baixada por uma checagem automática, ou null.
    public static Info downloaded(Context c) {
        android.content.SharedPreferences p = prefs(c);
        int code = p.getInt("dl_code", 0);
        if (code <= installedCode(c) || !new java.io.File(c.getCacheDir(), "update.apk").exists()) return null;
        Info i = new Info();
        i.code = code;
        i.name = p.getString("dl_name", "");
        i.notes = p.getString("dl_notes", "");
        i.apk = p.getString("dl_apk", "");
        i.sha256 = p.getString("dl_sha", "");
        return i;
    }

    public static void install(Context c, Info info, Progress cb) throws Exception {
        if (!ready(c, info)) download(c, info, cb);
        java.io.File apk = new java.io.File(c.getCacheDir(), "update.apk");
        saveWhatsNew(c, info);
        try {
            if (viaShell(c, apk)) return;
            viaSession(c, apk);
        } catch (Exception e) {
            viaSystemInstaller(c, apk);
        }
    }

    // Guarda as notas para mostrar "novidades" na primeira abertura da nova versão.
    private static void saveWhatsNew(Context c, Info info) {
        prefs(c).edit().putInt("wn_code", info.code).putString("wn_name", info.name).putString("wn_notes", info.notes).apply();
    }

    public static void download(Context c, Info info, Progress cb) throws Exception {
        if (info.sha256.length() != 64) throw new Exception("hash ausente");
        java.io.File apk = new java.io.File(c.getCacheDir(), "update.apk");
        prefs(c).edit().remove("dl_code").apply();
        HttpURLConnection h = open(BASE + info.apk);
        try {
            if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
            long total = h.getContentLengthLong();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = h.getInputStream(); OutputStream out = new java.io.FileOutputStream(apk)) {
                byte[] buf = new byte[32768];
                long done = 0;
                int n, last = -1;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                    out.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        int p = (int) (done * 100 / total);
                        if (p != last) {
                            last = p;
                            cb.onProgress(p);
                        }
                    }
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format("%02x", b));
            if (!hex.toString().equals(info.sha256)) {
                apk.delete();
                throw new Exception("arquivo corrompido (hash diferente)");
            }
        } finally {
            h.disconnect();
        }
        prefs(c).edit().putInt("dl_code", info.code).putString("dl_name", info.name).putString("dl_notes", info.notes)
                .putString("dl_apk", info.apk).putString("dl_sha", info.sha256).apply();
    }

    private static final java.util.concurrent.atomic.AtomicBoolean autoBusy = new java.util.concurrent.atomic.AtomicBoolean();

    // Chamada pelo serviço: a cada 6 h confere e baixa sozinho (só em rede sem tarifa), e avisa para instalar.
    public static void autoCheck(final Context c) {
        final Context app = c.getApplicationContext();
        android.content.SharedPreferences p = prefs(app);
        long now = System.currentTimeMillis();
        if (!p.getBoolean("auto_update", true) || now - p.getLong("auto_at", 0) < 15 * 60_000L) return;
        android.net.ConnectivityManager cm = app.getSystemService(android.net.ConnectivityManager.class);
        if (cm == null || cm.isActiveNetworkMetered() || cm.getActiveNetwork() == null) return;
        if (!autoBusy.compareAndSet(false, true)) return;
        p.edit().putLong("auto_at", now).apply();
        new Thread(() -> {
            try {
                Info i = check(app);
                if (i == null) return;
                if (!ready(app, i)) download(app, i, pc -> { });
                boolean idle = Monitor.activeGame() == null && !Monitor.sessionActive();
                if (idle && p.getBoolean("auto_install", true) && Shell.granted()) {
                    saveWhatsNew(app, i);
                    if (viaShell(app, new java.io.File(app.getCacheDir(), "update.apk"))) return;
                }
                notifyReady(app, i);
            } catch (Throwable e) {
                CrashLog.log(app, "Atualização automática", String.valueOf(e.getMessage()));
            } finally {
                autoBusy.set(false);
            }
        }).start();
    }

    // Notas em markdown simples → texto limpo para notificação.
    static String plain(String notes) {
        return notes.replaceAll("(?m)^#+\\s*(.*)$", "$1:").replaceAll("(?m)^[-*]\\s+", "• ");
    }

    private static void notifyReady(Context c, Info i) {
        android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
        nm.createNotificationChannel(new android.app.NotificationChannel("update", "Atualizações", android.app.NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_INSTALL, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 5, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify(5, new android.app.Notification.Builder(c, "update").setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle("Booster v" + i.name + " pronto para instalar")
                .setContentText("Toque para instalar a atualização já baixada.")
                .setStyle(new android.app.Notification.BigTextStyle().bigText(i.notes.isEmpty() ? "Toque para instalar." : plain(i.notes)))
                .setContentIntent(pi).setAutoCancel(true).build());
    }

    // Últimas versões publicadas, com as notas de cada uma.
    public static java.util.List<String> history() throws Exception {
        HttpURLConnection h = open("https://api.github.com/repos/Belin7z/Booster/releases?per_page=10");
        try {
            h.setRequestProperty("Accept", "application/vnd.github+json");
            if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
            org.json.JSONArray a = new org.json.JSONArray(readAll(h.getInputStream()));
            java.util.List<String> out = new java.util.ArrayList<>();
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                String body = o.optString("body", "").replace("\r", "").trim();
                out.add(o.optString("name", o.optString("tag_name")) + "\n" + (body.isEmpty() ? "Sem notas." : body));
            }
            return out;
        } finally {
            h.disconnect();
        }
    }

    // Com o modo avançado conectado, instala sem nenhuma tela de confirmação.
    static boolean viaShell(Context c, java.io.File apk) {
        if (!Shell.granted()) return false;
        try {
            java.io.File dir = c.getExternalCacheDir();
            if (dir == null) return false;
            java.io.File out = new java.io.File(dir, "update.apk");
            try (InputStream in = new java.io.FileInputStream(apk); OutputStream os = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            String r = Shell.exec("pm install -r " + out.getAbsolutePath(), 120_000);
            return r != null && r.contains("Success");
        } catch (Exception e) {
            return false;
        }
    }

    private static void viaSession(Context c, java.io.File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        int id = pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
        PackageInstaller.Session s = pi.openSession(id);
        try {
            try (InputStream in = new java.io.FileInputStream(apk); OutputStream out = s.openWrite("update.apk", 0, apk.length())) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent r = new Intent(c, InstallReceiver.class).setAction(ACTION_RESULT);
            PendingIntent p = PendingIntent.getBroadcast(c, id, r, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(p.getIntentSender());
        } catch (Exception e) {
            s.abandon();
            throw e;
        } finally {
            s.close();
        }
    }

    // Plano B para ROMs (como a HyperOS) que recusam a instalação por sessão.
    private static void viaSystemInstaller(Context c, java.io.File apk) {
        android.net.Uri uri = apkUri(c, apk);
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    private static android.net.Uri apkUri(Context c, java.io.File f) {
        return android.net.Uri.parse("content://" + c.getPackageName() + ".apk/update.apk");
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(20000);
        h.setUseCaches(false);
        return h;
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream i = in) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = i.read(b)) > 0 && o.size() < 262144) o.write(b, 0, n);
            return o.toString("UTF-8");
        }
    }
}
