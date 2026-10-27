# ADR 0002: Banco de dados SQLite

**Status:** aceita

## Contexto

O servidor precisa guardar o catálogo: usuários, sessões e, a partir do M1, os metadados das fotos, álbuns e a fila de tarefas. As fotos em si ficam em pastas comuns no disco, fora do banco. As opções avaliadas foram PostgreSQL, MariaDB e SQLite.

## Decisão

O Vitral usa SQLite, com o driver `org.xerial:sqlite-jdbc` e o `SQLiteDialect` do Hibernate.

- **Sem serviços extras:** o servidor sobe com um `java -jar`, sem banco separado e sem Docker. Isso vale para desenvolvimento, testes e produção, e combina com o princípio de ser leve.
- **Simples de mover:** o catálogo inteiro é um arquivo, `vitral.db`.

## Consequências

- **Uma escrita por vez:** o SQLite aceita leituras em paralelo, mas serializa as escritas. Para reduzir o impacto, as conexões usam modo WAL (leituras não esperam escritas), `transaction_mode=IMMEDIATE` (transações pegam a trava de escrita ao começar, então esperam na fila em vez de falhar no meio) e `busy_timeout` de 5 segundos. Para uma biblioteca de família isso é suficiente; o processamento pesado do M1 deve fazer escritas curtas.
- **Backup e migração:** copiar o `vitral.db` com o servidor ligado pode gerar uma cópia inconsistente. O caminho seguro é parar o servidor ou usar `VACUUM INTO` / `.backup` do próprio SQLite; o backup automático do Vitral deve usar `VACUUM INTO`.
- **Datas e ids:** datas são gravadas como milissegundos UTC (INTEGER) por um conversor JPA, e UUIDs como texto, para não depender do tratamento de tipos do driver.
- **Fila de tarefas:** o JobRunr, previsto inicialmente para o M1, tem suporte a SQLite. A fila acabou sendo própria (ADR 0003), com o status das tarefas no próprio SQLite.
- **Busca por IA (futura):** sem pgvector, a busca semântica precisará de outra solução, como a extensão `sqlite-vec` ou busca vetorial feita na própria aplicação. Fica para a fase de IA.
- **Saída, se um dia for preciso:** as entidades usam JPA, então migrar para PostgreSQL exigiria principalmente reescrever as migrações SQL e as configurações, não o código das funcionalidades.
- **Modo nativo:** o driver JDBC genérico (`db-kind=other`) funciona apenas em modo JVM. Se o modo nativo virar prioridade, será preciso uma extensão Quarkus para SQLite.