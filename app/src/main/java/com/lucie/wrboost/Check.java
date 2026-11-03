package com.lucie.wrboost;

import android.content.Context;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

// Testa cada recurso no aparelho e diz o que funciona e o que falta. Rode fora da thread principal.
public final class Check {
    private Check() {
    }

    public static final class Item {
        public final boolean ok;
        public final String name, detail;

        Item(boolean ok, String name, String detail) {
            this.ok = ok;
            this.name = name;
            this.detail = detail;
        }
    }

    public static List<Item> run(Context c) {
        List<Item> l = new ArrayList<>();
        boolean sh = Shell.granted();
        l.add(new Item(sh, "Modo avançado", sh ? (Adb.connected() ? "ADB embutido conectado" : "Shizuku conectado") : "Conecte o ADB embutido ou o Shizuku para FPS, limpeza forte e instalação silenciosa."));
        boolean usage = Games.hasUsageAccess(c);
        l.add(new Item(usage, "Acesso de uso", usage ? "Detecção automática de jogos ativa" : "Sem ele a bolha não aparece sozinha ao abrir o jogo."));
        boolean ov = Settings.canDrawOverlays(c);
        l.add(new Item(ov, "Sobrepor a outros apps", ov ? "Monitor e bolha podem aparecer" : "Permita para ver o monitor e a bolha."));
        boolean dnd = Booster.dndAllowed(c);
        l.add(new Item(dnd, "Não perturbe", dnd ? "Controle liberado" : "Permita o acesso para silenciar notificações no jogo."));
        boolean ws = Settings.System.canWrite(c) || sh;
        l.add(new Item(ws, "Brilho e taxa da tela", ws ? "Podem ser ajustados no modo jogo" : "Precisa da permissão de modificar configurações ou do modo avançado."));
        boolean inst = c.getPackageManager().canRequestPackageInstalls();
        l.add(new Item(inst, "Instalar atualizações", inst ? "Permitido" : "Permita \"instalar apps desconhecidos\" para o Booster."));

        if (sh) {
            String ff = Shell.exec("pm has-feature android.software.freeform_window_management 2>&1; settings get global enable_freeform_support", 4000);
            boolean free = ff != null && (ff.contains("true") || ff.trim().endsWith("1"));
            l.add(new Item(free, "Janela flutuante", free ? "O sistema aceita janelas livres" : "O sistema não anunciou suporte; os apps rápidos podem abrir em tela cheia."));

            String game = Monitor.activeGame();
            if (game == null) {
                l.add(new Item(false, "Leitura de FPS", "Abra um jogo para testar. Motivo atual: " + (Monitor.fpsNote.isEmpty() ? "sem jogo" : Monitor.fpsNote)));
            } else {
                boolean fps = Monitor.fpsNote.isEmpty() && Monitor.latest != null && Monitor.latest.fps > 0;
                l.add(new Item(fps, "Leitura de FPS", fps ? "Lendo " + Monitor.latest.fps + " FPS" : "Falha: " + (Monitor.fpsNote.isEmpty() ? "aguardando quadros" : Monitor.fpsNote)));
            }
            String t = Shell.exec("dumpsys thermalservice | head -n 3", 4000);
            l.add(new Item(t != null && !t.trim().isEmpty(), "Serviço térmico", t != null && !t.trim().isEmpty() ? "Respondendo" : "Sem resposta"));
        } else {
            l.add(new Item(false, "Janela flutuante e FPS", "Dependem do modo avançado."));
        }
        return l;
    }

    public static String text(List<Item> items) {
        StringBuilder b = new StringBuilder();
        for (Item i : items) b.append(i.ok ? "✓ " : "✗ ").append(i.name).append(" — ").append(i.detail).append('\n');
        return b.toString().trim();
    }
}
