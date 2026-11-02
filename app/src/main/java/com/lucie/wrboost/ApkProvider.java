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
    private File file(Uri uri) {
        return new File(getContext().getCacheDir(), "/report.png".equals(uri.getPath()) ? "report.png" : "update.apk");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String p = uri.getPath();
        if (!"r".equals(mode) || !("/update.apk".equals(p) || "/report.png".equals(p))) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{file(uri).getName(), file(uri).length()});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "/report.png".equals(uri.getPath()) ? "image/png" : "application/vnd.android.package-archive";
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
