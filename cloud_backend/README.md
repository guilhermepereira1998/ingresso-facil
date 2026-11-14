# Servidor opcional de IA

O servidor foi implementado e testado localmente com respostas simuladas. Não está implantado e não foi testado com uma chave real de Gemini.

## Configuração necessária

- Conta de hospedagem Cloudflare Workers ou ambiente Deno/Supabase com permissão de implantação.
- `GEMINI_API_KEY`: chave válida do provedor, armazenada somente como segredo no servidor.
- `GEMINI_MODEL`: identificador de um modelo habilitado para essa chave e compatível com generateContent e resposta JSON.
- `COACH_ACCESS_TOKEN`: segredo próprio para autorizar o aplicativo a chamar este servidor. Use um token aleatório, por exemplo com `openssl rand -hex 32`.

Nunca coloque a chave Gemini no APK ou no GitHub. O token de acesso ao seu servidor é informado no novo campo do APK e salvo no armazenamento privado do aplicativo; backup do app está desabilitado. Compartilhar esse token permite que outros consumam a quota do servidor.

## Cloudflare Workers

Com Node e Wrangler instalados pela fonte oficial, execute dentro de `cloud_backend`:

```sh
npx wrangler login
npx wrangler secret put GEMINI_API_KEY
npx wrangler secret put GEMINI_MODEL
npx wrangler secret put COACH_ACCESS_TOKEN
npx wrangler deploy
```

Copie o endereço HTTPS real retornado pela implantação e acrescente `/macro`. Informe essa URL e o `COACH_ACCESS_TOKEN` no APK. Não há uma URL ativa nesta entrega. Configure quotas e limites na conta do provedor conforme o uso pretendido; o intervalo de 20 segundos do APK não é um controle global de quota.

`GET /health` informa se as variáveis estão presentes, sem expor valores. Esse endpoint não comprova validade da chave, saldo ou disponibilidade do modelo; isso exige uma chamada real autenticada. `POST /macro` requer `Authorization: Bearer TOKEN` e JSON `{"state":{...}}`.

Para Deno/Supabase, `edge_function_macro.ts` adapta o mesmo handler; configure os mesmos segredos no provedor. Quando a plataforma tiver uma autenticação adicional, compatibilize essa camada com o cliente Android antes de usar a URL.

## Comportamento e testes

O backend valida o estado, impõe limite de corpo e até 12 palavras de orientação, usa prompt fixo no servidor e trata erros de quota, formato e timeout. Só envia estado estruturado; descarta o texto bruto de depuração OCR. A chamada do provedor tem timeout de 3 segundos. A orientação não é uma confirmação visual dos dados e não substitui um modelo do minimapa.

```sh
node --test macro-worker.test.mjs
```

Os testes usam respostas simuladas e não consomem quota. O aplicativo mostra estado da conexão e mantém as regras locais quando a consulta falha. Latência real, quotas, custo e disponibilidade precisam ser verificados após a implantação.

Fontes oficiais: https://developers.cloudflare.com/workers/configuration/secrets/ e https://ai.google.dev/api/generate-content.
