package com.lucie.wrboost;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Cópia das configurações (e do histórico de partidas) em um arquivo JSON, para restaurar após reinstalar.
public final class Backup {
    private Backup() {
    }

    // Estados temporários do modo jogo e do updater não entram na cópia.
    private static boolean skip(String k) {
        return k.startsWith("gm_") || k.startsWith("ba_") || k.equals("prevFilter") || k.startsWith("dl_") || k.startsWith("adb_");
    }

    public static String export(Context c) throws Exception {
        SharedPreferences sp = c.getSharedPreferences("wr", Context.MODE_PRIVATE);
        JSONObject root = new JSONObject();
        root.put("app", "booster").put("v", 1);
        JSONObject prefs = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (skip(e.getKey())) continue;
            Object v = e.getValue();
            JSONObject o = new JSONObject();
            if (v instanceof Boolean) o.put("t", "b").put("v", v);
            else if (v instanceof Integer) o.put("t", "i").put("v", v);
            else if (v instanceof Long) o.put("t", "l").put("v", v);
            else if (v instanceof Float) o.put("t", "f").put("v", (double) (Float) v);
            else if (v instanceof String) o.put("t", "s").put("v", v);
            else if (v instanceof Set) {
                JSONArray a = new JSONArray();
                for (Object s : (Set<?>) v) a.put(String.valueOf(s));
                o.put("t", "ss").put("v", a);
            } else continue;
            prefs.put(e.getKey(), o);
        }
        root.put("prefs", prefs);
        return root.toString();
    }

    // Devolve quantas opções foram restauradas.
    public static int restore(Context c, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"booster".equals(root.optString("app"))) throw new IllegalArgumentException("Arquivo não é uma cópia do Booster");
        JSONObject prefs = root.getJSONObject("prefs");
        SharedPreferences.Editor ed = c.getSharedPreferences("wr", Context.MODE_PRIVATE).edit();
        int n = 0;
        java.util.Iterator<String> it = prefs.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (skip(k)) continue;
            JSONObject o = prefs.getJSONObject(k);
            switch (o.getString("t")) {
                case "b": ed.putBoolean(k, o.getBoolean("v")); break;
                case "i": ed.putInt(k, o.getInt("v")); break;
                case "l": ed.putLong(k, o.getLong("v")); break;
                case "f": ed.putFloat(k, (float) o.getDouble("v")); break;
                case "s": ed.putString(k, o.getString("v")); break;
                case "ss":
                    Set<String> s = new HashSet<>();
                    JSONArray a = o.getJSONArray("v");
                    for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                    ed.putStringSet(k, s);
                    break;
                default: continue;
            }
            n++;
        }
        ed.apply();
        return n;
    }
}
