## Context

O Menthoros já possui infraestrutura para rastrear sincronização em `TreinoPlanejado` (14 estados em `StatusSincronizacao`, retry logic, `externalId`) e enum `FonteDados` com `STRAVA`. A entidade `Atleta` não possui nenhum campo de OAuth. A análise do modelo (ver `docs/ANALISE_MODELO_INTEGRACAO_STRAVA.md`) mapeou exatamente o que existe e o que falta.

A aplicação usa Spring Boot 3.5.4, Spring Security + OAuth2 Resource Server (Keycloak), Spring Data JPA, Flyway e multi-tenancy baseado em `tenant_id` no JWT. Não há nenhum WebClient ou client HTTP reativo configurado atualmente.

## Goals / Non-Goals

**Goals:**
- Fluxo OAuth2 completo com Strava (authorize → callback → token → refresh)
- Armazenar tokens em entidade `IntegracaoExterna` (extensível para Garmin, TrainingPeaks)
- Importar atividades Strava → `TreinoRealizado` com deduplicação
- Importar laps por atividade → `EtapaRealizada`
- Receber e processar eventos via webhooks Strava (create, update, delete)
- Respeitar isolamento multi-tenancy: `tenant_id` em todas as tabelas novas

**Non-Goals:**
- Exportar treinos planejados para o Strava (push — fase futura)
- Integração com Garmin ou outras plataformas (arquitetura suporta, mas não implementar agora)
- Criptografia de tokens em repouso (registrado como risco — fase de segurança futura)
- Cálculo avançado de TSS por potência (Strava não fornece dados de potência para corrida por padrão)
- Interface de usuário / frontend

## Decisions

### D1: Tabela separada `IntegracaoExterna` em vez de campos em `Atleta`

**Decisão:** Criar entidade `IntegracaoExterna` com FK para `Atleta` e campo `plataforma` (enum `FonteDados`).

**Rationale:** A análise do modelo identificou que adicionar campos Strava diretamente em `Atleta` não escala para múltiplas plataformas e aumenta o tamanho da tabela principal. A constraint `UNIQUE (atleta_id, plataforma)` garante um registro por plataforma por atleta.

**Alternativa descartada:** Campos diretos em `Atleta` — descartado por não suportar Garmin/TrainingPeaks futuro e por misturar responsabilidades na entidade central.

---

### D2: WebClient (reativo) para chamadas à API do Strava

**Decisão:** Adicionar `spring-boot-starter-webflux` apenas para usar `WebClient` de forma bloqueante (`.block()`). Não adotar programação reativa em todo o serviço.

**Rationale:** `RestTemplate` está deprecated. `RestClient` (Spring 6.1+) é a alternativa moderna e síncrona, mas `WebClient` já é a referência da documentação Strava e dos guias internos do projeto. Usar `.block()` evita refatoração do stack reativo.

**Alternativa considerada:** `RestClient` — válida, mas a documentação interna do projeto já adota `WebClient`. Mantemos consistência com o guia existente.

---

### D3: Processamento de webhooks síncrono com resposta imediata

**Decisão:** O endpoint `POST /api/strava/webhook` responde `HTTP 200` imediatamente e delega o processamento para um método `@Async` ou via `ApplicationEventPublisher`.

**Rationale:** O Strava exige resposta em menos de 2 segundos ou considera falha e faz retry. O processamento (buscar atividade na API, mapear, salvar) pode levar mais tempo.

**Alternativa descartada:** Processamento síncrono — risco de timeout no Strava causando eventos duplicados.

---

### D4: Deduplicação por `externalId` antes de persistir `TreinoRealizado`

**Decisão:** Antes de salvar qualquer atividade importada, verificar `treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, atletaId)`. Se existir, atualizar campos; se não, criar novo registro.

**Rationale:** O campo `external_id` já existe em `tb_treino_realizado` (migration V25). O Strava pode entregar o mesmo evento múltiplas vezes via webhook ou o usuário pode disparar sync manual após webhook já processar.

---

### D5: Inferência de `TipoTreino` a partir dos metadados Strava

**Decisão:** Mapear `sport_type` + `workout_type` do Strava para `TipoTreino` do Menthoros seguindo esta lógica:

