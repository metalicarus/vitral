# Vitral · Client desktop

Aplicativo desktop do [Vitral](../README.md) para navegar pelas fotos guardadas no servidor. Ele fala com o servidor só pela API REST, a mesma que o app mobile e a interface web vão usar.

> **Status:** primeira versão. Faz login, mostra a galeria, abre cada foto no visualizador, envia fotos ao servidor e, para administradores, gerencia contas e pastas monitoradas.

## O que já faz

- **Login:** pede o endereço do servidor, o e-mail e a senha. Lembra o endereço e o e-mail para a próxima vez; a senha e o token da sessão nunca são gravados.
- **Primeiro acesso:** num servidor que ainda não tem contas, a tela de login pede também o nome e a confirmação da senha e cria a conta de administrador. Não é preciso nenhum comando para começar.
- **Galeria:** mostra as fotos da mais recente para a mais antiga, agrupadas por mês, em quadrados que se ajustam à largura da janela. Mais fotos carregam sozinhas conforme você rola.
- **Miniaturas:** baixadas em segundo plano por poucas conexões de cada vez, com as mais recentes guardadas em memória. Enquanto o servidor ainda gera a miniatura, aparece um relógio no lugar e o app pergunta de novo sozinho, em intervalos crescentes; se o servidor não conseguiu gerá-la, aparece um alerta.
- **Envio de fotos:** pelo botão **Enviar fotos** (escolhendo fotos ou uma pasta inteira) ou arrastando fotos e pastas para a janela.
  - O app procura fotos JPEG, PNG, HEIC e WebP, inclusive dentro de subpastas, e pula arquivos e pastas ocultos, como caches.
  - Antes de enviar, calcula o SHA-256 de cada arquivo e pergunta ao servidor quais ele ainda não tem (`POST /api/v1/assets/check`). Só esses são enviados, então mandar a mesma pasta de novo, ou retomar um envio interrompido, não envia nada repetido. Cópias idênticas dentro da seleção vão uma vez só.
  - Junto com cada foto vai a data de modificação do arquivo, que o servidor usa como data da foto quando ela não tem EXIF.
  - Uma barra no rodapé mostra o andamento e permite cancelar. No fim, ela resume o que foi enviado, o que o servidor já tinha e o que falhou, com o motivo de cada falha em **Ver problemas**.
- **Visualizador:** um clique numa foto a abre do maior tamanho que a janela permite, sobre um fundo escuro. Ele mostra a prévia de até 1440 px, e não o original, que pode ter dezenas de megabytes; enquanto ela carrega, a miniatura aparece ampliada. As fotos vizinhas já vêm carregadas, então passar de uma para outra é imediato.
  - Teclas: **←** e **→** passam entre as fotos, **I** mostra os detalhes e **Esc** fecha.
  - Os detalhes trazem a data (e de onde ela veio), a câmera, a lente, a exposição (como `f/4 · 1/250 s · ISO 400 · 50 mm`), o local onde a foto foi tirada, as dimensões, o tamanho, o formato e a data do envio. Câmera e local só aparecem quando a foto tem essas informações.
  - **Ver no mapa** abre a posição da foto no OpenStreetMap, no navegador. É o único momento em que o app leva algo para fora do servidor, e só quando você clica.
  - **Baixar original** salva o arquivo original numa pasta escolhida, com o nome com que foi enviado. O app lembra a última pasta usada.
- **Administração** (só para administradores, pelo ícone de engrenagem no topo da galeria):
  - **Usuários:** lista as contas e cria contas novas, de membro ou de administrador.
  - **Pastas monitoradas:** cadastra uma pasta do servidor para uma conta, como a pasta que o Syncthing mantém sincronizada com o celular. A lista mostra quantas fotos cada pasta tem e quando foi a última varredura, e se atualiza sozinha enquanto está aberta. Dá para pedir uma varredura na hora ou parar de monitorar; o Vitral nunca altera nem apaga os arquivos da pasta.
- **Tema claro e escuro:** acompanha o tema do sistema operacional, inclusive se ele mudar com o app aberto. O visualizador é sempre escuro.

## Stack

| Camada | Tecnologia |
| --- | --- |
| Linguagem | Java 25 (LTS) |
| Interface | JavaFX 25 (LTS) |
| Visual | [AtlantaFX](https://github.com/mkpaz/atlantafx), temas Primer claro e escuro |
| Ícones | [Ikonli](https://github.com/kordamp/ikonli), conjunto Feather |
| API | `java.net.http.HttpClient` e Jackson |
| Build | Maven, com o `javafx-maven-plugin` |

## Rodando

Pré-requisitos: JDK 25 e Maven 3.9 ou mais recente, e um servidor Vitral rodando com o administrador já criado (veja o [README do servidor](../vitral-backend/README.md)).

```bash
mvn javafx:run
```

Na tela de login, o endereço padrão é `http://localhost:8080`, o do servidor em modo de desenvolvimento.

Para rodar os testes, use `mvn test`. Eles não precisam de servidor: um servidor HTTP falso, do próprio JDK, responde como o Vitral.

## Estrutura

```
vitral-client/
└── src/main/java/com/genosha/vitral/client/
    ├── VitralClient.java      # A aplicação: tema, janela e troca entre login e galeria
    ├── ClientSettings.java    # O que fica lembrado entre execuções
    ├── api/                   # Cliente da API REST do servidor
    └── ui/                    # Telas (login, galeria, visualizador), carregamento de imagens e envio
```

A galeria é uma `ListView` de linhas (o título de um mês ou uma fileira de fotos), e não uma grade com um nó por foto. Assim só as linhas visíveis existem na tela, e a memória não cresce com o tamanho da biblioteca.

## Próximos passos

- Pacote instalável com `jlink`/`jpackage`, sem exigir JDK na máquina
