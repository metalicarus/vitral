# ADR 0001: Servidor em Java com Quarkus

**Status:** aceita

## Contexto

O servidor do Vitral precisa armazenar, catalogar e processar fotos em hardware modesto (NAS, Raspberry Pi, PC antigo). O projeto é mantido principalmente por uma pessoa, que domina Java e quer conseguir manter o código sem depender de ferramentas de IA.

## Decisão

O servidor é escrito em Java 25 (LTS) com Quarkus, na linha 3.33 LTS, rodando em modo JVM.

- **Java** porque é a linguagem que o mantenedor domina; isso pesa mais do que pequenas vantagens de desempenho de outras linguagens.
- **Quarkus** em vez de Spring Boot porque consome menos memória e inicia mais rápido, o que importa em hardware doméstico, e porque usa APIs padrão do Jakarta EE (Jakarta REST, CDI), cujo conhecimento vale fora do framework.
- **Modo JVM** primeiro; o modo nativo (GraalVM/Mandrel) fica como opção futura para Raspberry Pi, já que exige builds lentos e configuração extra para bibliotecas com código nativo.

## Consequências

- O processamento pesado (imagens, vídeo) é delegado a ferramentas externas (libvips, FFmpeg), então a escolha da linguagem tem pouco impacto no desempenho real.
- Atualizações do Quarkus seguem as linhas LTS, com `quarkus update`.