```
sportType = "Run", workoutType = 1  → PROVA
sportType = "Run", workoutType = 2  → LONGO  
sportType = "Run", workoutType = 3  → INTERVALADO ou TEMPO_RUN (por FC média vs limiar)
sportType = "TrailRun"              → SUBIDA (se elevação > 100m) ou LONGO
sportType = "VirtualRun"            → CONTINUO
workoutType = 0 (padrão)            → inferir por duração e FC: FACIL, CONTINUO, LONGO
```

**Rationale:** O Menthoros tem enum `TipoTreino` com semântica rica. Mapear automaticamente melhora a qualidade dos dados para a IA sem requerer input manual do atleta.

---

### D6: SufferScore como cross-check do TSS, não substituição

**Decisão:** Armazenar `sufferScore` do Strava em campo separado em `TreinoRealizado`. O TSS continuará sendo calculado pelos métodos existentes (FC, pace, RPE). O `sufferScore` serve para validação e log de divergência.

**Rationale:** O Suffer Score do Strava usa fórmula proprietária não documentada. Usá-lo como TSS primário introduziria dependência opaca em um cálculo central do sistema.

---

### D7: Multi-tenancy em `IntegracaoExterna`

**Decisão:** Adicionar coluna `tenant_id` (UUID, NOT NULL) em `tb_integracao_externa`. O `JwtTenantFilter` existente popula `TenantContext` a cada requisição — todos os repositories devem filtrar por `tenant_id`.

**Rationale:** Padrão já estabelecido no projeto. Sem isso, atletas de diferentes assessorias poderiam, em teoria, acessar tokens de outros tenants.

## Risks / Trade-offs

**[Risco] Tokens OAuth em texto plano no banco** → Tokens de acesso e refresh do Strava ficarão armazenados sem criptografia em repouso na coluna TEXT de `tb_integracao_externa`. Mitigação de curto prazo: restringir acesso ao banco via roles PostgreSQL e garantir que logs não exponham tokens. Mitigação futura: criptografia com `@Convert` JPA + chave simétrica.

**[Risco] Rate limit da API do Strava** → Strava limita 100 requests/15min e 1000/dia por app. Importações em lote de histórico podem atingir esse limite. Mitigação: implementar backoff exponencial e respeitar header `X-RateLimit-Remaining`.

**[Risco] Webhook sem autenticação forte** → O Strava valida apenas o `hub.verify_token` na subscription. Eventos POST chegam sem assinatura verificável (diferente do GitHub). Mitigação: validar que o `object_id` (athlete ID) corresponde a um atleta cadastrado antes de processar.

**[Risco] Scope `activity:read_all` é amplo** → O atleta autoriza acesso a todas as atividades, incluindo as privadas. Mitigação: documentar claramente no fluxo de autorização e permitir desconexão a qualquer momento.

**[Trade-off] `.block()` no WebClient** → Adicionar webflux apenas para WebClient bloqueante é overhead de dependência. Aceitável porque o projeto pode evoluir para reativo no futuro, e RestClient seria uma reescrita adicional.

## Migration Plan

1. Executar migration V26 (ou próxima disponível): criar `tb_integracao_externa`
2. Executar migration V27: adicionar campos em `tb_treino_realizado`
3. Executar migration V28: adicionar campos em `tb_etapa_realizada`
4. Configurar variáveis de ambiente no `.env` local e em produção antes de iniciar a aplicação
5. Registrar webhook no Strava apenas após deploy com URL pública acessível (produção/staging)
6. Para rollback: as migrations são aditivas (ADD COLUMN IF NOT EXISTS, CREATE TABLE IF NOT EXISTS) — reverter aplicação sem desfazer migrations é seguro; as novas colunas ficarão com NULL sem causar erros

## Open Questions

- **Sequência de migrations:** Verificar qual é o próximo número disponível após V25 (já existe `V25__Add_external_id_to_treino_realizado.sql` como untracked)
- **Criptografia de tokens:** Decidir se implementa na fase 1 ou como issue técnica separada
- **Processamento assíncrono:** Usar `@Async` com `ThreadPoolTaskExecutor` configurado, ou `ApplicationEventPublisher` + listener? Depende da necessidade de rastreamento de falhas
- **URL pública para webhook:** Definir estratégia de staging/desenvolvimento (ngrok? túnel?) para testar webhooks localmente
- **Histórico retroativo:** Definir se o sync inicial importa todas as atividades dos últimos N dias ou apenas as futuras
