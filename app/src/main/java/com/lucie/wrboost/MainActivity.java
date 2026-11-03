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
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.animation.ValueAnimator;
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
    private static int BG, CARD, TXT, MUT;
    public static final String EXTRA_INSTALL = "install";
    private static int resumePage = -1, resumeCat = -1;
    private int curCat = -1;
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
    private TextView tvUpdate, tvTheme, tvAccent, tvDiag;
    private FrameLayout rootView;
    private int curPage;
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
        Theme.load(this);
        BG = Theme.BG;
        CARD = Theme.CARD;
        TXT = Theme.TXT;
        MUT = Theme.MUT;
        if (Theme.light) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
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
        btnPlay = button("Jogar", Theme.ACC, Theme.ONACC);
        withIcon(btnPlay, R.drawable.ic_play, Theme.ONACC);
        btnPlay.setOnClickListener(v -> {
            String g = lastGame();
            if (g == null) showPage(1);
            else play(g);
        });
        home.addView(btnPlay, btnParams(56));
        LinearLayout acts = new LinearLayout(this);
        btnBoost = button("Liberar RAM", Theme.BTN2, TXT);
        withIcon(btnBoost, R.drawable.ic_memory, TXT);
        btnBoost.setTextSize(14);
        btnBoost.setOnClickListener(v -> boostOnly());
        btnMonitor = button("", Theme.BTN2, TXT);
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
        Button addGame = button("Adicionar jogo", Theme.BTN2, TXT);
        withIcon(addGame, R.drawable.ic_add, TXT);
        addGame.setTextSize(14);
        addGame.setOnClickListener(v -> pickGames());
        gm.addView(addGame, btnParams(46));
        gamesList = new LinearLayout(this);
        gamesList.setOrientation(LinearLayout.VERTICAL);
        gm.addView(gamesList);

        // Relatórios
        LinearLayout rp = page("Relatórios", R.drawable.ic_chart);
        LinearLayout exp = new LinearLayout(this);
        Button csv = button("Exportar CSV", Theme.BTN2, TXT);
        withIcon(csv, R.drawable.ic_share, TXT);
        csv.setTextSize(13);
        csv.setOnClickListener(v -> export(false));
        Button pdf = button("Exportar PDF", Theme.BTN2, TXT);
        withIcon(pdf, R.drawable.ic_share, TXT);
        pdf.setTextSize(13);
        pdf.setOnClickListener(v -> export(true));
        LinearLayout.LayoutParams e1 = new LinearLayout.LayoutParams(0, (int) dp(44), 1);
        e1.setMargins(0, 0, (int) dp(4), 0);
        LinearLayout.LayoutParams e2 = new LinearLayout.LayoutParams(0, (int) dp(44), 1);
        e2.setMargins((int) dp(4), 0, 0, 0);
        exp.addView(csv, e1);
        exp.addView(pdf, e2);
        rp.addView(exp);
        reportsList = new LinearLayout(this);
        reportsList.setOrientation(LinearLayout.VERTICAL);
        rp.addView(reportsList);

        // Tempo de jogo
        LinearLayout pt = page("Tempo de jogo", R.drawable.ic_calendar);
        ptPerm = card();
        ptPerm.addView(text("O tempo de jogo vem do histórico de uso do Android, então conta mesmo quando você abre o jogo fora do Booster. Para isso, permita o acesso ao uso de apps.", 13, MUT, false));
        Button grant = button("Permitir acesso ao uso", Theme.ACC, Theme.ONACC);
        grant.setTextSize(14);
        grant.setOnClickListener(v -> usageAccess());
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
        gFps = new GraphView(this, "FPS (últimos 2 min)", Theme.ACC, 0, 60);
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
        btnAdb = button("", Theme.ACC, Theme.ONACC);
        btnAdb.setTextSize(14);
        btnAdb.setOnClickListener(v -> adbAction());
        adbCard.addView(btnAdb, btnParams(46));
        btnForget = button("Esquecer pareamento", Theme.BTN2, TXT);
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
        btnShizuku = button("", Theme.BTN2, TXT);
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
        btnUpdate = button("Verificar atualizações", Theme.BTN2, TXT);
        btnUpdate.setTextSize(14);
        btnUpdate.setOnClickListener(v -> onUpdateClick());
        upCard.addView(btnUpdate, btnParams(46));
        up.addView(upCard);
        LinearLayout upOpt = card();
        sw(upOpt, "Baixar atualizações sozinho (só no Wi-Fi)", "auto_update", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("auto_update", on).apply());
        sw(upOpt, "Instalar sozinho, sem confirmação (modo avançado, fora do jogo)", "auto_install", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("auto_install", on).apply());
        up.addView(upOpt);
        up.addView(text("Quando houver versão nova, o Booster baixa e confere a integridade em segundo plano e avisa por notificação. Você só confirma a instalação.", 12, MUT, false));
        Button hist = button("Histórico de versões", Theme.BTN2, TXT);
        withIcon(hist, R.drawable.ic_info, TXT);
        hist.setTextSize(14);
        hist.setOnClickListener(v -> showHistory());
        up.addView(hist, btnParams(46));

        // Sessão
        LinearLayout ses = page("Sessão", R.drawable.ic_timer);
        LinearLayout last = card();
        last.addView(text("Resumo da última sessão", 15, TXT, true));
        tvLast = text(prefs.getString("last", "Nenhuma sessão ainda."), 13, MUT, false);
        last.addView(tvLast);
        ses.addView(last);
        Button end = button("Encerrar sessão", Theme.BTN2, TXT);
        withIcon(end, R.drawable.ic_stop, TXT);
        end.setTextSize(14);
        end.setOnClickListener(v -> endSession());
        ses.addView(end, btnParams(48));

        // Configurações
        LinearLayout set = page("Configurações", R.drawable.ic_settings);
        LinearLayout hub = new LinearLayout(this);
        hub.setOrientation(LinearLayout.VERTICAL);
        set.addView(hub);
        settingsHub = hub;
        LinearLayout hubCard = card();
        hub.addView(hubCard);
        LinearLayout cMon = cat(set, "Monitor no jogo", "Itens, posição, tamanho e fundo", R.drawable.ic_overlay, hubCard);
        LinearLayout cBub = cat(set, "Bolha e painel", "Bolha flutuante e apps rápidos", R.drawable.ic_gamepad, hubCard);
        LinearLayout cDet = cat(set, "Detecção e otimização", "Detectar jogos, Não perturbe, acesso de uso", R.drawable.ic_play, hubCard);
        LinearLayout cClean = cat(set, "Limpeza de RAM", "Nível, apps protegidos e limpeza automática", R.drawable.ic_memory, hubCard);
        LinearLayout cAlert = cat(set, "Alertas e início", "Temperatura, rede, resumo e início com o celular", R.drawable.ic_thermo, hubCard);
        LinearLayout cLook = cat(set, "Aparência", "Tema e cor de destaque", R.drawable.ic_tune, hubCard);
        LinearLayout cPerm = cat(set, "Permissões", "Sobreposição, pop-ups e bateria", R.drawable.ic_shield, hubCard);
        LinearLayout det = card();
        LinearLayout opts = card();
        swOverlay = sw(opts, "Monitor flutuante", "overlay", true);
        swOverlay.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("overlay", on).apply();
            if (on && !Settings.canDrawOverlays(this)) askOverlay();
        });
        tvPos = rowItem(opts, "", R.drawable.ic_overlay, v -> cyclePosition());
        cMon.addView(opts);
        cMon.addView(section("ITENS DO MONITOR"));
        LinearLayout items = card();
        String[][] its = {{"FPS", "ov_fps", "1"}, {"FPS 1% low", "ov_low", "0"}, {"Temperatura da CPU", "ov_cpu", "1"},
                {"Temperatura da GPU", "ov_gpu", "1"}, {"Memória RAM", "ov_ram", "1"}, {"Bateria", "ov_bat", "0"}, {"Ping da rede", "ov_ping", "0"},
                {"Taxa da tela (Hz)", "ov_hz", "0"}, {"Relógio", "ov_clock", "0"}, {"Layout horizontal", "ov_h", "0"}};
        for (String[] it : its) {
            final String key = it[1];
            Switch s = sw(items, it[0], key, it[2].equals("1"));
            s.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean(key, on).apply());
        }
        tvFpsTarget2 = rowItem(items, "", R.drawable.ic_speed, v -> cycleFpsTarget());
        tvOvSize = rowItem(items, "", R.drawable.ic_tune, v ->
                choose("Tamanho do texto", OverlayText.SIZE_NAMES, prefs.getInt("ov_size", 1) % 4, i -> {
                    prefs.edit().putInt("ov_size", i).apply();
                    refreshOvLabels();
                }));
        tvOvBg = rowItem(items, "", R.drawable.ic_overlay, v ->
                choose("Fundo do monitor", OverlayText.BG_NAMES, prefs.getInt("ov_bg", 2) % 4, i -> {
                    prefs.edit().putInt("ov_bg", i).apply();
                    refreshOvLabels();
                }));
        refreshOvLabels();
        cMon.addView(items);
        cMon.addView(text("PRÉ-VISUALIZAÇÃO", 11, MUT, true));
        LinearLayout pv = card();
        tvPreview = text("", 13, TXT, false);
        pv.addView(tvPreview);
        cMon.addView(pv);
        LinearLayout cl = card();
        tvLevel = rowItem(cl, "", R.drawable.ic_memory, v -> pickLevel());
        tvKeep = rowItem(cl, "", R.drawable.ic_shield, v -> pickKeep());
        tvAutoClean = rowItem(cl, "", R.drawable.ic_timer, v -> cycleAutoClean());
        cClean.addView(cl);
        cClean.addView(text("Os níveis Equilibrada e Máxima fecham apps de verdade e precisam do modo avançado conectado. Na Máxima, só o telefone, as mensagens SMS e os apps que você marcar sobrevivem; o resto precisa ser aberto de novo. A limpeza automática roda durante o jogo quando a RAM passa do limite, no máximo a cada 3 minutos.", 12, MUT, false));
        LinearLayout al = card();
        tvHeat = rowItem(al, "", R.drawable.ic_thermo, v -> cycleHeat());
        sw(al, "Iniciar o monitor ao ligar o celular", "boot", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("boot", on).apply());
        sw(al, "Avisar se a temperatura vai subir rápido", "heat_predict", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("heat_predict", on).apply());
        sw(al, "Avisar quando o ping ou a perda de pacotes piorar", "net_alert", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("net_alert", on).apply());
        sw(al, "Resumo da partida em notificação", "post_summary", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("post_summary", on).apply());
        cAlert.addView(al);
        cAlert.addView(text("No Xiaomi, permita também o início automático do Booster nas permissões do app para o início com o celular funcionar.", 12, MUT, false));
        LinearLayout ap2 = card();
        tvTheme = rowItem(ap2, "Tema: " + Theme.MODES[Theme.mode(this)], R.drawable.ic_tune, v ->
                choose("Tema", Theme.MODES, Theme.mode(this), i -> {
                    prefs.edit().putInt("theme", i).apply();
                    restyle();
                }));
        tvAccent = rowItem(ap2, "Cor de destaque: " + Theme.ACCENT_NAMES[Theme.accentIndex(this)], R.drawable.ic_tune, v -> {
            CharSequence[] opts2 = new CharSequence[Theme.ACCENTS.length];
            for (int i = 0; i < opts2.length; i++) {
                android.text.SpannableString ss = new android.text.SpannableString("●   " + Theme.ACCENT_NAMES[i]);
                ss.setSpan(new android.text.style.ForegroundColorSpan(Theme.ACCENTS[i]), 0, 1, 0);
                ss.setSpan(new android.text.style.RelativeSizeSpan(1.4f), 0, 1, 0);
                opts2[i] = ss;
            }
            choose("Cor de destaque", opts2, Theme.accentIndex(this), i -> {
                prefs.edit().putInt("accent", i).apply();
                restyle();
            });
        });
        cLook.addView(ap2);
        LinearLayout gpc = card();
        sw(gpc, "Bolha do Booster durante o jogo", "panel", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("panel", on).apply());
        sw(gpc, "Esconder na borda quando parada", "bubble_dock", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("bubble_dock", on).apply());
        sw(gpc, "Abrir apps rápidos em janela flutuante", "panel_float", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("panel_float", on).apply());
        tvBubble = rowItem(gpc, "", R.drawable.ic_overlay, v -> cycleBubble());
        rowItem(gpc, "Apps do painel (acesso sem sair do jogo)", R.drawable.ic_add, v -> pickApps("panel_apps", "Apps no painel do jogo", GamePanel.apps(this)));
        cBub.addView(gpc);
        cBub.addView(text("A bolha aparece sozinha ao entrar em qualquer jogo da sua biblioteca, mesmo aberto fora do app (precisa do acesso de uso). Arraste-a para qualquer ponto da tela; parada, ela fica translúcida e se esconde na borda. Toque nela para liberar RAM, ligar o Não perturbe ou abrir outros apps. Com o modo avançado conectado, os apps abrem em janela flutuante centralizada; sem ele, ou se o sistema recusar, abrem em tela cheia.", 12, MUT, false));
        sw(det, "Detectar jogos automaticamente", "auto", true).setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("auto", on).apply();
            if (on && !OverlayService.running) startForegroundService(new Intent(this, OverlayService.class));
        });
        sw(det, "Otimizar ao abrir um jogo (liberar RAM, Game Mode)", "auto_boost", true)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("auto_boost", on).apply());
        sw(det, "Mostrar o monitor só durante jogos", "ov_only_game", false)
                .setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("ov_only_game", on).apply());
        swDnd = sw(det, "Não perturbe ao jogar", "dnd", true);
        swDnd.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean("dnd", on).apply();
            if (on && !dndAllowed()) {
                toast("Permita o acesso a \"Não perturbe\" para o app.");
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
            }
        });
        tvUsage = rowItem(det, "", R.drawable.ic_check, v -> usageAccess());
        cDet.addView(det);
        cDet.addView(text("Com o acesso ao uso de apps (ou o modo avançado conectado) o Booster sabe qual jogo está aberto, mede o FPS dele e grava o relatório sozinho.", 12, MUT, false));
        LinearLayout perms = card();
        tvOverlayPerm = rowItem(perms, "", R.drawable.ic_check, v -> askOverlay());
        rowItem(perms, "Pop-up em segundo plano (Xiaomi)", R.drawable.ic_overlay, v -> openMiuiPerms());
        rowItem(perms, "Bateria sem restrições", R.drawable.ic_battery, v -> openAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        cPerm.addView(perms);
        cPerm.addView(text("No HyperOS, para o monitor aparecer sobre o jogo, ative \"Exibir pop-ups durante execução em segundo plano\" e \"Exibir na tela de bloqueio\" nas permissões do Booster.", 12, MUT, false));

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

        LinearLayout dg = page("Diagnóstico", R.drawable.ic_info);
        LinearLayout dgc = card();
        tvDiag = text("", 13, TXT, false);
        dgc.addView(tvDiag);
        dg.addView(dgc);
        Button send = button("Enviar relatório de erros", Theme.ACC, Theme.ONACC);
        withIcon(send, R.drawable.ic_share, Theme.ONACC);
        send.setTextSize(14);
        send.setOnClickListener(v -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Relatório do Booster").putExtra(Intent.EXTRA_TEXT, CrashLog.report(this)), "Enviar relatório")));
        dg.addView(send, btnParams(46));
        Button viewLog = button("Ver registro de erros", Theme.BTN2, TXT);
        viewLog.setTextSize(14);
        viewLog.setOnClickListener(v -> {
            String log = CrashLog.read(this);
            ScrollView lsv = new ScrollView(this);
            TextView lt = text(log.isEmpty() ? "Nenhum erro registrado." : log, 11, TXT, false);
            lt.setTextIsSelectable(true);
            lt.setPadding((int) dp(16), (int) dp(12), (int) dp(16), (int) dp(12));
            lsv.addView(lt);
            dialog().setTitle("Registro de erros").setView(lsv).setPositiveButton("Fechar", null).show();
        });
        dg.addView(viewLog, btnParams(46));
        Button clr = button("Limpar registro", Theme.BTN2, TXT);
        clr.setTextSize(14);
        clr.setOnClickListener(v -> {
            CrashLog.clear(this);
            toast("Registro limpo.");
        });
        dg.addView(clr, btnParams(46));
        Button tut = button("Rever o tutorial", Theme.BTN2, TXT);
        tut.setTextSize(14);
        tut.setOnClickListener(v -> showTutorial());
        dg.addView(tut, btnParams(46));
        dg.addView(text("O ping é medido com conexões rápidas ao servidor 1.1.1.1 (Cloudflare) só durante o jogo; nenhum dado seu é enviado.", 12, MUT, false));

        // Sobre
        LinearLayout ab = page("Sobre", R.drawable.ic_bolt);
        LinearLayout abc = card();
        abc.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView abLogo = new ImageView(this);
        abLogo.setImageDrawable(icon(R.drawable.ic_bolt, Theme.ONACC));
        abLogo.setBackground(round(Theme.ACC, 22));
        int abp = (int) dp(16);
        abLogo.setPadding(abp, abp, abp, abp);
        LinearLayout.LayoutParams abl = new LinearLayout.LayoutParams((int) dp(72), (int) dp(72));
        abl.setMargins(0, (int) dp(8), 0, (int) dp(10));
        abc.addView(abLogo, abl);
        TextView abName = text("Booster", 22, TXT, true);
        abName.setGravity(Gravity.CENTER);
        abc.addView(abName);
        TextView abVer = text("Versão " + Updater.installedName(this), 12, MUT, false);
        abVer.setGravity(Gravity.CENTER);
        abc.addView(abVer);
        TextView abBy = text("Criado por", 11, MUT, false);
        abBy.setGravity(Gravity.CENTER);
        abBy.setPadding(0, (int) dp(16), 0, 0);
        abc.addView(abBy);
        TextView abAuthor = text("Belin7z", 20, Theme.ACC, true);
        abAuthor.setGravity(Gravity.CENTER);
        abc.addView(abAuthor);
        ab.addView(abc);
        Button gh = button("GitHub · Belin7z", Theme.BTN2, TXT);
        withIcon(gh, R.drawable.ic_code, TXT);
        gh.setTextSize(14);
        gh.setOnClickListener(v -> openUrl("https://github.com/Belin7z"));
        ab.addView(gh, btnParams(48));
        Button ig = button("Instagram · @vdb.belin7z", Theme.BTN2, TXT);
        withIcon(ig, R.drawable.ic_share, TXT);
        ig.setTextSize(14);
        ig.setOnClickListener(v -> openUrl("https://instagram.com/vdb.belin7z"));
        ab.addView(ig, btnParams(48));
        Button repo = button("Código e versões do Booster", Theme.BTN2, TXT);
        withIcon(repo, R.drawable.ic_download, TXT);
        repo.setTextSize(14);
        repo.setOnClickListener(v -> openUrl("https://github.com/Belin7z/Booster"));
        ab.addView(repo, btnParams(48));

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
        logo.setImageDrawable(icon(R.drawable.ic_bolt, Theme.ONACC));
        logo.setBackground(round(Theme.ACC, 10));
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
        gap.setBackgroundColor(Theme.LINE);
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

        exitPill = new LinearLayout(this);
        exitPill.setGravity(Gravity.CENTER_VERTICAL);
        exitPill.setPadding((int) dp(18), 0, (int) dp(18), 0);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(CARD);
        pb.setStroke((int) dp(1), 0xFFFF6B6B);
        float pr = dp(28);
        pb.setCornerRadii(new float[]{pr, pr, 0, 0, 0, 0, pr, pr});
        exitPill.setBackground(pb);
        ImageView pi = new ImageView(this);
        pi.setImageDrawable(icon(R.drawable.ic_stop, 0xFFFF6B6B));
        exitPill.addView(pi, new LinearLayout.LayoutParams((int) dp(22), (int) dp(22)));
        TextView pillText = text("Fechar o Booster", 14, TXT, true);
        pillText.setPadding((int) dp(10), 0, 0, 0);
        exitPill.addView(pillText);
        exitPill.setVisibility(View.GONE);
        exitPill.setOnClickListener(v -> finishAndRemoveTask());
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams((int) dp(200), (int) dp(56), Gravity.END | Gravity.CENTER_VERTICAL);
        root.addView(exitPill, plp);
        exitPill.setTranslationX(dp(200));

        rootView = root;
        setContentView(root);
        showPage(resumePage >= 0 && resumePage < pages.size() ? resumePage : 0);
        resumePage = -1;
        if (resumeCat >= 0 && resumeCat < settingsCats.size()) openCat(settingsCats.get(resumeCat));
        resumeCat = -1;
        if (b == null) playIntro(root, main);
        h.postDelayed(() -> {
            if (isDestroyed()) return;
            if (!prefs.getBoolean("onboarded", false)) showTutorial();
            else showWhatsNew();
        }, b == null ? IntroView.TOTAL + 900 : 300);
        handleIntent(getIntent());
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
        if (idx != curPage && idx == pageNames.indexOf("Configurações")) closeCat();
        curPage = idx;
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setVisibility(i == idx ? View.VISIBLE : View.GONE);
            TextView it = navItems.get(i);
            int col = i == idx ? Theme.ACC : Theme.NAVTXT;
            it.setBackground(i == idx ? round(Theme.SEL, 10) : null);
            it.setTextColor(col);
            it.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(pageIcons.get(i), col), null, null, null);
        }
        tvTitle.setText(pageNames.get(idx));
    }

    private LinearLayout exitPill;
    private ValueAnimator drawerAnim;
    private float dragX0, dragY0, dragLast, dragStartP, dragP, dragDir;
    private int dragKind;
    private boolean pillShown;
    private final Runnable hidePill = () -> animatePill(false);

    private float drawerProgress() {
        return Math.max(0f, Math.min(1f, 1f + drawer.getTranslationX() / dp(270)));
    }

    private void setProgress(float p) {
        drawer.setTranslationX(-dp(270) * (1f - p));
        scrim.setVisibility(p > 0f ? View.VISIBLE : View.GONE);
        scrim.setAlpha(p);
    }

    private void animateDrawer(final boolean open) {
        drawerOpen = open;
        if (drawerAnim != null) drawerAnim.cancel();
        float from = drawerProgress(), to = open ? 1f : 0f;
        drawerAnim = ValueAnimator.ofFloat(from, to);
        drawerAnim.setDuration((long) (120 + 380 * Math.abs(to - from)));
        drawerAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        drawerAnim.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
        drawerAnim.start();
    }

    private void animatePill(boolean show) {
        pillShown = show;
        h.removeCallbacks(hidePill);
        exitPill.animate().cancel();
        if (show) {
            exitPill.setVisibility(View.VISIBLE);
            exitPill.animate().translationX(0).alpha(1).setDuration(450)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            h.postDelayed(hidePill, 4000);
        } else {
            exitPill.animate().translationX(dp(200)).alpha(0).setDuration(350)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(() -> {
                        if (!pillShown) exitPill.setVisibility(View.GONE);
                    }).start();
        }
    }

    // Arrastar para a direita abre o menu; para a esquerda fecha o menu ou mostra "Fechar o Booster".
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragX0 = dragLast = e.getRawX();
                dragY0 = e.getRawY();
                dragKind = 0;
                dragDir = 0;
                h.removeCallbacks(hidePill);
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragKind == 0) {
                    float dx = e.getRawX() - dragX0, dy = e.getRawY() - dragY0;
                    if (Math.abs(dx) > slop * 2 && Math.abs(dx) > Math.abs(dy) * 1.6f) {
                        if (dx > 0 || drawerOpen || drawerProgress() > 0f) dragKind = 1;
                        else if (!pillShown) dragKind = 2;
                        if (dragKind != 0) {
                            if (drawerAnim != null) drawerAnim.cancel();
                            dragStartP = drawerProgress();
                            dragX0 = e.getRawX();
                            MotionEvent c = MotionEvent.obtain(e);
                            c.setAction(MotionEvent.ACTION_CANCEL);
                            super.dispatchTouchEvent(c);
                            c.recycle();
                            exitPill.setVisibility(dragKind == 2 ? View.VISIBLE : exitPill.getVisibility());
                        }
                    }
                }
                if (dragKind == 1) {
                    dragP = Math.max(0f, Math.min(1f, dragStartP + (e.getRawX() - dragX0) / dp(270)));
                    setProgress(dragP);
                    float step = e.getRawX() - dragLast;
                    if (Math.abs(step) > 1f) dragDir = step;
                    dragLast = e.getRawX();
                    return true;
                } else if (dragKind == 2) {
                    float q = Math.max(0f, Math.min(1f, (dragX0 - e.getRawX()) / dp(160)));
                    exitPill.setAlpha(q);
                    exitPill.setTranslationX(dp(200) * (1f - q));
                    dragP = q;
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragKind == 1) {
                    boolean open = dragDir > 0 ? dragP > 0.2f : dragDir < 0 ? dragP > 0.8f : dragP > 0.5f;
                    animateDrawer(open);
                    dragKind = 0;
                    return true;
                } else if (dragKind == 2) {
                    animatePill(dragP > 0.45f);
                    dragKind = 0;
                    return true;
                }
                if (pillShown) h.postDelayed(hidePill, 4000);
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    // Abertura: rajadas de velocidade, logo, nome e assinatura; depois o conteúdo entra em cascata.
    private void playIntro(final FrameLayout root, final View main) {
        final IntroView intro = new IntroView(this);
        intro.setClickable(true);
        root.addView(intro, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        main.setAlpha(0f);
        main.setScaleX(0.94f);
        main.setScaleY(0.94f);
        final ViewGroup hg = (ViewGroup) ((ViewGroup) pages.get(0)).getChildAt(0);
        for (int i = 0; i < hg.getChildCount(); i++) {
            hg.getChildAt(i).setAlpha(0f);
            hg.getChildAt(i).setTranslationY(dp(28));
        }
        final Runnable reveal = () -> {
            intro.animate().alpha(0f).scaleX(1.2f).scaleY(1.2f).setDuration(500)
                    .withEndAction(() -> root.removeView(intro)).start();
            main.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
            for (int i = 0; i < hg.getChildCount(); i++) {
                hg.getChildAt(i).animate().alpha(1f).translationY(0).setStartDelay(150 + 60L * i).setDuration(500)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
            }
        };
        intro.setOnClickListener(v -> intro.finish());
        intro.start(reveal);
    }

    private void toggleDrawer(boolean open) {
        animateDrawer(open);
    }

    private LinearLayout settingsHub;
    private final List<LinearLayout> settingsCats = new ArrayList<>();

    // Cria uma categoria de configurações (tela própria, com voltar) e a linha correspondente no menu.
    private LinearLayout cat(LinearLayout set, String title, String sub, int icon, LinearLayout hubCard) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setVisibility(View.GONE);
        TextView back = text("‹  " + title, 17, Theme.ACC, true);
        back.setPadding(0, (int) dp(8), 0, (int) dp(8));
        back.setOnClickListener(v -> closeCat());
        c.addView(back);
        set.addView(c);
        settingsCats.add(c);
        TextView row = rowItem(hubCard, title, icon, v -> openCat(c));
        android.text.SpannableString ss = new android.text.SpannableString(title + "\n" + sub);
        ss.setSpan(new android.text.style.RelativeSizeSpan(0.8f), title.length() + 1, ss.length(), 0);
        ss.setSpan(new android.text.style.ForegroundColorSpan(MUT), title.length() + 1, ss.length(), 0);
        row.setText(ss);
        return c;
    }

    private void openCat(LinearLayout c) {
        settingsHub.setVisibility(View.GONE);
        curCat = settingsCats.indexOf(c);
        for (LinearLayout x : settingsCats) x.setVisibility(x == c ? View.VISIBLE : View.GONE);
        ((ScrollView) pages.get(pageNames.indexOf("Configurações"))).scrollTo(0, 0);
    }

    private boolean closeCat() {
        if (settingsHub == null || settingsHub.getVisibility() == View.VISIBLE) return false;
        for (LinearLayout x : settingsCats) x.setVisibility(View.GONE);
        curCat = -1;
        settingsHub.setVisibility(View.VISIBLE);
        ((ScrollView) pages.get(pageNames.indexOf("Configurações"))).scrollTo(0, 0);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) toggleDrawer(false);
        else if (curPage == pageNames.indexOf("Configurações") && closeCat()) return;
        else super.onBackPressed();
    }

    private TextView rowItem(LinearLayout parent, String label, int ic, View.OnClickListener l) {
        TextView t = text(label, 14, TXT, false);
        t.setCompoundDrawablePadding((int) dp(14));
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(ic, MUT), null, icon(R.drawable.ic_chevron, Theme.CHEV), null);
        t.setPadding(0, (int) dp(12), 0, (int) dp(12));
        t.setOnClickListener(l);
        parent.addView(t);
        return t;
    }

    // 1) tenta conceder pelo modo avançado; 2) senão, leva ao "Info do app" para liberar as configurações restritas
    // (o Android bloqueia o botão em apps instalados fora da loja) e depois à tela de acesso de uso.
    private void usageAccess() {
        if (Games.hasUsageAccess(this)) {
            toast("O acesso já está permitido.");
            return;
        }
        new Thread(() -> {
            boolean ok = Games.grantUsageViaShell(this);
            runOnUiThread(() -> {
                if (ok) {
                    toast("Acesso concedido pelo modo avançado.");
                    return;
                }
                if (prefs.getBoolean("usage_hint", false)) {
                    openAction(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    return;
                }
                prefs.edit().putBoolean("usage_hint", true).apply();
                dialog().setTitle("Liberar acesso de uso")
                        .setMessage("Se o botão aparecer bloqueado:\n\n1. Toque em Abrir Info do app.\n2. Toque nos 3 pontinhos (canto superior direito) e em \"Permitir configurações restritas\".\n3. Volte e ative o Booster em Acesso de uso.\n\nOu conecte o modo avançado: o Booster concede o acesso sozinho.")
                        .setPositiveButton("Abrir Info do app", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                        .setNegativeButton("Já liberei", (d, w) -> openAction(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        .show();
            });
        }).start();
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

    private static final String[] POS = {"Topo esquerda", "Topo centro", "Topo direita", "Embaixo esquerda", "Embaixo centro", "Embaixo direita"};
    private TextView tvOvSize, tvOvBg;

    private void refreshOvLabels() {
        if (tvOvSize != null) tvOvSize.setText("Tamanho do texto: " + OverlayText.SIZE_NAMES[prefs.getInt("ov_size", 1) % 4]);
        if (tvOvBg != null) tvOvBg.setText("Fundo do monitor: " + OverlayText.BG_NAMES[prefs.getInt("ov_bg", 2) % 4]);
        if (tvPreview != null) {
            tvPreview.setTextSize(OverlayText.SIZES[prefs.getInt("ov_size", 1) % 4]);
            tvPreview.setBackgroundColor(OverlayText.BGS[prefs.getInt("ov_bg", 2) % 4] | 0x00000000);
        }
    }

    private void cyclePosition() {
        choose("Posição do monitor", POS, prefs.getInt("pos", 0) % POS.length, i -> {
            prefs.edit().putInt("pos", i).apply();
            updateChrome();
        });
    }

    // Lista de opções em pop-up: toque escolhe e fecha na hora.
    private void choose(String title, CharSequence[] names, int selected, java.util.function.IntConsumer pick) {
        dialog().setTitle(title).setSingleChoiceItems(names, selected, (d, w) -> {
            d.dismiss();
            pick.accept(w);
        }).setNegativeButton("Cancelar", null).show();
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
        showUpdateDialog(pending);
    }

    private void showUpdateDialog(final Updater.Info info) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) dp(22), (int) dp(20), (int) dp(22), (int) dp(4));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(icon(R.drawable.ic_download, Theme.ONACC));
        ic.setPadding((int) dp(11), (int) dp(11), (int) dp(11), (int) dp(11));
        ic.setBackground(round(Theme.ACC, 24));
        head.addView(ic, new LinearLayout.LayoutParams((int) dp(48), (int) dp(48)));
        LinearLayout nm = new LinearLayout(this);
        nm.setOrientation(LinearLayout.VERTICAL);
        nm.setPadding((int) dp(14), 0, 0, 0);
        nm.addView(text("Nova versão disponível", 12, MUT, false));
        nm.addView(text("Booster " + info.name, 22, TXT, true));
        nm.addView(text("Você tem a " + Updater.installedName(this), 12, MUT, false));
        head.addView(nm);
        box.addView(head);
        View div = new View(this);
        div.setBackgroundColor(Theme.LINE);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(1));
        dl.setMargins(0, (int) dp(16), 0, (int) dp(4));
        box.addView(div, dl);
        String notes = info.notes.isEmpty() ? "- Melhorias e correções." : info.notes;
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(notesView(notes));
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                notes.split("\n").length > 9 ? (int) dp(240) : ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog().setView(box)
                .setPositiveButton(Updater.ready(this, info) ? "Instalar" : "Atualizar agora", (d, w) -> startInstall(info))
                .setNegativeButton("Depois", null).show();
    }

    private void startInstall(final Updater.Info info) {
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
                        tvUpdate.setText("Nova versão disponível: v" + i.name);
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
        if (tutorial != null) renderTutorial();
        Updater.Info dl = Updater.downloaded(this);
        if (dl != null && pending == null && tvUpdate != null) {
            pending = dl;
            tvUpdate.setText("Nova versão já baixada: v" + dl.name);
            tvUpdate.setTextColor(OK);
            btnUpdate.setText("Instalar v" + dl.name);
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIntent(i);
    }

    private void handleIntent(Intent i) {
        if (i == null || !i.getBooleanExtra(EXTRA_INSTALL, false)) return;
        i.removeExtra(EXTRA_INSTALL);
        showPage(pageNames.indexOf("Atualizações"));
        Updater.Info dl = Updater.downloaded(this);
        if (dl != null) {
            pending = dl;
            h.postDelayed(this::onUpdateClick, 400);
        }
    }

    // Recria a tela para aplicar tema/cor, voltando para a mesma página.
    private void restyle() {
        resumePage = curPage;
        resumeCat = curCat;
        recreate();
    }

    private void updateDiag() {
        if (tvDiag == null || curPage != pageNames.indexOf("Diagnóstico")) return;
        tvDiag.setText(Diag.summary(this) + "\n\nErros registrados: " + CrashLog.count(this));
    }

    private void export(final boolean pdf) {
        toast("Gerando…");
        new Thread(() -> {
            try {
                if (Reports.all(this).isEmpty()) {
                    h.post(() -> toast("Ainda não há sessões para exportar."));
                    return;
                }
                java.io.File f = pdf ? Export.pdf(this) : Export.csv(this);
                h.post(() -> Export.share(this, f, pdf ? "application/pdf" : "text/csv"));
            } catch (Throwable e) {
                CrashLog.log(this, "Exportar", CrashLog.stack(e));
                h.post(() -> toast("Não foi possível exportar."));
            }
        }).start();
    }

    // Notas em markdown simples (### seção, - item) com visual de seções e marcadores.
    private LinearLayout notesView(String notes) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        for (String raw : notes.split("\n")) {
            String s = raw.trim();
            if (s.isEmpty() || s.startsWith("## ") || s.startsWith("---") || s.startsWith("Criado por")) continue;
            if (s.startsWith("#")) {
                TextView hd = text(s.replaceFirst("^#+\\s*", "").toUpperCase(Locale.ROOT), 11, Theme.ACC, true);
                hd.setLetterSpacing(0.14f);
                hd.setPadding(0, (int) dp(12), 0, (int) dp(4));
                l.addView(hd);
            } else if (s.startsWith("- ") || s.startsWith("* ")) {
                LinearLayout row = new LinearLayout(this);
                row.setPadding(0, (int) dp(3), 0, (int) dp(3));
                TextView dot = text("●", 8, Theme.ACC, false);
                dot.setPadding(0, (int) dp(5), (int) dp(10), 0);
                row.addView(dot);
                row.addView(text(s.substring(2), 13, TXT, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                l.addView(row);
            } else {
                TextView t = text(s, 13, MUT, false);
                t.setPadding(0, (int) dp(2), 0, (int) dp(2));
                l.addView(t);
            }
        }
        return l;
    }

    private ScrollView notesScroll(String notes) {
        ScrollView sv = new ScrollView(this);
        LinearLayout l = notesView(notes);
        l.setPadding((int) dp(22), (int) dp(8), (int) dp(22), (int) dp(8));
        sv.addView(l);
        return sv;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("Não foi possível abrir o link.");
        }
    }

    private void showHistory() {
        toast("Buscando versões…");
        new Thread(() -> {
            try {
                final List<String> rel = Updater.history();
                h.post(() -> {
                    if (isDestroyed()) return;
                    LinearLayout all = new LinearLayout(this);
                    all.setOrientation(LinearLayout.VERTICAL);
                    all.setPadding((int) dp(22), (int) dp(8), (int) dp(22), (int) dp(12));
                    for (String r : rel) {
                        int nl = r.indexOf('\n');
                        TextView title = text(nl < 0 ? r : r.substring(0, nl), 16, TXT, true);
                        title.setPadding(0, (int) dp(14), 0, 0);
                        all.addView(title);
                        if (nl >= 0) all.addView(notesView(r.substring(nl + 1)));
                        View div = new View(this);
                        div.setBackgroundColor(Theme.LINE);
                        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(1));
                        dl.topMargin = (int) dp(12);
                        all.addView(div, dl);
                    }
                    ScrollView sv = new ScrollView(this);
                    sv.addView(all);
                    dialog().setTitle("Histórico de versões").setView(sv).setPositiveButton("Fechar", null).show();
                });
            } catch (Exception e) {
                h.post(() -> toast("Não consegui buscar o histórico."));
            }
        }).start();
    }

    private void showWhatsNew() {
        int code = Updater.installedCode(this);
        if (prefs.getInt("wn_code", 0) != code || prefs.getInt("wn_seen", 0) == code) return;
        prefs.edit().putInt("wn_seen", code).apply();
        String notes = prefs.getString("wn_notes", "");
        if (notes.isEmpty()) return;
        dialog().setTitle("Novidades da v" + prefs.getString("wn_name", "")).setView(notesScroll(notes)).setPositiveButton("Entendi", null).show();
    }

    private LinearLayout tutorial;
    private int tutStep;

    private boolean usageGranted() {
        try {
            android.app.AppOpsManager ao = getSystemService(android.app.AppOpsManager.class);
            return ao.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName())
                    == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    // Tutorial de primeiro uso: cada passo mostra se a permissão já foi dada.
    private void showTutorial() {
        if (tutorial != null || rootView == null) return;
        tutorial = new LinearLayout(this);
        tutorial.setOrientation(LinearLayout.VERTICAL);
        tutorial.setGravity(Gravity.CENTER);
        tutorial.setBackgroundColor(BG);
        tutorial.setClickable(true);
        int p = (int) dp(24);
        tutorial.setPadding(p, (int) dp(40), p, p);
        rootView.addView(tutorial, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tutorial.setAlpha(0f);
        tutorial.animate().alpha(1f).setDuration(400).start();
        tutStep = 0;
        renderTutorial();
    }

    private void closeTutorial() {
        prefs.edit().putBoolean("onboarded", true).apply();
        final View t = tutorial;
        tutorial = null;
        t.animate().alpha(0f).setDuration(350).withEndAction(() -> rootView.removeView(t)).start();
    }

    private void renderTutorial() {
        if (tutorial == null) return;
        final String[] title = {"Bem-vindo ao Booster", "Monitor sobre o jogo", "Tempo de jogo certinho", "Não perturbe", "Modo avançado", "Tudo pronto"};
        final String[] body = {
                "Vou te guiar em poucos passos para liberar mais RAM, medir FPS e temperatura e gerar relatórios de cada jogo. Você pode pular o que quiser.",
                "Para mostrar FPS, temperatura e a bolha por cima do jogo, permita \"Sobrepor a outros apps\".",
                "O Booster lê o histórico de uso do Android para somar as horas de cada jogo, mesmo abrindo o jogo fora dele.",
                "Opcional: silencia notificações enquanto você joga e devolve tudo ao sair.",
                "Opcional: com o ADB embutido (Depuração sem fio) o Booster mede o FPS, limpa a RAM a fundo e muda a taxa da tela por jogo. Veja a página Modo avançado.",
                "Os jogos são detectados sozinhos. Abra um jogo e o relatório aparece aqui quando você sair. O menu abre deslizando da esquerda para a direita."};
        final String[] action = {null, "Permitir sobreposição", "Permitir acesso ao uso", "Permitir Não perturbe", "Abrir Modo avançado", null};
        boolean[] done = {false, Settings.canDrawOverlays(this), usageGranted(), dndAllowed(), Shell.granted(), false};
        tutorial.removeAllViews();
        TextView step = text((tutStep + 1) + " de " + title.length, 12, MUT, true);
        step.setGravity(Gravity.CENTER);
        tutorial.addView(step);
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(icon(R.drawable.ic_bolt, Theme.ONACC));
        logo.setBackground(round(Theme.ACC, 24));
        int pd = (int) dp(18);
        logo.setPadding(pd, pd, pd, pd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) dp(80), (int) dp(80));
        lp.setMargins(0, (int) dp(24), 0, (int) dp(24));
        tutorial.addView(logo, lp);
        TextView t = text(title[tutStep], 22, TXT, true);
        t.setGravity(Gravity.CENTER);
        tutorial.addView(t);
        TextView b = text(body[tutStep], 14, MUT, false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, (int) dp(10), 0, (int) dp(16));
        tutorial.addView(b);
        if (action[tutStep] != null) {
            if (done[tutStep]) {
                TextView ok = text("✓ Já está ativo", 14, OK, true);
                ok.setGravity(Gravity.CENTER);
                tutorial.addView(ok);
            } else {
                Button ab = button(action[tutStep], Theme.ACC, Theme.ONACC);
                ab.setTextSize(14);
                final int st = tutStep;
                ab.setOnClickListener(v -> {
                    if (st == 1) askOverlay();
                    else if (st == 2) usageAccess();
                    else if (st == 3) startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    else {
                        closeTutorial();
                        showPage(pageNames.indexOf("Modo avançado"));
                    }
                });
                tutorial.addView(ab, btnParams(48));
            }
        }
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(0, (int) dp(16), 0, 0);
        Button skip = button("Pular tudo", Theme.BTN2, TXT);
        skip.setTextSize(14);
        skip.setOnClickListener(v -> closeTutorial());
        Button next = button(tutStep == title.length - 1 ? "Começar" : "Continuar", Theme.ACC, Theme.ONACC);
        next.setTextSize(14);
        next.setOnClickListener(v -> {
            if (tutStep == title.length - 1) closeTutorial();
            else {
                tutStep++;
                renderTutorial();
            }
        });
        LinearLayout.LayoutParams n1 = new LinearLayout.LayoutParams(0, (int) dp(48), 1);
        n1.setMargins(0, 0, (int) dp(4), 0);
        LinearLayout.LayoutParams n2 = new LinearLayout.LayoutParams(0, (int) dp(48), 1);
        n2.setMargins((int) dp(4), 0, 0, 0);
        if (tutStep < title.length - 1) nav.addView(skip, n1);
        nav.addView(next, n2);
        tutorial.addView(nav);
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
            refreshLists();
        }
        tvLast.setText(prefs.getString("last", "Nenhuma sessão ainda."));
        if (ptTick++ % 60 == 0) loadPlayTime();
        if (ptTick % 3 == 1) {
            updateUsage();
            updateShizuku();
            updateAdb();
            updateChrome();
            Diag.sample();
            updateDiag();
        }
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
        final int[] rates = Monitor.supportedRates(this);
        int cur = prefs.getInt("fps_target", 0), sel = 0;
        String[] names = new String[rates.length + 1];
        names[0] = "Automática (" + Monitor.refreshRate(this) + " Hz)";
        for (int i = 0; i < rates.length; i++) {
            names[i + 1] = rates[i] + " Hz";
            if (rates[i] == cur) sel = i + 1;
        }
        choose("Taxa alvo do FPS", names, sel, i -> {
            prefs.edit().putInt("fps_target", i == 0 ? 0 : rates[i - 1]).apply();
            updateChrome();
        });
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

    private final java.util.concurrent.atomic.AtomicBoolean loadingLists = new java.util.concurrent.atomic.AtomicBoolean();

    // Varrer apps e ler relatórios é pesado: roda fora da thread principal para não gerar "não está respondendo".
    private void refreshLists() {
        if (!loadingLists.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                final List<Games.Game> gl = Games.list(this);
                final List<Reports.Agg> ags = Reports.byGame(this);
                h.post(() -> {
                    if (isDestroyed()) return;
                    refreshGames(gl, ags);
                    refreshReports(ags);
                });
            } catch (Throwable ignored) {
            } finally {
                loadingLists.set(false);
            }
        }).start();
    }

    private void refreshGames(List<Games.Game> gl, List<Reports.Agg> ags) {
        gamesList.removeAllViews();
        java.util.Map<String, Reports.Agg> agg = new java.util.HashMap<>();
        for (Reports.Agg a : ags) agg.put(a.pkg, a);
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
        go.setImageDrawable(icon(R.drawable.ic_play, Theme.ONACC));
        go.setBackground(round(Theme.ACC, 12));
        int p = (int) dp(10);
        go.setPadding(p, p, p, p);
        go.setOnClickListener(v -> play(g.pkg));
        row.addView(go, new LinearLayout.LayoutParams((int) dp(44), (int) dp(44)));
        row.setOnClickListener(v -> showGameOptions(g));
        return row;
    }

    private android.app.AlertDialog.Builder dialog() {
        return new android.app.AlertDialog.Builder(this, Theme.light ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert);
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
                refreshLists();
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
                "Não perturbe: " + onOff(Profiles.rawDnd(this, g.pkg)),
                "Brilho no jogo: " + (Profiles.rawBright(this, g.pkg) < 0 ? "não mexer" : Profiles.rawBright(this, g.pkg) + "%"),
                "Taxa da tela no jogo: " + (Profiles.rawHz(this, g.pkg) < 0 ? "não mexer" : Profiles.rawHz(this, g.pkg) + " Hz")};
        dialog().setTitle("Perfil: " + g.label).setItems(items, (d, which) -> {
            if (which == 0) Profiles.setLevel(this, g.pkg, Profiles.next(Profiles.rawLevel(this, g.pkg), 2));
            else if (which == 1) Profiles.setClean(this, g.pkg, Profiles.next(Profiles.rawClean(this, g.pkg), 1));
            else if (which == 3) {
                int[] opts = {-1, 25, 50, 75, 100};
                int cur = Profiles.rawBright(this, g.pkg), nx = opts[0];
                for (int i = 0; i < opts.length; i++) if (opts[i] == cur) nx = opts[(i + 1) % opts.length];
                Profiles.setBright(this, g.pkg, nx);
            } else if (which == 4) {
                int[] rates = Monitor.supportedRates(this);
                int cur = Profiles.rawHz(this, g.pkg), nx = rates[0];
                if (cur > 0) {
                    nx = -1;
                    for (int i = 0; i < rates.length; i++) if (rates[i] == cur && i + 1 < rates.length) nx = rates[i + 1];
                }
                Profiles.setHz(this, g.pkg, nx);
                if (nx > 0 && !Shell.granted()) toast("A taxa da tela precisa do modo avançado conectado.");
            } else if (which == 2) Profiles.setDnd(this, g.pkg, Profiles.next(Profiles.rawDnd(this, g.pkg), 1));
            showProfile(g);
        }).setNegativeButton("Fechar", null).show();
    }

    private void pickGames() {
        new Thread(() -> {
            final List<Games.Game> cands = Games.candidates(this);
            h.post(() -> {
                if (isDestroyed()) return;
                String[] names = new String[cands.size()];
                for (int i = 0; i < names.length; i++) names[i] = cands.get(i).label;
                final boolean[] on = new boolean[names.length];
                dialog().setTitle("Adicionar jogos").setMultiChoiceItems(names, on, (d, i, c) -> on[i] = c)
                        .setPositiveButton("Adicionar", (d, w) -> {
                            for (int i = 0; i < on.length; i++) if (on[i]) Games.add(this, cands.get(i).pkg);
                            refreshLists();
                        }).setNegativeButton("Cancelar", null).show();
            });
        }).start();
    }

    private int gradeColor(int score) {
        return score < 0 ? MUT : score >= 80 ? OK : score >= 65 ? Theme.ACC : score >= 50 ? WARN : BAD;
    }

    private void refreshReports(List<Reports.Agg> ags) {
        reportsList.removeAllViews();
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
            GradientDrawable gb = round(Theme.BG2, 12);
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
        if (r.pingAvg > 0) st.append("\nRede: ping médio ").append(r.pingAvg).append(" ms (máx ").append(r.pingMax).append(") · perda ").append(r.lossPct).append('%');
        l.addView(text(st.toString(), 13, TXT, false));

        if (r.measured) {
            GraphView g1 = new GraphView(this, "FPS durante a sessão", Theme.ACC, 0, Math.max(r.target, r.fpsMax));
            g1.set(toFloats(r.fpsSeries, 1));
            l.addView(g1, graphParams());
        }
        GraphView g2 = new GraphView(this, "Temperatura da CPU °C", 0xFFFF7A59, 30, 50);
        g2.set(toFloats(r.cpuSeries, 10));
        l.addView(g2, graphParams());

        String cmp = Reports.compare(rs);
        if (cmp != null) {
            TextView ct = text(cmp, 12, Theme.ACC, false);
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
        Button share = button("Compartilhar relatório", Theme.BTN2, TXT);
        withIcon(share, R.drawable.ic_share, TXT);
        share.setTextSize(14);
        share.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Reports.text(r));
            startActivity(Intent.createChooser(i, "Compartilhar relatório"));
        });
        l.addView(share, btnParams(44));
        Button img = button("Compartilhar como imagem", Theme.BTN2, TXT);
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
        if (rs.size() >= 2) {
            Button cmpB = button("Comparar sessões", Theme.BTN2, TXT);
            withIcon(cmpB, R.drawable.ic_chart, TXT);
            cmpB.setTextSize(14);
            cmpB.setOnClickListener(v -> CompareDialog.pick(this, label, rs));
            l.addView(cmpB, btnParams(44));
        }
        Button del = button("Apagar histórico deste jogo", Theme.blend(BAD, CARD, 0.16f), BAD);
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
        int cur = prefs.getInt("heat_alert", 46), sel = 0;
        String[] names = new String[HEAT.length];
        for (int i = 0; i < HEAT.length; i++) {
            names[i] = HEAT[i] == 0 ? "Desligado" : "CPU acima de " + HEAT[i] + "°C";
            if (HEAT[i] == cur) sel = i;
        }
        choose("Alerta de calor", names, sel, i -> {
            prefs.edit().putInt("heat_alert", HEAT[i]).apply();
            updateChrome();
        });
    }

    // Dois pop-ups em sequência: tamanho e depois transparência.
    private void cycleBubble() {
        choose("Tamanho da bolha", new String[]{"Pequena", "Média", "Grande"}, prefs.getInt("bubble_size", 1), s -> {
            prefs.edit().putInt("bubble_size", s).apply();
            choose("Transparência da bolha", new String[]{"Bem transparente", "Transparente", "Opaca"}, prefs.getInt("bubble_alpha", 1), a -> {
                prefs.edit().putInt("bubble_alpha", a).apply();
                updateChrome();
            });
        });
    }

    private void cycleAutoClean() {
        int cur = prefs.getInt("auto_clean_pct", 0), sel = 0;
        String[] names = new String[AUTO_PCT.length];
        for (int i = 0; i < AUTO_PCT.length; i++) {
            names[i] = AUTO_PCT[i] == 0 ? "Desligada" : "RAM acima de " + AUTO_PCT[i] + "%";
            if (AUTO_PCT[i] == cur) sel = i;
        }
        choose("Limpeza automática no jogo", names, sel, i -> {
            prefs.edit().putInt("auto_clean_pct", AUTO_PCT[i]).apply();
            updateChrome();
        });
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
            track.setBackground(round(Theme.TRACK, 3));
            View fillBar = new View(this);
            fillBar.setBackground(round(Theme.ACC, 3));
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
        cg.setStroke((int) dp(1), Theme.LINE);
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
