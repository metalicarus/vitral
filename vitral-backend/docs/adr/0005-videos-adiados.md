# ADR 0005: Processamento de vídeo adiado

**Status:** aceita

## Contexto

O roadmap do M1 previa processar vídeos: aceitá-los no upload e nas pastas monitoradas, gerar miniaturas e, quando o formato não toca em qualquer lugar, uma versão em H.264. Para isso o servidor precisaria do FFmpeg, chamado como processo externo, assim como hoje chama o libvips para as fotos. No client, a reprodução dependeria do player de mídia do JavaFX, que não toca vários formatos comuns de celular (HEVC, por exemplo) sem conversão.

## Decisão

O processamento de vídeo fica para depois. O foco, por enquanto, é fazer as fotos funcionarem muito bem: catálogo, organização, álbuns e, a seguir, compartilhamento.

## Por quê

- **Dependência grande:** o FFmpeg seria uma segunda ferramenta externa obrigatória para instalar e manter, maior que o libvips, num projeto que quer ser leve e fácil de instalar.
- **Custo no client:** tocar vídeo no JavaFX exigiria converter boa parte dos arquivos no servidor, o que pesa em hardware modesto.
- **Valor maior em outras partes:** para uma biblioteca de família, álbuns e compartilhamento são usados todos os dias; vídeo é importante, mas pode esperar sem bloquear o resto.

## Consequências

- O servidor continua aceitando só fotos (JPEG, PNG, HEIC/HEIF e WebP). O client ignora vídeos ao enviar uma pasta, o servidor recusa um vídeo enviado pela API com `415 unsupported_media_type`, e as pastas monitoradas simplesmente não os catalogam.
- O tipo `VIDEO` já existe no catálogo (`assets.type`), então a volta do assunto não exige mudar a tabela.
- Quando o tema voltar, a fila de tarefas própria (ADR 0003) é o lugar natural para a conversão de vídeo, talvez com a troca para o JobRunr que aquela ADR já prevê.
