# ADR 0004: Pastas monitoradas catalogadas no lugar

**Status:** aceita

## Contexto

Antes do app mobile, o backup do celular é feito por ferramentas de sincronização como o Syncthing, que mantêm uma pasta do servidor igual à câmera do celular. O Vitral precisa colocar essas fotos na biblioteca de um usuário sem atrapalhar a sincronização, e sem ocupar o disco duas vezes.

## Decisão

- **Catalogar no lugar, somente leitura:** as fotos de uma pasta monitorada não são copiadas para `library/`. O catálogo guarda a pasta (`assets.folder_id`) e o caminho dentro dela. O Vitral nunca cria, altera, move nem apaga nada na pasta. As miniaturas e prévias ficam em `derivatives/`, como as das fotos enviadas.
- **Cadastro pela API, por administradores:** as pastas ficam no banco (`watched_folders`) e são adicionadas e removidas em `/api/v1/admin/folders`, sem reiniciar o servidor. Cada pasta pertence a um usuário. Pastas não podem se sobrepor nem ficar dentro da pasta de dados.
- **Índice de arquivos vistos:** a tabela `folder_files` guarda, para cada arquivo da pasta, o tamanho, a data de modificação e o SHA-256 da última leitura. Uma varredura só lê de novo os arquivos cujo tamanho ou data mudou, então varrer uma pasta grande outra vez é rápido, mesmo quando as fotos já estavam na biblioteca por outro caminho, como o upload.
- **Mesma regra de duplicatas:** uma foto com o mesmo conteúdo de outra do usuário não vira um novo item; o arquivo só é ligado ao item existente. Um arquivo renomeado ou movido dentro da pasta mantém o item, que passa a apontar para o novo caminho. Um arquivo alterado no lugar atualiza o item.
- **Quando varrer:** na inicialização, a cada `VITRAL_IMPORT_SCAN_INTERVAL` (15 minutos) e alguns segundos depois de uma mudança percebida pelo sistema operacional (`WatchService`, inotify no Linux). Uma rajada de mudanças gera uma só varredura, `VITRAL_IMPORT_SETTLE` (5 segundos) depois da última. Arquivos modificados há menos que isso ficam para uma varredura logo em seguida, para não ler uma foto pela metade.
- **Fotos apagadas ficam no catálogo:** quando um arquivo some da pasta, o item continua na biblioteca, sem o original para baixar. Se a pasta inteira some, por exemplo com um disco desmontado, a varredura não roda.

## Por quê

Copiar as fotos para `library/` dobraria o espaço usado e criaria duas cópias que podem divergir. Mover os arquivos quebraria a sincronização. Catalogar no lugar mantém um único original, legível sem o Vitral, como pede o princípio de formatos abertos.

O cadastro pela API permite adicionar pastas com o servidor no ar e, depois, por uma tela do client. No arquivo de configuração, cada mudança exigiria reiniciar o servidor, e as listas em variáveis de ambiente são desajeitadas no Docker.

Manter as fotos apagadas evita o pior caso de remover automaticamente: um disco que não montou, ou uma sincronização que falhou, esvaziaria a galeria. A remoção deliberada vai chegar com a lixeira, no M2.

## Consequências

- Um item pode estar com o original ausente. O download responde `404 original_missing`, e a galeria e o visualizador continuam funcionando com a miniatura e a prévia.
- Remover uma pasta tira do catálogo os itens cujo original está nela. Os itens que só estavam ligados a ela, como fotos também enviadas pelo client, continuam na biblioteca.
- O monitoramento usa um "watch" do inotify por subpasta. Sem watches disponíveis, as fotos novas só aparecem na varredura periódica. O servidor avisa uma vez no log.
- As varreduras rodam uma de cada vez, em uma thread própria, e cada arquivo é gravado no banco numa transação curta, depois da leitura e do hash, para não segurar a trava de escrita do SQLite (ADR 0002).
- A mudança de unicidade de `assets.relative_path` exigiu reconstruir a tabela na migração V4, já que o SQLite não altera restrições existentes.
