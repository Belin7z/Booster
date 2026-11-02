package com.lucie.wrboost;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0B1220, CARD = 0xFF141D33, TXT = 0xFFE8EEFC, MUT = 0xFF8FA0C7;
    private static final int OK = 0xFF2ECC71, WARN = 0xFFF5A623, BAD = 0xFFFF5252;

    private final List<View> pages = new ArrayList<>();
    private final List<String> pageNames = new ArrayList<>();
    private final List<Integer> pageIcons = new ArrayList<>();
    private final List<TextView> navItems = new ArrayList<>();
    private FrameLayout content;
    private LinearLayout drawer;
    private View scrim;
    private boolean drawerOpen;
    private TextView tvTitle, tvDot, tvPos, tvOverlayPerm;
    private Button btnMonitor;
    private GaugeView gauge;
    private GraphView gFps, gCpu;
    private TextView tvFps, tvLow, tvCpu, tvGpu, tvBat, tvFree;
    private TextView tvSwap, tvThermal, tvTip, tvResult, tvShizuku, tvLast;
    private Switch swOverlay, swDnd;
    private TextView tvAdb, tvFpsTarget, tvFpsTarget2, tvPreview, tvRates;
    private Button btnAdb, btnForget;
    private LinearLayout gamesList, reportsList, ptDays, ptGames;
    private BarChartView ptBars;
    private TextView tvHeat, tvBubble, tvPtToday, tvPtWeek, tvPtMonth, ptSummary, tvHomeToday, tvLevel, tvKeep, tvAutoClean;
    private LinearLayout ptPerm;
    private int ptTick;
    private TextView tvUsage;
    private int reportsVer = -1;
    private Button btnPlay, btnBoost, btnShizuku, btnUpdate;
    private TextView tvUpdate;
    private Updater.Info pending;
    private SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            h.postDelayed(this, 1000);
        }
    };

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("wr", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(0, (int) dp(24), 0, 0);
        root.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) dp(4), (int) dp(4), (int) dp(16), (int) dp(4));
        ImageView menu = new ImageView(this);
        menu.setImageDrawable(icon(R.drawable.ic_menu, TXT));
        menu.setPadding((int) dp(14), (int) dp(14), (int) dp(14), (int) dp(14));
        menu.setOnClickListener(v -> toggleDrawer(true));
        bar.addView(menu, new LinearLayout.LayoutParams((int) dp(52), (int) dp(48)));
        tvTitle = text("", 20, TXT, true);
        bar.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tvDot = text("Shizuku", 12, MUT, false);
        tvDot.setCompoundDrawablePadding((int) dp(6));
        bar.addView(tvDot);
        main.addView(bar);

        content = new FrameLayout(this);
        main.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Início
        LinearLayout home = page("Início", R.drawable.ic_home);
        gauge = new GaugeView(this);
        home.addView(gauge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(200)));
        LinearLayout r1 = new LinearLayout(this);
        tvFps = stat(r1, "FPS");
        tvCpu = stat(r1, "CPU");
        tvGpu = stat(r1, "GPU");
        tvBat = stat(r1, "Bateria");
        home.addView(r1);
        tvHomeToday = text("", 12, MUT, false);
        tvHomeToday.setGravity(Gravity.CENTER);
        tvHomeToday.setPadding(0, (int) dp(6), 0, 0);
        tvHomeToday.setOnClickListener(v -> showPage(pageNames.indexOf("Tempo de jogo")));
        home.addView(tvHomeToday);
        tvFpsTarget = text("", 12, MUT, false);
        tvFpsTarget.setGravity(Gravity.CENTER);
        home.addView(tvFpsTarget);
        LinearLayout tipCard = card();
        tvTip = text("", 14, TXT, false);
        tipCard.addView(tvTip);
        home.addView(tipCard);
        btnPlay = button("Jogar", 0xFF4DA3FF, 0xFF04101F);
        withIcon(btnPlay, R.drawable.ic_play, 0xFF04101F);
        btnPlay.setOnClickListener(v -> {
            String g = lastGame();
            if (g == null) showPage(1);
            else play(g);
        });
        home.addView(btnPlay, btnParams(56));
        LinearLayout acts = new LinearLayout(this);
        btnBoost = button("Liberar RAM", 0xFF1D2A4A, TXT);
        withIcon(btnBoost, R.drawable.ic_memory, TXT);
        btnBoost.setTextSize(14);
        btnBoost.setOnClickListener(v -> boostOnly());
        btnMonitor = button("", 0xFF1D2A4A, TXT);
        btnMonitor.setTextSize(14);
        btnMonitor.setOnClickListener(v -> toggleMonitor());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, (int) dp(48), 1);
        half.setMargins(0, 0, (int) dp(4), 0);
        acts.addView(btnBoost, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, (int) dp(48), 1);
        half2.setMargins((int) dp(4), 0, 0, 0);
        acts.addView(btnMonitor, half2);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.setMargins(0, (int) dp(8), 0, 0);
        home.addView(acts, ap);
        tvResult = text("", 13, MUT, false);
        tvResult.setGravity(Gravity.CENTER);
        home.addView(tvResult);

        // Jogos
        LinearLayout gm = page("Jogos", R.drawable.ic_gamepad);
        gm.addView(text("Os jogos instalados são detectados automaticamente. Se algum não aparecer, adicione manualmente. Toque no jogo para opções.", 12, MUT, false));
        Button addGame = button("Adicionar jogo", 0xFF1D2A4A, TXT);
        withIcon(addGame, R.drawable.ic_add, TXT);
        addGame.setTextSize(14);
        addGame.setOnClickListener(v -> pickGames());
        gm.addView(addGame, btnParams(46));
        gamesList = new LinearLayout(this);
        gamesList.setOrientation(LinearLayout.VERTICAL);
        gm.addView(gamesList);

        // Relatórios
        LinearLayout rp = page("Relatórios", R.drawable.ic_chart);
        reportsList = new LinearLayout(this);
        reportsList.setOrientation(LinearLayout.VERTICAL);
        rp.addView(reportsList);

        // Tempo de jogo
        LinearLayout pt = page("Tempo de jogo", R.drawable.ic_calendar);
        ptPerm = card();
        ptPerm.addView(text("O tempo de jogo vem do histórico de uso do Android, então conta mesmo quando você abre o jogo fora do Booster. Para isso, permita o acesso ao uso de apps.", 13, MUT, false));
        Button grant = button("Permitir acesso ao uso", 0xFF4DA3FF, 0xFF04101F);
        grant.setTextSize(14);
        grant.setOnClickListener(v -> openAction(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        ptPerm.addView(grant, btnParams(44));
        pt.addView(ptPerm);
        LinearLayout ptRow = new LinearLayout(this);
        ptRow.setPadding(0, (int) dp(8), 0, 0);
        tvPtToday = stat(ptRow, "Hoje");
        tvPtWeek = stat(ptRow, "7 dias");
        tvPtMonth = stat(ptRow, "30 dias");
        tvPtToday.setTextSize(16);
        tvPtWeek.setTextSize(16);
        tvPtMonth.setTextSize(16);
        pt.addView(ptRow);
        ptBars = new BarChartView(this, "Horas jogadas por dia (14 dias)");
        pt.addView(ptBars, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(170)));
        ((LinearLayout.LayoutParams) ptBars.getLayoutParams()).setMargins(0, (int) dp(10), 0, 0);
        LinearLayout sm = card();
        ptSummary = text("", 13, TXT, false);
        sm.addView(ptSummary);
        pt.addView(sm);
        pt.addView(section("POR DIA"));
        ptDays = card();
        pt.addView(ptDays);
        pt.addView(section("POR JOGO"));
        ptGames = new LinearLayout(this);
        ptGames.setOrientation(LinearLayout.VERTICAL);
        pt.addView(ptGames);

        // Desempenho
        LinearLayout perf = page("Desempenho", R.drawable.ic_speed);
        gFps = new GraphView(this, "FPS (últimos 2 min)", 0xFF4DA3FF, 0, 60);
        gCpu = new GraphView(this, "Temperatura da CPU °C", 0xFFFF7A59, 30, 50);
        perf.addView(gFps, graphParams());
        perf.addView(gCpu, graphParams());
        LinearLayout r2 = new LinearLayout(this);
        r2.setPadding(0, (int) dp(8), 0, 0);
        tvLow = stat(r2, "1% low");
        tvFree = stat(r2, "Armazen.");
        perf.addView(r2);
        tvRates = text("", 13, MUT, false);
        perf.addView(tvRates);
        LinearLayout info = card();
        tvSwap = text("", 14, TXT, false);
        tvThermal = text("", 14, TXT, false);
        info.addView(tvSwap);
        info.addView(tvThermal);
        perf.addView(info);

        // Modo avançado
        LinearLayout adv = page("Modo avançado", R.drawable.ic_lock_open);
        LinearLayout adbCard = card();
        adbCard.addView(text("Conexão integrada (sem Shizuku)", 15, TXT, true));
        tvAdb = text("", 13, MUT, false);
        adbCard.addView(tvAdb);
        btnAdb = button("", 0xFF4DA3FF, 0xFF04101F);
        btnAdb.setTextSize(14);
        btnAdb.setOnClickListener(v -> adbAction());
        adbCard.addView(btnAdb, btnParams(46));
        btnForget = button("Esquecer pareamento", 0xFF1D2A4A, TXT);
        btnForget.setTextSize(13);
        btnForget.setOnClickListener(v -> {
            Adb.forget(this);
            toast("Pareamento removido.");
        });
        adbCard.addView(btnForget, btnParams(42));
        adv.addView(adbCard);
        adv.addView(text("Como parear: 1) ative a Depuração sem fio nas Opções do desenvolvedor (Wi-Fi ligado); 2) toque em Iniciar pareamento e, na tela \"Parear dispositivo com código\", digite o código na notificação do Booster. Só precisa fazer uma vez.", 12, MUT, false));
        LinearLayout advCard = card();
        advCard.addView(text("Alternativa: Shizuku", 15, TXT, true));
        tvShizuku = text("", 13, MUT, false);
        advCard.addView(tvShizuku);
        btnShizuku = button("", 0xFF1D2A4A, TXT);
        btnShizuku.setTextSize(14);
        btnShizuku.setOnClickListener(v -> shizukuAction());
        advCard.addView(btnShizuku, btnParams(46));
        adv.addView(advCard);
        adv.addView(text("Com o Shizuku o Booster lê FPS e temperatura de CPU/GPU, ativa o Game Mode e faz a limpeza profunda.", 12, MUT, false));

        // Atualizações
        LinearLayout up = page("Atualizações", R.drawable.ic_download);
        LinearLayout upCard = card();
        tvUpdate = text("Versão instalada: " + Updater.installedName(this), 13, MUT, false);
        upCard.addView(tvUpdate);
        btnUpdate = button("Verificar atualizações", 0xFF1D2A4A, TXT);
        btnUpdate.setTextSize(14);
        btnUpdate.setOnClickListener(v -> onUpdateClick());
        upCard.addView(btnUpdate, btnParams(46));
        up.addView(upCard);

        // Sessão
        LinearLayout ses = page("Sessão", R.drawable.ic_timer);
        LinearLayout last = card();
        last.addView(text("Resumo da última sessão", 15, TXT, true));
        tvLast = text(prefs.getString("last", "Nenhuma sessão ainda."), 13, MUT, false);
        last.addView(tvLast);
        ses.addView(last);
        Button end = button("Encerrar sessão", 0xFF1D2A4A, TXT);
        withIcon(end, R.drawable.ic_stop, TXT);
        end.setTextSize(14);
        end.setOnClickListener(v -> endSession());
        ses.addView(end, btnParams(48));

        // Configurações
        LinearLayout set = page("Configurações", R.drawable.ic_settings);
        set.addView(section("DURANTE O JOGO"));
        LinearLayout opts = card();
        swOverlay = sw(opts, "Monitor flutuante", "overlay", true);
        swOverlay.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("overlay", on).apply();
            if (on && !Settings.canDrawOverlays(this)) askOverlay();
        });
        swDnd = sw(opts, "Não perturbe ao jogar", "dnd", true);
        swDnd.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("dnd", on).apply();
            if (on && !dndAllowed()) {
                toast("Permita o acesso a \"Não perturbe\" para o app.");
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            }
        });
        tvPos = rowItem(opts, "", R.drawable.ic_overlay, v -> cyclePosition());
        set.addView(opts);
        set.addView(section("ITENS DO MONITOR"));
        LinearLayout items = card();
        String[][] its = {{"FPS", "ov_fps", "1"}, {"FPS 1% low", "ov_low", "0"}, {"Temperatura da CPU", "ov_cpu", "1"},
                {"Temperatura da GPU", "ov_gpu", "1"}, {"Memória RAM", "ov_ram", "1"}, {"Bateria", "ov_bat", "0"},
                {"Layout horizontal", "ov_h", "0"}};
        for (String[] it : its) {
            final String key = it[1];
            Switch s = sw(items, it[0], key, it[2].equals("1"));
            s.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(key, on).apply());
        }
        tvFpsTarget2 = rowItem(items, "", R.drawable.ic_speed, v -> cycleFpsTarget());
        set.addView(items);
        set.addView(text("PRÉ-VISUALIZAÇÃO", 11, MUT, true));
        LinearLayout pv = card();
        tvPreview = text("", 13, TXT, false);
        pv.addView(tvPreview);
        set.addView(pv);
        set.addView(section("LIMPEZA DE RAM"));
        LinearLayout cl = card();
        tvLevel = rowItem(cl, "", R.drawable.ic_memory, v -> pickLevel());
        tvKeep = rowItem(cl, "", R.drawable.ic_shield, v -> pickKeep());
        tvAutoClean = rowItem(cl, "", R.drawable.ic_timer, v -> cycleAutoClean());
        set.addView(cl);
        set.addView(text("Os níveis Equilibrada e Máxima fecham apps de verdade e precisam do modo avançado conectado. Na Máxima, só o telefone, as mensagens SMS e os apps que você marcar sobrevivem; o resto precisa ser aberto de novo. A limpeza automática roda durante o jogo quando a RAM passa do limite, no máximo a cada 3 minutos.", 12, MUT, false));
        set.addView(section("ALERTAS E INÍCIO"));
        LinearLayout al = card();
        tvHeat = rowItem(al, "", R.drawable.ic_thermo, v -> cycleHeat());
        sw(al, "Iniciar o monitor ao ligar o celular", "boot", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("boot", on).apply());
        set.addView(al);
        set.addView(text("No Xiaomi, permita também o início automático do Booster nas permissões do app para o início com o celular funcionar.", 12, MUT, false));
        set.addView(section("PAINEL NO JOGO"));
        LinearLayout gpc = card();
        sw(gpc, "Bolha do Booster durante o jogo", "panel", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("panel", on).apply());
        tvBubble = rowItem(gpc, "", R.drawable.ic_overlay, v -> cycleBubble());
        rowItem(gpc, "Apps do painel (acesso sem sair do jogo)", R.drawable.ic_add, v -> pickApps("panel_apps", "Apps no painel do jogo", GamePanel.apps(this)));
        set.addView(gpc);
        set.addView(text("Durante o jogo aparece uma bolha na lateral da tela. Toque nela para liberar RAM, ligar o Não perturbe ou abrir outros apps. Com o modo avançado conectado, o app abre em janela flutuante quando o sistema permitir.", 12, MUT, false));
        set.addView(section("DETECÇÃO DE JOGOS"));
        LinearLayout det = card();
        sw(det, "Detectar jogos automaticamente", "auto", true).setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("auto", on).apply();
            if (on && !OverlayService.running) startForegroundService(new Intent(this, OverlayService.class));
        });
        sw(det, "Otimizar ao abrir um jogo (liberar RAM, Game Mode)", "auto_boost", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("auto_boost", on).apply());
        sw(det, "Mostrar o monitor só durante jogos", "ov_only_game", false)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("ov_only_game", on).apply());
        tvUsage = rowItem(det, "", R.drawable.ic_check, v -> openAction(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        set.addView(det);
        set.addView(text("Com o acesso ao uso de apps (ou o modo avançado conectado) o Booster sabe qual jogo está aberto, mede o FPS dele e grava o relatório sozinho.", 12, MUT, false));
        set.addView(section("PERMISSÕES"));
        LinearLayout perms = card();
        tvOverlayPerm = rowItem(perms, "", R.drawable.ic_check, v -> askOverlay());
        rowItem(perms, "Pop-up em segundo plano (Xiaomi)", R.drawable.ic_overlay, v -> openMiuiPerms());
        rowItem(perms, "Bateria sem restrições", R.drawable.ic_battery, v -> openAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        set.addView(perms);
        set.addView(text("No HyperOS, para o monitor aparecer sobre o jogo, ative \"Exibir pop-ups durante execução em segundo plano\" e \"Exibir na tela de bloqueio\" nas permissões do Booster.", 12, MUT, false));

        // Atalhos
        LinearLayout sc = page("Atalhos e dicas", R.drawable.ic_tools);
        LinearLayout sh = card();
        rowItem(sh, "Modo desempenho / bateria", R.drawable.ic_battery, v -> openAction(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        rowItem(sh, "Opções do desenvolvedor", R.drawable.ic_code, v -> openAction(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        rowItem(sh, "Armazenamento", R.drawable.ic_storage, v -> openAction(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        rowItem(sh, "Wi-Fi", R.drawable.ic_wifi, v -> openAction(Settings.ACTION_WIFI_SETTINGS));
        sc.addView(sh);
        sc.addView(text("A limpeza profunda fecha apps de terceiros (menos mensagens e sistema); eles não "
                + "notificam até serem abertos de novo. Para mais RAM de verdade: Memória estendida em 3 GB "
                + "(Configurações → Sobre o telefone → Memória), Game Turbo e reinício semanal.", 12, MUT, false));

        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> toggleDrawer(false));
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(CARD);
        drawer.setPadding((int) dp(12), (int) dp(44), (int) dp(12), (int) dp(16));
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(icon(R.drawable.ic_bolt, 0xFF04101F));
        logo.setBackground(round(0xFF4DA3FF, 10));
        logo.setPadding((int) dp(8), (int) dp(8), (int) dp(8), (int) dp(8));
        brand.addView(logo, new LinearLayout.LayoutParams((int) dp(40), (int) dp(40)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding((int) dp(12), 0, 0, 0);
        names.addView(text("Booster", 18, TXT, true));
        names.addView(text("Versão " + Updater.installedName(this), 12, MUT, false));
        brand.addView(names);
        drawer.addView(brand);
        View gap = new View(this);
        gap.setBackgroundColor(0xFF22304F);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(1));
        gp.setMargins(0, (int) dp(20), 0, (int) dp(12));
        drawer.addView(gap, gp);
        for (int i = 0; i < pages.size(); i++) {
            final int idx = i;
            TextView it = text(pageNames.get(i), 15, TXT, false);
            it.setCompoundDrawablePadding((int) dp(14));
            it.setPadding((int) dp(14), (int) dp(13), (int) dp(14), (int) dp(13));
            it.setOnClickListener(v -> {
                showPage(idx);
                toggleDrawer(false);
            });
            navItems.add(it);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, (int) dp(2), 0, (int) dp(2));
            drawer.addView(it, lp);
        }
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams((int) dp(270), ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(drawer, dlp);
        drawer.setTranslationX(-dp(270));

        setContentView(root);
        showPage(0);
        if (prefs.getBoolean("auto", true) && !OverlayService.running) startForegroundService(new Intent(this, OverlayService.class));
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        if (System.currentTimeMillis() - prefs.getLong("lastCheck", 0) > 6 * 3600_000L) checkUpdate(false);
    }

    private LinearLayout page(String name, int icon) {
        ScrollView sv = new ScrollView(this);
        sv.setVisibility(View.GONE);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) dp(16);
        l.setPadding(p, (int) dp(4), p, (int) dp(24));
        sv.addView(l);
        content.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pages.add(sv);
        pageNames.add(name);
        pageIcons.add(icon);
        return l;
    }

    private void showPage(int idx) {
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setVisibility(i == idx ? View.VISIBLE : View.GONE);
            TextView it = navItems.get(i);
            int col = i == idx ? 0xFF4DA3FF : 0xFFB8C4E0;
            it.setBackground(i == idx ? round(0xFF1A2745, 10) : null);
            it.setTextColor(col);
            it.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(pageIcons.get(i), col), null, null, null);
        }
        tvTitle.setText(pageNames.get(idx));
    }

    private void toggleDrawer(boolean open) {
        drawerOpen = open;
        if (open) {
            scrim.setAlpha(0);
            scrim.setVisibility(View.VISIBLE);
        }
        scrim.animate().alpha(open ? 1 : 0).setDuration(180).withEndAction(() -> {
            if (!drawerOpen) scrim.setVisibility(View.GONE);
        });
        drawer.animate().translationX(open ? 0 : -dp(270)).setDuration(180);
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) toggleDrawer(false);
        else super.onBackPressed();
    }

    private TextView rowItem(LinearLayout parent, String label, int ic, View.OnClickListener l) {
        TextView t = text(label, 14, TXT, false);
        t.setCompoundDrawablePadding((int) dp(14));
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(ic, MUT), null, icon(R.drawable.ic_chevron, 0xFF4A5A80), null);
        t.setPadding(0, (int) dp(12), 0, (int) dp(12));
        t.setOnClickListener(l);
        parent.addView(t);
        return t;
    }

    private void openAction(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            toast("Abra manualmente pelas Configurações.");
        }
    }

    private void askOverlay() {
        toast("Permita \"Sobrepor a outros apps\" para o Booster.");
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
    }

    private void openMiuiPerms() {
        try {
            Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR");
            i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
            i.putExtra("extra_pkgname", getPackageName());
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    private static final String[] POS = {"Esquerda", "Centro", "Direita"};

    private void cyclePosition() {
        prefs.edit().putInt("pos", (prefs.getInt("pos", 0) + 1) % POS.length).apply();
        if (OverlayService.running) startForegroundService(new Intent(this, OverlayService.class));
    }

    private void toggleMonitor() {
        if (OverlayService.running) {
            stopService(new Intent(this, OverlayService.class));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            askOverlay();
            return;
        }
        prefs.edit().putBoolean("overlay", true).apply();
        swOverlay.setChecked(true);
        Monitor.startSession(null);
        startForegroundService(new Intent(this, OverlayService.class));
        toast("Monitor ligado. Abra o jogo.");
    }

    private void onUpdateClick() {
        if (pending == null) {
            checkUpdate(true);
            return;
        }
        if (!getPackageManager().canRequestPackageInstalls()) {
            toast("Permita \"Instalar apps desconhecidos\" para o Booster e toque de novo.");
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        final Updater.Info info = pending;
        btnUpdate.setEnabled(false);
        new Thread(() -> {
            try {
                Updater.install(this, info, p -> h.post(() -> tvUpdate.setText("Baixando v" + info.name + "… " + p + "%")));
                h.post(() -> {
                    tvUpdate.setText("Download concluído. Confirme a instalação na tela do Android.");
                    btnUpdate.setEnabled(true);
                });
            } catch (Exception e) {
                h.post(() -> {
                    tvUpdate.setText("Falha na atualização: " + e.getMessage());
                    btnUpdate.setEnabled(true);
                });
            }
        }).start();
    }

    private void checkUpdate(boolean manual) {
        if (manual) tvUpdate.setText("Verificando…");
        new Thread(() -> {
            try {
                Updater.Info i = Updater.check(this);
                prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply();
                h.post(() -> {
                    pending = i;
                    if (i == null) {
                        tvUpdate.setText("Versão instalada: " + Updater.installedName(this) + " (atualizado)");
                        btnUpdate.setText("Verificar atualizações");
                    } else {
                        tvUpdate.setText("Nova versão disponível: v" + i.name + "\n" + i.notes);
                        tvUpdate.setTextColor(OK);
                        btnUpdate.setText("Atualizar para v" + i.name);
                    }
                });
            } catch (Exception e) {
                if (manual) h.post(() -> tvUpdate.setText("Não consegui verificar: " + e.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Monitor.acquire(this);
        reportsVer = -1;
        ptTick = 0;
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
        Monitor.release();
    }

    private void refresh() {
        Monitor.Sample m = Monitor.latest;
        if (reportsVer != Reports.version || reportsVer < 0) {
            reportsVer = Reports.version;
            refreshGames();
            refreshReports();
        }
        tvLast.setText(prefs.getString("last", "Nenhuma sessão ainda."));
        if (ptTick++ % 60 == 0) loadPlayTime();
        updateUsage();
        updateShizuku();
        updateAdb();
        updateChrome();
        if (m != null) tvPreview.setText(OverlayText.build(m, prefs));
        if (m == null) return;
        SystemInfo s = m.sys;
        Locale us = Locale.US;
        gauge.set(s.ramUsedPct(), SystemInfo.gb(s.ramTotal - s.ramAvail) + " / " + SystemInfo.gb(s.ramTotal) + " GB");

        boolean adv = Shell.granted();
        int target = OverlayText.fpsTarget(prefs, m);
        tvFps.setText((m.fps >= 0 ? String.valueOf(m.fps) : "--") + " / " + target);
        tvFps.setTextColor(OverlayText.fpsColor(m.fps, target));
        tvFpsTarget.setText("Tela: " + m.hz + " Hz");
        tvLow.setText(Float.isNaN(m.low1) ? "--" : String.valueOf(Math.round(m.low1)));
        tvCpu.setText(Float.isNaN(m.cpu) ? "n/d" : String.format(us, "%.1f°", m.cpu));
        tvCpu.setTextColor(Float.isNaN(m.cpu) ? TXT : m.cpu >= 50 ? BAD : m.cpu >= 43 ? WARN : OK);
        tvGpu.setText(Float.isNaN(m.gpu) ? "n/d" : String.format(us, "%.1f°", m.gpu));
        tvBat.setText(s.battPct + "%" + (s.charging ? " +" : ""));
        tvFree.setText(SystemInfo.gb(s.storageFree) + " GB");
        tvFree.setTextColor(s.storageFree * 100 / Math.max(1, s.storageTotal) < 15 ? BAD : TXT);

        gFps.set(Monitor.series(0));
        gCpu.set(Monitor.series(1));

        tvSwap.setText("Memória estendida (zram/swap): " + SystemInfo.gb(s.swapTotal - s.swapFree) + " / "
                + SystemInfo.gb(s.swapTotal) + " GB");
        String hr = Float.isNaN(s.headroom) ? "" : String.format(us, " · margem térmica %.0f%%", s.headroom * 100);
        tvThermal.setText("Estado térmico: " + SystemInfo.thermalName(s.thermalStatus) + " · bateria "
                + String.format(us, "%.1f°C", s.tempC) + hr
                + (m.cpuSrc.isEmpty() ? "" : "\nSensor da CPU: " + m.cpuSrc));
        tvTip.setText(tip(m, adv));
    }

    private void cycleFpsTarget() {
        int[] rates = Monitor.supportedRates(this);
        int cur = prefs.getInt("fps_target", 0);
        int next = 0;
        if (cur == 0) next = rates[0];
        else for (int i = 0; i < rates.length; i++) if (rates[i] == cur) next = i + 1 < rates.length ? rates[i + 1] : 0;
        prefs.edit().putInt("fps_target", next).apply();
        updateChrome();
    }

    private void updateChrome() {
        tvLevel.setText("Nível da limpeza: " + Booster.LEVEL_NAMES[Math.max(0, Math.min(2, prefs.getInt("clean_level", 1)))]
                + (Shell.granted() ? "" : " (precisa do modo avançado)"));
        int kept = Booster.userKeep(this).size();
        tvKeep.setText("Apps protegidos: " + (kept == 0 ? "nenhum escolhido" : kept + " escolhidos"));
        int hl = prefs.getInt("heat_alert", 46);
        tvHeat.setText("Alerta de calor: " + (hl == 0 ? "desligado" : "CPU acima de " + hl + "°C"));
        String[] sz = {"pequena", "média", "grande"}, al = {"bem transparente", "transparente", "opaca"};
        tvBubble.setText("Bolha: " + sz[prefs.getInt("bubble_size", 1)] + ", " + al[prefs.getInt("bubble_alpha", 1)]);
        int ap = prefs.getInt("auto_clean_pct", 0);
        tvAutoClean.setText("Limpar sozinho no jogo: " + (ap == 0 ? "desligado" : "RAM acima de " + ap + "%"));
        int ft = prefs.getInt("fps_target", 0);
        tvFpsTarget2.setText("Taxa alvo do FPS: " + (ft == 0 ? "automática (" + Monitor.refreshRate(this) + " Hz)" : ft + " Hz"));
        StringBuilder rs = new StringBuilder("Taxas da tela: ");
        int[] all = Monitor.supportedRates(this);
        for (int i = 0; i < all.length; i++) rs.append(i > 0 ? ", " : "").append(all[i]).append(" Hz");
        tvRates.setText(rs);
        btnMonitor.setText(OverlayService.running ? "Parar monitor" : "Monitor flutuante");
        withIcon(btnMonitor, OverlayService.running ? R.drawable.ic_stop : R.drawable.ic_overlay, TXT);
        tvPos.setText("Posição do monitor: " + POS[prefs.getInt("pos", 0) % POS.length]);
        boolean ov = Settings.canDrawOverlays(this);
        tvOverlayPerm.setText("Sobrepor a outros apps: " + (ov ? "permitido" : "negado, toque para permitir"));
        boolean ok = Shell.granted();
        tvDot.setText(Adb.connected() ? "ADB ativo" : ok ? "Shizuku ativo" : "Modo avançado off");
        tvDot.setTextColor(ok ? OK : WARN);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(ok ? OK : WARN);
        dot.setBounds(0, 0, (int) dp(8), (int) dp(8));
        tvDot.setCompoundDrawablesRelative(dot, null, null, null);
    }

    private String tip(Monitor.Sample m, boolean adv) {
        SystemInfo s = m.sys;
        StringBuilder t = new StringBuilder();
        boolean hot = (!Float.isNaN(m.cpu) && m.cpu >= 48) || s.tempC >= 43 || s.thermalStatus >= 2;
        if (hot) t.append("Celular quente: o desempenho cai (throttling). Tire a capa, pare de carregar e espere esfriar.\n");
        else if ((!Float.isNaN(m.cpu) && m.cpu >= 43) || s.tempC >= 40) t.append("Esquentando. Evite jogar carregando.\n");
        if (s.charging && s.tempC >= 38) t.append("Carregando enquanto esquenta: desconecte o cabo para jogar.\n");
        if (s.battPct < 20 && !s.charging) t.append("Bateria baixa reduz o desempenho. Carregue antes.\n");
        if (s.ramUsedPct() >= 85) t.append("RAM muito cheia: toque em \"Liberar RAM agora\".\n");
        if (s.storageFree * 100 / Math.max(1, s.storageTotal) < 15) t.append("Menos de 15% de armazenamento livre.\n");
        if (s.swapTotal == 0) t.append("Ative a Memória estendida (3 GB) nas configurações.\n");
        if (m.fps >= 0 && m.fps < 45 && !hot) t.append("FPS baixo sem calor: reduza efeitos/sombras no jogo ou use Liberar RAM.\n");
        if (m.fps >= 0 && !Float.isNaN(m.low1) && m.low1 < m.fps * 0.6f) t.append("Muita oscilação (1% low baixo): prefira 60 FPS fixo e efeitos menores.\n");
        if (!adv) t.append("Ative o modo avançado para medir FPS e temperatura de CPU/GPU.\n");
        if (t.length() == 0) t.append("Tudo certo para jogar no gráfico alto estável.");
        return t.toString().trim();
    }

    private void adbAction() {
        if (Adb.connected()) {
            toast("Já conectado.");
        } else if (Adb.paired(this)) {
            Adb.connectAsync(this, true);
            toast("Conectando…");
        } else {
            Adb.startPairing(this);
            toast("Abra \"Parear dispositivo com código\" e digite o código na notificação.");
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
            }
        }
    }

    private void updateAdb() {
        boolean c = Adb.connected();
        tvAdb.setText(c ? "Conectado: FPS, CPU/GPU, Game Mode e limpeza profunda liberados." : Adb.status());
        btnAdb.setText(c ? "Conectado" : Adb.paired(this) ? "Conectar" : "Iniciar pareamento");
        btnAdb.setEnabled(!c);
        btnForget.setVisibility(Adb.paired(this) ? View.VISIBLE : View.GONE);
    }

    private void updateShizuku() {
        boolean inst = Shell.installed(this), run = Shell.running(), ok = Shell.granted();
        if (ok) {
            tvShizuku.setText("Ativo: FPS, CPU/GPU, Game Mode e limpeza profunda liberados.");
            btnShizuku.setVisibility(View.GONE);
            return;
        }
        btnShizuku.setVisibility(View.VISIBLE);
        if (!inst) {
            tvShizuku.setText("1) Instale o Shizuku (gratuito).\n2) Ative a Depuração sem fio e inicie o Shizuku por ela (sem PC e sem root).\n3) Volte aqui e conceda a permissão.");
            btnShizuku.setText("Baixar Shizuku");
        } else if (!run) {
            tvShizuku.setText("Shizuku instalado, mas parado. Abra-o e toque em \"Iniciar\" via Depuração sem fio (precisa refazer após reiniciar o celular).");
            btnShizuku.setText("Abrir Shizuku");
        } else {
            tvShizuku.setText("Shizuku rodando. Falta conceder permissão a este app.");
            btnShizuku.setText("Conceder permissão");
        }
    }

    private void shizukuAction() {
        if (!Shell.installed(this)) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")));
        } else if (!Shell.running()) {
            Intent i = getPackageManager().getLaunchIntentForPackage(Shell.SHIZUKU_PKG);
            if (i != null) startActivity(i);
        } else {
            Shell.request(100);
        }
    }

    private void boostOnly() {
        btnBoost.setEnabled(false);
        tvResult.setText("Liberando…");
        final int lvl = Booster.level(this);
        new Thread(() -> {
            Booster.Result r = Booster.boost(this, lvl, null);
            h.post(() -> {
                btnBoost.setEnabled(true);
                showResult(r);
            });
        }).start();
    }

    private void showResult(Booster.Result r) {
        long mb = r.freedBytes / 1048576;
        tvResult.setText((r.targeted + r.forced) + " apps encerrados · "
                + (mb > 0 ? "+" + mb + " MB livres" : "sem ganho (já estava limpo)"));
    }

    private void play(final String pkg) {
        if (prefs.getBoolean("overlay", true) && !Settings.canDrawOverlays(this)) {
            askOverlay();
            return;
        }
        final Intent game = Booster.launchIntent(this, pkg);
        if (game == null) {
            toast("Jogo não encontrado neste celular.");
            return;
        }
        prefs.edit().putString("last_game", pkg).apply();
        btnPlay.setEnabled(false);
        tvResult.setText("Otimizando…");
        final int lvl = Profiles.level(this, pkg);
        final boolean dnd = Profiles.rawDnd(this, pkg) >= 0 ? Profiles.dnd(this, pkg) : swDnd.isChecked();
        new Thread(() -> {
            Booster.Result r = Booster.boost(this, lvl, pkg);
            Booster.gameModePerformance(pkg);
            h.post(() -> {
                btnPlay.setEnabled(true);
                showResult(r);
                if (dnd) Booster.enableDnd(this);
                if (Monitor.sessionActive()) Monitor.endSession();
                Monitor.startSession(pkg);
                startForegroundService(new Intent(this, OverlayService.class));
                startActivity(game);
                updatePlay();
            });
        }).start();
    }

    private boolean dndAllowed() {
        return Booster.dndAllowed(this);
    }

    private void endSession() {
        if (!prefs.getBoolean("auto", true)) {
            startService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_STOP));
        }
        Booster.restoreDnd(this);
        String sum = Monitor.endSession();
        if (sum != null) tvLast.setText(sum);
        tvResult.setText("Sessão encerrada. Deixe o celular esfriar antes da próxima partida.");
    }

    private String lastGame() {
        String g = prefs.getString("last_game", null);
        if (g != null && Booster.launchIntent(this, g) != null) return g;
        List<Games.Game> l = Games.list(this);
        return l.isEmpty() ? null : l.get(0).pkg;
    }

    private void updatePlay() {
        String g = lastGame();
        btnPlay.setText(g == null ? "Escolher jogo" : "Jogar " + Games.label(this, g));
    }

    private void refreshGames() {
        gamesList.removeAllViews();
        List<Games.Game> gl = Games.list(this);
        java.util.Map<String, Reports.Agg> agg = new java.util.HashMap<>();
        for (Reports.Agg a : Reports.byGame(this)) agg.put(a.pkg, a);
        if (gl.isEmpty()) {
            gamesList.addView(text("Nenhum jogo encontrado. Toque em Adicionar jogo.", 13, MUT, false));
        }
        for (Games.Game g : gl) gamesList.addView(gameRow(g, agg.get(g.pkg)));
        updatePlay();
    }

    private View gameRow(final Games.Game g, Reports.Agg a) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        try {
            ic.setImageDrawable(getPackageManager().getApplicationIcon(g.pkg));
        } catch (Exception ignored) {
        }
        row.addView(ic, new LinearLayout.LayoutParams((int) dp(44), (int) dp(44)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding((int) dp(12), 0, (int) dp(8), 0);
        info.addView(text(g.label, 15, TXT, true));
        String sub = a == null ? "Sem sessões ainda" : a.sessions + (a.sessions == 1 ? " sessão · " : " sessões · ")
                + Reports.duration(a.totalSec) + (a.avgFps > 0 ? " · " + a.avgFps + " FPS" : "");
        info.addView(text(sub, 12, MUT, false));
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView go = new ImageView(this);
        go.setImageDrawable(icon(R.drawable.ic_play, 0xFF04101F));
        go.setBackground(round(0xFF4DA3FF, 12));
        int p = (int) dp(10);
        go.setPadding(p, p, p, p);
        go.setOnClickListener(v -> play(g.pkg));
        row.addView(go, new LinearLayout.LayoutParams((int) dp(44), (int) dp(44)));
        row.setOnClickListener(v -> showGameOptions(g));
        return row;
    }

    private android.app.AlertDialog.Builder dialog() {
        return new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
    }

    private int gameTarget(String pkg) {
        return prefs.getInt("gt_" + pkg, 0);
    }

    private void showGameOptions(final Games.Game g) {
        int t = gameTarget(g.pkg);
        String[] items = {"Jogar", "Taxa alvo de FPS: " + (t == 0 ? "padrão" : t + " Hz"), "Perfil do jogo", "Ver relatório", "Remover da lista"};
        dialog().setTitle(g.label).setItems(items, (d, which) -> {
            if (which == 0) {
                play(g.pkg);
            } else if (which == 1) {
                int[] rates = Monitor.supportedRates(this);
                int next = 0;
                if (t == 0) next = rates[0];
                else for (int i = 0; i < rates.length; i++) if (rates[i] == t) next = i + 1 < rates.length ? rates[i + 1] : 0;
                prefs.edit().putInt("gt_" + g.pkg, next).apply();
                toast(next == 0 ? "Taxa alvo: padrão" : "Taxa alvo: " + next + " Hz");
            } else if (which == 2) {
                showProfile(g);
            } else if (which == 3) {
                showReportDialog(g.pkg, g.label);
            } else {
                Games.remove(this, g.pkg);
                refreshGames();
            }
        }).setNegativeButton("Fechar", null).show();
    }

    private String levelLabel(int v) {
        return v < 0 ? "geral" : Booster.LEVEL_NAMES[v];
    }

    private String onOff(int v) {
        return v < 0 ? "geral" : v == 1 ? "ligado" : "desligado";
    }

    // Configuração própria de cada jogo; "geral" usa o que está em Configurações.
    private void showProfile(final Games.Game g) {
        String[] items = {"Nível da limpeza: " + levelLabel(Profiles.rawLevel(this, g.pkg)),
                "Limpar RAM ao abrir: " + onOff(Profiles.rawClean(this, g.pkg)),
                "Não perturbe: " + onOff(Profiles.rawDnd(this, g.pkg))};
        dialog().setTitle("Perfil: " + g.label).setItems(items, (d, which) -> {
            if (which == 0) Profiles.setLevel(this, g.pkg, Profiles.next(Profiles.rawLevel(this, g.pkg), 2));
            else if (which == 1) Profiles.setClean(this, g.pkg, Profiles.next(Profiles.rawClean(this, g.pkg), 1));
            else Profiles.setDnd(this, g.pkg, Profiles.next(Profiles.rawDnd(this, g.pkg), 1));
            showProfile(g);
        }).setNegativeButton("Fechar", null).show();
    }

    private void pickGames() {
        final List<Games.Game> cands = Games.candidates(this);
        String[] names = new String[cands.size()];
        for (int i = 0; i < names.length; i++) names[i] = cands.get(i).label;
        final boolean[] on = new boolean[names.length];
        dialog().setTitle("Adicionar jogos").setMultiChoiceItems(names, on, (d, i, c) -> on[i] = c)
                .setPositiveButton("Adicionar", (d, w) -> {
                    for (int i = 0; i < on.length; i++) if (on[i]) Games.add(this, cands.get(i).pkg);
                    refreshGames();
                }).setNegativeButton("Cancelar", null).show();
    }

    private int gradeColor(int score) {
        return score < 0 ? MUT : score >= 80 ? OK : score >= 65 ? 0xFF4DA3FF : score >= 50 ? WARN : BAD;
    }

    private void refreshReports() {
        reportsList.removeAllViews();
        List<Reports.Agg> ags = Reports.byGame(this);
        if (ags.isEmpty()) {
            reportsList.addView(text("Nenhuma sessão registrada ainda. Jogue com o Booster ativo (detecção automática ou botão Jogar) e o relatório aparece aqui ao sair do jogo.", 13, MUT, false));
            return;
        }
        int sessions = 0;
        long total = 0;
        for (Reports.Agg a : ags) {
            sessions += a.sessions;
            total += a.totalSec;
        }
        LinearLayout sum = card();
        sum.addView(text("Visão geral", 15, TXT, true));
        sum.addView(text(sessions + " sessões em " + ags.size() + (ags.size() == 1 ? " jogo" : " jogos") + " · "
                + Reports.duration(total) + " jogados", 13, MUT, false));
        reportsList.addView(sum);
        for (final Reports.Agg a : ags) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView gr = text(Reports.grade(a.avgScore), 22, gradeColor(a.avgScore), true);
            gr.setGravity(Gravity.CENTER);
            GradientDrawable gb = round(0xFF0F1830, 12);
            gb.setStroke((int) dp(2), gradeColor(a.avgScore));
            gr.setBackground(gb);
            row.addView(gr, new LinearLayout.LayoutParams((int) dp(48), (int) dp(48)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding((int) dp(12), 0, 0, 0);
            info.addView(text(a.label, 15, TXT, true));
            info.addView(text(a.sessions + " sessões · " + Reports.duration(a.totalSec)
                    + (a.avgFps > 0 ? " · FPS médio " + a.avgFps : ""), 12, MUT, false));
            info.addView(text("Último jogo: " + Reports.when(a.lastPlayed), 12, MUT, false));
            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setOnClickListener(v -> showReportDialog(a.pkg, a.label));
            reportsList.addView(row);
        }
    }

    private float[] toFloats(int[] a, float div) {
        float[] f = new float[a.length];
        for (int i = 0; i < a.length; i++) f[i] = a[i] < 0 ? Float.NaN : a[i] / div;
        return f;
    }

    private void showReportDialog(final String pkg, String label) {
        final List<Reports.Record> rs = Reports.forGame(this, pkg);
        if (rs.isEmpty()) {
            toast("Nenhuma sessão registrada para " + label + ".");
            return;
        }
        Reports.Record r = rs.get(0);
        ScrollView sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int pd = (int) dp(16);
        l.setPadding(pd, pd, pd, pd);
        sv.addView(l);

        l.addView(text(r.label, 18, TXT, true));
        l.addView(text(Reports.when(r.start) + " · " + Reports.duration(r.durSec), 12, MUT, false));
        TextView gr = text(r.measured ? "Nota " + Reports.grade(r.score()) + "  ·  " + r.score() + "/100" : "Nota indisponível (FPS não medido)",
                r.measured ? 20 : 13, gradeColor(r.score()), true);
        gr.setPadding(0, (int) dp(8), 0, (int) dp(8));
        l.addView(gr);

        Locale us = Locale.US;
        StringBuilder st = new StringBuilder();
        if (r.measured) {
            st.append("FPS médio ").append(r.fpsAvg).append(" / ").append(r.target).append(" · mín ").append(r.fpsMin)
                    .append(" · máx ").append(r.fpsMax).append(" · 1% low ").append(r.low1).append('\n')
                    .append("Estabilidade ").append(r.stablePct).append("% · travadas ").append(r.stutters).append('\n');
        }
        st.append("CPU máx ").append(String.format(us, "%.0f", r.cpuMax)).append("°C (média ").append(String.format(us, "%.0f", r.cpuAvg))
                .append("°) · GPU máx ").append(String.format(us, "%.0f", r.gpuMax)).append("°C\n")
                .append("RAM máx ").append(r.ramMaxMb).append(" MB (média ").append(r.ramAvgPct).append("%)\n")
                .append("Bateria ").append(r.battStart).append("% → ").append(r.battEnd).append("% · limitação térmica ")
                .append(Reports.duration(r.throttleSec));
        l.addView(text(st.toString(), 13, TXT, false));

        if (r.measured) {
            GraphView g1 = new GraphView(this, "FPS durante a sessão", 0xFF4DA3FF, 0, Math.max(r.target, r.fpsMax));
            g1.set(toFloats(r.fpsSeries, 1));
            l.addView(g1, graphParams());
        }
        GraphView g2 = new GraphView(this, "Temperatura da CPU °C", 0xFFFF7A59, 30, 50);
        g2.set(toFloats(r.cpuSeries, 10));
        l.addView(g2, graphParams());

        String cmp = Reports.compare(rs);
        if (cmp != null) {
            TextView ct = text(cmp, 12, 0xFF4DA3FF, false);
            ct.setPadding(0, (int) dp(8), 0, 0);
            l.addView(ct);
        }
        if (rs.size() >= 3) {
            int n = Math.min(15, rs.size());
            float[] sc = new float[n];
            for (int i = 0; i < n; i++) {
                Reports.Record x = rs.get(n - 1 - i);
                sc[i] = x.measured ? x.score() : Float.NaN;
            }
            GraphView g3 = new GraphView(this, "Evolução da nota (últimas " + n + " sessões)", 0xFF3DDC97, 0, 100);
            g3.set(sc);
            l.addView(g3, graphParams());
        }

        TextView it = text("Análise", 14, TXT, true);
        it.setPadding(0, (int) dp(12), 0, (int) dp(2));
        l.addView(it);
        for (String s : Reports.insights(r)) l.addView(text("• " + s, 13, MUT, false));

        TextView hs = text("Histórico", 14, TXT, true);
        hs.setPadding(0, (int) dp(12), 0, (int) dp(2));
        l.addView(hs);
        for (int i = 0; i < Math.min(10, rs.size()); i++) {
            Reports.Record x = rs.get(i);
            l.addView(text(Reports.when(x.start) + " · " + Reports.duration(x.durSec) + (x.measured
                    ? " · " + x.fpsAvg + " FPS · nota " + Reports.grade(x.score()) : " · sem FPS"), 12, MUT, false));
        }

        final android.app.AlertDialog d = dialog().setView(sv).setPositiveButton("Fechar", null).create();
        d.getWindow().setBackgroundDrawable(round(BG, 18));
        Button share = button("Compartilhar relatório", 0xFF1D2A4A, TXT);
        withIcon(share, R.drawable.ic_share, TXT);
        share.setTextSize(14);
        share.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Reports.text(r));
            startActivity(Intent.createChooser(i, "Compartilhar relatório"));
        });
        l.addView(share, btnParams(44));
        Button img = button("Compartilhar como imagem", 0xFF1D2A4A, TXT);
        withIcon(img, R.drawable.ic_share, TXT);
        img.setTextSize(14);
        img.setOnClickListener(v -> {
            try {
                ReportImage.share(this, r);
            } catch (Exception e) {
                toast("Não foi possível gerar a imagem.");
            }
        });
        l.addView(img, btnParams(44));
        Button del = button("Apagar histórico deste jogo", 0xFF3A1D24, BAD);
        withIcon(del, R.drawable.ic_delete, BAD);
        del.setTextSize(14);
        del.setOnClickListener(v -> dialog().setMessage("Apagar todas as sessões de " + label + "?")
                .setPositiveButton("Apagar", (x, w) -> {
                    Reports.clear(this, pkg);
                    d.dismiss();
                }).setNegativeButton("Cancelar", null).show());
        l.addView(del, btnParams(44));
        d.show();
    }

    private TextView section(String s) {
        TextView t = text(s, 11, MUT, true);
        t.setLetterSpacing(0.12f);
        t.setPadding((int) dp(2), (int) dp(14), 0, (int) dp(2));
        return t;
    }

    private void pickLevel() {
        String[] names = {"Leve: só apps em segundo plano", "Equilibrada: fecha apps de terceiros, mantém mensagens",
                "Máxima: fecha tudo que não é essencial e limpa caches"};
        dialog().setTitle("Nível da limpeza de RAM").setSingleChoiceItems(names, prefs.getInt("clean_level", 1), (d, w) -> {
            prefs.edit().putInt("clean_level", w).apply();
            if (w > 0 && !Shell.granted()) toast("Os níveis Equilibrada e Máxima precisam do modo avançado conectado.");
            d.dismiss();
        }).setNegativeButton("Fechar", null).show();
    }

    private void pickKeep() {
        pickApps("keep_apps", "Apps que nunca serão fechados", Booster.userKeep(this));
    }

    private void pickApps(String key, String title, java.util.Collection<String> current) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<String> pk = new ArrayList<>(), nm = new ArrayList<>();
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            pk.add(ri.activityInfo.packageName);
            nm.add(String.valueOf(ri.loadLabel(pm)));
        }
        Integer[] ord = new Integer[pk.size()];
        for (int i = 0; i < ord.length; i++) ord[i] = i;
        java.util.Arrays.sort(ord, (a, b) -> nm.get(a).compareToIgnoreCase(nm.get(b)));
        final java.util.Set<String> keep = new java.util.HashSet<>(current);
        final String[] names = new String[ord.length];
        final boolean[] on = new boolean[ord.length];
        for (int i = 0; i < ord.length; i++) {
            names[i] = nm.get(ord[i]);
            on[i] = keep.contains(pk.get(ord[i]));
        }
        dialog().setTitle(title).setMultiChoiceItems(names, on, (d, i, c) -> on[i] = c)
                .setPositiveButton("Salvar", (d, w) -> {
                    java.util.Set<String> out = new java.util.HashSet<>();
                    for (int i = 0; i < on.length; i++) if (on[i]) out.add(pk.get(ord[i]));
                    prefs.edit().putStringSet(key, out).apply();
                }).setNegativeButton("Cancelar", null).show();
    }

    private static final int[] AUTO_PCT = {0, 75, 80, 85, 90};

    private static final int[] HEAT = {0, 42, 44, 46, 48, 50};

    private void cycleHeat() {
        int cur = prefs.getInt("heat_alert", 46), next = HEAT[0];
        for (int i = 0; i < HEAT.length; i++) if (HEAT[i] == cur) next = HEAT[(i + 1) % HEAT.length];
        prefs.edit().putInt("heat_alert", next).apply();
        updateChrome();
    }

    private void cycleBubble() {
        int s = prefs.getInt("bubble_size", 1), a = prefs.getInt("bubble_alpha", 1);
        // percorre as 9 combinações de tamanho e transparência
        int k = (s * 3 + a + 1) % 9;
        prefs.edit().putInt("bubble_size", k / 3).putInt("bubble_alpha", k % 3).apply();
        updateChrome();
    }

    private void cycleAutoClean() {
        int cur = prefs.getInt("auto_clean_pct", 0), next = AUTO_PCT[0];
        for (int i = 0; i < AUTO_PCT.length; i++) if (AUTO_PCT[i] == cur) next = AUTO_PCT[(i + 1) % AUTO_PCT.length];
        prefs.edit().putInt("auto_clean_pct", next).apply();
    }

    private void loadPlayTime() {
        new Thread(() -> {
            PlayTime.update(this);
            final java.util.Map<String, java.util.Map<String, Long>> all = PlayTime.load(this);
            h.post(() -> fillPlayTime(all));
        }).start();
    }

    private String[] dayKeys(int n) {
        String[] k = new String[n];
        java.util.Calendar c = java.util.Calendar.getInstance();
        for (int i = n - 1; i >= 0; i--) {
            k[i] = PlayTime.key(c.getTimeInMillis());
            c.add(java.util.Calendar.DAY_OF_MONTH, -1);
        }
        return k;
    }

    private void fillPlayTime(java.util.Map<String, java.util.Map<String, Long>> all) {
        boolean perm = Games.hasUsageAccess(this);
        ptPerm.setVisibility(perm ? View.GONE : View.VISIBLE);
        String[] k30 = dayKeys(30), k14 = dayKeys(14);
        long today = PlayTime.dayTotal(all, k30[29]), week = 0, month = 0, best = 0;
        String bestDay = null;
        for (int i = 0; i < 30; i++) {
            long t = PlayTime.dayTotal(all, k30[i]);
            month += t;
            if (i >= 23) week += t;
            if (t > best) {
                best = t;
                bestDay = k30[i];
            }
        }
        tvPtToday.setText(PlayTime.fmt(today));
        tvPtWeek.setText(PlayTime.fmt(week));
        tvPtMonth.setText(PlayTime.fmt(month));

        float[] vals = new float[14];
        String[] top = new String[14], bot = new String[14];
        for (int i = 0; i < 14; i++) {
            long t = PlayTime.dayTotal(all, k14[i]);
            vals[i] = t / 3600000f;
            top[i] = PlayTime.fmtShort(t);
            bot[i] = k14[i].substring(8);
        }
        ptBars.set(vals, top, bot);

        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("EEE dd/MM", new Locale("pt", "BR"));
        ptSummary.setText("Média diária (30 dias): " + PlayTime.fmt(month / 30) + (bestDay == null ? ""
                : "\nDia mais jogado: " + bestDay.substring(8) + "/" + bestDay.substring(5, 7) + " (" + PlayTime.fmt(best) + ")"));

        ptDays.removeAllViews();
        for (int i = 29; i >= 23; i--) {
            long t = PlayTime.dayTotal(all, k30[i]);
            String top1 = null;
            long tv = 0;
            java.util.Map<String, Long> m = all.get(k30[i]);
            if (m != null) for (java.util.Map.Entry<String, Long> e : m.entrySet()) if (e.getValue() > tv) {
                tv = e.getValue();
                top1 = e.getKey();
            }
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int) dp(7), 0, (int) dp(7));
            String label;
            try {
                label = df.format(new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(k30[i]));
            } catch (Exception ex) {
                label = k30[i];
            }
            TextView a = text(label + (i == 29 ? " (hoje)" : ""), 13, TXT, false);
            row.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView b = text(t == 0 ? "-" : PlayTime.fmt(t) + (top1 == null ? "" : "  ·  " + Games.label(this, top1)), 12, t == 0 ? MUT : OK, false);
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.setMaxWidth((int) dp(190));
            row.addView(b);
            ptDays.addView(row);
        }

        java.util.Map<String, long[]> per = new java.util.HashMap<>();
        for (int i = 0; i < 30; i++) {
            java.util.Map<String, Long> m = all.get(k30[i]);
            if (m == null) continue;
            for (java.util.Map.Entry<String, Long> e : m.entrySet()) {
                long[] s = per.get(e.getKey());
                if (s == null) per.put(e.getKey(), s = new long[3]);
                s[2] += e.getValue();
                if (i >= 23) s[1] += e.getValue();
                if (i == 29) s[0] += e.getValue();
            }
        }
        final List<java.util.Map.Entry<String, long[]>> list = new ArrayList<>(per.entrySet());
        java.util.Collections.sort(list, (x, y) -> Long.compare(y.getValue()[2], x.getValue()[2]));
        ptGames.removeAllViews();
        if (list.isEmpty()) {
            ptGames.addView(text(perm ? "Ainda sem registros. O Booster lê o histórico do Android, então jogue um pouco e volte aqui."
                    : "Conceda o acesso ao uso de apps para ver o tempo de jogo.", 13, MUT, false));
        }
        long top30 = list.isEmpty() ? 1 : Math.max(1, list.get(0).getValue()[2]);
        for (java.util.Map.Entry<String, long[]> e : list) {
            LinearLayout row = card();
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView ic = new ImageView(this);
            try {
                ic.setImageDrawable(getPackageManager().getApplicationIcon(e.getKey()));
            } catch (Exception ignored) {
            }
            row.addView(ic, new LinearLayout.LayoutParams((int) dp(40), (int) dp(40)));
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding((int) dp(12), 0, 0, 0);
            info.addView(text(Games.label(this, e.getKey()), 14, TXT, true));
            long[] s = e.getValue();
            info.addView(text("Hoje " + PlayTime.fmt(s[0]) + "  ·  7 dias " + PlayTime.fmt(s[1]) + "  ·  30 dias " + PlayTime.fmt(s[2]), 12, MUT, false));
            LinearLayout track = new LinearLayout(this);
            track.setBackground(round(0xFF1B2745, 3));
            View fillBar = new View(this);
            fillBar.setBackground(round(0xFF4DA3FF, 3));
            track.addView(fillBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0.02f, s[2] / (float) top30)));
            track.addView(new View(this), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0f, 1f - s[2] / (float) top30)));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(6));
            tp.setMargins(0, (int) dp(8), 0, 0);
            info.addView(track, tp);
            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            ptGames.addView(row);
        }
        tvHomeToday.setText("Jogou hoje: " + PlayTime.fmt(today) + "  ·  7 dias: " + PlayTime.fmt(week));
    }

    private void updateUsage() {
        tvUsage.setText("Acesso ao uso de apps: " + (Games.hasUsageAccess(this) ? "permitido" : "negado, toque para permitir"));
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private Switch sw(LinearLayout parent, String label, String key, boolean def) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(TXT);
        s.setPadding(0, (int) dp(6), 0, (int) dp(6));
        s.setChecked(prefs.getBoolean(key, def));
        parent.addView(s);
        return s;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        t.setPadding(0, (int) dp(4), 0, (int) dp(4));
        return t;
    }

    private TextView stat(LinearLayout row, String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, 14));
        int p = (int) dp(10);
        c.setPadding(p, p, p, p);
        TextView l = text(label, 11, MUT, false);
        TextView v = text("--", 19, TXT, true);
        c.addView(l);
        c.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins((int) dp(3), 0, (int) dp(3), 0);
        row.addView(c, lp);
        return v;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cg = round(CARD, 14);
        cg.setStroke((int) dp(1), 0xFF1F2B48);
        c.setBackground(cg);
        int p = (int) dp(14);
        c.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int) dp(8), 0, (int) dp(4));
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout.LayoutParams graphParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(110));
        lp.setMargins(0, (int) dp(8), 0, 0);
        return lp;
    }

    private Button button(String s, int bg, int fg) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(fg);
        b.setBackground(round(bg, 14));
        return b;
    }

    private LinearLayout.LayoutParams btnParams(int heightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(heightDp));
        lp.setMargins(0, (int) dp(8), 0, 0);
        return lp;
    }

    private View shortcut(String label, String action) {
        TextView t = text(label, 15, TXT, false);
        t.setPadding(0, (int) dp(10), 0, (int) dp(10));
        t.setOnClickListener(v -> {
            try {
                startActivity(new Intent(action));
            } catch (Exception e) {
                toast("Abra manualmente pelas Configurações.");
            }
        });
        return t;
    }

    private android.graphics.drawable.Drawable icon(int res, int color) {
        android.graphics.drawable.Drawable d = getDrawable(res).mutate();
        d.setTint(color);
        int sz = (int) dp(22);
        d.setBounds(0, 0, sz, sz);
        return d;
    }

    private void withIcon(Button b, int res, int color) {
        b.setCompoundDrawablesRelative(icon(res, color), null, null, null);
        b.setCompoundDrawablePadding((int) dp(10));
        b.setPadding((int) dp(20), 0, (int) dp(20), 0);
    }

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }
}
