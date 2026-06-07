# Documentação Menthoros (Backend)

> Documentação organizada por tema. Os arquivos originais (antes da consolidação) estão preservados em `_originais/`.

---

## 🔐 Autenticação
Configuração e uso do Keycloak para autenticação e autorização.

| Arquivo | Descrição |
|---|---|
| [KEYCLOAK_GUIA_COMPLETO.md](autenticacao/KEYCLOAK_GUIA_COMPLETO.md) | Guia completo: setup, configuração manual, fluxos OAuth2, troubleshooting |

---

## 🏢 Multi-Tenancy
Isolamento de dados por assessoria (tenant).

| Arquivo | Descrição |
|---|---|
| [GUIA_COMPLETO.md](multi-tenancy/GUIA_COMPLETO.md) | Arquitetura, implementação Spring Security, roadmap de 12 sprints |
| [SEGURANCA.md](multi-tenancy/SEGURANCA.md) | Checklist de segurança + suite completa de testes |
| [DOCKER_SETUP.md](multi-tenancy/DOCKER_SETUP.md) | Configuração Docker com PostgreSQL, Keycloak e Redis |
| [ISSUES_BACKLOG.md](multi-tenancy/ISSUES_BACKLOG.md) | Backlog de problemas identificados e plano de correção |

---

## 🔗 Integrações
Conexões com plataformas externas (Strava, Garmin).

| Arquivo | Descrição |
|---|---|
| [STRAVA_GUIA_COMPLETO.md](integracoes/STRAVA_GUIA_COMPLETO.md) | Integração OAuth2, sincronização de atividades, webhooks, roadmap |
| [GARMIN_STRAVA.md](integracoes/GARMIN_STRAVA.md) | Integração combinada Garmin + Strava |

---

## 🏃 Treinos
Lógica de prescrição e avaliação de treinos.

| Arquivo | Descrição |
|---|---|
| [ZONAS_AVALIACAO_COMPLETO.md](treinos/ZONAS_AVALIACAO_COMPLETO.md) | Protocolos científicos de avaliação de zonas + roadmap de implementação |
| [intervalado-elegibilidade.md](treinos/intervalado-elegibilidade.md) | Critérios de elegibilidade para treinos intervalados |
| [melhoria-treinos-intervalados.md](treinos/melhoria-treinos-intervalados.md) | Melhorias na geração de treinos intervalados |
| [normalizacao-treinos-por-etapas.md](treinos/normalizacao-treinos-por-etapas.md) | Normalização de treinos por etapas do plano |
| [prescricao-pace.md](treinos/prescricao-pace.md) | Metodologia de prescrição de pace |

---

## 🤖 Inteligência Artificial
Spring AI, skills e evolução dos prompts.

| Arquivo | Descrição |
|---|---|
| [SPRING_AI_SKILLS_COMPLETO.md](ia/SPRING_AI_SKILLS_COMPLETO.md) | Arquitetura de skills com Spring AI, integração Claude, roadmap |
| [PROMPTS_EVOLUCAO.md](ia/PROMPTS_EVOLUCAO.md) | Prompt atual, melhorias aplicadas e sugestões futuras |
| [LLM_BEST_PRACTICES.md](ia/LLM_BEST_PRACTICES.md) | Boas práticas para uso de LLMs no projeto |

---

## ⚡ Processamento Assíncrono
Geração de planos via jobs assíncronos com Virtual Threads.

| Arquivo | Descrição |
|---|---|
| [PROCESSAMENTO_ASYNC_COMPLETO.md](processamento-async/PROCESSAMENTO_ASYNC_COMPLETO.md) | Guia técnico, relatório de implementação e roadmap de 11 sprints |

---

## 🏗️ Arquitetura
Decisões arquiteturais e planos de refatoração.

| Arquivo | Descrição |
|---|---|
| [comparacao_arquitetura.md](arquitetura/comparacao_arquitetura.md) | Comparação entre arquitetura atual e proposta |
| [plano_refatoracao_services.md](arquitetura/plano_refatoracao_services.md) | Plano de refatoração da camada de serviços |
| [roadmap-features-produto.md](arquitetura/roadmap-features-produto.md) | Roadmap de features do produto |

---

## 🐛 Issues
Rastreamento de bugs e melhorias identificadas.

→ Ver pasta [issues/](issues/README.md)

---

## 📁 Arquivos Originais
Os arquivos anteriores à consolidação estão preservados em [`_originais/`](_originais/) para referência.
