## 1. Modelo de dados

- [ ] 1.1 Criar enum `FaseMesociclo` com valores `BASE`, `ESPECIFICO`, `PICO`, `TAPER`, `TRANSICAO`
- [ ] 1.2 Criar entidade `Macrociclo` com `atleta`, `provaPrincipal`, `provaSecundaria` (opcional), `inicio`, `fim`, `objetivoTexto`, `tenantId`
- [ ] 1.3 Criar entidade `Mesociclo` com `macrociclo`, `fase`, `inicio`, `fim`, `ordem`, `objetivoCarga`, `destaques`, `tenantId`
- [ ] 1.4 Criar migration `Vxx__Create_macrociclo_table.sql`
- [ ] 1.5 Criar migration `Vxx__Create_mesociclo_table.sql` com índice `(macrociclo_id, ordem)`
- [ ] 1.6 Adicionar relacionamento opcional `PlanoSemanal → Mesociclo` (nullable, para não quebrar planos existentes)

## 2. DTOs e Mapper

- [ ] 2.1 Criar `MacrocicloOutputDto` com lista embedded de mesociclos
- [ ] 2.2 Criar `MesocicloOutputDto` e `MesocicloInputDto` (para edição pelo treinador)
- [ ] 2.3 Criar mappers MapStruct

## 3. Repository

- [ ] 3.1 Criar `MacrocicloRepository` com `findByAtletaIdAndInicioBefore`, `findAtivoByAtletaId` (data atual entre `inicio` e `fim`)
- [ ] 3.2 Criar `MesocicloRepository` com `findByMacrocicloIdOrderByOrdem`, `findAtivoByMacrocicloIdAndData`

## 4. Motor de composição

- [ ] 4.1 Criar `MacrocicloService` com método `criarMacrociclo(atletaId, provaPrincipalId, dataInicio)`
- [ ] 4.2 Implementar cálculo de duração mínima por distância da prova principal: 10 km = 8 sem, 21 km = 12 sem, 42 km = 16 sem
- [ ] 4.3 Implementar distribuição determinística de mesociclos com proporções configuráveis via `@ConfigurationProperties`
- [ ] 4.4 Garantir que fases sejam cronologicamente ordenadas e não sobreponham
- [ ] 4.5 Ajustar duração do `TAPER` para coincidir com `PeriodoTaper` quando change `add-taper-guidance` estiver ativa (consulta condicional via `@Autowired(required=false)`)

## 5. Integração com PlanoSemanalService

- [ ] 5.1 Ao gerar semana, chamar `MesocicloRepository.findAtivoByMacrocicloIdAndData` e herdar `fase` e `objetivoCarga`
- [ ] 5.2 Se não houver macrociclo ativo, `PlanoSemanalService` opera no comportamento atual (fallback)

## 6. Integração com progressao-treinos

- [ ] 6.1 Adicionar parâmetro `fase` na assinatura do motor de progressão (se já existir, integrar; caso contrário, deixar como nota de coordenação)
- [ ] 6.2 Regra de modulação por fase: `BASE` favorece progressão de volume, `ESPECIFICO` favorece intensidade específica de prova, `PICO` mantém volume e eleva qualidade, `TAPER` reduz volume
- [ ] 6.3 Documentar em `design.md` como `add-macrociclo-structure` e `progressao-treinos` se coordenam sem conflito

## 7. Integração com prompt builder

- [ ] 7.1 Adicionar seções `mesocicloAtual` e `macrocicloProgresso` ao contexto do `PlanoTreinoPromptBuilder`
- [ ] 7.2 Incluir `fase`, `semanaNdeM`, `objetivoCarga`, `destaques` em `mesocicloAtual`
- [ ] 7.3 Incluir `semanaAtual`, `totalSemanas` e narrativa curta em `macrocicloProgresso`

## 8. Endpoints REST

- [ ] 8.1 Criar `MacrocicloController` em `controller/`
- [ ] 8.2 Endpoint `POST /api/macrociclos?atletaId=X` (cria macrociclo a partir de prova-alvo e data de início)
- [ ] 8.3 Endpoint `GET /api/macrociclos/{id}`
- [ ] 8.4 Endpoint `GET /api/atletas/{atletaId}/macrociclo/atual`
- [ ] 8.5 Endpoint `PUT /api/mesociclos/{id}` (edição pelo treinador)
- [ ] 8.6 Anotações OpenAPI

## 9. Multi-tenancy

- [ ] 9.1 Queries filtram por `tenant_id` do `TenantContext`
- [ ] 9.2 Teste que atleta de outro tenant NÃO consegue acessar macrociclo alheio

## 10. Testes

- [ ] 10.1 Testes unitários do `MacrocicloService`: proporções por distância, ordenação cronológica, coincidência com taper
- [ ] 10.2 Testes de integração: PlanoSemanal herda fase do mesociclo correto
- [ ] 10.3 Testes do controller: criação, leitura, edição, multi-tenancy

## 11. Design e coordenação

- [ ] 11.1 Criar `design.md` documentando decisão arquitetural sobre como macrociclo coexiste com `progressao-treinos` e `add-taper-guidance` (diagrama de responsabilidades)
