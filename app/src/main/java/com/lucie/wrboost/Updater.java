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

// Atualizações vêm do repositório público Belin7z/Booster-releases (somente APKs + latest.json).
public final class Updater {
    private static final String BASE = "https://raw.githubusercontent.com/Belin7z/Booster-releases/main/";
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
        HttpURLConnection h = open(BASE + "latest.json?t=" + System.currentTimeMillis());
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

    public static void install(Context c, Info info, Progress cb) throws Exception {
        if (info.sha256.length() != 64) throw new Exception("hash ausente");
        java.io.File apk = new java.io.File(c.getCacheDir(), "update.apk");
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
        try {
            viaSession(c, apk);
        } catch (Exception e) {
            viaSystemInstaller(c, apk);
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
            while ((n = i.read(b)) > 0 && o.size() < 65536) o.write(b, 0, n);
            return o.toString("UTF-8");
        }
    }
}
