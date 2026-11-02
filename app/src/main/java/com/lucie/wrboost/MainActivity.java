package com.lucie.wrboost;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private final List<String> pageNames = new ArrayList<>(), pageIcons = new ArrayList<>();
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
    private Switch swOverlay, swDnd, swAggr;
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
        TextView menu = text("≡", 30, TXT, true);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> toggleDrawer(true));
        bar.addView(menu, new LinearLayout.LayoutParams((int) dp(52), (int) dp(48)));
        tvTitle = text("", 20, TXT, true);
        bar.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tvDot = text("● Shizuku", 12, MUT, false);
        bar.addView(tvDot);
        main.addView(bar);

        content = new FrameLayout(this);
        main.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Início
        LinearLayout home = page("Início", "🏠");
        gauge = new GaugeView(this);
        home.addView(gauge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(200)));
        LinearLayout r1 = new LinearLayout(this);
        tvFps = stat(r1, "FPS");
        tvCpu = stat(r1, "CPU");
        tvGpu = stat(r1, "GPU");
        tvBat = stat(r1, "Bateria");
        home.addView(r1);
        LinearLayout tipCard = card();
        tvTip = text("", 14, TXT, false);
        tipCard.addView(tvTip);
        home.addView(tipCard);
        btnPlay = button("🎮  Jogar Wild Rift", 0xFF4DA3FF, 0xFF04101F);
        btnPlay.setOnClickListener(v -> play());
        home.addView(btnPlay, btnParams(56));
        LinearLayout acts = new LinearLayout(this);
        btnBoost = button("🚀 Liberar RAM", 0xFF1D2A4A, TXT);
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

        // Desempenho
        LinearLayout perf = page("Desempenho", "📈");
        gFps = new GraphView(this, "FPS (últimos 2 min)", 0xFF4DA3FF, 0, 60);
        gCpu = new GraphView(this, "Temperatura da CPU °C", 0xFFFF7A59, 30, 50);
        perf.addView(gFps, graphParams());
        perf.addView(gCpu, graphParams());
        LinearLayout r2 = new LinearLayout(this);
        r2.setPadding(0, (int) dp(8), 0, 0);
        tvLow = stat(r2, "1% low");
        tvFree = stat(r2, "Armazen.");
        perf.addView(r2);
        LinearLayout info = card();
        tvSwap = text("", 14, TXT, false);
        tvThermal = text("", 14, TXT, false);
        info.addView(tvSwap);
        info.addView(tvThermal);
        perf.addView(info);

        // Modo avançado
        LinearLayout adv = page("Modo avançado", "🔓");
        LinearLayout advCard = card();
        advCard.addView(text("Shizuku", 15, TXT, true));
        tvShizuku = text("", 13, MUT, false);
        advCard.addView(tvShizuku);
        btnShizuku = button("", 0xFF1D2A4A, TXT);
        btnShizuku.setTextSize(14);
        btnShizuku.setOnClickListener(v -> shizukuAction());
        advCard.addView(btnShizuku, btnParams(46));
        adv.addView(advCard);
        adv.addView(text("Com o Shizuku o Booster lê FPS e temperatura de CPU/GPU, ativa o Game Mode e faz a limpeza profunda.", 12, MUT, false));

        // Atualizações
        LinearLayout up = page("Atualizações", "🔄");
        LinearLayout upCard = card();
        tvUpdate = text("Versão instalada: " + Updater.installedName(this), 13, MUT, false);
        upCard.addView(tvUpdate);
        btnUpdate = button("Verificar atualizações", 0xFF1D2A4A, TXT);
        btnUpdate.setTextSize(14);
        btnUpdate.setOnClickListener(v -> onUpdateClick());
        upCard.addView(btnUpdate, btnParams(46));
        up.addView(upCard);

        // Sessão
        LinearLayout ses = page("Sessão", "📊");
        LinearLayout last = card();
        last.addView(text("Resumo da última sessão", 15, TXT, true));
        tvLast = text(prefs.getString("last", "Nenhuma sessão ainda."), 13, MUT, false);
        last.addView(tvLast);
        ses.addView(last);
        Button end = button("■  Encerrar sessão e ver resumo", 0xFF1D2A4A, TXT);
        end.setTextSize(14);
        end.setOnClickListener(v -> endSession());
        ses.addView(end, btnParams(48));

        // Configurações
        LinearLayout set = page("Configurações", "⚙️");
        set.addView(text("DURANTE O JOGO", 11, MUT, true));
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
        swAggr = sw(opts, "Limpeza profunda (requer Shizuku)", "aggr", true);
        swAggr.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("aggr", on).apply());
        tvPos = rowItem(opts, "", v -> cyclePosition());
        set.addView(opts);
        set.addView(text("PERMISSÕES", 11, MUT, true));
        LinearLayout perms = card();
        tvOverlayPerm = rowItem(perms, "", v -> askOverlay());
        rowItem(perms, "Pop-up em segundo plano (Xiaomi) ›", v -> openMiuiPerms());
        rowItem(perms, "Bateria sem restrições ›", v -> openAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        set.addView(perms);
        set.addView(text("No HyperOS, para o monitor aparecer sobre o jogo, ative \"Exibir pop-ups durante execução em segundo plano\" e \"Exibir na tela de bloqueio\" nas permissões do Booster.", 12, MUT, false));

        // Atalhos
        LinearLayout sc = page("Atalhos e dicas", "🧰");
        LinearLayout sh = card();
        rowItem(sh, "🔋 Modo desempenho / bateria", v -> openAction(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        rowItem(sh, "🛠 Opções do desenvolvedor", v -> openAction(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        rowItem(sh, "💾 Armazenamento", v -> openAction(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        rowItem(sh, "📶 Wi-Fi", v -> openAction(Settings.ACTION_WIFI_SETTINGS));
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
        drawer.addView(text("⚡ Booster", 22, TXT, true));
        drawer.addView(text("v" + Updater.installedName(this), 12, MUT, false));
        View gap = new View(this);
        drawer.addView(gap, new LinearLayout.LayoutParams(1, (int) dp(16)));
        for (int i = 0; i < pages.size(); i++) {
            final int idx = i;
            TextView it = text(pageIcons.get(i) + "  " + pageNames.get(i), 16, TXT, false);
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
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        if (System.currentTimeMillis() - prefs.getLong("lastCheck", 0) > 6 * 3600_000L) checkUpdate(false);
    }

    private LinearLayout page(String name, String icon) {
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
            it.setBackground(i == idx ? round(0xFF1D2A4A, 12) : null);
            it.setTextColor(i == idx ? 0xFF4DA3FF : TXT);
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

    private TextView rowItem(LinearLayout parent, String label, View.OnClickListener l) {
        TextView t = text(label, 15, TXT, false);
        t.setPadding(0, (int) dp(11), 0, (int) dp(11));
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
        Monitor.startSession();
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
                        tvUpdate.setText("Versão instalada: " + Updater.installedName(this) + " ✅ (atualizado)");
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
        updateShizuku();
        updateChrome();
        if (m == null) return;
        SystemInfo s = m.sys;
        Locale us = Locale.US;
        gauge.set(s.ramUsedPct(), SystemInfo.gb(s.ramTotal - s.ramAvail) + " / " + SystemInfo.gb(s.ramTotal) + " GB");

        boolean adv = Shell.granted();
        tvFps.setText(m.fps >= 0 ? String.valueOf(m.fps) : "--");
        tvFps.setTextColor(m.fps < 0 ? TXT : m.fps >= 55 ? OK : m.fps >= 40 ? WARN : BAD);
        tvLow.setText(Float.isNaN(m.low1) ? "--" : String.valueOf(Math.round(m.low1)));
        tvCpu.setText(Float.isNaN(m.cpu) ? "n/d" : String.format(us, "%.1f°", m.cpu));
        tvCpu.setTextColor(Float.isNaN(m.cpu) ? TXT : m.cpu >= 50 ? BAD : m.cpu >= 43 ? WARN : OK);
        tvGpu.setText(Float.isNaN(m.gpu) ? "n/d" : String.format(us, "%.1f°", m.gpu));
        tvBat.setText(s.battPct + "%" + (s.charging ? " ⚡" : ""));
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

    private void updateChrome() {
        btnMonitor.setText(OverlayService.running ? "? Parar monitor" : "?? Monitor flutuante");
        tvPos.setText("Posi??o do monitor: " + POS[prefs.getInt("pos", 0) % POS.length] + " ?");
        boolean ov = Settings.canDrawOverlays(this);
        tvOverlayPerm.setText("Sobrepor a outros apps: " + (ov ? "? permitido" : "? negado (toque para permitir)"));
        boolean ok = Shell.granted();
        tvDot.setText(ok ? "? Shizuku ativo" : "? Shizuku off");
        tvDot.setTextColor(ok ? OK : WARN);
    }

    private String tip(Monitor.Sample m, boolean adv) {
        SystemInfo s = m.sys;
        StringBuilder t = new StringBuilder();
        boolean hot = (!Float.isNaN(m.cpu) && m.cpu >= 48) || s.tempC >= 43 || s.thermalStatus >= 2;
        if (hot) t.append("🔥 Celular quente: o desempenho cai (throttling). Tire a capa, pare de carregar e espere esfriar.\n");
        else if ((!Float.isNaN(m.cpu) && m.cpu >= 43) || s.tempC >= 40) t.append("🌡 Esquentando. Evite jogar carregando.\n");
        if (s.charging && s.tempC >= 38) t.append("⚡ Carregando enquanto esquenta: desconecte o cabo para jogar.\n");
        if (s.battPct < 20 && !s.charging) t.append("🔋 Bateria baixa reduz o desempenho. Carregue antes.\n");
        if (s.ramUsedPct() >= 85) t.append("🧠 RAM muito cheia: toque em \"Liberar RAM agora\".\n");
        if (s.storageFree * 100 / Math.max(1, s.storageTotal) < 15) t.append("💾 Menos de 15% de armazenamento livre.\n");
        if (s.swapTotal == 0) t.append("💡 Ative a Memória estendida (3 GB) nas configurações.\n");
        if (m.fps >= 0 && m.fps < 45 && !hot) t.append("🎯 FPS baixo sem calor: reduza efeitos/sombras no jogo ou use Liberar RAM.\n");
        if (m.fps >= 0 && !Float.isNaN(m.low1) && m.low1 < m.fps * 0.6f) t.append("📉 Muita oscilação (1% low baixo): prefira 60 FPS fixo e efeitos menores.\n");
        if (!adv) t.append("🔓 Ative o modo avançado (Shizuku) para medir FPS e temperatura de CPU/GPU.\n");
        if (t.length() == 0) t.append("✅ Tudo certo para jogar no gráfico alto estável.");
        return t.toString().trim();
    }

    private void updateShizuku() {
        boolean inst = Shell.installed(this), run = Shell.running(), ok = Shell.granted();
        if (ok) {
            tvShizuku.setText("✅ Ativo: FPS, CPU/GPU, Game Mode e limpeza profunda liberados.");
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

    private boolean aggressive() {
        return swAggr.isChecked() && Shell.granted();
    }

    private void boostOnly() {
        btnBoost.setEnabled(false);
        tvResult.setText("Liberando…");
        final boolean aggr = aggressive();
        new Thread(() -> {
            Booster.Result r = Booster.boost(this, aggr);
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

    private void play() {
        if (prefs.getBoolean("overlay", true) && !Settings.canDrawOverlays(this)) {
            askOverlay();
            return;
        }
        final Intent game = Booster.gameLaunchIntent(this);
        if (game == null) {
            toast("Wild Rift não encontrado neste celular.");
            return;
        }
        btnPlay.setEnabled(false);
        tvResult.setText("Otimizando…");
        final boolean aggr = aggressive();
        final String pkg = Booster.gamePackage(this);
        new Thread(() -> {
            Booster.Result r = Booster.boost(this, aggr);
            Booster.gameModePerformance(pkg);
            h.post(() -> {
                btnPlay.setEnabled(true);
                showResult(r);
                if (swDnd.isChecked() && dndAllowed()) enableDnd();
                Monitor.startSession();
                startForegroundService(new Intent(this, OverlayService.class));
                startActivity(game);
            });
        }).start();
    }

    private boolean dndAllowed() {
        return getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted();
    }

    private void enableDnd() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (!prefs.contains("prevFilter")) prefs.edit().putInt("prevFilter", nm.getCurrentInterruptionFilter()).apply();
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    }

    private void endSession() {
        startService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_STOP));
        if (prefs.contains("prevFilter") && dndAllowed()) {
            int prev = prefs.getInt("prevFilter", NotificationManager.INTERRUPTION_FILTER_ALL);
            getSystemService(NotificationManager.class).setInterruptionFilter(prev);
            prefs.edit().remove("prevFilter").apply();
        }
        String sum = Monitor.endSession();
        if (sum != null) {
            prefs.edit().putString("last", sum).apply();
            tvLast.setText(sum);
        }
        tvResult.setText("Sessão encerrada. Deixe o celular esfriar antes da próxima partida.");
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
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
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
        c.setBackground(round(CARD, 14));
        int p = (int) dp(12);
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

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }
}
