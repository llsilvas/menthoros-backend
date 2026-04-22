## 1. Modelo de dados

- [ ] 1.1 Verificar se `Atleta.disponibilidadeSemanal` existe; se não, adicionar como `Map<DiaSemana, Integer>` persistido em JSONB (ou tabela `tb_atleta_disponibilidade` se preferir normalização)
- [ ] 1.2 Criar migration `Vxx__Add_disponibilidade_semanal_atleta.sql` (se necessário)
- [ ] 1.3 Definir defaults aplicados quando o atleta não tiver disponibilidade cadastrada: `seg-sex=60min`, `sab=120min`, `dom=180min`

## 2. DTOs

- [ ] 2.1 Criar `AtletaDisponibilidadeInputDto` para `PUT /api/atletas/{id}/disponibilidade`
- [ ] 2.2 Expandir `AtletaOutputDto` para expor `disponibilidadeSemanal` (breaking change de payload? — se sim, documentar)
- [ ] 2.3 Criar `RebalanceamentoResultadoDto` expondo distribuição antes/depois e motivos de cada movimento

## 3. Regras e helpers

- [ ] 3.1 Criar enum/método `ClassificacaoSessao.isChave(TreinoPlanejado)` — LONGO, INTERVALADO, TEMPO_RUN, PROVA_SIMULADA
- [ ] 3.2 Criar função `espacamentoMinimoEntre(TipoTreino, TipoTreino)` retornando mínimo de dias fáceis entre duas sessões-chave (matriz configurável)
- [ ] 3.3 Criar função `scoreDia(Atleta, DiaSemana, TreinoPlanejado)` combinando `disponibilidadeMinutos` vs. `duracaoPrevistaMin` e coerência hard/easy com sessões vizinhas

## 4. Motor de distribuição

- [ ] 4.1 Criar `DistribuicaoSemanalService` com método `distribuir(List<TreinoPlanejado> sessoes, Atleta atleta, LocalDate inicioSemana)` → `Map<DiaSemana, TreinoPlanejado>`
- [ ] 4.2 Implementar busca por permutação com função de custo composta (espaçamento, disponibilidade, variação vs. ordem original)
- [ ] 4.3 Limitar custo computacional a 7! permutações (≈ 5040) — aceitável; usar early pruning se sessão violar espaçamento mínimo
- [ ] 4.4 Retornar também `List<MovimentoSessao>` (para cada alteração, motivo textual em pt-BR)

## 5. Integração com PlanoSemanalService

- [ ] 5.1 Após `IaService` gerar estrutura, `PlanoSemanalService` chama `DistribuicaoSemanalService.distribuir(...)` antes de persistir
- [ ] 5.2 Persistir `movimentos` como log da geração em coluna JSONB na `PlanoSemanal` (campo `rebalanceamentoLog`)

## 6. Endpoint de rebalanceamento

- [ ] 6.1 Criar `DistribuicaoController` com endpoint `POST /api/planos-semanais/{id}/rebalancear`
- [ ] 6.2 Endpoint `PUT /api/atletas/{id}/disponibilidade` (se campo ainda não existe em AtletaController)
- [ ] 6.3 Anotações OpenAPI

## 7. Integração com prompt builder

- [ ] 7.1 Adicionar seção `disponibilidadeSemanal` ao contexto do `PlanoTreinoPromptBuilder`
- [ ] 7.2 Adicionar instrução curta no prompt: "o motor pós-processará distribuição; foque em sessões e volume"

## 8. Multi-tenancy

- [ ] 8.1 Queries de `Atleta.disponibilidadeSemanal` SHALL filtrar por `tenant_id`

## 9. Testes

- [ ] 9.1 Teste unitário do `scoreDia`: variações de disponibilidade e tipos de treino
- [ ] 9.2 Teste unitário do `distribuir`: cenário com 2 sessões-chave em semana com dias limitados
- [ ] 9.3 Teste: LONGO não deve ficar no dia seguinte a INTERVALADO (regra de espaçamento)
- [ ] 9.4 Teste: sessão-chave SHALL cair no dia de maior disponibilidade quando possível
- [ ] 9.5 Teste de integração: endpoint de rebalanceamento retorna `movimentos` coerentes
- [ ] 9.6 Teste de fallback: atleta sem disponibilidade usa defaults e loga WARN

## 10. Observabilidade

- [ ] 10.1 Métrica `weekly_load_rebalancing_total{resultado}` — quantos movimentos foram aplicados
- [ ] 10.2 Log ao final do rebalanceamento: número de movimentos, razões agregadas
