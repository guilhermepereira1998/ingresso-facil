# Modelo do minimapa — pendência de dados e pesos

Não há `.tflite`, imagens de treino ou rótulos no ZIP recebido. O APK não simula detecções.

O projeto público https://universe.roboflow.com/trinh-dinh-hung-vnl3v/wild-rift-x93jb tem imagens de Wild Rift e 83 classes de campeões, com licença CC BY 4.0 declarada pelo autor. A imagem de exemplo examinada mostra a tela de jogo. Esse conjunto não corresponde às quatro classes de minimapa previstas aqui, não identifica por si só se cada ícone é aliado/inimigo e não fornece a posição real do jogador no contrato deste app. Não foi baixado, incorporado ou usado para treinamento. Licença, rótulos e adequação de qualquer dado reaproveitado precisam ser conferidos antes da incorporação.

## Dados necessários

Recortes reais do minimapa de partidas, com autorização de uso e caixas YOLO anotadas nas classes abaixo, nesta ordem:

0. aliado
1. inimigo
2. objetivo_ativo
3. onda_minions

Inclua variação de HUD, escala, resolução, mapa/modo, efeitos e situações negativas. Separe treino e validação por partida para reduzir vazamento. Use recortes com o mesmo redimensionamento para 320x320 aplicado no APK. A posição do próprio jogador exige um detector ou mecanismo adicional; o app mantém alertas de proximidade desligados quando ela é desconhecida.

Estrutura: `datasets/wildrift_minimap/images/{train,val}` e `datasets/wildrift_minimap/labels/{train,val}`. Cada imagem deve ter um arquivo `.txt`, inclusive vazio para uma imagem negativa. Não existe quantidade universal de imagens que garanta a precisão do plano.

## Validar e treinar

Instale PyYAML e Ultralytics em um ambiente Python separado, com as dependências de exportação indicadas pelo fornecedor. Registre versões e licença do conjunto de dados utilizado.

```sh
python train_minimap_yolov8n.py --check-only
python train_minimap_yolov8n.py --device cpu --epochs 80
```

Para CUDA configurado, use `--device 0`. O script não inicia treinamento se o dataset estiver ausente. A exportação atual usa LiteRT/INT8 e dados representativos explícitos. O treino/exportação não foram executados nesta entrega, pois faltam dados anotados.

## Contrato Android

O leitor trata FLOAT32, INT8 e UINT8 com scale/zeroPoint reais do tensor; aceita entradas RGB 320x320 NHWC ou NCHW e saídas YOLOv8 `[1,8,N]` ou `[1,N,8]`, sem NMS embutido. Há NMS por classe no aplicativo. Outros formatos, outros números de classes e modelos sem metadados são recusados.

Depois de validar o modelo exportado em imagens de partidas reservadas e no runtime Android, copie somente os artefatos aprovados para `app/src/main/assets/`:

- `yolov8n_wr_minimap_int8.tflite`
- `minimap_model.json` (o exemplo em `minimap_model.example.json` mostra o contrato, não métricas reais)

Informe explicitamente `coordinates: normalized` ou `pixels`, conforme o modelo. Não determine a unidade olhando valores isolados. Exportações LiteRT recentes podem usar operações não suportadas pelo runtime TFLite 2.16.1 do APK: a compatibilidade com pesos reais permanece uma etapa obrigatória antes de ativar o modelo.

Fontes: https://docs.ultralytics.com/integrations/litert e https://docs.ultralytics.com/modes/export.
