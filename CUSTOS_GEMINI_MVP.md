# Custos do Gemini para o Wild Rift AI Coach — MVP

Consulta às fontes oficiais em 14/11/2026. Valores em USD, modalidade Standard/interativa. Os cálculos abaixo são estimativas com premissas explícitas, não uma medição da sua conta.

## O que será enviado

O MVP consulta o servidor a cada 20 segundos enquanto o coach tem um estado válido. Envia JSON de relógio, ouro, rota e detecções disponíveis. A voz é gerada pelo Android. PNGs e MP4s de treinamento ficam no aparelho e são exportados pelo usuário; o app não os envia automaticamente ao Gemini.

Este orçamento não contempla análise contínua de vídeo na nuvem, armazenamento remoto dos vídeos ou treinamento do detector local. Esses usos exigem orçamento separado.

## Preços consultados

| Modelo | Entrada por 1 milhão de tokens | Saída por 1 milhão de tokens |
| --- | ---: | ---: |
| Gemini 2.5 Flash-Lite | US$ 0,10 | US$ 0,40 |
| Gemini 3.1 Flash-Lite | US$ 0,25 | US$ 1,50 |
| Gemini 3.5 Flash-Lite | US$ 0,30 | US$ 2,50 |

A saída cobrada inclui tokens de raciocínio. Confirme a disponibilidade do modelo na conta antes de configurar. A documentação atual informa que 2.5 Flash está em descontinuação, enquanto 2.5 Flash-Lite continua disponível sem data anunciada de encerramento. Não escolha 2.5 Flash como padrão de uma integração nova apenas por constar no exemplo antigo do projeto.

Fontes: [preços oficiais Gemini](https://ai.google.dev/gemini-api/docs/pricing), [ciclo de vida dos modelos](https://ai.google.dev/gemini-api/docs/deprecations).

## Cenário de referência: Gemini 3.1 Flash-Lite

Premissas: partidas de 30 minutos; 90 consultas por partida; 600 tokens de entrada e 60 tokens de saída por consulta; 30 dias de uso. O consumo por consulta é uma hipótese e precisa ser medido com a chave real. A contagem de saída deve incluir raciocínio.

Fórmula: `consultas × [(entrada × preço_entrada + saída × preço_saída) / 1.000.000]`.

Custo por consulta: US$ 0,00024. Custo por partida de 30 minutos: US$ 0,0216.

| Uso de um jogador | Consultas/mês | API/mês (USD) | Conversão ilustrativa em reais |
| --- | ---: | ---: | ---: |
| 1 partida/dia | 2.700 | US$ 0,65 | R$ 3,56 |
| 3 partidas/dia | 8.100 | US$ 1,94 | R$ 10,69 |
| 10 partidas/dia | 27.000 | US$ 6,48 | R$ 35,64 |

A conversão usa exclusivamente a hipótese **US$ 1 = R$ 5,50**; não é cotação atual. Os reais foram calculados antes do arredondamento do valor em USD. A cobrança efetiva depende do câmbio, tributos e condições do meio de pagamento.

Para 10 partidas/dia, mantendo as mesmas premissas: 2.5 Flash-Lite custaria US$ 2,27/mês; 3.5 Flash-Lite, US$ 8,91/mês. Não há evidência neste projeto de que um desses modelos seja mais preciso no jogo; essa escolha exige comparar orientações com partidas anotadas.

## Sensibilidade e escala

Se cada consulta passar a 4.000 tokens de entrada e 128 de saída, o mesmo cenário de 10 partidas/dia em 3.1 Flash-Lite sobe para **US$ 32,18/mês**, antes da hospedagem. O tamanho do estado e o raciocínio têm impacto maior que a frase curta exibida.

Com 100 jogadores usando 10 partidas/dia, o cenário de referência passa a 2,7 milhões de consultas e US$ 648/mês somente de Gemini. A estimativa não inclui chamadas extras, erros faturáveis, outros consumidores do token ou mudanças de preços.

## Hospedagem e franquias

Um Worker intermediário é necessário para guardar a chave do Gemini fora do APK. A Cloudflare publica plano gratuito com 100.000 solicitações/dia e limite de CPU por execução, e plano pago a partir de US$ 5/mês. O cenário individual de 10 partidas/dia gera 900 solicitações/dia; cabe na franquia de solicitações, mas o limite de CPU e as condições da conta precisam ser validados na implantação.

No cenário de referência, o total mensal para 3 partidas/dia seria US$ 1,94 com Worker gratuito, ou cerca de US$ 6,94 com a assinatura paga básica. Para 10 partidas/dia: US$ 6,48 ou US$ 11,48, respectivamente, enquanto os demais limites incluídos forem respeitados.

Gemini também oferece franquias gratuitas, mas os limites variam por modelo, projeto e conta. Não foi descontada franquia gratuita dos cálculos e não há promessa de operação gratuita contínua.

Fontes: [Cloudflare Workers](https://developers.cloudflare.com/workers/platform/pricing/), [limites Gemini](https://ai.google.dev/gemini-api/docs/rate-limits).

## Fechamento do MVP

A coleta local não cobra tokens. Para o piloto, configure um modelo habilitado, faça uma chamada real e acompanhe os campos `usage` retornados pelo servidor e os tokens mostrados pelo aplicativo. Compare o consumo medido com estas premissas antes de definir orçamento mensal. O painel de faturamento do provedor continua sendo a referência da cobrança.

Faltam conta/chave Gemini válidas, hospedagem e teste real para ativar a nuvem. O código do backend e o campo de configuração estão prontos; nenhuma conta foi contratada e nenhum gasto foi assumido nesta entrega. O custo de anotação, treinamento e validação do minimapa permanece separado e não pode ser determinado sem volume de dados, hardware e metas de avaliação.
