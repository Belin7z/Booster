package com.lucie.wrboost;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.FileReader;

public final class SystemInfo {
    public long ramTotal, ramAvail;
    public long swapTotal, swapFree;
    public long storageTotal, storageFree;
    public int battPct;
    public boolean charging;
    public float tempC;
    public int thermalStatus = -1;
    public float headroom = Float.NaN;

    public int ramUsedPct() {
        return ramTotal == 0 ? 0 : (int) Math.round(100.0 * (ramTotal - ramAvail) / ramTotal);
    }

    public static SystemInfo read(Context c) {
        SystemInfo s = new SystemInfo();

        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        s.ramTotal = mi.totalMem;
        s.ramAvail = mi.availMem;

        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) s.swapTotal = kb(line);
                else if (line.startsWith("SwapFree:")) s.swapFree = kb(line);
            }
        } catch (Exception ignored) {
        }

        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        s.storageTotal = fs.getTotalBytes();
        s.storageFree = fs.getAvailableBytes();

        Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            s.battPct = level < 0 ? 0 : Math.round(level * 100f / scale);
            int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            s.charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                    || b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            s.tempC = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        }

        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= 29) s.thermalStatus = pm.getCurrentThermalStatus();
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                s.headroom = pm.getThermalHeadroom(10);
            } catch (Exception ignored) {
            }
        }
        return s;
    }

    private static long kb(String line) {
        String[] p = line.trim().split("\\s+");
        return Long.parseLong(p[1]) * 1024L;
    }

    public static String gb(long bytes) {
        return String.format(java.util.Locale.US, "%.1f", bytes / 1073741824.0);
    }

    public static String thermalName(int st) {
        switch (st) {
            case 0: return "Normal";
            case 1: return "Leve";
            case 2: return "Moderado";
            case 3: return "Severo";
            case 4: return "Crítico";
            case 5: return "Emergência";
            case 6: return "Desligando";
            default: return "n/d";
        }
    }
}
