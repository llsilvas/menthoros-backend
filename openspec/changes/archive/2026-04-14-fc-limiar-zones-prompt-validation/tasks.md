## 1. Recalcular zonas de FC com base em fcLimiar (ZonaTreinoService)

- [x] 1.1 Substituir a constante `FC_PERCENTUAIS` e `Z4_FC_LIMIAR_PERCENTUAIS` pelos percentuais LTHR: `{0.75, 0.85}`, `{0.85, 0.89}`, `{0.89, 0.94}`, `{0.94, 1.00}`, `{1.00, 1.06}`
- [x] 1.2 Atualizar `calcularZonasFC(Integer fcMaxima, Integer fcLimiar)` para usar `fcLimiar` como base em todas as 5 zonas (remover a bifurcação `Z4_FC_LIMIAR_PERCENTUAIS`)
- [x] 1.3 Atualizar o JavaDoc da classe e dos métodos para refletir o modelo LTHR, incluindo a tabela de percentuais por zona
- [x] 1.4 Escrever teste unitário `ZonaTreinoServiceTest` para `calcularZonasFC()` com `fcLimiar=160`: verificar Z1=`[120,136]`, Z2=`[136,142]`, Z3=`[142,150]`, Z4=`[150,160]`, Z5=`[160,170]`
- [x] 1.5 Escrever teste de fallback: atleta sem `fcLimiar` explícito usa `getFcLimiarCalculada()` e retorna zonas sem NPE

## 2. Corrigir schema JSON de fcAlvoEtapa (IaServiceImpl)

- [x] 2.1 Em `buildSchemaTightInlineOrDefs()`, localizar o bloco que define o pattern de `fcAlvoEtapa` e substituir `"^[0-9]{1,3}-[0-9]{1,3}% FCmax$"` por `"^[0-9]{2,3}-[0-9]{2,3} bpm$"`
- [x] 2.2 Injetar `ZonaTreinoService` no construtor de `IaServiceImpl`

## 3. Adicionar validação pós-geração de FC por zona (IaServiceImpl)

- [x] 3.1 Criar método privado `parseFcRange(String fcAlvoEtapa)` que extrai `[fcMin, fcMax]` do formato `"NNN-NNN bpm"`; retorna `null` se não parseable
- [x] 3.2 Criar método privado `zonaEsperadaParaTipo(String tipoEtapa)` que mapeia: `AQUECIMENTO/RECUPERACAO/DESAQUECIMENTO → Z1`, `PRINCIPAL → Z2-Z4`, `INTERVALADO → Z4-Z5`
- [x] 3.3 Criar método privado `validarFcEtapa(EtapaTreinoLlmDto etapa, List<ZonaFC> zonasAtleta)` que verifica sobreposição ≥50% entre FC prescrita e zona esperada; retorna etapa corrigida ou original com log WARN
- [x] 3.4 Integrar `validarFcEtapa` no stream de normalização em `validarENormalizarPlanoGerado`, chamado por etapa de cada treino
- [x] 3.5 Garantir que `validarFcEtapa` é pulado quando `atleta.getFcLimiar() == null && atleta.getFcMaxima() == null`

## 4. Testes

- [x] 4.1 Escrever teste unitário para `parseFcRange`: caso válido `"140-160 bpm"`, formato inválido, null
- [x] 4.2 Escrever teste para `validarFcEtapa`: etapa dentro da zona esperada (sem alteração), etapa fora da zona (log WARN + correção), fcAlvoEtapa não parseable (sem exceção)
- [x] 4.3 Verificar que os testes existentes de `ZonaTreinoService` (se houver) ainda passam após mudança dos percentuais
