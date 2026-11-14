# Validação da beta 1.0.2

- Build local: assembleDebug testDebugUnitTest lintDebug concluído com sucesso.
- 15 testes Android/JVM: 0 falhas, 0 erros, 0 ignorados.
- 9 testes Node do backend: aprovados, com provedor simulado e sem chave real.
- Lint: 0 erros bloqueantes; 44 avisos, principalmente textos fixos e localização.
- Script Python: sintaxe validada; --check-only confirmou ausência do dataset antes de treinar.
- Assinatura APK v2 validada com apksigner. Certificado de desenvolvimento, igual à beta anterior.
- APK universal: arm64-v8a, armeabi-v7a, x86 e x86_64.
- Emulador não utilizado, conforme solicitação. Não testado em aparelho físico nem em partida real.
- O leitor TFLite possui testes de quantização e pós-processamento, mas falta validação com modelo treinado real.
- Servidor de IA implementado e testado com mocks; sem implantação e sem chamada real ao Gemini.
- SHA-256 do APK: 3c4b9fda3326ae7e6679640b20ca76e646b7182ef9cd8f9417fcf4bc65c7a70f
