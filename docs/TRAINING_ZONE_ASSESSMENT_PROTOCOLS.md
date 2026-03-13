# Protocolos de Avaliação de Zonas de Treinamento

## Índice

1. [Visão Geral](#visão-geral)
2. [Fundamentação Científica](#fundamentação-científica)
3. [Protocolo de 3K (Brasil)](#protocolo-de-3k-brasil)
4. [Protocolo de 20 Minutos](#protocolo-de-20-minutos)
5. [Protocolo de 30 Minutos](#protocolo-de-30-minutos)
6. [Protocolo de 5K](#protocolo-de-5k)
7. [Protocolo de Cooper (12 minutos)](#protocolo-de-cooper-12-minutos)
8. [Protocolos por Nível de Atleta](#protocolos-por-nível-de-atleta)
9. [Cálculo de Zonas de Treinamento](#cálculo-de-zonas-de-treinamento)
10. [Arquitetura da Solução](#arquitetura-da-solução)
11. [Modelo de Dados](#modelo-de-dados)
12. [Algoritmos de Cálculo](#algoritmos-de-cálculo)
13. [Endpoints da API](#endpoints-da-api)
14. [Integração com Sistema Existente](#integração-com-sistema-existente)
15. [Roadmap de Implementação](#roadmap-de-implementação)

---

## Visão Geral

Esta feature implementa testes de campo **validados cientificamente** para avaliação precisa das zonas de treinamento de corredores, permitindo:

- **Determinação do Limiar Anaeróbico (LT)** através de protocolos de 3K (Brasil), 20min, 30min, 5K ou Cooper
- **Cálculo automático de zonas de FC e Pace** baseado nos resultados dos testes
- **Recomendação do melhor protocolo** baseado no nível do atleta
- **Histórico de evolução** para tracking de progresso
- **Validação de resultados** com ranges fisiológicos esperados

### Benefícios

1. **Precisão**: Substitui fórmulas genéricas (220-idade) por dados individuais
2. **Simplicidade**: Testes executáveis em qualquer lugar (sem laboratório)
3. **Validação**: Protocolos com embasamento científico comprovado (Joe Friel, Jack Daniels, TrainingPeaks)
4. **Personalização**: Protocolo adequado ao nível de cada atleta

---

## Fundamentação Científica

### Por que FC Máxima de "220 - idade" é imprecisa?

A fórmula clássica tem **desvio padrão de ±10-12 bpm**, tornando-a inadequada para prescrição individual de treinos.

**Exemplo:**
- Atleta A (30 anos): FC máxima real = 185 bpm
- Atleta B (30 anos): FC máxima real = 195 bpm
- Fórmula genérica: 220 - 30 = **190 bpm** (erro de -5 bpm para A, +5 bpm para B)

### Limiar Anaeróbico (LT) - A Métrica Mais Importante

O **Limiar Anaeróbico (Lactate Threshold)** é mais importante que FC máxima para prescrição de treino:

- **Definição**: Maior intensidade sustentável por ~60min sem acúmulo excessivo de lactato
- **Variação individual**: Ocorre em **85-92% FCmax** (alta variação entre atletas)
- **Treinável**: Pode subir de 85% para 92% FCmax com treino adequado (ganho de ~7% em desempenho)
- **Determina zonas**: Todas as zonas de treino são calculadas em relação ao limiar

### Protocolos de Campo vs Laboratório

| Aspecto | Teste de Laboratório | Teste de Campo |
|---------|---------------------|----------------|
| Precisão | ★★★★★ (gold standard) | ★★★★☆ (r > 0.90) |
| Custo | Alto (R$ 300-800) | Gratuito |
| Acessibilidade | Baixa (requer lab) | Alta (qualquer lugar) |
| Frequência | 2-4x/ano | A cada 4-8 semanas |
| Equipamento | Analisador de gases | Monitor FC + GPS |

**Conclusão**: Testes de campo têm correlação > 0.90 com testes de laboratório e são ideais para monitoramento regular.

### Referências Científicas

1. **Friel, J. (2016)** - "The Cyclist's Training Bible" / "The Triathlete's Training Bible"
   - Base Publishing
   - Protocolo de 30 minutos para determinação de FTP/LTHR

2. **Daniels, J. (2013)** - "Daniels' Running Formula"
   - Human Kinetics
   - VDOT system e testes de campo validados

3. **Allen, H. & Coggan, A. (2010)** - "Training and Racing with a Power Meter"
   - VeloPress
   - Protocolo de 20 minutos (×0.95 para FTP)

4. **Seiler, S. & Kjerland, G. (2006)** - "Quantifying training intensity distribution"
   - International Journal of Sports Physiology and Performance
   - Fundamentação das zonas de treinamento polarizado

5. **Pallarés et al. (2016)** - "Validity and reliability of ventilatory threshold"
   - European Journal of Applied Physiology
   - Validação de testes de campo (r = 0.91-0.95 com lab)

---

## Protocolo de 3K (Brasil)

### Descrição

Teste de **3000 metros em máximo esforço** - protocolo **muito popular no Brasil**, amplamente utilizado por assessorias esportivas.

**Conceito**: 3K em máximo esforço = ~104-108% do limiar anaeróbico (duração típica: 11-18 minutos)

### Nível Recomendado
✅ **Iniciante** (distância acessível, menos intimidante que 5K)
✅ **Intermediário** (protocolo ideal para reavaliações frequentes)
✅ **Avançado** (reavaliaç rápida entre testes mais longos)

### Por que 3K é Popular no Brasil?

1. **Acessibilidade**: Distância gerenciável para a maioria dos corredores
2. **Duração ideal**: 11-18min = zona de esforço próxima ao limiar
3. **Adotado por assessorias**: Protocolo padrão de muitas equipes brasileiras
4. **Correlação alta**: r > 0.85 com testes de laboratório
5. **Menos desgastante**: Permite reteste mais frequente que 5K (a cada 4 semanas)
6. **Prático em pista**: Exatos **7,5 voltas** (fácil de executar e contar)

### Como Executar

#### Pré-requisitos
- Base aeróbica mínima (conseguir correr 5-6km confortavelmente)
- Recuperado (sem treino intenso 48h antes)
- Monitor de FC com GPS
- Percurso plano e medido (ideal: pista de atletismo)

#### Protocolo Completo

1. **Aquecimento (15-20 minutos)**:
   - 12-15 min em zona 2 (ritmo confortável)
   - 3 acelerações progressivas (80m, 60m, 40m)
   - 3 min recuperação ativa

2. **Teste Principal (3000m = 7,5 voltas)**:
   - Objetivo: **menor tempo possível em 3000 metros**
   - Estratégia: "Even pace" ou "negative split"
   - **Primeiro km (2,5 voltas)**: Controlado, não começar muito rápido
   - **Segundo km (2,5 voltas)**: Manter ritmo consistente, encontrar o limite
   - **Último km (2,5 voltas)**: Aumentar esforço gradualmente, "kick" final permitido
   - **Registrar**: tempo total, splits por km, FC média (total e últimos 2km), FC máxima

3. **Recuperação (10-15 min)**:
   - 10 min zona 1 muito leve
   - Hidratação
   - Alongamento leve

#### Métricas Coletadas

```json
{
  "distanciaMetros": 3000,
  "tempoSegundos": 780,        // 13:00
  "tempoFormatado": "13:00",
  "paceMedio": 260,            // 4:20/km
  "fcMedia": 176,
  "fcMediaUltimos2K": 178,     // ⚠️ MAIS IMPORTANTE
  "fcMaxima": 184,
  "splits": [265, 258, 257],   // por km (negative split ideal)
  "percepcaoEsforco": 9,       // RPE 1-10
  "condicoesAmbientais": {
    "temperatura": 22,
    "vento": "Calmo",
    "terreno": "PISTA"
  }
}
```

### Interpretação dos Resultados

#### FC no Limiar Anaeróbico

```
FC_limiar = FC_média_últimos_2K × fator_ajuste_tempo
```

**Por que últimos 2K?**
- Primeiro km: FC ainda está subindo
- Últimos 2km: FC estabilizada mais próxima do limiar real

**Fator de ajuste baseado no tempo de prova**:

| Tempo 3K | Nível Aprox. | FC_3K vs LTHR | Fator Ajuste | Equivalente 5K |
|----------|--------------|---------------|--------------|----------------|
| < 10:30  | Elite        | ~110%         | 0.91         | < 17:30        |
| 10:30-12:30 | Avançado+  | ~108%         | 0.93         | 17:30-21:00    |
| 12:30-14:30 | Avançado   | ~106%         | 0.95         | 21:00-24:30    |
| 14:30-16:30 | Intermediário| ~104%       | 0.96         | 24:30-27:30    |
| 16:30-18:00 | Iniciante+ | ~102%         | 0.98         | 27:30-30:00    |
| > 18:00  | Iniciante    | ~100%         | 1.00         | > 30:00        |

**Exemplo**: Tempo 3K = 13:00, FC média últimos 2K = 178 bpm
→ Fator = 0.95
→ FC limiar = 178 × 0.95 = **169 bpm**

**Razão**: Quanto mais rápido o atleta, maior a % acima do limiar que consegue sustentar em provas curtas.

#### Pace no Limiar

```
Pace_limiar = Pace_médio_3K + ajuste_tempo_segundos
```

**Ajuste baseado no tempo**:

| Tempo 3K | Pace Médio | Ajuste (s/km) | Pace Limiar Resultante |
|----------|------------|---------------|------------------------|
| < 11min  | < 3:40/km  | +8s           | Ex: 3:40 → 3:48/km     |
| 11-13min | 3:40-4:20  | +10s          | Ex: 4:20 → 4:30/km     |
| 13-15min | 4:20-5:00  | +12s          | Ex: 4:40 → 4:52/km     |
| 15-17min | 5:00-5:40  | +15s          | Ex: 5:10 → 5:25/km     |
| > 17min  | > 5:40     | +18s          | Ex: 5:50 → 6:08/km     |

**Exemplo**: Pace 3K = 4:20/km (260s), tempo = 13:00
→ Ajuste = +10s
→ Pace limiar = 260 + 10 = **270s/km = 4:30/km**

**Cálculo alternativo (% velocidade)**:
```java
// Velocidade 3K
velocidade_3k = (3000.0 / tempo_segundos) * 3.6; // km/h

// Fator baseado no tempo
double fator_vel = tempo < 630 ? 0.93 :   // < 10:30
                   tempo < 750 ? 0.95 :   // 10:30-12:30
                   tempo < 870 ? 0.96 :   // 12:30-14:30
                   tempo < 990 ? 0.97 :   // 14:30-16:30
                   tempo < 1080 ? 0.98 :  // 16:30-18:00
                   0.99;                   // > 18:00

// Velocidade limiar
velocidade_limiar = velocidade_3k * fator_vel;

// Converter para pace
pace_limiar = 3600.0 / velocidade_limiar;
```

#### VO2max Estimado

Podemos estimar VO2max usando a relação 3K → 5K:

```java
// Prever tempo de 5K baseado no 3K
// Fórmula empírica: pace 5K = pace 3K + 8-12 segundos/km
double pace_5k = (tempo_3k / 3.0) + 10; // +10s/km em média
double tempo_5k_estimado = pace_5k * 5.0;

// Aplicar fórmula Léger-Mercier
double velocidade_m_min = 5000.0 / (tempo_5k_estimado / 60.0);
double vo2max = -4.6
              + (0.182258 * velocidade_m_min)
              + (0.000104 * velocidade_m_min * velocidade_m_min);
```

**Exemplo**: 3K em 13:00 (pace 4:20/km)
→ Pace 5K estimado ≈ 4:30/km
→ Tempo 5K estimado ≈ 22:30
→ VO2max ≈ **51.2 ml/kg/min**

#### Relação 3K ↔ 5K

Tabela de equivalência aproximada (baseada em dados de assessorias brasileiras):

| Tempo 3K | Tempo 5K Equiv. | Pace 3K | Pace 5K Equiv. | VDOT |
|----------|----------------|---------|----------------|------|
| 9:30     | 16:00          | 3:10/km | 3:12/km        | 61   |
| 10:30    | 17:45          | 3:30/km | 3:33/km        | 57   |
| 11:30    | 19:30          | 3:50/km | 3:54/km        | 53   |
| 12:30    | 21:15          | 4:10/km | 4:15/km        | 49   |
| 13:30    | 23:00          | 4:30/km | 4:36/km        | 46   |
| 14:30    | 24:45          | 4:50/km | 4:57/km        | 43   |
| 15:30    | 26:30          | 5:10/km | 5:18/km        | 40   |
| 16:30    | 28:15          | 5:30/km | 5:39/km        | 38   |
| 17:30    | 30:00          | 5:50/km | 6:00/km        | 36   |

### Validação de Consistência

Analisar splits para verificar se teste foi bem executado:

```java
public String analisarConsistenciaSplits3K(List<Integer> splits) {
    if (splits.size() != 3) {
        return "Dados insuficientes (esperado 3 splits de 1km)";
    }

    int primeiroKm = splits.get(0);
    int segundoKm = splits.get(1);
    int terceiroKm = splits.get(2);

    // Calcular variação
    int variacaoPrimeiroUltimo = primeiroKm - terceiroKm;
    int variacaoMaxima = Math.max(
        Math.abs(primeiroKm - segundoKm),
        Math.abs(segundoKm - terceiroKm)
    );

    // Análise de pacing
    if (terceiroKm < segundoKm && segundoKm < primeiroKm && variacaoPrimeiroUltimo > 10) {
        // Negative split progressivo (ideal)
        return "Excelente - negative split progressivo (pacing perfeito)";
    } else if (variacaoMaxima < 5) {
        // Even pace
        return "Muito bom - pacing constante (even pace)";
    } else if (terceiroKm > primeiroKm + 15) {
        // Positive split acentuado
        return "⚠️ Atenção - começou muito rápido, fadiga acentuada no final";
    } else if (terceiroKm < primeiroKm && variacaoPrimeiroUltimo < 10) {
        // Negative split moderado
        return "Bom - negative split controlado";
    } else if (primeiroKm < segundoKm && terceiroKm < segundoKm) {
        // Split do meio mais lento (erro comum)
        return "⚠️ Atenção - km do meio mais lento (ajustar estratégia)";
    } else {
        return "Aceitável - variações no pacing";
    }
}
```

**Exemplos de análise**:
- Splits: [265s, 258s, 257s] → "Excelente - negative split progressivo"
- Splits: [260s, 260s, 261s] → "Muito bom - pacing constante"
- Splits: [250s, 258s, 272s] → "⚠️ Atenção - começou muito rápido"
- Splits: [260s, 268s, 258s] → "⚠️ Atenção - km do meio mais lento"

### Vantagens do 3K

✅ **Popular no Brasil**: Protocolo estabelecido em assessorias
✅ **Acessível**: Distância gerenciável para iniciantes
✅ **Duração ideal**: 11-18min = zona próxima ao limiar
✅ **Menos fadiga**: Permite reteste mais frequente (4 semanas vs 6-8 do 5K)
✅ **Boa correlação**: r > 0.85 com testes de laboratório
✅ **Prático em pista**: 7,5 voltas exatas (fácil contar)
✅ **Progressão clara**: Facilita tracking de evolução
✅ **Menor risco lesão**: Esforço mais curto

### Desvantagens

❌ **Menos validado**: Menos estudos científicos que 5K ou protocolos de tempo fixo
❌ **Distância não-olímpica**: 3000m não é prova oficial (mas 3000m com obstáculos é)
❌ **Requer pista**: 7,5 voltas pode ser difícil contar (erro comum: fazer 7 ou 8 voltas)
❌ **Ajustes necessários**: Não é medida direta do limiar (como teste 30min)
❌ **Menos específico**: Para maratonistas, testes mais longos são melhores

### Quando Usar

- **Iniciantes**: Primeiro teste de avaliação (menos intimidante que 5K)
- **Intermediários**: Protocolo principal de reavaliaç (a cada 4-6 semanas)
- **Assessorias brasileiras**: Manter compatibilidade com protocolo já usado
- **Reavaliação rápida**: Entre testes mais longos (30min ou 5K)
- **Retorno de lesão**: Teste menos agressivo para avaliar condição
- **Progressão**: Teste 3K → evoluir para 5K quando atleta avançar

### Comparação com Outros Protocolos

| Aspecto | 3K | 5K | 20min | 30min | Cooper |
|---------|----|----|-------|-------|--------|
| **Duração** | 11-18min | 15-30min | 20min | 30min | 12min |
| **Precisão FC** | ★★★☆☆ | ★★★★☆ | ★★★★☆ | ★★★★★ | ★★☆☆☆ |
| **Precisão Pace** | ★★★☆☆ | ★★★★☆ | ★★★★☆ | ★★★★★ | ★★☆☆☆ |
| **Acessibilidade** | ★★★★★ | ★★★☆☆ | ★★★☆☆ | ★★☆☆☆ | ★★★★☆ |
| **Recuperação** | 36-48h | 48-72h | 36-48h | 48-72h | 24-36h |
| **Iniciantes** | ✅ Ideal | ⚠️ OK | ❌ Difícil | ❌ Muito difícil | ✅ Bom |
| **Popular BR** | ✅✅✅ | ✅✅ | ⚠️ | ⚠️ | ✅ |
| **Científico** | ★★★☆☆ | ★★★★★ | ★★★★★ | ★★★★★ | ★★★★☆ |

### Dicas para Execução Perfeita

1. **Contar voltas**: Usar contador manual ou pedir ajuda de alguém
2. **Negative split**: Começar controlado, acelerar progressivamente
3. **Respiração**: Deve estar difícil mas não impossível de falar
4. **Pista livre**: Evitar horários de pico (muitas pessoas)
5. **Raia interna**: Sempre correr na raia 1 (400m exatos)
6. **Aquecimento adequado**: 15-20min + acelerações
7. **Clima**: Evitar calor extremo (>30°C) ou chuva forte

---

## Protocolo de 20 Minutos

### Descrição

Teste de **20 minutos em esforço máximo controlado** para determinação do limiar funcional.

**Conceito**: 20 minutos all-out = ~102-105% do limiar real (protocolo TrainingPeaks/Allen & Coggan adaptado para corrida)

### Nível Recomendado
✅ **Intermediário** (volume > 30km/semana, experiência > 6 meses)
✅ **Avançado**
⚠️ **Iniciante**: Pode ser muito intenso, preferir teste de 30min

### Como Executar

#### Pré-requisitos
- Atleta descansado (sem treino intenso nas últimas 48h)
- Base aeróbica mínima (conseguir correr 10km confortavelmente)
- Monitor de FC com GPS
- Percurso plano e medido (ideal: pista ou percurso marcado)

#### Protocolo Completo

1. **Aquecimento (20 minutos)**:
   - 15 min em zona 2 (ritmo confortável)
   - 3 acelerações progressivas de 20 segundos cada
   - 2 min recuperação

2. **Teste Principal (20 minutos)**:
   - Objetivo: **máximo esforço sustentável por 20 minutos**
   - Começar controlado (não sprint inicial!)
   - Manter ritmo constante ou "negative split" (segunda metade igual ou mais rápida)
   - Últimos 5 minutos podem ser mais intensos (mas sem "sprint" final)
   - **Registrar**: distância total, FC média (últimos 15min), FC máxima, pace médio

3. **Recuperação (10-15 min)**:
   - Ritmo muito lento em zona 1
   - Alongamento leve

#### Métricas Coletadas

```json
{
  "distanciaTotal": 5200,  // metros em 20 minutos
  "tempoSegundos": 1200,
  "fcMedia": 175,          // últimos 15 minutos
  "fcMaxima": 182,
  "paceMedioSegKm": 231,   // segundos/km (3:51/km neste exemplo)
  "percepcaoEsforco": 9,   // RPE 1-10
  "splits": [232, 230, 231, 230], // splits de 5min
  "condicoesAmbientais": {
    "temperatura": 22,
    "vento": "Calmo",
    "terreno": "PISTA"
  }
}
```

### Interpretação dos Resultados

#### FC no Limiar Anaeróbico (LTHR)

```
FC_limiar = FC_média_teste × 0.98
```

**Fundamento**: Em teste de 20min all-out, FC média fica ~102% do limiar real.

**Exemplo**: FC média teste = 175 bpm → FC limiar = 175 × 0.98 = **172 bpm**

**Ajuste por Experiência**:
| Nível | Fator de Ajuste | Razão |
|-------|----------------|-------|
| Iniciante | 0.96 | Tende a começar muito rápido, fadiga antes |
| Intermediário | 0.98 | Padrão |
| Avançado | 0.99 | Ótimo pacing, sustenta intensidade alta |
| Elite | 1.00 | FC média = LTHR (domínio perfeito) |

#### Pace no Limiar (Threshold Pace)

```
Pace_limiar = Pace_médio_teste + 6 segundos/km
```

**Fundamento**: 20min all-out = ~103-105% da velocidade limiar.

**Exemplo**: Pace teste = 3:51/km (231s/km) → Pace limiar = 3:51 + 0:06 = **3:57/km** (237s/km)

**Cálculo alternativo (por velocidade)**:
```java
velocidade_teste_km_h = (distancia_metros / 1200) * 3.0; // 5200m → 15.6 km/h
velocidade_limiar = velocidade_teste_km_h / 1.03;        // 15.6 / 1.03 = 15.15 km/h
pace_limiar_seg_km = 3600 / velocidade_limiar;           // 3600 / 15.15 = 237s/km (3:57/km)
```

#### Functional Threshold Pace (FTP)

```
FTP_pace = Pace_médio_20min + 5-7 segundos/km
```

FTP é o pace que pode ser sustentado por ~60 minutos em máximo esforço.

### Vantagens
✅ Duração moderada (20min de esforço)
✅ Alta correlação com teste de laboratório (r > 0.92)
✅ Protocolo bem estabelecido (TrainingPeaks, TrainerRoad)
✅ Recuperação mais rápida que teste de 30min ou 5K

### Desvantagens
❌ Requer experiência em pacing (iniciantes começam muito rápido)
❌ Esforço mental alto (20min all-out é sofrido)
❌ Pode superestimar limiar em iniciantes (fator de ajuste necessário)

### Quando Usar
- **Atletas intermediários**: Protocolo principal (a cada 6-8 semanas)
- **Avançados**: Reavaliaç��o rápida entre mesociclos
- **Pré-temporada**: Estabelecer zonas base
- **Pós-lesão**: Retorno gradual (após 3-4 semanas de treino base)

---

## Protocolo de 30 Minutos

### Descrição

Teste de **30 minutos em esforço máximo controlado** - considerado o **gold standard** de testes de campo.

**Conceito**: 30 minutos all-out = pace e FC no limiar anaeróbico (protocolo Joe Friel)

### Nível Recomendado
✅ **Avançado** (volume > 50km/semana)
✅ **Elite**
✅ **Intermediário** (com supervisão)
⚠️ **Iniciante**: Muito intenso e longo

### Como Executar

#### Pré-requisitos
- Base aeróbica sólida (mínimo 8 semanas de treino consistente)
- Capacidade de correr 15km confortavelmente
- Monitor FC com GPS preciso
- Percurso plano (ideal: pista de atletismo ou circuito fechado)

#### Protocolo Completo

1. **Aquecimento (20-25 minutos)**:
   - 15-20 min em zona 2
   - 3-4 acelerações progressivas (30s, 20s, 20s, 10s)
   - 3 min recuperação leve

2. **Teste Principal (30 minutos)**:
   - Objetivo: **máximo esforço sustentável por 30 minutos**
   - Estratégia: "Even pace" (ritmo constante) ou "negative split"
   - **Primeiros 10 minutos**: Estabelecer ritmo, não começar muito rápido
   - **10-20 minutos**: Manter ritmo, monitorar FC
   - **Últimos 10 minutos**: Aumentar esforço gradualmente se conseguir
   - **Registrar a cada minuto**: FC, pace, distância

3. **Recuperação (15 min)**:
   - 10-15 min zona 1
   - Hidratação e alongamento

#### Métricas Coletadas

```json
{
  "distanciaTotal": 7800,   // metros em 30 minutos
  "tempoSegundos": 1800,
  "fcMediaTotal": 172,
  "fcMediaUltimos20Min": 174,  // ⚠️ MAIS IMPORTANTE
  "fcMaxima": 180,
  "paceTotal": 231,            // 3:51/km média total
  "paceUltimos20Min": 228,     // ⚠️ MAIS IMPORTANTE (3:48/km)
  "percepcaoEsforco": 9,
  "splitsMinuto": [235, 232, 230, ...], // 30 valores
  "condicoesAmbientais": {...}
}
```

### Interpretação dos Resultados

#### FC no Limiar Anaeróbico (LTHR)

```
FC_limiar = FC_média_últimos_20_minutos
```

**Fundamento**: Em teste de 30min bem executado, os últimos 20 minutos representam o verdadeiro limiar.

**Exemplo**: FC média últimos 20min = 174 bpm → FC limiar = **174 bpm** (sem ajuste!)

**Por que últimos 20 minutos?**
- Primeiros 10min: FC ainda está subindo (não atingiu steady state)
- Minutos 10-30: FC estabilizada no limiar real
- Usar média total subestimaria o limiar

#### Pace no Limiar (Threshold Pace)

```
Pace_limiar = Pace_médio_últimos_20_minutos
```

**Exemplo**: Pace últimos 20min = 3:48/km (228s/km) → Pace limiar = **3:48/km**

**Sem ajustes necessários** - é a medida direta!

#### Functional Threshold Pace (FTP)

```
FTP = Pace_médio_últimos_20_minutos
```

Também conhecido como "ritmo de 1 hora".

#### VO2max Estimado (Fórmula Jack Daniels)

```java
// Distância em metros nos últimos 20 minutos
distancia20min = distanciaTotal * (20.0 / 30.0);

// Velocidade em m/min
velocidade_m_min = distancia20min / 20.0;

// VO2max (ml/kg/min) - Fórmula simplificada
vo2max = -4.6 + (0.182258 × velocidade_m_min) + (0.000104 × velocidade_m_min²);
```

**Exemplo**:
- Distância total 30min = 7800m
- Distância últimos 20min ≈ 5200m
- Velocidade = 260 m/min
- VO2max ≈ **53.5 ml/kg/min**

### Vantagens
✅ **Máxima precisão** de teste de campo (r > 0.95 com lab)
✅ **Sem ajustes**: Resultados diretos = limiar real
✅ **Gold standard** segundo Joe Friel e Jack Daniels
✅ Serve também como treino de qualidade

### Desvantagens
❌ Duração longa (30min all-out + aquecimento = 1h total)
❌ Demanda física e mental muito alta
❌ Recuperação de 48-72h necessária
❌ Risco de "queimar" nos primeiros 10min (erro de pacing)

### Quando Usar
- **Atletas avançados/elite**: Protocolo principal (a cada 8-12 semanas)
- **Início de temporada**: Baseline para mesociclos
- **Pré-prova importante**: 4-6 semanas antes para ajuste fino
- **Plateau**: Verificar se houve evolução real

---

## Protocolo de 5K

### Descrição

Teste de **5000 metros em máximo esforço** - simula uma prova de 5K.

**Conceito**: 5K time trial = ~107-112% do pace limiar (dependendo do nível)

### Nível Recomendado
✅ **Intermediário** (com experiência em provas)
✅ **Avançado**
✅ **Elite**
⚠️ **Iniciante**: OK se tiver experiência em provas de 5K

### Como Executar

#### Pré-requisitos
- Experiência em provas de 5K (saber fazer pacing)
- Base aeróbica (volume > 25km/semana)
- Recuperado (sem treino intenso 48-72h antes)
- Monitor FC com GPS

#### Protocolo Completo

1. **Aquecimento (20-25 minutos)**:
   - 15 min zona 2
   - 4-5 acelerações progressivas (100m, 80m, 60m, 40m, 20m)
   - 3-5 min de recuperação ativa

2. **Teste Principal (5000m)**:
   - Objetivo: **menor tempo possível**
   - Estratégia: "Even pace" ou "negative split"
   - **1º km**: Controlado (não começar muito rápido!)
   - **km 2-4**: Manter ritmo consistente
   - **Último km**: Aumentar esforço, "kick" final é permitido
   - **Registrar**: tempo total, splits por km, FC média, FC máx

3. **Recuperação (15 min)**:
   - 10 min zona 1 muito leve
   - Hidratação

#### Métricas Coletadas

```json
{
  "distanciaMetros": 5000,
  "tempoSegundos": 1260,       // 21:00
  "tempoFormatado": "21:00",
  "paceMedio": 252,            // 4:12/km
  "fcMedia": 178,              // média total
  "fcMediaUltimos3K": 180,     // mais representativo
  "fcMaxima": 186,
  "splits": [250, 252, 251, 253, 254], // por km
  "percepcaoEsforco": 10,      // máximo!
  "condicoesAmbientais": {...}
}
```

### Interpretação dos Resultados

#### FC no Limiar Anaeróbico

```
FC_limiar = FC_média_teste × fator_ajuste_tempo
```

**Fator de ajuste baseado no tempo de prova**:

| Tempo 5K | Nível Aprox. | FC_5K vs LTHR | Fator Ajuste |
|----------|--------------|---------------|--------------|
| < 17min  | Elite        | ~110%         | 0.91         |
| 17-19min | Avançado+    | ~108%         | 0.93         |
| 19-22min | Avançado     | ~106%         | 0.94         |
| 22-25min | Intermediário| ~104%         | 0.96         |
| 25-28min | Iniciante+   | ~102%         | 0.98         |
| > 28min  | Iniciante    | ~100%         | 1.00         |

**Exemplo**: Tempo 5K = 21:00, FC média = 178 bpm
→ Fator = 0.94
→ FC limiar = 178 × 0.94 = **167 bpm**

**Razão**: Atletas mais rápidos conseguem correr maior % acima do limiar em provas.

#### Pace no Limiar

```
Pace_limiar = Pace_5K + ajuste_tempo_segundos
```

**Ajuste baseado no nível**:

| Tempo 5K | Ajuste (s/km) | Razão |
|----------|---------------|-------|
| < 18min  | +10s          | Elite: limiar muito próximo ao 5K |
| 18-21min | +12s          | Avançado |
| 21-24min | +15s          | Intermediário (padrão) |
| 24-28min | +18s          | Iniciante+ |
| > 28min  | +20s          | Iniciante: grande diferença |

**Exemplo**: Pace 5K = 4:12/km (252s), tempo = 21:00
→ Ajuste = +15s
→ Pace limiar = 252 + 15 = **267s/km = 4:27/km**

**Cálculo alternativo (% velocidade)**:
```java
// Velocidade 5K
velocidade_5k = (5000.0 / tempo_segundos) * 3.6; // km/h

// Fator baseado no tempo
double fator_vel = tempo < 1080 ? 0.92 :  // < 18min
                   tempo < 1260 ? 0.94 :  // 18-21min
                   tempo < 1440 ? 0.95 :  // 21-24min
                   0.96;                   // > 24min

// Velocidade limiar
velocidade_limiar = velocidade_5k * fator_vel;

// Converter para pace
pace_limiar = 3600.0 / velocidade_limiar;
```

#### VO2max Estimado

```java
// Fórmula de Léger-Mercier (1984) validada
double velocidade_m_min = 5000.0 / (tempo_segundos / 60.0);

double vo2max = -4.6
              + (0.182258 * velocidade_m_min)
              + (0.000104 * velocidade_m_min * velocidade_m_min);
```

**Exemplo**: 5K em 21:00
→ Velocidade = 238 m/min
→ VO2max ≈ **52.8 ml/kg/min**

#### VDOT (Jack Daniels)

Sistema VDOT correlaciona tempos de prova com VO2max e prediz outros tempos.

```
VDOT ≈ tempo_5K (usar tabela Jack Daniels)
```

**Exemplo**: 5K em 21:00 → VDOT ≈ **47-48**

Com VDOT 47, previsões:
- 10K: ~43:40
- Meia Maratona: ~1:37:00
- Maratona: ~3:23:00

### Validação de Consistência

Analisar splits para verificar se teste foi bem executado:

```java
public String analisarConsistenciaSplits(List<Integer> splits) {
    if (splits.size() < 5) return "Dados insuficientes";

    int primeiroKm = splits.get(0);
    int ultimoKm = splits.get(splits.size() - 1);

    // Calcular desvio padrão
    double media = splits.stream().mapToInt(i -> i).average().orElse(0);
    double variancia = splits.stream()
        .mapToDouble(s -> Math.pow(s - media, 2))
        .average().orElse(0);
    double desvioPadrao = Math.sqrt(variancia);

    // Variação primeiro-último
    int diferenca = Math.abs(ultimoKm - primeiroKm);

    if (desvioPadrao < 3 && diferenca < 5) {
        return "Excelente - pacing muito consistente (even pace)";
    } else if (ultimoKm < primeiroKm && diferenca > 5 && diferenca < 15) {
        return "Muito bom - negative split controlado";
    } else if (primeiroKm < ultimoKm - 10) {
        return "⚠️ Atenção - começou muito rápido, fadiga no final";
    } else if (desvioPadrao > 8) {
        return "⚠️ Atenção - pacing irregular, refazer teste";
    } else {
        return "Bom - pacing aceitável";
    }
}
```

### Vantagens
✅ Teste familiar (atletas costumam correr provas de 5K)
✅ Simula situação real de prova
✅ Fornece múltiplas métricas (LTHR, FTP, VO2max, VDOT)
✅ Serve como referência de performance

### Desvantagens
❌ Requer experiência em pacing (iniciantes começam muito rápido)
❌ Fadiga alta (48-72h recuperação)
❌ Precisa de ajustes baseados no tempo (não é medida direta)
❌ Pode ser intimidante psicologicamente

### Quando Usar
- **Atletas com experiência em provas**: Protocolo principal
- **Início de temporada**: Benchmark de performance
- **Pré-competição**: 3-4 semanas antes de prova importante
- **Validação de treino**: Verificar progresso real

---

## Protocolo de Cooper (12 minutos)

### Descrição

Teste de **máxima distância em 12 minutos** - protocolo clássico desenvolvido por Dr. Kenneth Cooper (1968).

**Conceito**: Correlação direta entre distância em 12min e VO2max

### Nível Recomendado
✅ **Iniciante** (simples de executar)
✅ **Intermediário**
⚠️ **Avançado/Elite**: Preferir testes mais específicos

### Como Executar

#### Pré-requisitos
- Pista de atletismo (ideal para medir distância precisa)
- Monitor FC com GPS
- Recuperado (48h sem treino intenso)

#### Protocolo Completo

1. **Aquecimento (15 minutos)**:
   - 10 min zona 2
   - 3 acelerações de 20s
   - 2 min recuperação

2. **Teste (12 minutos)**:
   - Objetivo: **máxima distância possível em 12 minutos exatos**
   - Começar controlado
   - Acelerar progressivamente
   - Últimos 2-3min: máximo esforço
   - **Registrar**: distância total, FC média, FC máx

3. **Recuperação (10 min)**:
   - Zona 1 leve

#### Métricas Coletadas

```json
{
  "distanciaMetros": 3200,
  "tempoSegundos": 720,
  "fcMedia": 176,
  "fcMaxima": 184,
  "paceMedia": 225,  // 3:45/km
  "percepcaoEsforco": 9
}
```

### Interpretação dos Resultados

#### VO2max Estimado (Fórmula Cooper Original)

```
VO2max (ml/kg/min) = (distância_metros - 504.9) / 44.73
```

**Exemplo**: Distância = 3200m
→ VO2max = (3200 - 504.9) / 44.73 = **60.3 ml/kg/min**

#### Classificação por Idade e Sexo

**Homens (20-29 anos)**:
| Distância (m) | VO2max | Classificação |
|--------------|--------|---------------|
| < 2100       | < 35   | Muito fraco   |
| 2100-2400    | 35-42  | Fraco         |
| 2400-2700    | 42-48  | Médio         |
| 2700-3000    | 48-54  | Bom           |
| 3000-3400    | 54-61  | Muito bom     |
| > 3400       | > 61   | Excelente     |

**Mulheres (20-29 anos)**:
| Distância (m) | VO2max | Classificação |
|--------------|--------|---------------|
| < 1800       | < 29   | Muito fraco   |
| 1800-2100    | 29-35  | Fraco         |
| 2100-2400    | 35-42  | Médio         |
| 2400-2700    | 42-48  | Bom           |
| 2700-3000    | 48-54  | Muito bom     |
| > 3000       | > 54   | Excelente     |

#### FC no Limiar (Estimativa)

```
FC_limiar = FC_máxima_teste × 0.88
```

**Fundamento**: 12min all-out atinge ~95-100% FCmax. Limiar fica ~88-92% da FCmax.

**Exemplo**: FC máx = 184 bpm
→ FC limiar ≈ 184 × 0.88 = **162 bpm**

⚠️ **Atenção**: Esta é uma estimativa menos precisa. Usar com cautela.

#### Pace no Limiar (Estimativa)

```
Pace_limiar = Pace_Cooper × 1.12
```

**Exemplo**: Pace Cooper = 3:45/km (225s)
→ Pace limiar ≈ 225 × 1.12 = **252s/km = 4:12/km**

### Vantagens
✅ **Simples**: Fácil de entender e executar
✅ **Histórico**: Protocolo validado desde 1968
✅ **VO2max**: Boa estimativa de capacidade aeróbica
✅ **Iniciantes**: Menos intimidante que 5K

### Desvantagens
❌ **Impreciso para limiar**: Melhor para VO2max que para threshold
❌ **Duração curta**: 12min pode não atingir steady state
❌ **Requer pista**: Difícil medir distância precisa em outros locais

### Quando Usar
- **Iniciantes**: Primeira avaliação (menos intimidante)
- **Avaliação VO2max**: Quando foco é capacidade aeróbica
- **Tracking**: Mesma distância permite comparação fácil
- **Educação física**: Protocolo padrão em escolas/academias

---

## Protocolos por Nível de Atleta

### Iniciante (0-6 meses de corrida, < 20km/semana)

#### Protocolo Recomendado: **Cooper (12 min)** ou **5K controlado**

**Por quê?**
- ✅ Menos intimidante
- ✅ Duração gerenciável
- ✅ Baixo risco de lesão
- ✅ Familiaridade (provas de 5K são comuns)

**Alternativa conservadora**: Usar fórmulas genéricas inicialmente
```
FC_limiar_estimada = (FC_max_estimada) × 0.88
FC_max_estimada = 220 - idade (ou 208 - 0.7 × idade - mais precisa)
```

**Exemplo**: Atleta 30 anos
→ FC máx = 208 - (0.7 × 30) = 187 bpm
→ FC limiar ≈ 187 × 0.88 = **165 bpm**

Após 2-3 meses, realizar primeiro teste real.

### Intermediário (6 meses - 2 anos, 20-50km/semana)

#### Protocolo Recomendado: **Teste de 20 minutos**

**Por quê?**
- ✅ Duração moderada
- ✅ Alta precisão (r > 0.92)
- ✅ Recuperação mais rápida que 30min
- ✅ Protocolo bem estabelecido

**Frequência**: A cada 6-8 semanas

**Progressão**:
1. Mês 1-2: Estabelecer zonas com 20min
2. Mês 3-4: Treinar nas zonas calculadas
3. Mês 5-6: Refazer teste, ajustar zonas

**Alternativa**: Teste de 5K (se tiver experiência em provas)

### Avançado (> 2 anos, 50-80km/semana)

#### Protocolo Recomendado: **Teste de 30 minutos** (gold standard)

**Por quê?**
- ✅ Máxima precisão de campo
- ✅ Medida direta do limiar (sem ajustes)
- ✅ Serve como treino de qualidade
- ✅ Ideal para periodização avançada

**Frequência**: A cada 8-12 semanas (início de mesociclo)

**Protocolo complementar**: Teste de 5K a cada 4-6 semanas (tracking de performance)

**Combinação ideal**:
- Teste de 30min: Estabelecer zonas (início de mesociclo)
- Teste de 5K: Validar progresso (meio de mesociclo)
- Prova real: Verificar performance (final de mesociclo)

### Elite (> 5 anos, > 80km/semana, competitivo)

#### Protocolo Recomendado: **Laboratório** (4x/ano) + **30 minutos** (8x/ano)

**Por quê?**
- ✅ Precisão máxima (análise de lactato)
- ✅ Múltiplos parâmetros (VLa2, VLa4, VO2max real)
- ✅ Teste de campo para ajustes finos

**Frequência**:
- Laboratório: 3-4x/ano (início de macrociclos)
- Teste 30min: A cada 6 semanas
- Teste 5K: Provas reais

**Análise avançada**:
- Correlação lactato × FC
- Economia de corrida
- Custo energético por pace
- Drift cardiovascular

---

## Cálculo de Zonas de Treinamento

### Modelo de 5 Zonas (Joe Friel / TrainingPeaks)

Após determinar **FC Limiar (LTHR)** e **Pace Limiar**, calculamos 5 zonas baseadas em % do limiar.

#### Zonas de Frequência Cardíaca

| Zona | Nome             | % FC Limiar | % FC Reserva | Lactato (mmol/L) | Descrição                           | Duração Máx  |
|------|------------------|-------------|--------------|------------------|-------------------------------------|--------------|
| Z1   | Recuperação      | < 81%       | < 60%        | < 1.5            | Muito fácil, conversa livre         | Ilimitado    |
| Z2   | Base Aeróbica    | 81-89%      | 60-75%       | 1.5-2.5          | Fácil, respiração confortável       | 2-6 horas    |
| Z3   | Tempo            | 90-93%      | 75-82%       | 2.5-3.5          | Moderado, conversação difícil       | 60-90 min    |
| Z4   | Limiar           | 94-99%      | 82-90%       | 3.5-5.0          | Difícil, frases curtas              | 20-60 min    |
| Z5   | VO2max           | 100-102%    | 90-100%      | > 5.0            | Muito difícil, palavras soltas      | 3-8 min      |

**Exemplo de Cálculo** (FC Limiar = 170 bpm):

```java
public Map<String, ZonaFC> calcularZonasFC(int fcLimiar, Integer fcMax) {
    Map<String, ZonaFC> zonas = new HashMap<>();

    // Z1: < 81% do limiar
    zonas.put("z1", new ZonaFC(1, "Recuperação",
                               0,
                               (int)(fcLimiar * 0.81)));

    // Z2: 81-89%
    zonas.put("z2", new ZonaFC(2, "Base Aeróbica",
                               (int)(fcLimiar * 0.81),
                               (int)(fcLimiar * 0.89)));

    // Z3: 90-93%
    zonas.put("z3", new ZonaFC(3, "Tempo",
                               (int)(fcLimiar * 0.90),
                               (int)(fcLimiar * 0.93)));

    // Z4: 94-99%
    zonas.put("z4", new ZonaFC(4, "Limiar",
                               (int)(fcLimiar * 0.94),
                               (int)(fcLimiar * 0.99)));

    // Z5: 100-102% (ou até FC máx)
    int z5Max = fcMax != null ? fcMax : (int)(fcLimiar * 1.05);
    zonas.put("z5", new ZonaFC(5, "VO2max",
                               fcLimiar,
                               Math.min((int)(fcLimiar * 1.02), z5Max)));

    return zonas;
}
```

**Resultado**:
- Z1: 0-138 bpm (< 81% de 170)
- Z2: 138-151 bpm (81-89%)
- Z3: 153-158 bpm (90-93%)
- Z4: 160-168 bpm (94-99%)
- Z5: 170-173 bpm (100-102%)

### Zonas de Pace (Ritmo)

⚠️ **IMPORTANTE**: Pace é tempo/distância, então valores **menores** = mais **rápido**.

Zonas baseadas em **velocidade** relativa ao limiar, depois convertidas para pace.

| Zona | Nome          | % Velocidade Limiar | Equivalente Pace         | Descrição                     | Exemplo (Limiar 4:30/km) |
|------|---------------|---------------------|--------------------------|-------------------------------|--------------------------|
| Z1   | Recuperação   | 70-85%              | 118-143% pace limiar     | Muito lento, recuperação      | 5:19-6:27/km             |
| Z2   | Base Aeróbica | 85-95%              | 105-118% pace limiar     | Ritmo confortável             | 4:44-5:19/km             |
| Z3   | Marathon Pace | 95-100%             | 100-105% pace limiar     | Ritmo de maratona             | 4:30-4:44/km             |
| Z4   | Limiar        | 100-105%            | 95-100% pace limiar      | Ritmo de 10K-HM               | 4:17-4:30/km             |
| Z5   | VO2max        | 105-115%            | 87-95% pace limiar       | Ritmo de 3K-5K                | 3:55-4:17/km             |
| Z6   | Velocidade    | > 115%              | < 87% pace limiar        | Ritmo de 800m-1500m           | < 3:55/km                |

**Exemplo de Cálculo** (Pace Limiar = 270s/km = 4:30/km):

```java
public Map<String, ZonaPace> calcularZonasPace(int paceLimiarSegKm) {
    // Converter pace para velocidade (km/h)
    double velocidadeLimiar = 3600.0 / paceLimiarSegKm; // 3600/270 = 13.33 km/h

    Map<String, ZonaPace> zonas = new HashMap<>();

    // Z1: 70-85% da velocidade = pace 118-143% mais lento
    zonas.put("z1", calcularZonaPorVelocidade(velocidadeLimiar, 0.70, 0.85));

    // Z2: 85-95%
    zonas.put("z2", calcularZonaPorVelocidade(velocidadeLimiar, 0.85, 0.95));

    // Z3: 95-100%
    zonas.put("z3", calcularZonaPorVelocidade(velocidadeLimiar, 0.95, 1.00));

    // Z4: 100-105%
    zonas.put("z4", calcularZonaPorVelocidade(velocidadeLimiar, 1.00, 1.05));

    // Z5: 105-115%
    zonas.put("z5", calcularZonaPorVelocidade(velocidadeLimiar, 1.05, 1.15));

    // Z6: > 115%
    zonas.put("z6", calcularZonaPorVelocidade(velocidadeLimiar, 1.15, 1.30));

    return zonas;
}

private ZonaPace calcularZonaPorVelocidade(double velLimiar,
                                           double fatorMin,
                                           double fatorMax) {
    // Velocidades da zona (km/h)
    double velMin = velLimiar * fatorMin;
    double velMax = velLimiar * fatorMax;

    // Converter para pace (segundos/km)
    // Pace MAIOR = velocidade MENOR (inverso!)
    int paceMax = (int)(3600.0 / velMin); // velocidade menor → pace maior
    int paceMin = (int)(3600.0 / velMax); // velocidade maior → pace menor

    return new ZonaPace(paceMin, paceMax);
}
```

**Resultado** (Limiar = 4:30/km = 270s/km):
- Z1: 5:19-6:27/km (319-387s/km) - LENTO
- Z2: 4:44-5:19/km (284-319s/km)
- Z3: 4:30-4:44/km (270-284s/km)
- Z4: 4:17-4:30/km (257-270s/km)
- Z5: 3:55-4:17/km (235-257s/km)
- Z6: < 3:55/km (< 235s/km) - RÁPIDO

### Validação de Zonas Calculadas

```java
public void validarZonasCalculadas(ZonasTreinamento zonas, Atleta atleta) {
    // Validar FC
    if (zonas.getZ5FcMax() > atleta.getFcMaximaCalculada()) {
        log.warn("Z5 FC máx ({}) excede FC máx do atleta ({})",
                 zonas.getZ5FcMax(), atleta.getFcMaximaCalculada());
    }

    // Validar que zonas não se sobrepõem
    if (zonas.getZ2FcMax() >= zonas.getZ3FcMin()) {
        throw new IllegalStateException("Zonas Z2 e Z3 se sobrepõem");
    }

    // Validar ranges fisiológicos
    double percLimiarSobreFcMax = (zonas.getZ4FcMin() * 100.0) /
                                  atleta.getFcMaximaCalculada();

    if (percLimiarSobreFcMax < 75 || percLimiarSobreFcMax > 95) {
        log.warn("FC limiar é {}% da FC máx (esperado: 85-92%)",
                 String.format("%.1f", percLimiarSobreFcMax));
    }

    // Validar pace zones (Z1 mais lento que Z5)
    if (zonas.getZ1PaceMaxSegKm() < zonas.getZ5PaceMinSegKm()) {
        throw new IllegalStateException(
            "Zonas de pace invertidas! Z1 deve ser MAIS LENTO (maior valor) que Z5"
        );
    }
}
```

---

## Arquitetura da Solução

### Visão Geral

```
┌─────────────────────────────────────────────────────────────────┐
│                    Camada de Apresentação                       │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │ POST /testes   │  │ GET /testes    │  │ GET /zonas-atuais│  │
│  │ /20-minutos    │  │ /atleta/{id}   │  │ /{atletaId}      │  │
│  │ /30-minutos    │  │ Histórico      │  │ Zonas vigentes   │  │
│  │ /5k            │  │                │  │                  │  │
│  │ /cooper        │  │                │  │                  │  │
│  └────────────────┘  └────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                       Camada de Serviço                         │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │           TesteAvaliacaoService                            │ │
│  │  • registrarTeste20Min()                                   │ │
│  │  • registrarTeste30Min()                                   │ │
│  │  • registrarTeste5K()                                      │ │
│  │  • registrarTesteCooper()                                  │ │
│  │  • recomendarProtocolo(nivelAtleta)                        │ │
│  │  • validarResultados()                                     │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              ↓                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │         CalculadoraZonasService                            │ │
│  │  • calcularFcLimiar(protocolo, dados)                      │ │
│  │  • calcularPaceLimiar(protocolo, dados)                    │ │
│  │  • calcularZonasFC(fcLimiar, fcMax)                        │ │
│  │  • calcularZonasPace(paceLimiar)                           │ │
│  │  • calcularVO2max(resultado)                               │ │
│  │  • validarZonas()                                          │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Camada de Persistência                       │
│  ┌──────────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ TesteAvaliacao   │  │   Atleta     │  │ZonasTreinamento │  │
│  │   Repository     │  │  Repository  │  │   Repository     │  │
│  └──────────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Fluxo de Processamento

```
1. Cliente → POST /api/testes/20-minutos
                ↓
2. Controller → validar entrada (JSON schema)
                ↓
3. TesteAvaliacaoService → processar teste
                ↓
4. CalculadoraZonasService → calcular FC/Pace limiar
                ↓
5. CalculadoraZonasService → calcular 5 zonas FC + 6 zonas Pace
                ↓
6. TesteAvaliacaoService → validar resultados (ranges fisiológicos)
                ↓
7. TesteAvaliacaoService → persistir TesteAvaliacao
                ↓
8. TesteAvaliacaoService → criar/atualizar ZonasTreinamento
                ↓
9. TesteAvaliacaoService → atualizar Atleta (fcLimiar, paceLimiar, vo2max)
                ↓
10. Controller → retornar TesteOutputDto com zonas calculadas
```

---

## Modelo de Dados

### Entidade: `TesteAvaliacao`

```java
@Entity
@Table(name = "tb_teste_avaliacao",
       indexes = {
           @Index(name = "idx_teste_atleta_data",
                  columnList = "atleta_id,data_realizacao"),
           @Index(name = "idx_teste_tipo_valido",
                  columnList = "tipo_protocolo,valido")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TesteAvaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_protocolo", nullable = false)
    private TipoProtocoloTeste tipoProtocolo; // TESTE_20MIN, TESTE_30MIN, TESTE_5K, COOPER

    @Column(name = "data_realizacao", nullable = false)
    private LocalDate dataRealizacao;

    // ===== DADOS ESPECÍFICOS POR PROTOCOLO =====

    // Para testes de tempo fixo (20min, 30min, Cooper)
    @Column(name = "distancia_metros")
    private Integer distanciaMetros;

    @Column(name = "tempo_teste_segundos")
    private Integer tempoTesteSegundos;

    // Para teste de 5K (distância fixa)
    @Column(name = "tempo_total_segundos")
    private Integer tempoTotalSegundos;

    // Splits (JSON array) - para análise de consistência
    @Column(name = "splits_por_km", columnDefinition = "jsonb")
    @Convert(converter = SplitsJsonConverter.class)
    private List<Integer> splitsPorKm;

    // ===== DADOS DE FREQUÊNCIA CARDÍACA =====

    @Column(name = "fc_media_total")
    private Integer fcMediaTotal;

    @Column(name = "fc_media_segunda_metade")
    private Integer fcMediaSegundaMetade;

    @Column(name = "fc_media_ultimos_20min")
    private Integer fcMediaUltimos20Min; // Para teste de 30min

    @Column(name = "fc_maxima")
    private Integer fcMaxima;

    // ===== DADOS SUBJETIVOS =====

    @Column(name = "percepcao_esforco")
    private Integer percepcaoEsforco; // RPE 1-10

    @Column(name = "sensacao_teste")
    @Enumerated(EnumType.STRING)
    private SensacaoTeste sensacaoTeste; // OTIMO, BOM, NORMAL, DIFICIL, PESSIMO

    // ===== CONDIÇÕES AMBIENTAIS =====

    @Column(name = "temperatura_celsius", precision = 4, scale = 1)
    private BigDecimal temperaturaCelsius;

    @Column(name = "altitude_metros")
    private Integer altitudeMetros;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_terreno")
    private TipoTerreno tipoTerreno; // PISTA, ASFALTO, TRILHA, ESTEIRA

    @Column(name = "condicoes_vento")
    private String condicoesVento; // "Calmo", "Moderado contrário", "Forte favorável"

    // ===== RESULTADOS CALCULADOS =====

    @Column(name = "fc_limiar_calculada")
    private Integer fcLimiarCalculada;

    @Column(name = "pace_limiar_segundos_km")
    private Integer paceLimiarSegundosKm;

    @Column(name = "velocidade_limiar_km_h", precision = 5, scale = 2)
    private BigDecimal velocidadeLimiarKmH;

    @Column(name = "vo2max_estimado", precision = 5, scale = 2)
    private BigDecimal vo2maxEstimado;

    @Column(name = "vdot")
    private Integer vdot; // Jack Daniels VDOT (para teste 5K)

    // ===== ANÁLISE DE QUALIDADE =====

    @Column(name = "consistencia_splits")
    private String consistenciaSplits; // "Excelente", "Bom", "Irregular"

    @Column(name = "desvio_padrao_splits", precision = 5, scale = 2)
    private BigDecimal desvioPadraoSplits;

    @Column(name = "pacing_estrategia")
    @Enumerated(EnumType.STRING)
    private PacingEstrategia pacingEstrategia; // EVEN_PACE, NEGATIVE_SPLIT, POSITIVE_SPLIT

    // ===== VALIDAÇÃO E OBSERVAÇÕES =====

    @Column(name = "valido", nullable = false)
    private Boolean valido = true;

    @Column(name = "motivo_invalidacao")
    private String motivoInvalidacao;

    @Column(name = "observacoes", columnDefinition = "TEXT")
    private String observacoes;

    // ===== RELAÇÃO COM ZONAS =====

    @OneToOne(mappedBy = "testeAvaliacao", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ZonasTreinamento zonasTreinamento;

    // ===== AUDITORIA =====

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
        if (valido == null) valido = true;
    }

    @PreUpdate
    protected void onUpdate() {
        atualizadoEm = LocalDateTime.now();
    }

    // ===== MÉTODOS AUXILIARES =====

    public String getTempoFormatado() {
        if (tempoTotalSegundos == null) return null;
        int min = tempoTotalSegundos / 60;
        int seg = tempoTotalSegundos % 60;
        return String.format("%d:%02d", min, seg);
    }

    public String getPaceFormatado() {
        if (paceLimiarSegundosKm == null) return null;
        int min = paceLimiarSegundosKm / 60;
        int seg = paceLimiarSegundosKm % 60;
        return String.format("%d:%02d/km", min, seg);
    }
}
```

### Entidade: `ZonasTreinamento`

```java
@Entity
@Table(name = "tb_zonas_treinamento",
       indexes = {
           @Index(name = "idx_zonas_atleta_ativo",
                  columnList = "atleta_id,ativo")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZonasTreinamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atleta_id", nullable = false)
    private Atleta atleta;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teste_avaliacao_id")
    private TesteAvaliacao testeAvaliacao;

    @Column(name = "data_calculo", nullable = false)
    private LocalDate dataCalculo;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true; // Apenas uma versão ativa por atleta

    // ===== ZONAS DE FREQUÊNCIA CARDÍACA (BPM) =====

    @Column(name = "z1_fc_min")
    private Integer z1FcMin;

    @Column(name = "z1_fc_max")
    private Integer z1FcMax;

    @Column(name = "z2_fc_min")
    private Integer z2FcMin;

    @Column(name = "z2_fc_max")
    private Integer z2FcMax;

    @Column(name = "z3_fc_min")
    private Integer z3FcMin;

    @Column(name = "z3_fc_max")
    private Integer z3FcMax;

    @Column(name = "z4_fc_min")
    private Integer z4FcMin;

    @Column(name = "z4_fc_max")
    private Integer z4FcMax;

    @Column(name = "z5_fc_min")
    private Integer z5FcMin;

    @Column(name = "z5_fc_max")
    private Integer z5FcMax;

    // ===== ZONAS DE PACE (segundos/km) =====
    // IMPORTANTE: Valores MAIORES = pace MAIS LENTO

    @Column(name = "z1_pace_min_seg_km")
    private Integer z1PaceMinSegKm; // Pace mais rápido (valor menor)

    @Column(name = "z1_pace_max_seg_km")
    private Integer z1PaceMaxSegKm; // Pace mais lento (valor maior)

    @Column(name = "z2_pace_min_seg_km")
    private Integer z2PaceMinSegKm;

    @Column(name = "z2_pace_max_seg_km")
    private Integer z2PaceMaxSegKm;

    @Column(name = "z3_pace_min_seg_km")
    private Integer z3PaceMinSegKm;

    @Column(name = "z3_pace_max_seg_km")
    private Integer z3PaceMaxSegKm;

    @Column(name = "z4_pace_min_seg_km")
    private Integer z4PaceMinSegKm;

    @Column(name = "z4_pace_max_seg_km")
    private Integer z4PaceMaxSegKm;

    @Column(name = "z5_pace_min_seg_km")
    private Integer z5PaceMinSegKm;

    @Column(name = "z5_pace_max_seg_km")
    private Integer z5PaceMaxSegKm;

    @Column(name = "z6_pace_min_seg_km")
    private Integer z6PaceMinSegKm;

    @Column(name = "z6_pace_max_seg_km")
    private Integer z6PaceMaxSegKm;

    // ===== AUDITORIA =====

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    protected void onCreate() {
        criadoEm = LocalDateTime.now();
        if (ativo == null) ativo = true;
    }

    // ===== MÉTODOS AUXILIARES =====

    public String formatarZonaFC(int zona) {
        return switch (zona) {
            case 1 -> String.format("%d-%d bpm", z1FcMin, z1FcMax);
            case 2 -> String.format("%d-%d bpm", z2FcMin, z2FcMax);
            case 3 -> String.format("%d-%d bpm", z3FcMin, z3FcMax);
            case 4 -> String.format("%d-%d bpm", z4FcMin, z4FcMax);
            case 5 -> String.format("%d-%d bpm", z5FcMin, z5FcMax);
            default -> "Zona inválida";
        };
    }

    public String formatarZonaPace(int zona) {
        Integer paceMin = null, paceMax = null;

        switch (zona) {
            case 1 -> { paceMin = z1PaceMinSegKm; paceMax = z1PaceMaxSegKm; }
            case 2 -> { paceMin = z2PaceMinSegKm; paceMax = z2PaceMaxSegKm; }
            case 3 -> { paceMin = z3PaceMinSegKm; paceMax = z3PaceMaxSegKm; }
            case 4 -> { paceMin = z4PaceMinSegKm; paceMax = z4PaceMaxSegKm; }
            case 5 -> { paceMin = z5PaceMinSegKm; paceMax = z5PaceMaxSegKm; }
            case 6 -> { paceMin = z6PaceMinSegKm; paceMax = z6PaceMaxSegKm; }
        }

        if (paceMin == null || paceMax == null) return "Zona inválida";

        return String.format("%s-%s/km",
                formatarPace(paceMin),
                formatarPace(paceMax));
    }

    private String formatarPace(int segundos) {
        int min = segundos / 60;
        int seg = segundos % 60;
        return String.format("%d:%02d", min, seg);
    }
}
```

### Enums

```java
public enum TipoProtocoloTeste {
    TESTE_20MIN("TESTE_20MIN", "Teste de 20 Minutos",
                "Teste de máximo esforço por 20 minutos"),

    TESTE_30MIN("TESTE_30MIN", "Teste de 30 Minutos",
                "Gold standard de campo para determinação de limiar"),

    TESTE_5K("TESTE_5K", "Teste de 5 Quilômetros",
             "Teste de 5000 metros em máximo esforço"),

    COOPER("COOPER", "Teste de Cooper (12 minutos)",
           "Máxima distância em 12 minutos");

    private final String value;
    private final String label;
    private final String description;

    // Constructor, getters...
}

public enum SensacaoTeste {
    PESSIMO("Péssimo", 1),
    DIFICIL("Difícil", 2),
    NORMAL("Normal", 3),
    BOM("Bom", 4),
    OTIMO("Ótimo", 5);

    private final String label;
    private final int valor;

    // Constructor, getters...
}

public enum PacingEstrategia {
    EVEN_PACE("Even Pace", "Ritmo constante do início ao fim"),
    NEGATIVE_SPLIT("Negative Split", "Segunda metade mais rápida que primeira"),
    POSITIVE_SPLIT("Positive Split", "Primeira metade mais rápida (fadiga)");

    private final String nome;
    private final String descricao;

    // Constructor, getters...
}

public enum TipoTerreno {
    PISTA("Pista de Atletismo"),
    ASFALTO("Asfalto/Rua"),
    TRILHA("Trilha/Trail"),
    ESTEIRA("Esteira");

    private final String descricao;

    // Constructor, getters...
}
```

---

## Algoritmos de Cálculo

### Classe: `CalculadoraZonasService`

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class CalculadoraZonasService {

    /**
     * Calcula FC no limiar baseado em teste de 20 minutos
     *
     * Fundamento: Em 20min all-out, FC média fica ~102% do limiar real
     * Fonte: Allen & Coggan (2010), adaptado para corrida
     *
     * @param fcMediaTeste FC média durante os 20 minutos (ou últimos 15min)
     * @param nivelExperiencia Nível do atleta (ajusta fator)
     * @return FC no limiar anaeróbico
     */
    public Integer calcularFcLimiarTeste20Min(Integer fcMediaTeste,
                                              NivelExperiencia nivelExperiencia) {
        if (fcMediaTeste == null || fcMediaTeste < 100) {
            throw new IllegalArgumentException("FC média inválida: " + fcMediaTeste);
        }

        // Fator de ajuste por nível
        double fator = switch (nivelExperiencia) {
            case INICIANTE -> 0.96;      // Tende a começar rápido, fadiga
            case INTERMEDIARIO -> 0.98;  // Padrão
            case AVANCADO -> 0.99;       // Bom pacing
            case ELITE -> 1.00;          // Pacing perfeito
        };

        double fcLimiar = fcMediaTeste * fator;

        log.debug("FC Limiar calc: {} (FC média: {}, fator: {}, nível: {})",
                  Math.round(fcLimiar), fcMediaTeste, fator, nivelExperiencia);

        return (int) Math.round(fcLimiar);
    }

    /**
     * Calcula FC no limiar baseado em teste de 30 minutos
     *
     * Fundamento: Últimos 20min do teste de 30min = LTHR direto
     * Fonte: Joe Friel (2016)
     *
     * @param fcMediaUltimos20Min FC média dos minutos 10-30
     * @return FC no limiar (SEM ajustes)
     */
    public Integer calcularFcLimiarTeste30Min(Integer fcMediaUltimos20Min) {
        if (fcMediaUltimos20Min == null || fcMediaUltimos20Min < 100) {
            throw new IllegalArgumentException("FC inválida: " + fcMediaUltimos20Min);
        }

        log.debug("FC Limiar = {} (direto do teste 30min)", fcMediaUltimos20Min);

        // Retorna direto, sem ajustes!
        return fcMediaUltimos20Min;
    }

    /**
     * Calcula FC no limiar baseado em teste de 5K
     *
     * Fundamento: 5K é corrido acima do limiar. Quanto mais rápido o atleta,
     * maior a % acima do limiar que consegue sustentar.
     *
     * @param fcMedia5K FC média durante o teste de 5K
     * @param tempo5KSegundos Tempo total do teste em segundos
     * @return FC no limiar anaeróbico
     */
    public Integer calcularFcLimiarTeste5K(Integer fcMedia5K, Integer tempo5KSegundos) {
        if (fcMedia5K == null || fcMedia5K < 100) {
            throw new IllegalArgumentException("FC inválida: " + fcMedia5K);
        }

        if (tempo5KSegundos == null || tempo5KSegundos < 600) {
            throw new IllegalArgumentException("Tempo inválido: " + tempo5KSegundos);
        }

        // Determinar fator baseado no tempo de prova
        double fator;

        if (tempo5KSegundos < 1020) { // < 17min (elite)
            fator = 0.91;
        } else if (tempo5KSegundos < 1140) { // 17-19min (avançado+)
            fator = 0.93;
        } else if (tempo5KSegundos < 1320) { // 19-22min (avançado)
            fator = 0.94;
        } else if (tempo5KSegundos < 1500) { // 22-25min (intermediário)
            fator = 0.96;
        } else if (tempo5KSegundos < 1680) { // 25-28min (iniciante+)
            fator = 0.98;
        } else { // > 28min (iniciante)
            fator = 1.00;
        }

        double fcLimiar = fcMedia5K * fator;

        log.debug("FC Limiar: {} (FC 5K: {}, tempo: {}s, fator: {})",
                  Math.round(fcLimiar), fcMedia5K, tempo5KSegundos, fator);

        return (int) Math.round(fcLimiar);
    }

    /**
     * Calcula Pace no limiar baseado em teste de 20 minutos
     *
     * @param distanciaMetros Distância percorrida em 20 minutos
     * @return Pace limiar em segundos por km
     */
    public Integer calcularPaceLimiarTeste20Min(Integer distanciaMetros) {
        if (distanciaMetros == null || distanciaMetros < 3000) {
            throw new IllegalArgumentException("Distância inválida: " + distanciaMetros);
        }

        // Pace médio do teste (segundos por km)
        double paceTeste = (1200.0 / distanciaMetros) * 1000.0;

        // Limiar é ~3% mais lento que pace de 20min
        double paceLimiar = paceTeste + 6; // +6 segundos/km

        log.debug("Pace Limiar: {}s/km (pace teste: {}s/km, distância: {}m)",
                  Math.round(paceLimiar), Math.round(paceTeste), distanciaMetros);

        return (int) Math.round(paceLimiar);
    }

    /**
     * Calcula Pace no limiar baseado em teste de 30 minutos
     *
     * @param distanciaUltimos20Min Distância percorrida nos minutos 10-30
     * @return Pace limiar em segundos/km (medida direta!)
     */
    public Integer calcularPaceLimiarTeste30Min(Integer distanciaUltimos20Min) {
        if (distanciaUltimos20Min == null || distanciaUltimos20Min < 3000) {
            throw new IllegalArgumentException("Distância inválida: " + distanciaUltimos20Min);
        }

        // Pace = (tempo / distância) × 1000
        double paceLimiar = (1200.0 / distanciaUltimos20Min) * 1000.0;

        log.debug("Pace Limiar = {}s/km (direto do teste 30min)", Math.round(paceLimiar));

        // Retorna direto, sem ajustes!
        return (int) Math.round(paceLimiar);
    }

    /**
     * Calcula Pace no limiar baseado em teste de 5K
     *
     * @param tempo5KSegundos Tempo total do 5K
     * @return Pace limiar em segundos/km
     */
    public Integer calcularPaceLimiarTeste5K(Integer tempo5KSegundos) {
        if (tempo5KSegundos == null || tempo5KSegundos < 600) {
            throw new IllegalArgumentException("Tempo inválido: " + tempo5KSegundos);
        }

        // Pace médio do 5K (segundos por km)
        double pace5K = tempo5KSegundos / 5.0;

        // Ajuste baseado no tempo de prova
        int ajuste;

        if (tempo5KSegundos < 1080) { // < 18min (elite)
            ajuste = 10;
        } else if (tempo5KSegundos < 1260) { // 18-21min (avançado)
            ajuste = 12;
        } else if (tempo5KSegundos < 1440) { // 21-24min (intermediário)
            ajuste = 15;
        } else if (tempo5KSegundos < 1680) { // 24-28min (iniciante+)
            ajuste = 18;
        } else { // > 28min (iniciante)
            ajuste = 20;
        }

        double paceLimiar = pace5K + ajuste;

        log.debug("Pace Limiar: {}s/km (pace 5K: {}s/km, ajuste: +{}s)",
                  Math.round(paceLimiar), Math.round(pace5K), ajuste);

        return (int) Math.round(paceLimiar);
    }

    /**
     * Calcula VO2max estimado baseado em teste de Cooper
     *
     * @param distanciaMetros Distância em 12 minutos
     * @return VO2max em ml/kg/min
     */
    public BigDecimal calcularVO2maxCooper(Integer distanciaMetros) {
        if (distanciaMetros == null || distanciaMetros < 1000) {
            throw new IllegalArgumentException("Distância inválida: " + distanciaMetros);
        }

        // Fórmula Cooper original (1968)
        double vo2max = (distanciaMetros - 504.9) / 44.73;

        return BigDecimal.valueOf(vo2max).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula VO2max estimado baseado em teste de 5K
     * Fórmula de Léger-Mercier (1984)
     *
     * @param tempo5KSegundos Tempo do 5K em segundos
     * @return VO2max em ml/kg/min
     */
    public BigDecimal calcularVO2maxTeste5K(Integer tempo5KSegundos) {
        // Velocidade média em m/min
        double velocidadeMetrosPorMin = 5000.0 / (tempo5KSegundos / 60.0);

        // Fórmula Léger-Mercier
        double vo2max = -4.6
                      + (0.182258 * velocidadeMetrosPorMin)
                      + (0.000104 * velocidadeMetrosPorMin * velocidadeMetrosPorMin);

        return BigDecimal.valueOf(vo2max).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula zonas de FC baseadas no limiar e FC máxima
     * Modelo: Joe Friel / TrainingPeaks (5 zonas)
     *
     * @param fcLimiar FC no limiar anaeróbico (LTHR)
     * @param fcMaxima FC máxima do atleta (opcional)
     * @return Mapa com zonas (z1_min, z1_max, z2_min, ...)
     */
    public Map<String, Integer> calcularZonasFC(Integer fcLimiar, Integer fcMaxima) {
        Map<String, Integer> zonas = new HashMap<>();

        // Zona 1: < 81% do limiar
        zonas.put("z1_min", 0);
        zonas.put("z1_max", (int) Math.round(fcLimiar * 0.81));

        // Zona 2: 81-89% do limiar
        zonas.put("z2_min", (int) Math.round(fcLimiar * 0.81));
        zonas.put("z2_max", (int) Math.round(fcLimiar * 0.89));

        // Zona 3: 90-93% do limiar
        zonas.put("z3_min", (int) Math.round(fcLimiar * 0.90));
        zonas.put("z3_max", (int) Math.round(fcLimiar * 0.93));

        // Zona 4: 94-99% do limiar
        zonas.put("z4_min", (int) Math.round(fcLimiar * 0.94));
        zonas.put("z4_max", (int) Math.round(fcLimiar * 0.99));

        // Zona 5: 100-102% do limiar (ou até FC máxima)
        int z5Max = fcMaxima != null ? fcMaxima : (int) Math.round(fcLimiar * 1.05);
        zonas.put("z5_min", fcLimiar);
        zonas.put("z5_max", Math.min((int) Math.round(fcLimiar * 1.02), z5Max));

        return zonas;
    }

    /**
     * Calcula zonas de Pace baseadas no pace limiar
     * Modelo: Jack Daniels / Joe Friel (6 zonas)
     *
     * ⚠️ IMPORTANTE: Pace é tempo/distância
     * - Valores MENORES = mais RÁPIDO
     * - Valores MAIORES = mais LENTO
     *
     * Portanto trabalhamos com % de VELOCIDADE e invertemos para pace.
     *
     * @param paceLimiarSegKm Pace no limiar em segundos/km
     * @return Mapa com zonas (z1_min, z1_max, ...)
     */
    public Map<String, Integer> calcularZonasPace(Integer paceLimiarSegKm) {
        // Converter pace para velocidade (km/h)
        double velocidadeLimiar = 3600.0 / paceLimiarSegKm;

        Map<String, Integer> zonas = new HashMap<>();

        // Z1: 70-85% da velocidade limiar (LENTO)
        zonas.putAll(calcularZonaPorVelocidade("z1", velocidadeLimiar, 0.70, 0.85));

        // Z2: 85-95% da velocidade limiar
        zonas.putAll(calcularZonaPorVelocidade("z2", velocidadeLimiar, 0.85, 0.95));

        // Z3: 95-100% da velocidade limiar (Marathon Pace)
        zonas.putAll(calcularZonaPorVelocidade("z3", velocidadeLimiar, 0.95, 1.00));

        // Z4: 100-105% da velocidade limiar (Threshold)
        zonas.putAll(calcularZonaPorVelocidade("z4", velocidadeLimiar, 1.00, 1.05));

        // Z5: 105-115% da velocidade limiar (VO2max / 5K pace)
        zonas.putAll(calcularZonaPorVelocidade("z5", velocidadeLimiar, 1.05, 1.15));

        // Z6: > 115% da velocidade limiar (Velocidade / 800m-1500m)
        zonas.putAll(calcularZonaPorVelocidade("z6", velocidadeLimiar, 1.15, 1.30));

        return zonas;
    }

    /**
     * Calcula limites de pace para uma zona baseado em % velocidade
     *
     * @param nomeZona Nome da zona (ex: "z1")
     * @param velocidadeLimiar Velocidade limiar em km/h
     * @param fatorMin % mínima da velocidade limiar
     * @param fatorMax % máxima da velocidade limiar
     * @return Map com "z1_min" e "z1_max" (em segundos/km)
     */
    private Map<String, Integer> calcularZonaPorVelocidade(String nomeZona,
                                                             double velocidadeLimiar,
                                                             double fatorMin,
                                                             double fatorMax) {
        // Velocidades da zona (km/h)
        double velMin = velocidadeLimiar * fatorMin;
        double velMax = velocidadeLimiar * fatorMax;

        // Converter para pace (segundos/km)
        // ATENÇÃO: Velocidade menor → pace maior (inverso!)
        int paceMax = (int) Math.round(3600.0 / velMin); // vel menor → pace maior
        int paceMin = (int) Math.round(3600.0 / velMax); // vel maior → pace menor

        Map<String, Integer> zona = new HashMap<>();
        zona.put(nomeZona + "_min", paceMin); // Pace mais rápido (valor menor)
        zona.put(nomeZona + "_max", paceMax); // Pace mais lento (valor maior)

        return zona;
    }

    /**
     * Valida se resultados calculados estão dentro de ranges fisiológicos
     *
     * @param fcLimiar FC limiar calculada
     * @param fcMaxima FC máxima do atleta
     * @param paceLimiar Pace limiar calculado
     * @throws IllegalStateException se resultados fora do esperado
     */
    public void validarResultados(Integer fcLimiar, Integer fcMaxima, Integer paceLimiar) {
        // Validar FC limiar vs FC máx
        if (fcMaxima != null) {
            double percLimiar = (fcLimiar * 100.0) / fcMaxima;

            if (percLimiar < 75 || percLimiar > 95) {
                log.warn("⚠️ FC limiar é {}}% da FC máx (esperado: 85-92%)",
                         String.format("%.1f", percLimiar));
            }
        }

        // Validar pace limiar (range razoável: 3:00-7:00/km)
        if (paceLimiar < 180 || paceLimiar > 420) {
            log.warn("⚠️ Pace limiar fora do range comum: {}s/km (esperado: 180-420s)",
                     paceLimiar);
        }
    }
}
```

---

## Endpoints da API

### 1. Registrar Teste de 20 Minutos

```http
POST /api/testes/20-minutos
Content-Type: application/json

{
  "atletaId": "uuid-do-atleta",
  "dataRealizacao": "2025-01-15",
  "distanciaMetros": 5100,
  "fcMedia": 175,
  "fcMaxima": 183,
  "percepcaoEsforco": 9,
  "sensacaoTeste": "BOM",
  "temperatura": 22.5,
  "altitude": 850,
  "tipoTerreno": "PISTA",
  "condicoesVento": "Calmo",
  "observacoes": "Ritmo controlado, segunda metade mais forte"
}
```

**Response 201 Created:**
```json
{
  "id": "teste-uuid",
  "atletaId": "atleta-uuid",
  "tipoProtocolo": "TESTE_20MIN",
  "dataRealizacao": "2025-01-15",
  "dadosTeste": {
    "distanciaMetros": 5100,
    "paceMedia": "3:55/km",
    "velocidadeMedia": "15.3 km/h"
  },
  "resultados": {
    "fcLimiarCalculada": 172,
    "paceLimiarSegundosKm": 241,
    "paceLimiarFormatado": "4:01/km",
    "velocidadeLimiarKmH": 14.9,
    "vo2maxEstimado": 55.2,
    "zonasFC": {
      "z1": { "min": 0, "max": 139, "descricao": "Recuperação" },
      "z2": { "min": 139, "max": 153, "descricao": "Base Aeróbica" },
      "z3": { "min": 155, "max": 160, "descricao": "Tempo" },
      "z4": { "min": 162, "max": 170, "descricao": "Limiar" },
      "z5": { "min": 172, "max": 175, "descricao": "VO2max" }
    },
    "zonasPace": {
      "z1": { "min": "4:51/km", "max": "5:43/km" },
      "z2": { "min": "4:14/km", "max": "4:51/km" },
      "z3": { "min": "4:01/km", "max": "4:14/km" },
      "z4": { "min": "3:50/km", "max": "4:01/km" },
      "z5": { "min": "3:30/km", "max": "3:50/km" },
      "z6": { "min": "0:00/km", "max": "3:30/km" }
    }
  },
  "atletaAtualizado": true,
  "zonasAtualizadas": true
}
```

### 2. Registrar Teste de 30 Minutos

```http
POST /api/testes/30-minutos
Content-Type: application/json

{
  "atletaId": "uuid-do-atleta",
  "dataRealizacao": "2025-01-20",
  "distanciaTotal": 7800,
  "distanciaUltimos20Min": 5200,
  "fcMediaTotal": 172,
  "fcMediaUltimos20Min": 174,
  "fcMaxima": 180,
  "percepcaoEsforco": 9,
  "sensacaoTeste": "OTIMO",
  "temperatura": 18.0,
  "tipoTerreno": "ASFALTO"
}
```

**Response 201 Created:**
```json
{
  "id": "teste-uuid",
  "tipoProtocolo": "TESTE_30MIN",
  "resultados": {
    "fcLimiarCalculada": 174,
    "paceLimiarFormatado": "3:51/km",
    "velocidadeLimiarKmH": 15.6,
    "vo2maxEstimado": 57.1,
    "zonasFC": {
      "z1": { "min": 0, "max": 141 },
      "z2": { "min": 141, "max": 155 },
      "z3": { "min": 157, "max": 162 },
      "z4": "{ "min": 164, "max": 172 },
      "z5": { "min": 174, "max": 177 }
    }
  },
  "observacao": "Valores diretos do teste (sem ajustes) - maior precisão"
}
```

### 3. Registrar Teste de 5K

```http
POST /api/testes/5k
Content-Type: application/json

{
  "atletaId": "uuid-do-atleta",
  "dataRealizacao": "2025-01-25",
  "tempoSegundos": 1260,
  "fcMedia": 178,
  "fcMaxima": 186,
  "splitsPorKm": [250, 252, 251, 253, 254],
  "percepcaoEsforco": 10,
  "sensacaoTeste": "BOM",
  "temperatura": 20.0,
  "tipoTerreno": "PISTA"
}
```

**Response 201 Created:**
```json
{
  "id": "teste-uuid",
  "tipoProtocolo": "TESTE_5K",
  "performance": {
    "tempoTotal": "21:00",
    "paceMedia": "4:12/km",
    "velocidadeMedia": "14.3 km/h",
    "vdot": 47
  },
  "analise": {
    "consistenciaSplits": "Excelente",
    "desvioPadrao": 1.4,
    "pacingEstrategia": "EVEN_PACE"
  },
  "resultados": {
    "fcLimiarCalculada": 167,
    "paceLimiarFormatado": "4:27/km",
    "vo2maxEstimado": 52.8
  },
  "predicoes": {
    "tempo10K": "43:40",
    "tempoMeiaMaratona": "1:37:00",
    "tempoMaratona": "3:23:00"
  }
}
```

### 4. Obter Histórico de Testes

```http
GET /api/testes/atleta/{atletaId}?tipo=TESTE_5K&limit=10&apenasValidos=true
```

### 5. Obter Zonas Atuais

```http
GET /api/testes/atleta/{atletaId}/zonas-atuais
```

### 6. Verificar Necessidade de Reteste

```http
GET /api/testes/atleta/{atletaId}/necessita-reteste
```

### 7. Recomendar Protocolo

```http
GET /api/testes/atleta/{atletaId}/recomendar-protocolo
```

**Response 200 OK:**
```json
{
  "atletaId": "atleta-uuid",
  "nivelExperiencia": "INTERMEDIARIO",
  "volumeSemanalMedio": 45.2,
  "ultimoTeste": {
    "tipo": "TESTE_5K",
    "dataRealizacao": "2024-11-15",
    "diasDesde": 71
  },
  "recomendacao": {
    "protocoloPrincipal": "TESTE_20MIN",
    "motivacao": "Ideal para seu nível e permite reavaliaç��o frequente",
    "protocolosAlternativos": [
      {
        "tipo": "TESTE_30MIN",
        "quando": "Para maior precisão no início de mesociclo"
      },
      {
        "tipo": "TESTE_5K",
        "quando": "Se tiver prova de 5K agendada"
      }
    ]
  }
}
```

---

## Integração com Sistema Existente

### 1. Atualização Automática do Atleta

```java
@Transactional
public void atualizarAtletaAposTeste(UUID atletaId, TesteAvaliacao teste) {
    Atleta atleta = atletaRepository.findById(atletaId)
            .orElseThrow(() -> new IllegalArgumentException("Atleta não encontrado"));

    // Atualizar parâmetros fisiológicos
    atleta.setFcLimiar(teste.getFcLimiarCalculada());
    atleta.setPaceLimiar(BigDecimal.valueOf(teste.getPaceLimiarSegundosKm()));
    atleta.setVelocidadeLimiar(teste.getVelocidadeLimiarKmH());
    atleta.setVo2maxEstimado(teste.getVo2maxEstimado());

    // Atualizar datas de último teste
    atleta.setDataUltimoTesteFc(teste.getDataRealizacao());
    atleta.setDataUltimoTestePace(teste.getDataRealizacao());

    // Atualizar FC máxima se teste forneceu valor confiável
    if (teste.getFcMaxima() != null &&
        teste.getFcMaxima() > atleta.getFcMaximaCalculada()) {
        atleta.setFcMaxima(teste.getFcMaxima());
        log.info("FC máxima atualizada: {} → {}",
                 atleta.getFcMaximaCalculada(), teste.getFcMaxima());
    }

    atletaRepository.save(atleta);

    log.info("Atleta {} atualizado: LTHR={}, FTP={}, VO2max={}",
             atletaId,
             teste.getFcLimiarCalculada(),
             formatarPace(teste.getPaceLimiarSegundosKm()),
             teste.getVo2maxEstimado());
}
```

### 2. Uso das Zonas na Geração de Planos

```java
public String buildEnhancedPrompt(Atleta atleta, PlanoMetaDados metaDados,
                                   Prova prova, LocalDate inicioSemana) {
    StringBuilder prompt = new StringBuilder();

    // ... código existente

    // Incluir zonas de treinamento se disponíveis
    ZonasTreinamento zonas = zonasRepository
            .findByAtletaIdAndAtivoTrue(atleta.getId())
            .orElse(null);

    if (zonas != null) {
        prompt.append("\n## 🎯 Zonas de Treinamento (baseadas em teste recente)\n\n");

        prompt.append("### Zonas de Frequência Cardíaca:\n");
        prompt.append("- **Z1 (Recuperação)**: ").append(zonas.formatarZonaFC(1)).append("\n");
        prompt.append("- **Z2 (Base Aeróbica)**: ").append(zonas.formatarZonaFC(2)).append("\n");
        prompt.append("- **Z3 (Tempo)**: ").append(zonas.formatarZonaFC(3)).append("\n");
        prompt.append("- **Z4 (Limiar)**: ").append(zonas.formatarZonaFC(4)).append("\n");
        prompt.append("- **Z5 (VO2max)**: ").append(zonas.formatarZonaFC(5)).append("\n\n");

        prompt.append("### Zonas de Pace:\n");
        prompt.append("- **Z1 (Recuperação)**: ").append(zonas.formatarZonaPace(1)).append("\n");
        prompt.append("- **Z2 (Base)**: ").append(zonas.formatarZonaPace(2)).append("\n");
        prompt.append("- **Z3 (Marathon)**: ").append(zonas.formatarZonaPace(3)).append("\n");
        prompt.append("- **Z4 (Threshold)**: ").append(zonas.formatarZonaPace(4)).append("\n");
        prompt.append("- **Z5 (VO2max)**: ").append(zonas.formatarZonaPace(5)).append("\n");
        prompt.append("- **Z6 (Velocidade)**: ").append(zonas.formatarZonaPace(6)).append("\n\n");

        prompt.append("**IMPORTANTE**: Use essas zonas para prescrever:\n");
        prompt.append("- FC alvo em cada etapa (ex: 'fcAlvoEtapa': '150-160% FCmáx' → '").append(zonas.formatarZonaFC(2)).append("')\n");
        prompt.append("- Ritmo alvo (ex: 'ritmoAlvo': 'Z2' → '").append(zonas.formatarZonaPace(2)).append("')\n\n");
    } else {
        prompt.append("\n⚠️ **Atleta sem zonas calculadas**. Recomendar fazer teste de avaliação.\n\n");
    }

    return prompt.toString();
}
```

### 3. Validação de Treinos Realizados

```java
public void analisarAderenciaZonas(TreinoRealizado treino) {
    ZonasTreinamento zonas = zonasRepository
            .findByAtletaIdAndAtivoTrue(treino.getAtleta().getId())
            .orElse(null);

    if (zonas == null) {
        log.warn("Atleta {} sem zonas calculadas", treino.getAtleta().getId());
        return;
    }

    Integer fcMedia = treino.getFcMedia();
    if (fcMedia == null) return;

    // Determinar zona real do treino
    int zonaRealizada = identificarZonaFC(fcMedia, zonas);

    // Comparar com zona planejada
    TreinoPlanejado planejado = treino.getTreinoPlanejado();
    if (planejado != null && planejado.getIntensidadePlanejada() != null) {
        int zonaEsperada = mapearIntensidadeParaZona(planejado.getIntensidadePlanejada());

        int desvio = Math.abs(zonaRealizada - zonaEsperada);

        if (desvio > 0) {
            log.info("Desvio de zona: esperado Z{}, realizado Z{} (treino: {})",
                     zonaEsperada, zonaRealizada, treino.getId());

            // Registrar métrica para análise futura
            metricasService.registrar("desvio_zona", Map.of(
                    "treinoId", treino.getId(),
                    "zonaEsperada", zonaEsperada,
                    "zonaRealizada", zonaRealizada,
                    "desvio", desvio
            ));
        }
    }
}

private int identificarZonaFC(int fcMedia, ZonasTreinamento zonas) {
    if (fcMedia >= zonas.getZ5FcMin()) return 5;
    if (fcMedia >= zonas.getZ4FcMin()) return 4;
    if (fcMedia >= zonas.getZ3FcMin()) return 3;
    if (fcMedia >= zonas.getZ2FcMin()) return 2;
    return 1;
}
```

---

## Roadmap de Implementação

### Fase 1: Fundação (2-3 semanas)

#### Sprint 1.1 - Modelo de Dados
- [ ] Criar entidade `TesteAvaliacao`
- [ ] Criar entidade `ZonasTreinamento`
- [ ] Criar enums (`TipoProtocoloTeste`, `SensacaoTeste`, `PacingEstrategia`, `TipoTerreno`)
- [ ] Criar migração Flyway V15
- [ ] Testes de persistência

#### Sprint 1.2 - Algoritmos de Cálculo
- [ ] Implementar `CalculadoraZonasService`
- [ ] Testes unitários: cálculos de FC limiar (20min, 30min, 5K)
- [ ] Testes unitários: cálculos de Pace limiar
- [ ] Testes unitários: cálculos de VO2max
- [ ] Testes unitários: cálculos de zonas FC
- [ ] Testes unitários: cálculos de zonas Pace (verificar inversão!)

#### Sprint 1.3 - Serviço Principal
- [ ] Implementar `TesteAvaliacaoService`
- [ ] Registrar testes (20min, 30min, 5K, Cooper)
- [ ] Atualizar atleta após teste
- [ ] Criar zonas de treinamento
- [ ] Validar resultados
- [ ] Testes de integração

### Fase 2: API e Interface (2 semanas)

#### Sprint 2.1 - Endpoints REST
- [ ] Controller `TesteAvaliacaoController`
- [ ] DTOs de input/output
- [ ] Validações (Bean Validation)
- [ ] Documentação OpenAPI
- [ ] Testes de API

#### Sprint 2.2 - Consultas e Histórico
- [ ] GET histórico de testes
- [ ] GET zonas atuais
- [ ] GET necessidade reteste
- [ ] GET recomendar protocolo
- [ ] PUT invalidar teste
- [ ] Queries otimizadas

### Fase 3: Integração (2 semanas)

#### Sprint 3.1 - Integração com Planos
- [ ] Modificar `PlanoTreinoPromptBuilder`
- [ ] Ajustar DTOs do LLM
- [ ] Testes com geração usando zonas

#### Sprint 3.2 - Análise de Treinos
- [ ] Serviço de análise de aderência
- [ ] Dashboard de conformidade
- [ ] Métricas de qualidade

### Fase 4: Features Avançadas (2-3 semanas)

#### Sprint 4.1 - Alertas
- [ ] Job scheduled para retestes
- [ ] Sistema de notificações
- [ ] Templates de mensagens

#### Sprint 4.2 - Evolução
- [ ] Gráficos de evolução
- [ ] Predições de performance
- [ ] Comparação com atletas similares

---

**Documento revisado em**: 28 de Outubro de 2025
**Versão**: 2.0 (Corrigida)
**Autor**: Sistema Menthoros - Equipe de Produto

---

## Principais Correções

### ❌ Removido
- ~~Teste de 3 minutos para limiar~~ (serve apenas para VO2max)

### ✅ Adicionado
- **Teste de 20 minutos** (protocolo intermediário ideal)
- **Teste de 30 minutos** (gold standard de campo)
- **Teste de Cooper** (para iniciantes e VO2max)
- Protocolos recomendados por nível de atleta

### ✅ Corrigido
- **Zonas de Pace**: Agora calcula corretamente usando velocidade (valores menores = mais rápido)
- **Fórmulas de FC limiar**: Ajustadas por tempo de prova e nível
- **Algoritmos de cálculo**: Todos revisados com fundamentação científica
- **Validações**: Ranges fisiológicos corretos