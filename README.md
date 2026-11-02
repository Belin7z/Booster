# Booster

Otimizador para jogos no Android: libera RAM, monitora FPS, temperatura e uso em tempo real, e gera relatórios de desempenho por jogo.

**[Baixar a versão mais recente](../../releases/latest)** (arquivo `.apk` em Assets). Depois de instalar, as atualizações chegam dentro do próprio app.

## O que faz

- Monitor flutuante com FPS (`x / taxa da tela`), CPU, GPU, RAM e bateria
- Detecta qualquer jogo instalado e faz a limpeza de RAM em três níveis
- Perfil próprio por jogo, relatório com nota, comparativo entre sessões e imagem para compartilhar
- Tempo de jogo por dia, mesmo quando o jogo não é aberto pelo Booster
- Painel durante o jogo com captura, gravação, volume, brilho e apps rápidos
- Medição de FPS e sensores por ADB sem fio embutido (sem root, sem instalar outro app)

## Compilar

```
gradle assembleRelease -PversionCode=1 -PversionName=1.0
```

A assinatura usa as variáveis `WRB_KEYSTORE` e `WRB_PASS` ou um `keystore.properties` local (fora do git).

## Publicar uma versão

Escrever as notas em `notas/vX.Y.md` (seções `### Novo`, `### Melhorado`, `### Corrigido`) e enviar a tag `vX.Y`. O GitHub Actions compila, assina e cria a release com o APK e o `latest.json`,
que o app consulta para se atualizar.

## Criador

**Belin7z** · [GitHub](https://github.com/Belin7z) · Instagram [@vdb.belin7z](https://instagram.com/vdb.belin7z)
