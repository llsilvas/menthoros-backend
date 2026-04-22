## 1. Modelo de dados

- [ ] 1.1 Criar enum `EstrategiaTaper` com valores `LINEAR`, `EXPONENCIAL`, `STEP`
- [ ] 1.2 Verificar enum `FaseSemanal`; se não tiver valor `TAPER`, adicionar
- [ ] 1.3 Criar entidade `PeriodoTaper` em `entity/` com `atleta`, `prova`, `inicio`, `fim`, `duracaoSemanas`, `reducaoVolumePct`, `manutencaoIntensidade`, `estrategia`, `tenantId`
- [ ] 1.4 Criar migration `Vxx__Create_periodo_taper_table.sql` com índices `(atleta_id, prova_id)` e `(inicio, fim)`

## 2. DTOs e Mapper

- [ ] 2.1 Criar `PeriodoTaperOutputDto` com todos os campos calculados
- [ ] 2.2 Criar `PeriodoTaperMapper` (MapStruct)

## 3. Repository

- [ ] 3.1 Criar `PeriodoTaperRepository` com `findByAtletaIdAndProvaId` e `findAtivosByAtletaId` (dentro da janela atual)

## 4. Motor de taper

- [ ] 4.1 Criar `TaperService` com método `calcular(atletaId, provaId)` retornando `PeriodoTaper`
- [ ] 4.2 Implementar cálculo de `duracaoSemanas` baseado em distância e nível de experiência (tabela determinística)
- [ ] 4.3 Implementar estratégia `LINEAR`: redução uniforme de volume ao longo das semanas
- [ ] 4.4 Implementar estratégia `EXPONENCIAL`: redução acelerada nos últimos dias
- [ ] 4.5 Implementar estratégia `STEP`: platôs semanais (ex: 60% → 75% → 90%)
- [ ] 4.6 Implementar lógica de seleção automática de estratégia por nível: `INICIANTE` → STEP, `INTERMEDIARIO` → LINEAR, `AVANCADO` → EXPONENCIAL (configurável)

## 5. Integração com PlanoSemanalService

- [ ] 5.1 Expor método `PeriodoTaper getTaperAtivo(atletaId, semanaInicio)` para consulta em tempo de montagem
- [ ] 5.2 `PlanoSemanalService` SHALL consultar `getTaperAtivo` antes de gerar semanas e marcar `faseSemanal=TAPER` quando dentro da janela
- [ ] 5.3 Aplicar `reducaoVolumePct` ao volume-base da semana quando `faseSemanal=TAPER`

## 6. Integração com IntervaladoElegibilidadeService

- [ ] 6.1 Adicionar portão `taperPermite()` como sétimo portão (após readiness)
- [ ] 6.2 Regra: dentro dos últimos 7 dias do taper → bloqueia intervalados de alto volume (> 4 km em ritmo acima do limiar)
- [ ] 6.3 Permitir "tune-up" em dias 4–6 antes da prova: intervalados curtos de ativação (4×400m ou 3×1km em pace alvo)

## 7. Integração com prompt builder

- [ ] 7.1 Adicionar seção `taper` ao contexto do `PlanoTreinoPromptBuilder`
- [ ] 7.2 Incluir: `estaEmTaper`, `diasAteProva`, `estrategia`, `reducaoVolumePct`, `manutencaoIntensidade`

## 8. Endpoints REST

- [ ] 8.1 Criar `TaperController` em `controller/`
- [ ] 8.2 Endpoint `GET /api/provas/{provaId}/taper?atletaId=X` com cache (TTL 12h)
- [ ] 8.3 Endpoint `POST /api/provas/{provaId}/taper/recalcular?atletaId=X` (uso do treinador)
- [ ] 8.4 Anotações OpenAPI

## 9. Multi-tenancy

- [ ] 9.1 Queries filtram por `tenant_id` do `TenantContext`

## 10. Testes

- [ ] 10.1 Testes unitários do `TaperService`: duração por distância, redução por estratégia, seleção por nível
- [ ] 10.2 Testes de integração: portão de intervalado dentro/fora do taper, prompt builder com seção taper
- [ ] 10.3 Testes de borda: prova em 3 dias, prova em 30 dias, atleta sem prova-alvo
