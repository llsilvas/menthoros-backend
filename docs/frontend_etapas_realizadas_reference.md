# Referencia Frontend - Etapas Realizadas dos Treinos

> Documento de referencia para implementacao React das etapas realizadas de treinos.
> Baseado nas alteracoes da branch `feature/etapas_realizadas`.

---

## 1. Resumo das Alteracoes no Backend

O backend agora suporta **etapas detalhadas** dentro de cada treino realizado. Um treino realizado pode conter N etapas (aquecimento, tiros, recuperacao, etc.), permitindo analise granular da execucao.

### O que mudou:
- `TreinoRealizadoInputDto` agora aceita um campo **opcional** `etapasRealizadas: EtapaRealizadaInputDto[]`
- `TreinoRealizadoOutputDto` agora retorna `etapasRealizadas: EtapaRealizadaOutputDto[]`
- Nova entidade `EtapaRealizada` com tabela `tb_etapa_realizada`
- Campos `null` sao omitidos na resposta (`@JsonInclude(NON_NULL)`)

---

## 2. Endpoints Impactados

### POST `/{treinoPlanejadoId}/marcar-realizado`
Marca um treino planejado como realizado. Agora aceita etapas opcionalmente.

### POST `/{atletaId}/lancar-treino`
Lanca um treino manual (sem treino planejado). Agora aceita etapas opcionalmente.

> **Ambos os endpoints retornam `201 Created`** com o `TreinoRealizadoOutputDto` completo (incluindo etapas).

---

## 3. Contratos de Dados

### 3.1 Request - TreinoRealizadoInputDto

```typescript
interface TreinoRealizadoInput {
  atletaId: string;              // UUID
  planoSemanalId?: string;       // UUID (opcional)
  treinoPlanejadoId?: string;    // UUID (opcional)
  dataTreino: string;            // "YYYY-MM-DD"
  diaSemana: DiaSemana;
  tipoTreino: TipoTreino;
  descricao?: string;
  zonaAlvo?: string;
  duracaoMin: string;            // "HH:MM:SS", "MM:SS" ou minutos
  distanciaKm?: number;
  ritmoAlvo?: string;            // "5:30 min/km"
  ritmoMedio?: string;           // "5:45 min/km"
  elevacaoGanhoMetros?: number;
  elevacaoPerdaMetros?: number;
  observacao?: string;

  // Metricas Fisiologicas
  fcMedia?: number;              // bpm
  fcMax?: number;                // bpm
  cadenciaMedia?: number;        // passos/min
  potenciaMedia?: number;        // watts
  velocidadeMedia?: number;      // km/h
  percepcaoEsforco?: number;     // 1-10

  // Feedback do Atleta
  feedbackAtleta?: string;
  qualidadeSonoNoiteAnterior?: number;  // 1-10
  nivelEstresse?: number;               // 1-10

  // Metadados
  fonteDados?: FonteDados;
  status: TreinoExecucaoStatus;
  externalId?: string;

  // *** NOVO - Etapas Realizadas (opcional) ***
  etapasRealizadas?: EtapaRealizadaInput[];
}
```

### 3.2 Request - EtapaRealizadaInputDto (NOVO)

```typescript
interface EtapaRealizadaInput {
  etapaPlanejadaId?: string;     // UUID - vincula a etapa planejada (opcional)
  ordem: number;                 // Obrigatorio - posicao da etapa no treino
  tipoEtapa?: TipoEtapa;        // Tipo da etapa
  descricao?: string;            // Ex: "Tiro 1 - 1000m"
  duracao?: string;              // "MM:SS" ou "HH:MM:SS"
  distanciaKm?: number;
  fcMedia?: number;              // bpm
  fcMax?: number;                // bpm
  paceMedia?: string;            // "MM:SS"
  velocidadeMedia?: number;      // km/h
  percepcaoEsforco?: number;     // 1-10
  cadenciaMedia?: number;        // passos/min
  potenciaMedia?: number;        // watts
  observacao?: string;
}
```

