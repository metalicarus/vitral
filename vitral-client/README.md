# Vitral · Client desktop

Aplicativo desktop do [Vitral](../README.md) para navegar pelas fotos guardadas no servidor. Ele fala com o servidor só pela API REST, a mesma que o app mobile e a interface web vão usar.

> **Status:** primeira versão. Faz login e mostra a galeria; ainda não envia fotos nem abre uma foto em tela cheia.

## O que já faz

- **Login:** pede o endereço do servidor, o e-mail e a senha. Lembra o endereço e o e-mail para a próxima vez; a senha e o token da sessão nunca são gravados.
- **Galeria:** mostra as fotos da mais recente para a mais antiga, agrupadas por mês, em quadrados que se ajustam à largura da janela. Mais fotos carregam sozinhas conforme você rola.
- **Miniaturas:** baixadas em segundo plano por poucas conexões de cada vez, com as mais recentes guardadas em memória. Enquanto o servidor ainda gera a miniatura, aparece um relógio no lugar; se ele não conseguiu gerá-la, um alerta.
- **Tema claro e escuro:** acompanha o tema do sistema operacional, inclusive se ele mudar com o app aberto.

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
    └── ui/                    # Telas: login, galeria e o carregamento das miniaturas
```

A galeria é uma `ListView` de linhas (o título de um mês ou uma fileira de fotos), e não uma grade com um nó por foto. Assim só as linhas visíveis existem na tela, e a memória não cresce com o tamanho da biblioteca.

## Próximos passos

- Visualizador: abrir uma foto pela prévia, navegar entre fotos e baixar o original
- Upload de fotos, pulando as que o servidor já tem (`POST /api/v1/assets/check`)
- Setup inicial pelo próprio client, quando o servidor ainda não tem administrador
- Pacote instalável com `jlink`/`jpackage`, sem exigir JDK na máquina
