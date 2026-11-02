package com.lucie.wrboost;

import android.content.Context;
import android.content.res.Configuration;

// Tema (escuro, claro ou automático) e cor de destaque escolhidos pelo usuário.
public final class Theme {
    public static final int[] ACCENTS = {0xFF4DA3FF, 0xFF2ECC71, 0xFFA66BFF, 0xFFFF9F43, 0xFFFF6B9D};
    public static final String[] ACCENT_NAMES = {"Azul", "Verde", "Roxo", "Laranja", "Rosa"};
    public static final String[] MODES = {"Escuro", "Claro", "Automático"};

    public static boolean light;
    public static int ACC = ACCENTS[0], ONACC = 0xFF04101F;
    public static int BG, CARD, TXT, MUT, BTN2, LINE, SEL, NAVTXT, CHEV, GRID, TRACK, BG2, BAR;

    public static boolean isLight() {
        return light;
    }

    private Theme() {
    }

    public static int mode(Context c) {
        return c.getSharedPreferences("wr", Context.MODE_PRIVATE).getInt("theme", 0);
    }

    public static int accentIndex(Context c) {
        return Math.max(0, Math.min(ACCENTS.length - 1, c.getSharedPreferences("wr", Context.MODE_PRIVATE).getInt("accent", 0)));
    }

    public static void load(Context c) {
        int m = mode(c);
        light = m == 1 || (m == 2 && (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES);
        ACC = ACCENTS[accentIndex(c)];
        if (light) {
            BG = 0xFFF1F4FA;
            CARD = 0xFFFFFFFF;
            TXT = 0xFF14203A;
            MUT = 0xFF5C6B8C;
            BTN2 = 0xFFE1E8F5;
            LINE = 0xFFD5DDEC;
            SEL = 0xFFE3ECFB;
            NAVTXT = 0xFF3C4A6B;
            CHEV = 0xFF9AA7C4;
            GRID = 0xFFD9E0EE;
            TRACK = 0xFFE1E8F5;
            BG2 = 0xFFEAEFF8;
        } else {
            BG = 0xFF0B1220;
            CARD = 0xFF141D33;
            TXT = 0xFFE8EEFC;
            MUT = 0xFF8FA0C7;
            BTN2 = 0xFF1D2A4A;
            LINE = 0xFF1F2B48;
            SEL = 0xFF1A2745;
            NAVTXT = 0xFFB8C4E0;
            CHEV = 0xFF4A5A80;
            GRID = 0xFF243152;
            TRACK = 0xFF1B2745;
            BG2 = 0xFF0F1830;
        }
        BAR = blend(ACC, BG, 0.55f);
    }

    public static int blend(int a, int b, float wa) {
        int r = Math.round(((a >> 16) & 0xFF) * wa + ((b >> 16) & 0xFF) * (1 - wa));
        int g = Math.round(((a >> 8) & 0xFF) * wa + ((b >> 8) & 0xFF) * (1 - wa));
        int bl = Math.round((a & 0xFF) * wa + (b & 0xFF) * (1 - wa));
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
