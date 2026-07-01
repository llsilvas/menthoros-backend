# Menthoros Backend

Backend da plataforma **Menthoros**: o sistema de decisão para treinadores de corrida/endurance.

Este repositório concentra a API e a camada de domínio que alimentam a experiência
**coach-in-the-loop**: a IA analisa e sugere, mas o treinador continua decidindo.
Nada deve chegar ao atleta sem revisão explícita do coach.

## O que este backend faz

- expõe a API usada pelo frontend do Menthoros;
- aplica regras de negócio de treino, perfil, atenção e aprovação de planos;
- integra autenticação/autorização via Keycloak JWT;
- persiste dados em PostgreSQL com migrações Flyway;
- fornece suportes de observabilidade e resiliência;
- integra recursos de IA via Spring AI.

## Stack principal

- Java 21
- Spring Boot 3.5.x
- Spring Data JPA
- Spring Security OAuth2 Resource Server
- Keycloak (JWT) para identidade e multi-tenancy
- PostgreSQL
- Flyway
- Spring AI com modelos OpenAI e Anthropic
- MapStruct
- Lombok
- Micrometer / Prometheus

## Como rodar

Pré-requisitos:

- JDK 21
- Maven Wrapper (`./mvnw`)
- acesso ao banco e ao Keycloak configurados no ambiente

Comandos úteis:

```bash
./mvnw spring-boot:run
./mvnw clean test
```

A aplicação sobe na porta configurada no `application.yml` / variáveis de ambiente.

## Convenções de produto

- O backend é **coach-first** e **decision-first**.
- A IA deve sempre ser explicável.
- Sugestões são propostas para revisão do treinador, não enviadas diretamente ao atleta.
- Integrações ainda em evolução, como Strava, podem existir no código, mas seguem a estratégia
  de produto definida no OpenSpec.

## Estrutura esperada

- `src/main/java` — código-fonte principal
- `src/main/resources` — configuração e migrações
- `src/test/java` — testes

## Onde olhar primeiro

- `CLAUDE.md` deste repositório: regras de execução e padrões do módulo
- `menthoros-product/openspec/SPRINTS.md`: roadmap canônico
- `menthoros-product/openspec/changes/**`: mudanças ativas
