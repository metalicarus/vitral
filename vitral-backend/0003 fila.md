# ADR 0003: Fila própria para as miniaturas, em vez do JobRunr

**Status:** aceita

## Contexto

As miniaturas e prévias precisam ser geradas em segundo plano, para o upload continuar rápido, e não podem se perder se o servidor parar no meio do trabalho. O plano inicial era usar o JobRunr, que guarda a fila no banco, tenta de novo automaticamente e tem um painel de acompanhamento.

## Decisão

A geração usa uma fila própria e pequena:

- **Status no banco:** cada foto tem um status de miniatura (`PENDING`, `READY` ou `FAILED`).
- **Fila em memória:** logo após o upload, a foto entra numa fila processada por alguns threads (`VITRAL_MEDIA_WORKERS`).
- **Recuperação na inicialização:** ao subir, o servidor enfileira todas as fotos ainda `PENDING`. Isso cobre fotos enviadas antes da funcionalidade existir, fotos que estavam na fila quando o servidor parou e fotos enviadas enquanto o libvips não estava instalado.
- **Falhas separadas:** se o libvips rejeita o arquivo, a foto fica `FAILED`; se o libvips nem consegue rodar, ela continua `PENDING` e é tentada de novo na próxima inicialização.

## Por quê

Para um único tipo de tarefa, o JobRunr traria tabelas próprias, mais configuração e uma espera de alguns segundos entre as verificações da fila, enquanto a fila própria cabe em uma classe (`ThumbnailService`) e começa a trabalhar no instante do upload. O status no banco garante o essencial que se esperava do JobRunr: nada se perde quando o servidor para.

## Consequências

- Não há novas tentativas automáticas com espera entre elas, nem agendamento, nem painel. Se vídeos, pastas monitoradas ou IA precisarem disso, a troca para o JobRunr fica concentrada na fila, sem mudar o restante do código.
- Uma foto `FAILED` só é processada de novo se o status voltar a `PENDING`. Uma rota de administração para isso pode vir depois.