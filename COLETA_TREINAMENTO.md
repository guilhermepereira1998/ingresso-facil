# Coleta de capturas e partidas — beta 1.0.3

## Capturas PNG com o coach

1. Calibre as áreas usando um print do jogo na horizontal.
2. Ative **Salvar PNG a cada 5s** e inicie o coach. Na autorização Android, prefira compartilhar somente o Wild Rift quando essa opção estiver disponível.
3. Abra a partida. O app salva a tela completa em PNG sem perdas, o recorte do minimapa e os metadados da mesma imagem. O modo automático usa a resolução capturada completa, em vez da escala reduzida do modo normal; isso aumenta consumo de memória e armazenamento.
4. A opção **Salvar próxima captura da partida** pede uma amostra manual. Se o app estiver na vertical, a captura aguarda uma imagem horizontal.
5. Use **Encerrar coach / gravação** e depois **Exportar material de treino (.ZIP)**.

A coleta automática acompanha os ciclos de análise: a separação mínima é de 5 segundos, mas OCR, temperatura, rede e pausas podem aumentar esse intervalo. O limite é de 600 amostras por sessão. Com espaço livre abaixo de 200 MiB, novas capturas são suspensas. Uma imagem e seu minimapa só aparecem na exportação depois que a gravação completa da amostra foi concluída.

## Gravar partida em MP4

**Gravar partida em MP4 (modo de coleta)** encerra o coach e solicita uma nova autorização Android. Este modo não analisa nem verbaliza dicas durante a gravação.

O vídeo usa H.264/MP4 sem áudio, resolução com lado maior até 1280 pixels e alvo de 24 fps/4 Mbps (aproximadamente 30 MB por minuto; o tamanho real varia). Uma partida de 30 minutos pode gerar cerca de 900 MB. A gravação é segmentada quando a região capturada muda de tamanho ou gira; os arquivos JSON registram os intervalos relativos de cada segmento.

Encerre pela notificação ou pelo botão do aplicativo para concluir o MP4. Revogar a projeção pelo Android também encerra a sessão. Vídeos muito curtos podem ser descartados pelo codificador. Se o processo for encerrado abruptamente, o trecho aberto pode ficar incompleto; ele não é incluído no ZIP. O limite por arquivo é de até 1,9 GB ou o espaço disponível acima da reserva de 200 MiB, o que for menor; ao atingir esse limite, a gravação termina e o status informa o motivo.

A gravação foi implementada com MediaRecorder e MediaProjection, mas ainda precisa ser validada no aparelho físico. Não foi utilizado emulador, conforme solicitado.

## Onde ficam e como exportar

Os dados ficam na pasta de documentos privada do app. No armazenamento externo específico do aplicativo, o caminho usual é `Android/data/com.wildrift.aicoach/files/Documents/training/`. O Android pode limitar a navegação direta nessa pasta; use o botão de exportação para escolher uma pasta pública e salvar o ZIP.

O ZIP reúne os arquivos concluídos. Se houver uma coleta ativa, o app solicita encerrá-la antes de exportar. Os dados podem ocupar bastante espaço; faça sessões curtas de teste antes de gravar partidas inteiras. Desinstalar o app pode remover os dados privados: exporte o material antes. Nenhum PNG ou vídeo é enviado automaticamente ao servidor de IA.

## Uso no desenvolvimento

Cada sessão tem identificador próprio e metadados técnicos; cada amostra PNG inclui horário, resolução, calibração, hashes SHA-256 e, quando disponível, a leitura OCR correspondente. Essas leituras são marcadas como **não sendo anotações de verdade**: elas precisam de revisão antes de virar rótulos de treinamento. O material não contém a chave de IA nem o token de acesso.

Os PNGs de minimapa servem para anotação das classes; a tela completa e os vídeos ajudam a revisar contexto. Separe treino, validação e teste por partida/sessão e confira falsas detecções, omissões e posição do jogador. Ter mais capturas não comprova aumento de precisão por si só.

Para extrair imagens de um MP4 exportado, use em um computador com ffmpeg:

```sh
python ml_training/extract_training_frames.py partida.mp4 --every 5 --output amostras_partida
```

O script produz PNGs e proveniência aproximada, não rótulos YOLO. Foi verificado com um vídeo sintético; isso não mede precisão no jogo. Prefira revisar e anotar capturas reais antes de treinar.
