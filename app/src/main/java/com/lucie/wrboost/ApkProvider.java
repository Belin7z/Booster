package com.lucie.wrboost;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

// Entrega somente o update.apk do cache ao instalador do sistema.
public class ApkProvider extends ContentProvider {
    private static final String[] NAMES = {"/update.apk", "/report.png", "/historico.csv", "/historico.pdf"};

    private File file(Uri uri) {
        String p = uri.getPath() == null ? "" : uri.getPath();
        return new File(getContext().getCacheDir(), p.startsWith("/") ? p.substring(1) : p);
    }

    private boolean allowed(Uri uri) {
        for (String n : NAMES) if (n.equals(uri.getPath())) return true;
        return false;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode) || !allowed(uri) || !file(uri).exists()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        if (!allowed(uri)) return c;
        c.addRow(new Object[]{file(uri).getName(), file(uri).length()});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        String p = uri.getPath() == null ? "" : uri.getPath();
        return p.endsWith(".png") ? "image/png" : p.endsWith(".csv") ? "text/csv" : p.endsWith(".pdf") ? "application/pdf"
                : "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
