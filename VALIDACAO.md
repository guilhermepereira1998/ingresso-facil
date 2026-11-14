# Validação da beta 1.0.3 — coleta para treinamento

- Build local assembleDebug testDebugUnitTest lintDebug concluído com sucesso.
- 19 testes Android/JVM: 0 falhas, 0 erros, 0 ignorados. Incluem política de coleta, tamanhos de vídeo, ZIP sem arquivos incompletos e bloqueio de links para fora da pasta.
- 10 testes Node do backend: aprovados, com provedor simulado; incluem metadados de consumo de tokens.
- Extração de um MP4 sintético: 3 PNGs e proveniência produzidos e conferidos com ffmpeg.
- Lint: 0 erros bloqueantes, 57 avisos não bloqueantes.
- Assinatura APK v2 validada; mesmo certificado da beta 1.0.2.
- APK universal: arm64-v8a, armeabi-v7a, x86 e x86_64.
- Emulador não utilizado, conforme solicitado.
- MediaProjection/MediaRecorder, escrita PNG e exportação SAF ainda precisam ser validados no aparelho físico. Os testes JVM não comprovam compatibilidade do codificador nem gravação real da partida.
- Modelo do minimapa não treinado; Gemini sem chamada real e sem implantação.
- PNGs e vídeos são dados locais, não treinamento automático.
- SHA-256 do APK: eaaded17afeab25845c4f438cec0507a983df3b973aca01f41a73b0cacf1acf9