### 3.3 Response - TreinoRealizadoOutputDto

```typescript
interface TreinoRealizadoOutput {
  id: string;                    // UUID
  dataTreino: string;            // "YYYY-MM-DD"
  diaSemana: DiaSemana;
  tipoTreino: TipoTreino;
  descricao?: string;
  zonaAlvo?: string;
  duracaoMin?: string;           // "HH:MM:SS" ou "MM:SS"
  distanciaKm?: number;
  ritmoAlvo?: string;
  paceMedia?: string;            // "MM:SS" (convertido de Duration)
  elevacaoGanhoMetros?: number;
  elevacaoPerdaMetros?: number;
  observacao?: string;

  // Metricas Fisiologicas
  fcMedia?: number;
  fcMax?: number;
  cadenciaMedia?: number;
  potenciaMedia?: number;
  velocidadeMedia?: number;
  percepcaoEsforco?: number;
  tssCalculado?: number;         // Calculado pelo backend
  metodoCalculoTss?: string;     // "FC", "PACE", "RPE"
  intensidadeReal?: number;      // IF calculado

  // Feedback
  feedbackAtleta?: string;
  qualidadeSonoNoiteAnterior?: number;
  nivelEstresse?: number;

  // Metadados
  fonteDados?: FonteDados;
  status: TreinoExecucaoStatus;
  externalId?: string;

  // *** NOVO - Etapas Realizadas ***
  etapasRealizadas?: EtapaRealizadaOutput[];
}
```

### 3.4 Response - EtapaRealizadaOutputDto (NOVO)

```typescript
interface EtapaRealizadaOutput {
  id: string;                    // UUID gerado pelo backend
  etapaPlanejadaId?: string;     // UUID da etapa planejada (se vinculada)
  ordem: number;
  tipoEtapa?: TipoEtapa;
  descricao?: string;
  duracao?: string;              // "MM:SS" ou "HH:MM:SS" (string, nao number)
  distanciaKm?: number;
  fcMedia?: number;
  fcMax?: number;
  paceMedia?: string;            // "MM:SS" (string, nao number)
  velocidadeMedia?: number;
  percepcaoEsforco?: number;
  cadenciaMedia?: number;
  potenciaMedia?: number;
  observacao?: string;
}
```

---

## 4. Enums de Referencia

### TipoEtapa (string literal no backend)

```typescript
type TipoEtapa =
  | "AQUECIMENTO"
  | "PRINCIPAL"
  | "INTERVALADO"
  | "RECUPERACAO"
  | "DESAQUECIMENTO";

const TIPO_ETAPA_OPTIONS = [
  { value: "AQUECIMENTO",    label: "Aquecimento",    color: "#4CAF50" },
  { value: "PRINCIPAL",      label: "Principal",      color: "#2196F3" },
  { value: "INTERVALADO",    label: "Intervalado",    color: "#FF5722" },
  { value: "RECUPERACAO",    label: "Recuperacao",    color: "#FF9800" },
  { value: "DESAQUECIMENTO", label: "Desaquecimento", color: "#9E9E9E" },
];
```

### TipoTreino

```typescript
type TipoTreino =
  | "REGENERATIVO"   // Zona 1 - cor: #4CAF50
  | "INTERVALADO"    // Zona 5 - cor: #FF5722
  | "CONTINUO"       // Zona 2-3 - cor: #2196F3
  | "LONGO"          // Zona 2 - cor: #9C27B0
  | "TIRO"           // Zona 5+ - cor: #FF9800
  | "FARTLEK"        // Zona 2-4 - cor: #607D8B
  | "TEMPO_RUN"      // Zona 4 - cor: #F44336
  | "FACIL"          // Zona 2 - cor: #8BC34A
  | "SUBIDA"         // Zona 4-5 - cor: #795548
  | "PROVA";         // Zona 3-4 - cor: #E91E63
```

### TreinoExecucaoStatus

