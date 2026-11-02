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

Criar e enviar uma tag `vX.Y`. O GitHub Actions compila, assina e cria a release com o APK e o `latest.json`,
que o app consulta para se atualizar.

POCO/duchamp_in/duchamp:16/BP2A.250605.031.A3/OS3.0.302.0.WNLINXM:user/release-keys

      #00 pc 00000000005c4eb4  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat ([DEDUPED]+4)
      #01 pc 0000000000764bbc  /apex/com.android.art/lib64/libart.so (nterp_helper+2732) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #02 pc 0000000000dc4d2e  /data/app/~~eS3t8X7v-h96BTl1zUrtYg==/com.itsaky.androidide-H76SrYmP8DltcHn2snKYIQ==/oat/arm64/base.vdex (org.lsposed.hiddenapibypass.HiddenApiBypass.getDeclaredMethods+186)
      #03 pc 0000000000764184  /apex/com.android.art/lib64/libart.so (nterp_helper+116) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #04 pc 0000000000dc4b8e  /data/app/~~eS3t8X7v-h96BTl1zUrtYg==/com.itsaky.androidide-H76SrYmP8DltcHn2snKYIQ==/oat/arm64/base.vdex (org.lsposed.hiddenapibypass.HiddenApiBypass.setHiddenApiExemptions+10)
      #05 pc 00000000003345c0  /apex/com.android.art/lib64/libart.so (art_quick_invoke_static_stub+640) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #06 pc 00000000006963f0  /apex/com.android.art/lib64/libart.so (bool art::interpreter::DoCall<false>(art::ArtMethod*, art::Thread*, art::ShadowFrame&, art::Instruction const*, unsigned short, bool, art::JValue*)+2400) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #07 pc 000000000073f2e8  /apex/com.android.art/lib64/libart.so (void art::interpreter::ExecuteSwitchImplCpp<false>(art::interpreter::SwitchImplContext*)+700) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #08 pc 000000000041e788  /apex/com.android.art/lib64/libart.so (ExecuteSwitchImplAsm+8) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #09 pc 00000000005107c8  /data/app/~~eS3t8X7v-h96BTl1zUrtYg==/com.itsaky.androidide-H76SrYmP8DltcHn2snKYIQ==/oat/arm64/base.vdex (com.termux.shared.reflection.ReflectionUtils.bypassHiddenAPIReflectionRestrictions+0)
      #10 pc 000000000041daac  /apex/com.android.art/lib64/libart.so (artQuickToInterpreterBridge+1260) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #11 pc 000000000034ba18  /apex/com.android.art/lib64/libart.so (art_quick_to_interpreter_bridge+88) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #12 pc 0000000000764184  /apex/com.android.art/lib64/libart.so (nterp_helper+116) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #13 pc 0000000000b0956a  /data/app/~~eS3t8X7v-h96BTl1zUrtYg==/com.itsaky.androidide-H76SrYmP8DltcHn2snKYIQ==/oat/arm64/base.vdex (kotlin.TuplesKt.getContext+10)
      #14 pc 000000000075bc6c  /apex/com.android.art/lib64/libart.so (nterp2_op_invoke_static+44) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #15 pc 0000000000b0a3ae  /data/app/~~eS3t8X7v-h96BTl1zUrtYg==/com.itsaky.androidide-H76SrYmP8DltcHn2snKYIQ==/oat/arm64/base.vdex (kotlin.TuplesKt.setTermuxAppEnvironment+574)
      #16 pc 000000000075bc6c  /apex/com.android.art/lib64/libart.so (nterp2_op_invoke_static+44) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #17 pc 00000000005041bc  /data/app/~~eS3t8X7v-h96BTl1zUrtYg==/com.itsaky.androidide-H76SrYmP8DltcHn2snKYIQ==/oat/arm64/base.vdex (com.termux.app.TermuxApplication.onCreate+316)
      #18 pc 0000000000764904  /apex/com.android.art/lib64/libart.so (nterp_helper+2036) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #19 pc 000000000043f136  /data/app/~~eS3t8X7v-h96BTl1zUrtYg==/com.itsaky.androidide-H76SrYmP8DltcHn2snKYIQ==/oat/arm64/base.vdex (com.itsaky.androidide.app.IDEApplication.onCreate+34)
      #20 pc 0000000000791c74  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (android.app.Instrumentation.callApplicationOnCreate+68)
      #21 pc 00000000006fe288  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (android.app.ActivityThread.handleBindApplication+6200)
      #22 pc 00000000006f7390  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (android.app.ActivityThread.-$$Nest$mhandleBindApplication+48)
      #23 pc 00000000006f5a3c  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (android.app.ActivityThread$H.handleMessage+7436)
      #24 pc 0000000000a0e6d8  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (android.os.Handler.dispatchMessage+152)
      #25 pc 0000000000a53d10  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (android.os.Looper.loopOnce+1264)
      #26 pc 0000000000a537a4  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (android.os.Looper.loop+244)
      #27 pc 0000000000708460  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (android.app.ActivityThread.main+1568)
      #28 pc 00000000003345c0  /apex/com.android.art/lib64/libart.so (art_quick_invoke_static_stub+640) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #29 pc 00000000002a42bc  /apex/com.android.art/lib64/libart.so (art::ArtMethod::Invoke(art::Thread*, unsigned int*, unsigned int, art::JValue*, char const*)+156) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #30 pc 0000000000328980  /apex/com.android.art/lib64/libart.so (_jobject* art::InvokeMethod<(art::PointerSize)8>(art::ScopedObjectAccessAlreadyRunnable const&, _jobject*, _jobject*, _jobject*, unsigned long)+616) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #31 pc 000000000031bb70  /apex/com.android.art/lib64/libart.so (art::Method_invoke(_JNIEnv*, _jobject*, _jobject*, _jobjectArray*) (.__uniq.165753521025965369065708152063621506277)+36) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #32 pc 00000000010ef3b4  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (art_jni_trampoline+116)
      #33 pc 0000000000dcd1f4  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run+116)
      #34 pc 0000000000dd6320  /data/misc/apexdata/com.android.art/dalvik-cache/arm64/boot.oat (com.android.internal.os.ZygoteInit.main+3360)
      #35 pc 00000000003345c0  /apex/com.android.art/lib64/libart.so (art_quick_invoke_static_stub+640) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #36 pc 00000000003335b8  /apex/com.android.art/lib64/libart.so (art::JValue art::InvokeWithVarArgs<_jmethodID*>(art::ScopedObjectAccessAlreadyRunnable const&, _jobject*, _jmethodID*, std::__va_list)+1536) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #37 pc 0000000000703b7c  /apex/com.android.art/lib64/libart.so (art::JNI<true>::CallStaticVoidMethodV(_JNIEnv*, _jclass*, _jmethodID*, std::__va_list)+288) (BuildId: 3cbf3abf381359e1b115f3319c1ca145)
      #38 pc 000000000010f568  /system/lib64/libandroid_runtime.so (_JNIEnv::CallStaticVoidMethod(_jclass*, _jmethodID*, ...)+104) (BuildId: 338b6bbd4189b5d95056ba391de25352)
      #39 pc 000000000013d184  /system/lib64/libandroid_runtime.so (android::AndroidRuntime::start(char const*, android::Vector<android::String8> const&, bool)+1204) (BuildId: 338b6bbd4189b5d95056ba391de25352)
      #40 pc 0000000000004698  /system/bin/app_process64 (main+1464) (BuildId: 8298230449e9ce4665160e74afe511e7)
      #41 pc 000000000006c1a8  /apex/com.android.runtime/lib64/bionic/libc.so (__libc_init+120) (BuildId: 9511d9cb89af590535458e21a5811d1a)

