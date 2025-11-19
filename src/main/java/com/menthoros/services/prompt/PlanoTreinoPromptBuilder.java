package com.menthoros.services.prompt;

import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.ProvaOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.Prova;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.DiaSemana;
import com.menthoros.repository.ProvaRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PlanoTreinoPromptBuilder {

    private final String promptTemplate;
    private final ProvaRepository provaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;

    public PlanoTreinoPromptBuilder(@Value("classpath:prompts/plano-treino-prompt.txt") Resource promptResource, ProvaRepository provaRepository, TreinoRealizadoRepository treinoRealizadoRepository) {
        this.provaRepository = provaRepository;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        try {
            this.promptTemplate = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível carregar o template do prompt.", e);
        }
    }

    public String buildRequest(AtletaOutputDto atleta, List<TreinoRealizadoOutputDto> treinosRecentes, PlanoSemanalOutputDto planoSemanalOutputDto) {
        return String.format(promptTemplate,
                atleta.nome(),
                atleta.idade(),
                atleta.objetivo(),
                atleta.nivelExperiencia(),
                atleta.diasDisponiveis(),
                atleta.diaPreferidoLongo(),
                formatarProvas(atleta.provas()),
                formatarTreinos(treinosRecentes),
                formatarHistorico(planoSemanalOutputDto)
        );
    }

    private String formatarHistorico(PlanoSemanalOutputDto planoSemanalOutputDto) {
        if (planoSemanalOutputDto == null) {
            return "### Volume da semana anterior:\n- Nenhum plano concluído encontrado.\n";
        }

        Double volumeAlvo = planoSemanalOutputDto.volumeAlvoKm();
        Double volumePlanejado = planoSemanalOutputDto.volumePlanejadoKm();
        Double volumeRealizado = planoSemanalOutputDto.volumeRealizadoKm();

        return String.format("""
                        ### Volume da semana anterior:
                        - volume alvo: %.1f km
                        - volume planejado: %.1f km
                        - volume realizado: %.1f km
                        """,
                volumeAlvo != null ? volumeAlvo : 0.0,
                volumePlanejado != null ? volumePlanejado : 0.0,
                volumeRealizado != null ? volumeRealizado : 0.0
        );
    }


    private String formatarProvas(List<ProvaOutputDto> provas) {

        if (provas == null || provas.isEmpty()) {
            return "Nenhuma prova cadastrada";
        }

        return provas.stream()
                .map(p -> String.format("- %s em %s (%.1f km)",
                        p.nomeProva(),
                        p.dataProva().toString(),
                        p.distanciaKm().doubleValue())).collect(Collectors.joining("\n"));

    }

    private String formatarTreinos(List<TreinoRealizadoOutputDto> treinos) {
        if (treinos == null || treinos.isEmpty()) {
            return "Nenhum treino recente encontrado.";
        }

        return treinos.stream()
                .map(t -> String.format("- %s\n Tipo: %s\n Distância: %.1f km | Duração: %d min | Ritmo alvo: %s | FC alvo: %s\n Observações: %s\n",
                        t.dataTreino().toString(),
                        t.tipoTreino(),
                        t.distanciaKm(),
                        t.duracaoMin(),
                        t.paceMedia() != null ? t.paceMedia() : "N/A",
                        t.fcMedia() != null ? t.fcMedia() : "N/A",
                        t.observacao() != null ? t.observacao() : "Sem observações")
                )
                .collect(Collectors.joining("\n"));
    }

    // Método atualizado para construir prompt
    public String buildEnhancedPrompt(Atleta atleta, PlanoMetaDados metaDados, Prova provaAlvo, LocalDate inicioSemana) {

        var historicoTreinos = formatarHistoricoTreinos(atleta);
        var metricas = formatarMetricas(metaDados);
        var dadosFisiologicos = formatarDadosFisiologicos(atleta);
        var diasFormatados = formatarDias(atleta, metaDados, inicioSemana); // ADICIONAR
        var provas = formatarProvas(atleta, provaAlvo);
        var alertas = gerarAlertas(metaDados);

        // ADICIONAR BLOCO COMPLETO DE FALLBACKS:
        Double rampRate = metaDados.getRampRateAtual() != null ? metaDados.getRampRateAtual() : 0.0;
        Integer tssMedio = metaDados.getTssSemanalMedio() != null ? metaDados.getTssSemanalMedio() : 0;
        Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino() != null ? metaDados.getDiasConsecutivosTreino() : 0;
        int tssAlvo = calcularTssAlvo(metaDados);
        int maxDiasConsecutivos = calcularMaxDiasConsecutivos(metaDados, atleta);
        String periodizacao = formatarPeriodizacaoProva(provaAlvo);
        String diaPreferidoLongo = atleta.getDiaPreferidoLongo() != null ?
                atleta.getDiaPreferidoLongo().toString() : "SABADO";
        Integer fcLimiar = atleta.getFcLimiarCalculada() != null ? atleta.getFcLimiarCalculada() : 150;
        String paceLimiar = atleta.getPaceLimiar() != null ?
                atleta.getPaceLimiar().toString() : "5:30";

        return String.format("""
                        Você é um treinador de corrida com 20+ anos de experiência, especialista em desempenho, prevenção de lesões e periodização moderna (polarizada, piramidal e híbrida).
                       Seu papel é criar e ajustar planos semanais personalizados de corrida, levando em conta:

                       Princípios fundamentais:
                        - Sobrecarga progressiva
                        - Variabilidade de estímulos
                        - Recuperação ativa
                        - Economia de corrida
                        - Prevenção de lesões
                        - Integração cardiorrespiratória e neuromuscular

                       Dados do atleta disponíveis, como:
                        - Ritmo recente, cadência, VO₂ estimado
                        - FC (zonas Z1–Z5), potência, GAP
                        - TSS, CTL, ATL, TSB
                        - Volume recente, elevação acumulada
                        - Fadiga percebida (RPE)
                        - Histórico de lesões
                        - Estado atual (gripe, dor, recuperação de lesão, etc.)

                       Objetivos do atleta:
                        - Prova alvo, distância, altimetria
                        - Ritmos desejados
                        - Manutenção, performance ou retorno gradual
                        - Restrições de dias e horários

                        ### INSTRUÇÕES CRÍTICAS
                        - Responda somente com 1 objeto JSON válido.
                        - Não inclua texto antes ou depois.
                        - Não use reticências, campos vazios ("") ou null.
                        - Não inclua campos extras além dos listados.
                        - Use hífen - para intervalos (ex.: 5:00-5:30/km).
                        - Máximo 5 treinos, mínimo 3. Se necessário, reduza para garantir JSON completo e válido.
                        
                        ### ENUMS (STRING, UPPERCASE — APENAS ESTES VALORES)
                        - status: PLANEJADO, INICIADO, EM_ANDAMENTO, ATIVO, CONCLUIDO
                        - diaSemana: SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO, DOMINGO
                        - tipoTreino: REGENERATIVO, FACIL, INTERVALADO, CONTINUO, LONGO, TIRO, FARTLEK, TEMPO_RUN, SUBIDA, PROVA
                        - tipoEtapa: AQUECIMENTO, PRINCIPAL, INTERVALADO, RECUPERACAO, DESAQUECIMENTO
                        - statusTreino: PENDENTE, REALIZADO, CANCELADO (se usado)
                        
                        ### ⛔ REGRA INQUEBRÁVEL - TREINOS INTERVALADOS (VALIDAÇÃO AUTOMÁTICA)
                        
                         IMPORTANTE: Para tipoTreino INTERVALADO ou TIRO, você DEVE expandir CADA tiro individualmente.
                        
                         PROIBIDO (Backend rejeitará):
                         - Usar repeticoes maior que 1
                         - Agrupar intervalos tipo 5x400m ou 4 repeticoes
                         - Descrições no plural
                         - Limitar arbitrariamente o número de tiros (calcule baseado na distância total)
                        
                         OBRIGATÓRIO:
                         - repeticoes sempre igual a 1
                         - Cada tiro = 1 etapa separada
                         - Cada recuperação = 1 etapa separada
                         - descricaoEtapa DEVE incluir a zona de treino (ex.: "Tiro 1 em Z5", "Recuperação 1 em Z2")
                         - Usar TODA a distância planejada do treino, respeitando as regras de aquecimento e desaquecimento abaixo
                        
                         CÁLCULO DO NÚMERO DE TIROS:
                         1. Pegue a distância total do treino (ex: 10km)
                         2. Reserve aquecimento (1.0-2.0 km) e desaquecimento (0.8-1.5 km, máx. 20%%%% da distância total)
                         3. Distância restante = distância disponível para intervalos (tiros + recuperações)
                         4. Cada tiro = 0.4-1.2 km, cada recuperação = 0.2-0.4 km
                         5. Calcule: quantos tiros cabem? Use os km disponíveis ajustando o número e a distância dos tiros, NÃO o desaquecimento
                        
                         REGRAS DE AQUECIMENTO E DESAQUECIMENTO EM INTERVALADOS/TIROS:
                         - Aquecimento:
                           • 10–15 minutos em Z1-Z2
                           • Distância total entre 1.0 e 2.0 km
                           • Nunca mais que 25%%%% da distância total do treino
                        
                         - Desaquecimento:
                           • 8–12 minutos em Z1-Z2
                           • Distância entre 0.8 e 1.5 km
                           • NUNCA ultrapassar 20%%%% da distância total do treino
                           • Exemplo:
                             – Treino de 8 km: desaquecimento entre 0.8 e 1.2 km
                             – Treino de 10 km: desaquecimento entre 1.0 e 1.5 km
                        
                         IMPORTANTE:
                         - NÃO coloque toda a "distância que sobrar" no desaquecimento.
                         - Se sobrar muita distância após definir aquecimento + desaquecimento:
                           • ajuste levemente a distância dos tiros (por exemplo de 0.8 km para 0.85 km)
                           • ou aumente o número de tiros, mantendo cada recuperação entre 0.2-0.4 km.
                         - Desaquecimento serve apenas para baixar gradualmente a intensidade, não é a parte principal do treino.
                        
                        ### CONSISTÊNCIA OBRIGATÓRIA (DISTÂNCIA)
                        
                        - A soma de distanciaKm de TODAS as etapas DEVE ficar muito próxima da distanciaKm do treino.
                        - Aceite uma diferença máxima de 0.1–0.2 km (ajuste as etapas para isso).
                        - Se a soma das etapas estiver menor que a distanciaKm:
                          • aumente levemente a distância dos TIROS ou das RECUPERAÇÕES
                          • NUNCA corrija isso aumentando apenas o desaquecimento
                        - Se a soma das etapas estiver maior que a distanciaKm:
                          • reduza levemente a distância dos tiros ou das recuperações
                        
                        ### CONSISTÊNCIA OBRIGATÓRIA (DURAÇÃO)
                        
                        - A soma de duracaoMin das etapas deve ser coerente com o campo duracaoMin do treino.
                        - Para treinos INTERVALADO/TIRO:
                          • A diferença máxima aceitável é de ±5 minutos.
                        - Se a soma das etapas estiver muito menor:
                          • aumente levemente o tempo de aquecimento, desaquecimento ou recuperação
                        - Se estiver maior:
                          • reduza um pouco aquecimento ou desaquecimento, mantendo os tiros como prioridade
                        
                        PRIORIDADE AO AJUSTAR DISTÂNCIA:
                        
                        1. Mantenha aquecimento e desaquecimento dentro das faixas recomendadas (não mexa muito neles).
                        2. Use os km que sobrarem para:
                           - aumentar levemente a distância dos TIROS (ex.: de 0.8 para 0.85 km)
                           - ou adicionar mais um tiro, se caber dentro da duração e da distância planejada.
                        3. Só use pequenas variações nas RECUPERAÇÕES (0.2–0.4 km).
                        4. NÃO use o desaquecimento como "lixeira" para os km que sobraram.
                        
                        
                        ### PERFIL DO ATLETA
                        - Nome: %s
                        - Idade: %d anos
                        - Objetivo: %s
                        - Nível: %s
                        - Dias disponíveis: %s
                        - Dia preferido para treino longo: %s
                        - Provas: %s
                        
                        ### DADOS FISIOLÓGICOS E MÉTRICAS RECENTES
                        %s
                        
                        ### HISTÓRICO DE TREINOS RECENTES
                        %s
                        
                        ### MÉTRICAS DE CARGA
                        %s
                        
                        ### PADRÕES E DISPONIBILIDADE
                        %s
                        
                        ### PARÂMETROS CALCULADOS PARA ESTA SEMANA
                        
                        **Carga de Trabalho:**
                        - **TSS Alvo Semanal:** %d pontos
                        - **TSS Médio Recente:** %d pontos/semana
                        - **Ramp Rate Atual:** %.1f pts/semana
                        - **Progressão:** %s
                        
                        **Distribuição de Dias:**
                        - **Dias consecutivos atuais:** %d dias
                        - **Máximo recomendado:** %d dias consecutivos
                        - **Status:** %s
                        
                        **Periodização:**
                        %s
                        
                        **ALERTAS IMPORTANTES:**
                        %s
                        
                        ---
                        
                        ### REGRAS DO PLANO
                        - 3–5 treinos na semana, com variação de estímulos.
                        - Distribua nos dias disponíveis; longo preferencialmente no dia preferido.
                        - Se houver 4 ou mais treinos, segunda-feira deve ser regenerativo.
                        - Nunca posicione regenerativo após intervalado ou longo.
                        - Progressão ≤ 10%%%% no volume semanal (salvo subexecução recente).
                        - intensidadePlanejada: 0.5–1.5; percepcaoEsforcoEsperada: 1–10.
                        - **IMPORTANTE:** Respeite o TSS alvo semanal de %d pontos (distribuir entre os treinos).
                        - **IMPORTANTE:** Não exceda %d dias consecutivos de treino sem descanso.
                        
                        - Em QUALQUER treino:
                          • AQUECIMENTO + DESAQUECIMENTO não devem somar mais que 35%%%% da distância total do treino.
                          • A parte PRINCIPAL (PRINCIPAL ou INTERVALADO) deve conter a maior parte da distância e da intensidade.
                        
                        
                        ### APLICAÇÃO DAS ZONAS FISIOLÓGICAS (OBRIGATÓRIO)
                        
                        IMPORTANTE: Os dados fisiológicos acima incluem ZONAS CALCULADAS (Z1-Z5). Você DEVE usar essas zonas:
                        
                        **Zonas de Treino e Aplicação:**
                        - **Z1 (Recuperação)**: Use para REGENERATIVO, FACIL e etapas de RECUPERACAO/DESAQUECIMENTO
                          → fcAlvo e ritmoAlvo devem estar nos intervalos de Z1 fornecidos
                        
                        - **Z2 (Aeróbico)**: Use para treinos LONGO (etapa principal), CONTINUO leve e AQUECIMENTO
                          → fcAlvo e ritmoAlvo devem estar nos intervalos de Z2 fornecidos
                        
                        - **Z3 (Tempo)**: Use para TEMPO_RUN e treinos CONTINUO moderados
                          → fcAlvo e ritmoAlvo devem estar nos intervalos de Z3 fornecidos
                        
                        - **Z4 (Limiar)**: Use para FARTLEK (partes rápidas) e alguns INTERVALADO
                          → fcAlvo e ritmoAlvo devem estar nos intervalos de Z4 fornecidos
                        
                        - **Z5 (VO2max)**: Use para INTERVALADO intenso, TIRO e etapas INTERVALADO em treinos de qualidade
                          → fcAlvo e ritmoAlvo devem estar nos intervalos de Z5 fornecidos
                        
                        **Regras de Consistência:**
                        1. SEMPRE copie os valores de FC e Pace das zonas fornecidas (não invente valores)
                        2. fcAlvoEtapa DEVE mencionar a zona (ex: "85-92%%%% FCmáx (Z4)" ou apenas usar os valores de Z4)
                        3. ritmoAlvo DEVE usar o formato fornecido nas zonas (ex: "5:15-5:45/km" baseado em Z3)
                        4. Em etapas de um mesmo treino intervalado:
                           - AQUECIMENTO/DESAQUECIMENTO: Z1 ou Z2
                           - INTERVALADO (tiros): Z4 ou Z5
                           - RECUPERACAO: Z1 ou Z2
                        5. Justifique na descricaoEtapa qual zona está sendo usada (ex: "Tiro 1 em Z5", "Recuperação em Z2")
                        
                        ### CAMPOS OBRIGATÓRIOS (POR TREINO)
                        - diaSemana (string), tipoTreino (string), fcAlvo (ex.: "70-80%%%% FCmáx"),
                        - tssPlanejado (≥ 0), intensidadePlanejada (0.5–1.5),
                        - percepcaoEsforcoEsperada (1–10), justificativaIa (≤ 200 chars),
                        - duracaoMin (≥ 1), distanciaKm (≥ 0),
                        - ritmoAlvo (padrão m:ss-m:ss/km, ex.: 5:00-5:30/km),
                        - etapas (array, nunca vazio):
                          • INTERVALADO ou TIRO: MÍNIMO 8 etapas, SEM LIMITE MÁXIMO (expandir todos os tiros)
                          • LONGO: exatamente 3 etapas (AQUECIMENTO, PRINCIPAL, DESAQUECIMENTO)
                          • REGENERATIVO, FACIL, CONTINUO, FARTLEK, TEMPO_RUN, SUBIDA, PROVA: 2-4 etapas
                        
                        ### CAMPOS OBRIGATÓRIOS (POR ETAPA)
                        - ordem (1,2,3...), tipoEtapa (string),
                        - descricaoEtapa (≤ 120 chars, sem aspas duplicadas),
                        - duracaoMin (≥ 1), distanciaKm (≥ 0),
                        - fcAlvoEtapa (string), repeticoes (sempre = 1).
                        
                        Fallbacks obrigatórios se faltar valor específico:
                        - fcAlvoEtapa := fcAlvo do treino.
                        - repeticoes := sempre 1 (NUNCA usar > 1, expandir etapas individualmente).
                        - distanciaKm := 0.0.
                        
                        ### REGRAS EXTRAS
                        - O último treino da semana segue as mesmas regras (sem nulos).
                        - No treino LONGO: 3 etapas (AQUECIMENTO, PRINCIPAL, DESAQUECIMENTO).
                        - Não repita texto na justificativaIa (evite redundância).
                        - Não use travessão/en-dash; apenas hífen -.
                        """,
                // Parâmetros do String.format (na ordem correta dos placeholders)
                atleta.getNome(),
                atleta.getIdade(),
                atleta.getObjetivo() != null ? atleta.getObjetivo() : "Melhorar condicionamento",
                atleta.getNivelExperiencia() != null ? atleta.getNivelExperiencia().toString() : "INTERMEDIARIO",
                formatarDias(atleta.getDiasDisponiveis()),
                diaPreferidoLongo,
                provas,
                dadosFisiologicos,
                historicoTreinos,
                metricas,
                diasFormatados,
                // PARÂMETROS CALCULADOS PARA ESTA SEMANA
                tssAlvo,
                tssMedio,
                rampRate,
                interpretarRampRate(rampRate),
                diasConsecutivos,
                maxDiasConsecutivos,
                gerarStatusDiasConsecutivos(diasConsecutivos, maxDiasConsecutivos),
                periodizacao,
                alertas,
                // Repetidos nas REGRAS DO PLANO
                tssAlvo,
                maxDiasConsecutivos

        );
    }

    /**
     * Formata histórico recente de treinos do atleta
     */
    private String formatarHistoricoTreinos(Atleta atleta) {
        LocalDate dataLimite = LocalDate.now().minusDays(16); // Últimos 14 dias

        List<TreinoRealizado> treinosRecentes = treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(
                        atleta,
                        dataLimite
                );

        if (treinosRecentes.isEmpty()) {
            return "Nenhum treino realizado nos últimos 14 dias.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Últimos 14 dias:**\n\n");

        int totalTreinos = treinosRecentes.size();

        BigDecimal volumeTotal = treinosRecentes.stream()
                .map(t -> t.getDistanciaKm() != null ? t.getDistanciaKm() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int tssTotal = treinosRecentes.stream()
                .mapToInt(t -> t.getTssCalculado() != null ? t.getTssCalculado() : 0)
                .sum();

        // Calcular médias
        BigDecimal volumeMedio = volumeTotal.divide(
                BigDecimal.valueOf(totalTreinos),
                2,
                RoundingMode.HALF_UP
        );

        int tssMedio = totalTreinos > 0 ? tssTotal / totalTreinos : 0;

        sb.append(String.format("- **Total de treinos:** %d\n", totalTreinos));
        sb.append(String.format("- **Volume total:** %.1f km\n", volumeTotal.doubleValue()));
        sb.append(String.format("- **TSS total:** %d pontos\n", tssTotal));
        sb.append(String.format("- **Média por treino:** %.1f km | %d TSS\n\n",
                volumeMedio.doubleValue(),
                tssMedio));

        sb.append("**Últimos 5 treinos:**\n");
        treinosRecentes.stream()
                .limit(5)
                .forEach(treino -> {
                    sb.append(String.format("- %s: %s - %.1f km, %s min, TSS %d",
                            treino.getDataTreino(),
                            treino.getTipoTreino(),
                            treino.getDistanciaKm() != null ? treino.getDistanciaKm().doubleValue() : 0.0,
                            treino.getDuracaoMin() != null ? treino.getDuracaoMin() : 0,
                            treino.getTssCalculado() != null ? treino.getTssCalculado() : 0
                    ));

                    if (treino.getPercepcaoEsforco() != null) {
                        sb.append(String.format(" | RPE %d/10", treino.getPercepcaoEsforco()));
                    }
                    sb.append("\n");
                });

        return sb.toString();
    }

    private String formatarMetricas(PlanoMetaDados metaDados) {
        // USAR DADOS JÁ PROCESSADOS DA ENTIDADE
        String statusGeral = metaDados.getStatusGeral() != null
                ? metaDados.getStatusGeral()
                : "COLETANDO DADOS";

        String recomendacao = metaDados.getRecomendacaoTreino() != null
                ? metaDados.getRecomendacaoTreino()
                : "Continuar treinamento normalmente, respeitando os princípios de progressão.";

        return String.format("""
                        ## MÉTRICAS DE CARGA E FADIGA
                        
                        **Status Geral:** %s
                        **Recomendação:** %s
                        
                        ### Métricas Principais
                        - **CTL (Fitness):** %.1f pontos - Forma física acumulada (6 semanas)
                        - **ATL (Fadiga):** %.1f pontos - Fadiga recente (última semana)
                        - **TSB (Prontidão):** %.1f pontos - %s
                        - **Ramp Rate:** %.1f pts/sem - %s
                        
                        ### Médias Semanais
                        - Volume: %.1f km | TSS: %d pts | Treinos: %.1f sessões
                        
                        ### Padrões de Treino
                        - Dias consecutivos: %d | Desde último descanso: %d | Progressão: %d semanas
                        - Dia preferido longo: %s
                        
                        ### Alertas Ativos
                        %s
                        
                        ---
                        """,
                statusGeral,
                recomendacao,
                metaDados.getCtlAtual() != null ? metaDados.getCtlAtual() : 0.0,
                metaDados.getAtlAtual() != null ? metaDados.getAtlAtual() : 0.0,
                metaDados.getTsbAtual() != null ? metaDados.getTsbAtual() : 0.0,
                metaDados.getInterpretacaoTsb(),
                metaDados.getRampRateAtual() != null ? metaDados.getRampRateAtual() : 0.0,
                interpretarRampRate(metaDados.getRampRateAtual()),
                metaDados.getVolumeSemanalMedio() != null ? metaDados.getVolumeSemanalMedio().doubleValue() : 0.0,
                metaDados.getTssSemanalMedio() != null ? metaDados.getTssSemanalMedio() : 0,
                metaDados.getTreinosPorSemanaMedio() != null ? metaDados.getTreinosPorSemanaMedio() : 0.0,
                metaDados.getDiasConsecutivosTreino() != null ? metaDados.getDiasConsecutivosTreino() : 0,
                metaDados.getDiasDesdeUltimoDescanso() != null ? metaDados.getDiasDesdeUltimoDescanso() : 0,
                metaDados.getSemanasProgressaoContinua() != null ? metaDados.getSemanasProgressaoContinua() : 0,
                metaDados.getDiaPreferidoLongo() != null ? metaDados.getDiaPreferidoLongo().toString() : "Não definido",
                gerarAlertas(metaDados)
        );
    }

    /**
     * Interpreta o Ramp Rate com recomendações
     */
    private String interpretarRampRate(Double rampRate) {
        if (rampRate == null) {
            return "Sem dados suficientes";
        }

        if (rampRate > 10) {
            return "MUITO ALTO - Progressão perigosa, risco de lesão!";
        } else if (rampRate > 8) {
            return "ALTO - Progressão rápida, monitorar sinais de fadiga";
        } else if (rampRate >= 5 && rampRate <= 8) {
            return "IDEAL - Progressão segura e efetiva";
        } else if (rampRate >= 3 && rampRate < 5) {
            return "BOM - Progressão moderada e sustentável";
        } else if (rampRate >= 0 && rampRate < 3) {
            return "BAIXO - Manutenção ou progressão muito lenta";
        } else if (rampRate > -3 && rampRate < 0) {
            return "NEGATIVO - Destreino leve, normal em recuperação";
        } else {
            return "MUITO NEGATIVO - Perda significativa de forma";
        }
    }

    private String interpretarTsb(Double tsb) {
        if (tsb == null) {
            return "Sem dados suficientes";
        }

        if (tsb < -35) {
            return "FADIGA CRÍTICA - Risco de overtraining, descanso obrigatório";
        } else if (tsb < -25) {
            return "FADIGA ALTA - Reduzir volume, priorizar recuperação";
        } else if (tsb < -15) {
            return "FADIGA MODERADA - Monitorar, considerar dia mais leve";
        } else if (tsb < -5) {
            return "ACUMULANDO FADIGA - Normal em fase de treino intenso";
        } else if (tsb < 5) {
            return "RECUPERANDO - Corpo assimilando treinos";
        } else if (tsb < 15) {
            return "FORMA IDEAL - Prontidão ótima para performance";
        } else if (tsb < 25) {
            return "BEM RECUPERADO - Ótimo para treinos intensos ou provas";
        } else {
            return "MUITO DESCANSADO - Considerar aumentar volume/intensidade";
        }
    }

    /**
     * Fornece recomendação de ação baseada no TSB
     */
    private String getRecomendacaoTsb(Double tsb) {
        if (tsb == null) return "";

        if (tsb < -35) {
            return "\nAÇÃO: Dia de descanso completo ou atividade regenerativa (30min caminhada leve)";
        } else if (tsb < -25) {
            return "\nAÇÃO: Reduzir volume em 40%, manter apenas treinos regenerativos";
        } else if (tsb < -15) {
            return "\nSUGESTÃO: Reduzir intensidade, aumentar dias de recuperação";
        } else if (tsb >= 5 && tsb <= 15) {
            return "\nOPORTUNIDADE: Janela ideal para treinos intensos ou provas importantes";
        } else if (tsb > 25) {
            return "\nSUGESTÃO: Aumentar volume ou incluir sessão de qualidade";
        }

        return "";
    }

    /**
     * Fornece recomendação de ação baseada no Ramp Rate
     */
    private String getRecomendacaoRampRate(Double rampRate) {
        if (rampRate == null) return "";

        if (rampRate > 10) {
            return "\nAÇÃO: Reduzir volume em 20-30% nas próximas 2 semanas";
        } else if (rampRate > 8) {
            return "\nSUGESTÃO: Manter volume atual sem aumentar mais";
        } else if (rampRate < 0) {
            return "\nSUGESTÃO: Volume pode ser aumentado gradualmente";
        }

        return "";
    }

    /**
     * Determina o status geral do atleta considerando TSB e Ramp Rate
     */
    private String avaliarStatusGeral(PlanoMetaDados metaDados) {
        if (metaDados == null) {
            return "DADOS INSUFICIENTES - Aguardando coleta de métricas";
        }

        Double tsb = metaDados.getTsbAtual();
        Double rampRate = metaDados.getRampRateAtual();
        Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino();

        List<String> alertas = new ArrayList<>();

        // Alertas críticos - com validação null
        if (tsb != null && tsb < -35) {
            alertas.add("FADIGA CRÍTICA");
        }
        if (rampRate != null && rampRate > 10) {
            alertas.add("PROGRESSÃO PERIGOSA");
        }
        if (diasConsecutivos != null && diasConsecutivos >= 6) {
            alertas.add("EXCESSO DE DIAS CONSECUTIVOS");
        }

        // Alertas de atenção - com validação null
        if (tsb != null && tsb >= -35 && tsb < -25) {
            alertas.add("FADIGA ELEVADA");
        }
        if (rampRate != null && rampRate > 8 && rampRate <= 10) {
            alertas.add("RAMP RATE ALTO");
        }
        if (diasConsecutivos != null && diasConsecutivos == 5) {
            alertas.add("5 DIAS CONSECUTIVOS");
        }

        // Status positivo
        if (alertas.isEmpty()) {
            // Verificar se temos dados para afirmar status positivo
            if (tsb == null && rampRate == null) {
                return "COLETANDO DADOS - Aguardando histórico de treinos para análise";
            }

            if (tsb != null && tsb >= 5 && tsb <= 15) {
                return "FORMA IDEAL - Atleta pronto para treinos de qualidade ou provas";
            }
            if (rampRate != null && rampRate >= 3 && rampRate <= 8) {
                return "PROGRESSÃO SAUDÁVEL - Continue o bom trabalho";
            }
            return "SEM ALERTAS - Atleta em condições normais de treino";
        }

        return "REQUER ATENÇÃO: " + String.join(", ", alertas);
    }

    private String formatarDadosFisiologicos(Atleta atleta) {
        if (atleta.getFcLimiar() == null && atleta.getPaceLimiar() == null) {
            return """
                    **ATENÇÃO:** Atleta sem dados fisiológicos cadastrados!
                    - Usar valores estimados: FC Limiar ~85%% FCmáx, Pace conservador
                    - Recomendar teste de limiar urgente
                    """;
        }

        return String.format("""
                        **Frequência Cardíaca:**
                        - FC Máxima: %d bpm %s
                        - FC Repouso: %d bpm
                        - FC Limiar: %d bpm (%.0f%%%% FCmáx)
                        - Última atualização: %s
                        
                        **Pace/Velocidade:**
                        - Pace Limiar: %.2f min/km
                        - Velocidade Limiar: %.2f km/h
                        - Última atualização: %s
                        
                        **Zonas de Treino Calculadas:**
                        - Z1 (Recuperação): %.2f-%.2f min/km | %d-%d bpm
                        - Z2 (Aeróbico): %.2f-%.2f min/km | %d-%d bpm
                        - Z3 (Tempo): %.2f-%.2f min/km | %d-%d bpm
                        - Z4 (Limiar): %.2f-%.2f min/km | %d-%d bpm
                        - Z5 (VO2max): %.2f-%.2f min/km | %d-%d bpm
                        """,
                atleta.getFcMaximaCalculada(),
                atleta.getFcMaxima() != null ? "" : "(estimada)",
                atleta.getFcRepouso() != null ? atleta.getFcRepouso() : 60,
                atleta.getFcLimiarCalculada(),
                (atleta.getFcLimiarCalculada() * 100.0 / atleta.getFcMaximaCalculada()),
                atleta.getDataUltimoTesteFc() != null ?
                        atleta.getDataUltimoTesteFc().toString() : "Nunca testado",
                atleta.getPaceLimiar(),
                atleta.getVelocidadeLimiar(),
                atleta.getDataUltimoTestePace() != null ?
                        atleta.getDataUltimoTestePace().toString() : "Nunca testado",

                // Z1 - Recuperação (115-125%% pace limiar | 60-70%% FCmáx)
                calcularPaceZona(atleta.getPaceLimiar(), 1.15, 1.25),
                calcularPaceZona(atleta.getPaceLimiar(), 1.15, 1.25),
                calcularFcZona(atleta.getFcMaximaCalculada(), 0.60, 0.70),
                calcularFcZona(atleta.getFcMaximaCalculada(), 0.60, 0.70),

                // Z2 - Aeróbico (105-115%% pace limiar | 70-80%% FCmáx)
                calcularPaceZona(atleta.getPaceLimiar(), 1.05, 1.15),
                calcularPaceZona(atleta.getPaceLimiar(), 1.05, 1.15),
                calcularFcZona(atleta.getFcMaximaCalculada(), 0.70, 0.80),
                calcularFcZona(atleta.getFcMaximaCalculada(), 0.70, 0.80),

                // Z3 - Tempo (98-105%% pace limiar | 80-88%% FCmáx)
                calcularPaceZona(atleta.getPaceLimiar(), 0.98, 1.05),
                calcularPaceZona(atleta.getPaceLimiar(), 0.98, 1.05),
                calcularFcZona(atleta.getFcMaximaCalculada(), 0.80, 0.88),
                calcularFcZona(atleta.getFcMaximaCalculada(), 0.80, 0.88),

                // Z4 - Limiar (95-100%% pace limiar | 88-95%% FCmáx)
                calcularPaceZona(atleta.getPaceLimiar(), 0.95, 1.00),
                calcularPaceZona(atleta.getPaceLimiar(), 0.95, 1.00),
                calcularFcZona(atleta.getFcLimiarCalculada(), 0.95, 1.00),
                calcularFcZona(atleta.getFcLimiarCalculada(), 0.95, 1.00),

                // Z5 - VO2max (90-97%% pace limiar | 95-100%% FCmáx)
                calcularPaceZona(atleta.getPaceLimiar(), 0.90, 0.97),
                calcularPaceZona(atleta.getPaceLimiar(), 0.90, 0.97),
                calcularFcZona(atleta.getFcMaximaCalculada(), 0.95, 1.00),
                calcularFcZona(atleta.getFcMaximaCalculada(), 0.95, 1.00)
        );
    }

    /**
     * Calcula pace para uma zona específica baseado no pace limiar
     */
    private BigDecimal calcularPaceZona(BigDecimal paceLimiar, double fatorMin, double fatorMax) {
        if (paceLimiar == null) {
            return BigDecimal.ZERO;
        }

        // Calcula o fator médio da zona
        double fatorMedio = (fatorMin + fatorMax) / 2;

        // Retorna pace * fator médio
        return paceLimiar.multiply(BigDecimal.valueOf(fatorMedio))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula FC para uma zona específica baseado na FC limiar
     */
    private int calcularFcZona(Integer fcBase, double percentualMin, double percentualMax) {
        if (fcBase == null) {
            return 0;
        }

        // Retorna o valor médio da zona
        double percentualMedio = (percentualMin + percentualMax) / 2;
        return (int) Math.round(fcBase * percentualMedio);
    }

    private String formatarProvas(Atleta atleta, Prova provaAlvo) {
        if (provaAlvo == null) {
            return "Nenhuma prova alvo definida. Treinos focados em desenvolvimento geral.";
        }

        int diasFaltando = provaAlvo.diasFaltando();
        int semanasFaltando = diasFaltando / 7;
        String fase = determinarFasePreparacao(diasFaltando);

        return String.format("""
                        **PROVA ALVO:** %s
                        - Data: %s (%d dias / %d semanas)
                        - Distância: %s (%.1f km)
                        - Objetivo: %s
                        - Pace alvo: %.2f min/km
                        - TSB ideal na prova: %.1f
                        
                        **FASE ATUAL:** %s
                        **FOCO DESTA SEMANA:** %s
                        
                        **Provas preparatórias:**
                        %s
                        """,
                provaAlvo.getNomeProva(),
                provaAlvo.getDataProva(),
                diasFaltando,
                semanasFaltando,
                provaAlvo.getDistancia(),
                provaAlvo.getDistanciaKm(),
                provaAlvo.getTempoObjetivo(),
                provaAlvo.getPaceObjetivo(),
                provaAlvo.getTsbIdealProva(),
                fase,
                getFocoPorFase(fase, semanasFaltando),
                formatarProvasPreparatorias(atleta)
        );
    }

    /**
     * Determina a fase de preparação baseada em dias faltando para a prova
     */
    private String determinarFasePreparacao(int diasFaltando) {
        int semanas = diasFaltando / 7;

        if (semanas > 12) {
            return "BASE";
        } else if (semanas > 8) {
            return "BUILD";
        } else if (semanas > 3) {
            return "ESPECÍFICO";
        } else if (semanas > 1) {
            return "TAPER";
        } else if (semanas == 1) {
            return "SEMANA DA PROVA";
        } else {
            return "PÓS-PROVA";
        }
    }

    /**
     * Formata lista de provas preparatórias antes da prova alvo
     */
    private String formatarProvasPreparatorias(Atleta atleta) {
        LocalDate hoje = LocalDate.now();

        List<Prova> provasPreparatorias = provaRepository
                .findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(
                        atleta,
                        hoje,
                        hoje.plusMonths(6)
                )
                .stream()
                .filter(p -> !p.isProvaAlvo()) // Excluir a prova alvo
                .collect(Collectors.toList());

        if (provasPreparatorias.isEmpty()) {
            return "Nenhuma prova preparatória cadastrada.";
        }

        StringBuilder sb = new StringBuilder();
        provasPreparatorias.forEach(prova -> {
            int diasFaltando = prova.diasFaltando();
            sb.append(String.format("- %s: %s (%d dias) - %s\n",
                    prova.getDataProva().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    prova.getNomeProva(),
                    diasFaltando,
                    prova.getDistancia()
            ));
        });

        return sb.toString();
    }

    private String gerarAlertas(PlanoMetaDados metaDados) {
        if (metaDados == null) {
            return "⚠️ Metadados não disponíveis. Inicialize o perfil do atleta.";
        }

        // USAR ALERTAS JÁ CALCULADOS DA ENTIDADE
        var alertasAtivos = metaDados.getAlertasAtivos();

        if (alertasAtivos.isEmpty()) {
            // Verificar se é falta de dados ou realmente está tudo ok
            if (metaDados.getTsbAtual() == null && metaDados.getCtlAtual() == null) {
                return """
                        📊 **Iniciando coleta de dados:**
                        ℹ️ TSB e CTL não calculados ainda - necessário histórico de treinos
                        
                        💡 Após alguns treinos, o sistema gerará recomendações personalizadas baseadas em suas métricas.
                        """;
            }
            return "✅ Nenhum alerta. Atleta em condições ideais para progredir.";
        }

        // Separar alertas por nível de criticidade
        var criticos = alertasAtivos.stream()
                .filter(a -> a.nivel() == com.menthoros.enums.NivelAlerta.CRITICO)
                .toList();

        var altos = alertasAtivos.stream()
                .filter(a -> a.nivel() == com.menthoros.enums.NivelAlerta.ALTO)
                .toList();

        var atencao = alertasAtivos.stream()
                .filter(a -> a.nivel() == com.menthoros.enums.NivelAlerta.ATENCAO)
                .toList();

        var info = alertasAtivos.stream()
                .filter(a -> a.nivel() == com.menthoros.enums.NivelAlerta.INFO)
                .toList();

        StringBuilder resultado = new StringBuilder();

        // Formatar alertas críticos
        if (!criticos.isEmpty()) {
            resultado.append("🔴 **ALERTAS CRÍTICOS:**\n");
            criticos.forEach(a ->
                    resultado.append(String.format("- %s\n  **AÇÃO OBRIGATÓRIA:** %s\n\n",
                            a.mensagem(), a.recomendacao()))
            );
        }

        // Formatar alertas altos
        if (!altos.isEmpty()) {
            resultado.append("⚠️ **ALERTAS IMPORTANTES:**\n");
            altos.forEach(a ->
                    resultado.append(String.format("- %s\n  **RECOMENDAÇÃO:** %s\n\n",
                            a.mensagem(), a.recomendacao()))
            );
        }

        // Formatar pontos de atenção
        if (!atencao.isEmpty()) {
            resultado.append("🟡 **PONTOS DE ATENÇÃO:**\n");
            atencao.forEach(a ->
                    resultado.append(String.format("- %s\n  **SUGESTÃO:** %s\n\n",
                            a.mensagem(), a.recomendacao()))
            );
        }

        // Formatar informações positivas
        if (!info.isEmpty()) {
            info.forEach(a ->
                    resultado.append(String.format("✅ %s\n  %s\n\n",
                            a.mensagem(), a.recomendacao()))
            );
        }

        return resultado.toString().trim();
    }

    private String formatarPeriodizacaoProva(Prova provaAlvo) {
        if (provaAlvo == null) return "N/A - sem prova alvo";

        int semanas = provaAlvo.diasFaltando() / 7;

        if (semanas > 12) {
            return "Fase BASE: Construir volume aeróbico. 80% treinos fáceis, 20% moderados.";
        } else if (semanas > 8) {
            return "Fase BUILD: Adicionar qualidade. 70% fáceis, 20% específicos, 10% intensos.";
        } else if (semanas > 3) {
            return "Fase ESPECÍFICO: Treinos no pace de prova. 60% fáceis, 30% específicos, 10% regenerativos.";
        } else if (semanas > 1) {
            return "Fase TAPER: Reduzir volume 40-60%, manter intensidade. Foco em recuperação.";
        } else {
            return "SEMANA DA PROVA: Apenas treinos leves curtíssimos. TSB deve estar entre +5 e +10.";
        }
    }

    /**
     * Retorna o foco específico para a fase e semana atual
     */
    private String getFocoPorFase(String fase, int semanasFaltando) {
        switch (fase) {
            case "BASE":
                return "Construir base aeróbica com volume progressivo. Foco em Z2.";

            case "BUILD":
                if (semanasFaltando > 10) {
                    return "Introduzir treinos de qualidade (tempo run, intervalados Z3-Z4).";
                } else {
                    return "Aumentar volume de treinos específicos. Manter base aeróbica.";
                }

            case "ESPECÍFICO":
                if (semanasFaltando > 5) {
                    return "Treinos no pace de prova. Simulações parciais da distância.";
                } else {
                    return "Refinar pace de prova. Últimos ajustes técnicos e táticos.";
                }

            case "TAPER":
                if (semanasFaltando == 3) {
                    return "Reduzir volume 20-30%. Manter intensidade em treinos curtos.";
                } else if (semanasFaltando == 2) {
                    return "Reduzir volume 40-50%. Treinos de manutenção apenas.";
                } else {
                    return "Reduzir volume 60-70%. Foco total em recuperação.";
                }

            case "SEMANA DA PROVA":
                return "Apenas treinos leves curtíssimos. TSB deve estar +5 a +10.";

            case "PÓS-PROVA":
                return "Recuperação ativa. Treinos regenerativos por 1-2 semanas.";

            default:
                return "Desenvolvimento geral. Manter consistência.";
        }
    }

    private int calcularTssAlvo(PlanoMetaDados metaDados) {
        if (metaDados == null) {
            return 150; // Valor padrão conservador para iniciantes
        }

        Double ctlAtual = metaDados.getCtlAtual();
        Double rampRate = metaDados.getRampRateAtual();
        Double tsbAtual = metaDados.getTsbAtual();

        // Se não tem dados, retornar valor conservador baseado no nível
        if (ctlAtual == null || ctlAtual == 0.0) {
            return 150; // Primeira semana - valor conservador
        }

        // Usar valores padrão para nulls
        rampRate = rampRate != null ? rampRate : 0.0;
        tsbAtual = tsbAtual != null ? tsbAtual : 0.0;

        // CTL ideal = CTL atual + progressão segura
        double progressaoSegura = calcularProgressaoSegura(rampRate, tsbAtual);
        double ctlAlvo = ctlAtual + progressaoSegura;

        // TSS semanal = CTL alvo × 7
        int tssAlvo = (int) (ctlAlvo * 7);

        // Ajustar baseado em TSB
        if (tsbAtual < -25) {
            tssAlvo = (int) (tssAlvo * 0.7); // Reduzir 30%
        } else if (tsbAtual < -15) {
            tssAlvo = (int) (tssAlvo * 0.85); // Reduzir 15%
        } else if (tsbAtual > 10) {
            tssAlvo = (int) (tssAlvo * 1.1); // Aumentar 10%
        }

        // Garantir valor mínimo razoável
        return Math.max(tssAlvo, 100);
    }

    /**
     * Calcula a progressão segura de CTL baseada em Ramp Rate e TSB
     */
    private double calcularProgressaoSegura(Double rampRate, Double tsb) {
        if (rampRate == null || tsb == null) {
            return 0.0; // Sem progressão se não há dados
        }

        double progressaoBase = 0.0;

        // Baseado no TSB atual
        if (tsb < -30) {
            return -2.0; // Reduzir carga drasticamente
        } else if (tsb < -20) {
            return -1.0; // Reduzir carga levemente
        } else if (tsb < -10) {
            progressaoBase = 0.5; // Progressão muito conservadora
        } else if (tsb < 0) {
            progressaoBase = 1.0; // Progressão moderada
        } else if (tsb < 10) {
            progressaoBase = 1.5; // Progressão normal
        } else {
            progressaoBase = 2.0; // Pode progredir mais
        }

        // Ajustar baseado no Ramp Rate
        if (rampRate > 10) {
            return -1.5; // Frear progressão
        } else if (rampRate > 8) {
            return Math.min(progressaoBase, 0.5); // Limitar progressão
        } else if (rampRate > 6) {
            return Math.min(progressaoBase, 1.0);
        } else if (rampRate < 0) {
            return Math.max(progressaoBase, 1.5); // Pode aumentar mais
        }

        return progressaoBase;
    }

    /**
     * Formata informações sobre padrões de dias e disponibilidade do atleta
     */
    private String formatarDias(Atleta atleta, PlanoMetaDados metaDados, LocalDate inicioSemana) {
        StringBuilder sb = new StringBuilder();

        sb.append("## 📅 PADRÕES DE TREINO E DISPONIBILIDADE\n\n");

        // Dias disponíveis do atleta
        sb.append("### Disponibilidade Semanal\n");
        if (atleta.getDiasDisponiveis() != null && !atleta.getDiasDisponiveis().isEmpty()) {
            sb.append("**Dias disponíveis para treinar:** ");
            sb.append(atleta.getDiasDisponiveis().stream()
                    .map(DiaSemana::getLabel)
                    .collect(Collectors.joining(", ")));
            sb.append("\n");
            sb.append(String.format("**Total:** %d dias/semana\n\n", atleta.getDiasDisponiveis().size()));
        } else {
            sb.append("⚠️ **Dias disponíveis não cadastrados** - Assumir disponibilidade total\n\n");
        }

        // Dia preferido para treino longo
        if (metaDados.getDiaPreferidoLongo() != null) {
            sb.append(String.format("**Dia preferido para treino longo:** %s\n\n",
                    metaDados.getDiaPreferidoLongo().getLabel()));
        }

        // Padrões atuais
        sb.append("### Padrão Atual de Treino\n");
        sb.append(String.format("- **Dias consecutivos treinando:** %d dias\n",
                metaDados.getDiasConsecutivosTreino() != null ? metaDados.getDiasConsecutivosTreino() : 0));
        sb.append(String.format("- **Dias desde último descanso:** %d dias\n",
                metaDados.getDiasDesdeUltimoDescanso() != null ? metaDados.getDiasDesdeUltimoDescanso() : 0));
        sb.append(String.format("- **Semanas de progressão contínua:** %d semanas\n\n",
                metaDados.getSemanasProgressaoContinua() != null ? metaDados.getSemanasProgressaoContinua() : 0));

        // Máximo de dias consecutivos recomendado
        int maxDiasConsecutivos = calcularMaxDiasConsecutivos(metaDados, atleta);
        sb.append("### Recomendações para Esta Semana\n");
        sb.append(String.format("- **Máximo de dias consecutivos recomendado:** %d dias\n", maxDiasConsecutivos));

        // Alerta se já está no limite
        if (metaDados.getDiasConsecutivosTreino() != null &&
                metaDados.getDiasConsecutivosTreino() >= maxDiasConsecutivos) {
            sb.append("- ⚠️ **ALERTA:** Atleta já atingiu ou ultrapassou o limite recomendado\n");
            sb.append("- **AÇÃO:** Incluir dia de descanso completo ou regenerativo OBRIGATÓRIO\n");
        }

        // Dia de descanso recomendado
        String diaDescansoRecomendado = recomendarDiaDescanso(metaDados, atleta, inicioSemana);
        if (diaDescansoRecomendado != null) {
            sb.append(String.format("- **Dia de descanso sugerido:** %s\n", diaDescansoRecomendado));
        }

        sb.append("\n");

        // Volume médio por dia
        if (metaDados.getVolumeSemanalMedio() != null && metaDados.getTreinosPorSemanaMedio() != null) {
            double volumeMedioPorTreino = metaDados.getVolumeSemanalMedio().doubleValue() /
                    metaDados.getTreinosPorSemanaMedio();
            sb.append(String.format("**Volume médio por treino:** %.1f km\n", volumeMedioPorTreino));
        }

        if (metaDados.getTssSemanalMedio() != null && metaDados.getTreinosPorSemanaMedio() != null) {
            double tssMedioPorTreino = metaDados.getTssSemanalMedio() /
                    metaDados.getTreinosPorSemanaMedio();
            sb.append(String.format("**TSS médio por treino:** %.0f pontos\n", tssMedioPorTreino));
        }

        sb.append("\n---\n");

        return sb.toString();
    }

    /**
     * Calcula o máximo de dias consecutivos que o atleta pode treinar com segurança
     * Baseado em TSB, fadiga, experiência e histórico
     */
    private int calcularMaxDiasConsecutivos(PlanoMetaDados metaDados, Atleta atleta) {
        int maxDias = 5; // Valor padrão conservador

        // Fator 1: TSB (Prontidão/Fadiga)
        Double tsb = metaDados.getTsbAtual();
        if (tsb != null) {
            if (tsb < -30) {
                maxDias = 2; // Fadiga crítica - máximo 2 dias seguidos
            } else if (tsb < -20) {
                maxDias = 3; // Alta fadiga - máximo 3 dias
            } else if (tsb < -10) {
                maxDias = 4; // Fadiga moderada - máximo 4 dias
            } else if (tsb >= -10 && tsb < 5) {
                maxDias = 5; // Normal - padrão de 5 dias
            } else if (tsb >= 5 && tsb < 15) {
                maxDias = 6; // Bem recuperado - pode fazer 6 dias
            } else {
                maxDias = 6; // Muito descansado - até 6 dias
            }
        }

        // Fator 2: Nível do atleta (experiência)
        if (atleta.getNivelExperiencia() != null) {
            switch (atleta.getNivelExperiencia()) {
                case INICIANTE:
                    maxDias = Math.min(maxDias, 4); // Iniciantes: máximo 4 dias consecutivos
                    break;
                case INTERMEDIARIO:
                    maxDias = Math.min(maxDias, 5); // Intermediários: máximo 5 dias
                    break;
                case AVANCADO:
                    // Avançados podem usar o valor calculado pelo TSB
                    break;
                case ELITE:
                    maxDias = Math.min(maxDias + 1, 7); // Elite pode treinar mais
                    break;
            }
        }

        // Fator 3: Ramp Rate (taxa de progressão)
        Double rampRate = metaDados.getRampRateAtual();
        if (rampRate != null && rampRate > 8) {
            maxDias = Math.max(maxDias - 1, 3); // Reduz 1 dia se progressão muito rápida
        }

        // Fator 4: Dias já consecutivos
        Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino();
        if (diasConsecutivos != null && diasConsecutivos >= 5) {
            // Se já treinou 5+ dias seguidos, reduzir capacidade
            maxDias = Math.min(maxDias, 3);
        }

        // Fator 5: CTL (Fitness de base)
        Double ctl = metaDados.getCtlAtual();
        if (ctl != null && ctl > 80) {
            // Atletas com CTL alto têm melhor capacidade de recuperação
            maxDias = Math.min(maxDias + 1, 7);
        } else if (ctl != null && ctl < 30) {
            // CTL baixo indica menos capacidade de suportar volume
            maxDias = Math.max(maxDias - 1, 3);
        }

        // Fator 6: Histórico de lesões (se houver)
        if (atleta.getHistoricoLesoes() != null && !atleta.getHistoricoLesoes().isEmpty()) {
            // Se tem histórico de lesões, ser mais conservador
            maxDias = Math.max(maxDias - 1, 3);
        }

        // Garantir limites razoáveis
        maxDias = Math.max(3, Math.min(maxDias, 7));

        return maxDias;
    }

    /**
     * Recomenda qual dia da semana deveria ser de descanso
     */
    private String recomendarDiaDescanso(PlanoMetaDados metaDados, Atleta atleta, LocalDate inicioSemana) {
        Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino();
        Integer diasDesdeDescanso = metaDados.getDiasDesdeUltimoDescanso();

        // Se não precisa de descanso urgente, retornar null
        if ((diasConsecutivos == null || diasConsecutivos < 4) &&
                (diasDesdeDescanso == null || diasDesdeDescanso < 5)) {
            return null;
        }

        // Dias disponíveis do atleta
        List<DiaSemana> diasDisponiveis = atleta.getDiasDisponiveis();
        if (diasDisponiveis == null || diasDisponiveis.isEmpty()) {
            return "Quarta-feira (meio da semana)";
        }

        // Se treina 6-7 dias, recomendar meio da semana
        if (diasDisponiveis.size() >= 6) {
            return "Quarta-feira ou Quinta-feira (quebrar a semana)";
        }

        // Se treina 5 dias, um dos dias que não treina já serve
        if (diasDisponiveis.size() == 5) {
            List<DiaSemana> diasNaoTreina = Arrays.stream(DiaSemana.values())
                    .filter(d -> !diasDisponiveis.contains(d))
                    .collect(Collectors.toList());

            if (!diasNaoTreina.isEmpty()) {
                return diasNaoTreina.get(0).getLabel() + " (já é dia de folga)";
            }
        }

        // Recomendar meio da semana como padrão
        return "Quarta-feira (meio da semana)";
    }

    /**
     * Retorna distribuição ideal de treinos na semana
     */
    private String sugerirDistribuicaoSemanal(PlanoMetaDados metaDados, Atleta atleta) {
        int maxDiasConsecutivos = calcularMaxDiasConsecutivos(metaDados, atleta);
        List<DiaSemana> diasDisponiveis = atleta.getDiasDisponiveis();

        if (diasDisponiveis == null || diasDisponiveis.isEmpty()) {
            return "Distribuição sugerida não disponível - cadastrar dias disponíveis do atleta";
        }

        int totalDias = diasDisponiveis.size();

        if (totalDias <= 3) {
            return String.format("%d treinos distribuídos nos dias disponíveis (sem dias consecutivos necessários)", totalDias);
        } else if (totalDias == 4) {
            return "Sugestão: 2-1-1 (2 dias, descanso, 1 dia, descanso, 1 dia)";
        } else if (totalDias == 5) {
            return String.format("Sugestão: 2-1-2 ou 3-1-1 (máx %d dias consecutivos)", maxDiasConsecutivos);
        } else if (totalDias == 6) {
            return String.format("Sugestão: 3-1-2 ou 2-1-3 (máx %d dias consecutivos, 1 descanso obrigatório)", maxDiasConsecutivos);
        } else {
            return String.format("Treino diário disponível - incluir pelo menos 1 dia de descanso (máx %d consecutivos)", maxDiasConsecutivos);
        }
    }

    /**
     * Gera alertas específicos sobre padrões de dias
     */
    private String gerarAlertasDias(PlanoMetaDados metaDados) {
        List<String> alertas = new ArrayList<>();

        Integer diasConsecutivos = metaDados.getDiasConsecutivosTreino();
        Integer diasDesdeDescanso = metaDados.getDiasDesdeUltimoDescanso();

        if (diasConsecutivos != null && diasConsecutivos >= 7) {
            alertas.add("🔴 CRÍTICO: 7+ dias consecutivos sem descanso! DESCANSO IMEDIATO OBRIGATÓRIO!");
        } else if (diasConsecutivos != null && diasConsecutivos >= 6) {
            alertas.add("🟠 ALERTA: 6 dias consecutivos. Dia de descanso urgente!");
        } else if (diasConsecutivos != null && diasConsecutivos >= 5) {
            alertas.add("🟡 ATENÇÃO: 5 dias consecutivos. Considerar descanso em breve.");
        }

        if (diasDesdeDescanso != null && diasDesdeDescanso >= 10) {
            alertas.add("🔴 CRÍTICO: 10+ dias desde último descanso completo!");
        } else if (diasDesdeDescanso != null && diasDesdeDescanso >= 7) {
            alertas.add("🟠 ALERTA: 7+ dias desde último descanso.");
        }

        Integer semanas = metaDados.getSemanasProgressaoContinua();
        if (semanas != null && semanas >= 4) {
            alertas.add("🟡 4+ semanas de progressão contínua. Considerar semana regenerativa.");
        }

        if (alertas.isEmpty()) {
            return "";
        }

        return "\n### ⚠️ Alertas de Padrão de Dias\n" + String.join("\n", alertas) + "\n";
    }

    /**
     * Formata lista simples de dias disponíveis (para o prompt)
     */
    private String formatarDias(List<DiaSemana> diasDisponiveis) {
        if (diasDisponiveis == null || diasDisponiveis.isEmpty()) {
            return "Não informado (assumir 7 dias/semana)";
        }

        return diasDisponiveis.stream()
                .map(DiaSemana::getLabel)
                .collect(Collectors.joining(", "));
    }

    /**
     * Versão simplificada quando chamada apenas com Atleta
     */
    private int calcularMaxDiasConsecutivos(Atleta atleta) {
        // Retorna valor baseado apenas no nível, sem considerar TSB
        if (atleta.getNivelExperiencia() == null) {
            return 5; // Padrão conservador
        }

        switch (atleta.getNivelExperiencia()) {
            case INICIANTE:
                return 4;
            case INTERMEDIARIO:
                return 5;
            case AVANCADO:
                return 6;
            case ELITE:
                return 7;
            default:
                return 5;
        }
    }

    /**
     * Gera status descritivo sobre dias consecutivos de treino
     */
    private String gerarStatusDiasConsecutivos(Integer diasConsecutivos, int maxDiasConsecutivos) {
        if (diasConsecutivos == null || diasConsecutivos == 0) {
            return "✅ Descansado - Pronto para iniciar semana";
        }

        if (diasConsecutivos >= maxDiasConsecutivos + 1) {
            return "🔴 CRÍTICO - Excedeu limite seguro! DESCANSO OBRIGATÓRIO";
        } else if (diasConsecutivos == maxDiasConsecutivos) {
            return "⚠️ NO LIMITE - Próximo treino DEVE ser descanso ou regenerativo";
        } else if (diasConsecutivos >= maxDiasConsecutivos - 1) {
            return "🟡 ATENÇÃO - Próximo do limite, planejar descanso";
        } else if (diasConsecutivos >= 3) {
            return "✅ DENTRO DO LIMITE - Monitorar sinais de fadiga";
        } else if (diasConsecutivos >= 1) {
            return "✅ SAUDÁVEL - Padrão seguro de treino";
        }

        return "✅ Normal";
    }
}