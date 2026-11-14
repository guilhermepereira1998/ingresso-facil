# Wild Rift AI Coach — Android beta 1.0.2

Aplicativo Android em Kotlin, Android 8.0 ou superior (minSdk 26, targetSdk 35).

## Instalar e iniciar

1. Instale o APK beta fornecido. Quando solicitado pelo Android, permita instalar pelo aplicativo usado para abrir o arquivo.
2. Abra Wild Rift AI Coach e conceda a permissão de exibir sobre outros aplicativos. Autorize as notificações para acompanhar o serviço.
3. Em **Calibrar Recortes**, carregue um print de uma partida na horizontal. Mova as quatro áreas; arraste o canto inferior direito para redimensionar. Teste o OCR e salve.
4. Deixe a URL de IA vazia para usar o modo local. Um servidor próprio opcional deve usar HTTPS e responder `{"callout":"..."}`, com até 12 palavras. Informe também o token de acesso do seu servidor de IA, sem usar a chave do provedor no APK. O estado OCR será enviado exclusivamente ao endereço configurado pelo usuário.
5. Toque em **Iniciar**, autorize a captura de tela pelo Android e abra o jogo na horizontal. Quando houver opção de compartilhar só um aplicativo, selecione o jogo, ou compartilhe a tela inteira para trocar de aplicativo durante a sessão.
6. Toque na bolha para pausar/retomar. Mantenha pressionado e solte para abrir os controles e testar a voz. Para uma nova partida, encerre e inicie uma nova sessão de captura.

A voz depende de um mecanismo Android TextToSpeech com português instalado. As permissões são solicitadas pelo sistema; o aplicativo não concede permissões por conta própria.

## O que esta versão contém

- Captura passiva via MediaProjection, serviço em primeiro plano, redimensionamento ao girar a tela e encerramento pelo painel Android.
- OCR local ML Kit, recortes normalizados e calibração individual persistente.
- Orientações por voz com prioridades, descarte de avisos antigos, intervalos por categoria e foco de áudio.
- Redução da frequência de análise por temperatura da bateria.
- Motor de lembretes locais, cliente opcional de IA via HTTPS e arquivos de exemplo do servidor.
- Gradle Wrapper, workflow GitHub Actions e testes de regressão.

## Limitações verificadas no material fornecido

**O ZIP original não contém dataset de partidas nem o modelo `yolov8n_wr_minimap_int8.tflite`.** Por isso esta versão não detecta campeões, aliados, inimigos ou objetivos no minimapa. A tela de diagnóstico informa essa ausência. Não há detecções simuladas. Os arquivos `ml_training/` são um ponto de partida para treinamento posterior; o adaptador agora trata FLOAT32, INT8 e UINT8, NHWC/NCHW e saídas YOLOv8 com NMS. Ainda exige validação com os pesos reais e com o arquivo de contrato `minimap_model.json`. Consulte `ml_training/README.md`.

A posição real do jogador também não é extraída do material recebido. Alertas de proximidade ficam desabilitados quando essa posição é desconhecida, para evitar tratar o centro do minimapa como a posição do jogador.

**A IA na nuvem não está provisionada.** O diretório `cloud_backend/` contém um servidor para Workers ou Deno, com autenticação, validação e testes com respostas simuladas. Ele exige credencial do provedor, modelo habilitado e implantação próprios. Consulte `cloud_backend/README.md`. Nenhuma chave de IA é distribuída no APK.

Os lembretes de objetivos usam os marcos fixos de 05:00 e 12:00 previstos no plano fornecido. Não identificam nascimentos ou mortes reais de objetivos, não acompanham alterações de temporada e exigem revisão conforme o modo/versão do jogo. O lembrete de ouro usa ouro >= 1300 e relógio >= 03:00; não mede quanto tempo o jogador ficou fora da base.

Precisão de OCR de 98%, inferência abaixo de 40 ms, latência total inferior a 1800 ms e estabilidade térmica de 40 minutos são metas do plano; não são resultados medidos nesta entrega. Compatibilidade de captura, áudio e desempenho ainda requer teste no aparelho e com capturas reais do jogo. A assinatura é de desenvolvimento (debug), para instalação direta e testes; não é uma versão assinada para publicação na loja.

O código usa APIs públicas do Android, sem root, AccessibilityService, leitura de memória de outros processos ou automação de toques. Isso não comprova aprovação da Riot nem garante ausência de penalidades; o documento fornecido não demonstra essa aprovação.

## Compilar

Requisitos: JDK 17 (ou 21), Android SDK 35, Build Tools 34.0.0 e acesso aos repositórios Maven/Google.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon
```

Windows:

```bat
gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

O backend pode ser testado sem credenciais com `node --test cloud_backend/macro-worker.test.mjs`.

No GitHub Actions, o workflow **Wild Rift AI Coach - APK e testes** gera o artefato **WildRiftAICoach-1.0.2-beta** e relatórios de validação.

## Referências da implementação

- https://developer.android.com/media/grow/media-projection
- https://developer.android.com/build/releases/agp-8-6-0-release-notes
