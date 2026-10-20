# Vitral

**Suas fotos, no seu servidor.** O Vitral é um gerenciador de fotos e vídeos self-hosted e totalmente open source: uma alternativa ao Google Fotos que roda em um servidor seu, sem entregar sua biblioteca a terceiros.

> **Status:** em desenvolvimento inicial. Ainda não há versão utilizável.

## Partes do projeto

| Pasta | O que é | Tecnologia | Status |
| --- | --- | --- | --- |
| [`vitral-backend/`](vitral-backend/) | Servidor: armazena, cataloga e processa as fotos e expõe a API | Java 25, Quarkus | Fundação (M0) em andamento |
| `vitral-frontend/` | Interface web para navegar, organizar e compartilhar as fotos | SvelteKit | Planejado |

Mais adiante, um app mobile vai cuidar do backup automático do celular, usando a mesma API do servidor. Até lá, o backup pode ser feito com ferramentas de sincronização como o Syncthing, apontando para uma pasta que o servidor monitora.

## Princípios

- **Seus dados, seu servidor:** nenhuma chamada a serviços externos por padrão e nenhuma telemetria.
- **Originais intocáveis:** o Vitral nunca altera um arquivo original.
- **Leve primeiro:** roda sem GPU e em hardware modesto, como um PC antigo, um NAS ou um Raspberry Pi.
- **Formatos abertos:** os originais ficam em pastas comuns, legíveis sem o Vitral.

## Começando

O servidor é a única parte que já existe. Com JDK 25 e Maven instalados (não é preciso Docker nem banco de dados):

```bash
cd vitral-backend/server
mvn quarkus:dev
```

A API sobe em `http://localhost:8080`, com documentação interativa em `/q/swagger-ui`. O [README do servidor](vitral-backend/README.md) tem a arquitetura, a stack, o roadmap e os primeiros passos com a API, e a pasta `vitral-backend/` traz uma coleção pronta para o Insomnia em `insomnia.json`.

## Decisões de arquitetura

As decisões importantes ficam registradas como ADRs em [`vitral-backend/docs/adr/`](vitral-backend/docs/adr/).

## Licença

[AGPL-3.0](LICENSE) (proposta).