```typescript
type TreinoExecucaoStatus =
  | "REALIZADO"   // cor: #4CAF50
  | "PERDIDO"     // cor: #F44336
  | "PARCIAL"     // cor: #FF9800
  | "LIVRE"       // cor: #2196F3
  | "PENDENTE"    // cor: #FFEB3B
  | "CONCLUIDO";  // cor: #8BC34A
```

### FonteDados

```typescript
type FonteDados =
  | "MANUAL"          // cor: #9E9E9E
  | "IA_GERADO"       // cor: #9C27B0
  | "GARMIN"          // cor: #007ACC
  | "STRAVA"          // cor: #FC4C02
  | "TRAINING_PEAKS"  // cor: #F57C00
  | "POLAR"           // cor: #E30613
  | "WAHOO";          // cor: #0066CC
```

### DiaSemana

```typescript
type DiaSemana =
  | "DOMINGO"   // short: "DOM", order: 0
  | "SEGUNDA"   // short: "SEG", order: 1
  | "TERCA"     // short: "TER", order: 2
  | "QUARTA"    // short: "QUA", order: 3
  | "QUINTA"    // short: "QUI", order: 4
  | "SEXTA"     // short: "SEX", order: 5
  | "SABADO";   // short: "SAB", order: 6
```

---

## 5. Pontos Criticos para o Frontend

### 5.1 Etapas sao Opcionais

O campo `etapasRealizadas` e **opcional** tanto no envio quanto no retorno. O frontend deve:
- Permitir registrar treinos **sem** etapas (comportamento atual mantido)
- Oferecer opcao de "Adicionar etapas detalhadas" para quem quiser granularidade
- Na exibicao, verificar se `etapasRealizadas` existe e tem itens antes de renderizar

```tsx
// Exemplo de verificacao
{treino.etapasRealizadas && treino.etapasRealizadas.length > 0 && (
  <EtapasRealizadasList etapas={treino.etapasRealizadas} />
)}
```

### 5.2 Formato de Duracao e Pace (String, nao Number)

O backend espera e retorna duracoes como **string** nos formatos:
- `"HH:MM:SS"` - ex: `"01:05:30"`
- `"MM:SS"` - ex: `"04:15"`

**NAO enviar como numero de minutos.** O frontend deve:
- Usar inputs com mascara de tempo
- Converter para string formatada antes de enviar

```typescript
// Funcao auxiliar para formatar duracao
function formatDuration(hours: number, minutes: number, seconds: number): string {
  if (hours > 0) {
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}
```

### 5.3 Campo `ordem` e Obrigatorio e Define a Sequencia

As etapas sao ordenadas por `ordem ASC` no backend. O frontend deve:
- Gerenciar a ordem automaticamente (1, 2, 3...)
- Permitir reordenacao via drag-and-drop (recalcular `ordem` ao reordenar)
- Garantir que `ordem` nunca seja `null`

### 5.4 Vinculacao com Etapa Planejada (`etapaPlanejadaId`)

Quando o treino vem de um plano (`marcar-realizado`), cada etapa realizada pode ser **vinculada** a uma etapa planejada via `etapaPlanejadaId`. Isso permite:
- Comparacao lado-a-lado (planejado vs realizado)
- Pre-preencher dados da etapa planejada no formulario
- Mostrar desvios (ex: pace planejado vs pace real)

```tsx
// Ao marcar treino como realizado, pre-popular etapas do plano
const etapasPrePopuladas = treinoPlanejado.etapas.map((ep, index) => ({
  etapaPlanejadaId: ep.id,
  ordem: index + 1,
  tipoEtapa: ep.tipoEtapa,
  descricao: ep.descricaoEtapa,
  // Campos de metricas ficam vazios para o atleta preencher
}));
```

### 5.5 Campos null Omitidos na Resposta

O output usa `@JsonInclude(NON_NULL)`, entao campos nao preenchidos **nao aparecem no JSON**. Use optional chaining:

```typescript
// Seguro
const pace = etapa?.paceMedia ?? "—";
const fc = etapa?.fcMedia ?? null;
```

