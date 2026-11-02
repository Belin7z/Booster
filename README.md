# Booster

App Android para melhorar o desempenho em jogos (feito para Wild Rift no POCO X6 Pro).

- Monitor de RAM, temperatura de bateria, CPU/GPU e FPS em tempo real
- Liberação de RAM antes de jogar e monitor flutuante durante a partida
- Medição de FPS e sensores via Shizuku (sem root)
- Atualização dentro do app, a partir do repositório Booster-releases

## Compilar

```
gradle assembleRelease -PversionCode=1 -PversionName=1.0
```

A assinatura usa as variáveis `WRB_KEYSTORE` e `WRB_PASS` ou um `keystore.properties` local (fora do git).

## Publicar uma versão

Criar e enviar uma tag `vX.Y`. O GitHub Actions compila, assina e publica o APK e o `latest.json`
no repositório Booster-releases.