### 5.6 Percepcao de Esforco (RPE) - Validacao 1-10

O campo `percepcaoEsforco` tem constraint no banco (CHECK 1-10). O frontend deve validar:

```typescript
const isValidRPE = (value: number) => value >= 1 && value <= 10;
```

---

## 6. Sugestao de Componentes React

### 6.1 Estrutura de Componentes

```
src/
  components/
    treino-realizado/
      TreinoRealizadoForm.tsx          // Formulario principal (ja existe, adaptar)
      EtapasRealizadasSection.tsx       // NOVO - secao colapsavel de etapas
      EtapaRealizadaFormRow.tsx         // NOVO - linha do formulario de uma etapa
      EtapaRealizadaCard.tsx            // NOVO - card de exibicao de uma etapa
      EtapasRealizadasTimeline.tsx      // NOVO - timeline visual das etapas
      EtapaComparacaoCard.tsx           // NOVO - comparacao planejado vs realizado
```

### 6.2 Formulario de Etapas (Padrao Repeater)

Recomendacao de UX para o formulario de etapas:

```tsx
// Hook com react-hook-form useFieldArray
const { fields, append, remove, move } = useFieldArray({
  control,
  name: "etapasRealizadas",
});

// Template de nova etapa
const novaEtapa: EtapaRealizadaInput = {
  ordem: fields.length + 1,
  tipoEtapa: "PRINCIPAL",
  descricao: "",
  duracao: "",
  distanciaKm: undefined,
  fcMedia: undefined,
  fcMax: undefined,
  paceMedia: "",
  percepcaoEsforco: undefined,
};
```

### 6.3 Exibicao - Timeline Visual

Para exibir etapas de um treino ja registrado, considerar uma timeline vertical:

```
 [Aquecimento]  10:00 | 2.0km | FC 135 | Pace 05:00
       |
 [Principal]    25:00 | 5.5km | FC 162 | Pace 04:32
       |
 [Intervalado]  04:00 | 1.0km | FC 178 | Pace 04:00  RPE: 9
       |
 [Recuperacao]  02:00 | 0.4km | FC 145 | Pace 05:00
       |
 [Intervalado]  04:00 | 1.0km | FC 175 | Pace 04:05  RPE: 8
       |
 [Desaquecimento] 08:00 | 1.5km | FC 128 | Pace 05:20
```

---

## 7. Exemplos de Payloads

### 7.1 POST - Treino SEM etapas (compativel com versao anterior)

```json
{
  "atletaId": "123e4567-e89b-12d3-a456-426614174000",
  "dataTreino": "2024-01-15",
  "diaSemana": "SEGUNDA",
  "tipoTreino": "INTERVALADO",
  "descricao": "Intervalado 5x1000m",
  "duracaoMin": "01:05:30",
  "distanciaKm": 12.5,
  "ritmoMedio": "05:15",
  "fcMedia": 165,
  "fcMax": 185,
  "percepcaoEsforco": 8,
  "status": "CONCLUIDO",
  "fonteDados": "MANUAL"
}
```

### 7.2 POST - Treino COM etapas detalhadas

```json
{
  "atletaId": "123e4567-e89b-12d3-a456-426614174000",
  "dataTreino": "2024-01-15",
  "diaSemana": "SEGUNDA",
  "tipoTreino": "INTERVALADO",
  "descricao": "Intervalado 5x1000m com recuperacao",
  "duracaoMin": "01:05:30",
  "distanciaKm": 12.5,
  "ritmoMedio": "05:15",
  "fcMedia": 165,
  "fcMax": 185,
  "percepcaoEsforco": 8,
  "status": "CONCLUIDO",
  "fonteDados": "GARMIN",
  "etapasRealizadas": [
    {
      "ordem": 1,
      "tipoEtapa": "AQUECIMENTO",
      "descricao": "Aquecimento progressivo",
      "duracao": "10:00",
      "distanciaKm": 2.0,
      "fcMedia": 135,
      "fcMax": 148,
      "paceMedia": "05:00"
    },
    {
      "etapaPlanejadaId": "abc12345-e89b-12d3-a456-426614174010",
      "ordem": 2,
      "tipoEtapa": "INTERVALADO",
      "descricao": "Tiro 1 - 1000m",
      "duracao": "04:00",
      "distanciaKm": 1.0,
      "fcMedia": 178,
      "fcMax": 185,
      "paceMedia": "04:00",
      "percepcaoEsforco": 9,
      "cadenciaMedia": 182
    },
    {
      "ordem": 3,
      "tipoEtapa": "RECUPERACAO",
      "descricao": "Recuperacao entre tiros",
      "duracao": "02:00",
      "distanciaKm": 0.4,
      "fcMedia": 145,
      "paceMedia": "05:00"
    },
    {
      "ordem": 4,
      "tipoEtapa": "DESAQUECIMENTO",
      "descricao": "Trote leve final",
      "duracao": "08:00",
      "distanciaKm": 1.5,
      "fcMedia": 128,
      "paceMedia": "05:20",
      "observacao": "Senti leve dor no joelho direito"
    }
  ]
}
```

### 7.3 Response Exemplo

```json
{
  "id": "999e4567-e89b-12d3-a456-426614174099",
  "dataTreino": "2024-01-15",
  "diaSemana": "SEGUNDA",
  "tipoTreino": "INTERVALADO",
  "descricao": "Intervalado 5x1000m com recuperacao",
  "duracaoMin": "01:05:30",
  "distanciaKm": 12.5,
  "paceMedia": "05:15",
  "fcMedia": 165,
  "fcMax": 185,
  "percepcaoEsforco": 8,
  "tssCalculado": 95,
  "metodoCalculoTss": "FC",
  "intensidadeReal": 0.88,
  "status": "CONCLUIDO",
  "fonteDados": "GARMIN",
  "etapasRealizadas": [
    {
      "id": "aaa11111-e89b-12d3-a456-426614174001",
      "ordem": 1,
      "tipoEtapa": "AQUECIMENTO",
      "descricao": "Aquecimento progressivo",
      "duracao": "10:00",
      "distanciaKm": 2.0,
      "fcMedia": 135,
      "fcMax": 148,
      "paceMedia": "05:00"
    },
    {
      "id": "aaa22222-e89b-12d3-a456-426614174002",
      "etapaPlanejadaId": "abc12345-e89b-12d3-a456-426614174010",
      "ordem": 2,
      "tipoEtapa": "INTERVALADO",
      "descricao": "Tiro 1 - 1000m",
      "duracao": "04:00",
      "distanciaKm": 1.0,
      "fcMedia": 178,
      "fcMax": 185,
      "paceMedia": "04:00",
      "percepcaoEsforco": 9,
      "cadenciaMedia": 182
    }
  ]
}
```

---

## 8. Checklist de Implementacao Frontend

- [ ] Atualizar types/interfaces TypeScript com os novos contratos
- [ ] Adicionar `EtapaRealizadaInput` e `EtapaRealizadaOutput` ao schema de tipos
- [ ] Criar secao colapsavel "Etapas do Treino" no formulario de treino realizado
- [ ] Implementar repeater (adicionar/remover/reordenar etapas)
- [ ] Input com mascara para campos de duracao e pace (`MM:SS` / `HH:MM:SS`)
- [ ] Select para `tipoEtapa` com as 5 opcoes e cores
- [ ] Slider ou input numerico para RPE (1-10) com validacao
- [ ] Ao "marcar realizado", pre-popular etapas a partir das etapas planejadas
- [ ] Exibicao em timeline/accordion das etapas na tela de detalhe
- [ ] Comparacao visual planejado vs realizado quando `etapaPlanejadaId` existir
- [ ] Tratar `etapasRealizadas` como `undefined | []` (campos null omitidos)
- [ ] Recalcular campo `ordem` automaticamente ao reordenar ou remover etapas
