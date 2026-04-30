package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.dto.output.AtletaOutputDto;
import br.com.menthoros.backend.dto.output.PlanoSemanalOutputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;
import br.com.menthoros.backend.services.helper.IntervaladoElegibilidadeService;
import br.com.menthoros.backend.services.helper.PaceZoneCalculator;
import br.com.menthoros.backend.services.helper.RecomendacaoIntervalado;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider;
import br.com.menthoros.backend.services.helper.TreinoHistoricoProvider.ContextoTreino;
import br.com.menthoros.backend.services.helper.ZonaTreinoService;
import br.com.menthoros.backend.services.helper.ZonaTreinoService.ZonaCompleta;
import br.com.menthoros.backend.services.impl.MetricasAlertaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PlanoTreinoPromptBuilder {

    private final String promptTemplate;
    private final PromptTemplateLoader templateLoader;
    private final MetricasAlertaService metricasAlertaService;
    private final ZonaTreinoService zonaTreinoService;
    private final TreinoHistoricoProvider treinoHistoricoProvider;
    private final MetricasPromptFormatter metricasPromptFormatter;
    private final AlertasPromptFormatter alertasPromptFormatter;
    private final RecuperacaoPromptFormatter recuperacaoPromptFormatter;
    private final PeriodizacaoPromptFormatter periodizacaoPromptFormatter;
    private final VariabilidadePromptFormatter variabilidadePromptFormatter;
    private final DisponibilidadePromptFormatter disponibilidadePromptFormatter;
    private final IntervaladoElegibilidadeService intervaladoElegibilidadeService;
    private final PaceHistoricoFormatter paceHistoricoFormatter;
    private final PaceZoneCalculator paceZoneCalculator;

    public PlanoTreinoPromptBuilder(@Value("classpath:prompts/plano-treino-prompt.txt") Resource promptResource,
                                    PromptTemplateLoader templateLoader,
                                    MetricasAlertaService metricasAlertaService,
                                    ZonaTreinoService zonaTreinoService,
                                    TreinoHistoricoProvider treinoHistoricoProvider,
                                    MetricasPromptFormatter metricasPromptFormatter,
                                    AlertasPromptFormatter alertasPromptFormatter,
                                    RecuperacaoPromptFormatter recuperacaoPromptFormatter,
                                    PeriodizacaoPromptFormatter periodizacaoPromptFormatter,
                                    VariabilidadePromptFormatter variabilidadePromptFormatter,
                                    DisponibilidadePromptFormatter disponibilidadePromptFormatter,
                                    IntervaladoElegibilidadeService intervaladoElegibilidadeService,
                                    PaceHistoricoFormatter paceHistoricoFormatter,
                                    PaceZoneCalculator paceZoneCalculator) {
        this.templateLoader = templateLoader;
        this.metricasAlertaService = metricasAlertaService;
        this.zonaTreinoService = zonaTreinoService;
        this.treinoHistoricoProvider = treinoHistoricoProvider;
        this.metricasPromptFormatter = metricasPromptFormatter;
        this.alertasPromptFormatter = alertasPromptFormatter;
        this.recuperacaoPromptFormatter = recuperacaoPromptFormatter;
        this.periodizacaoPromptFormatter = periodizacaoPromptFormatter;
        this.variabilidadePromptFormatter = variabilidadePromptFormatter;
        this.disponibilidadePromptFormatter = disponibilidadePromptFormatter;
        this.intervaladoElegibilidadeService = intervaladoElegibilidadeService;
        this.paceHistoricoFormatter = paceHistoricoFormatter;
        this.paceZoneCalculator = paceZoneCalculator;
        try {
            this.promptTemplate = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível carregar o template do prompt.", e);
        }
    }

    // ======================== MÉTODO LEGADO (DTO-based) ========================

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


    // ======================== MÉTODO OTIMIZADO (principal) ========================

    public String buildOptimizedPrompt(Atleta atleta, PlanoMetaDados metaDados, Prova provaAlvo,
                                       LocalDate inicioSemana, List<DiaSemana> diasEfetivos) {
        var ctx = treinoHistoricoProvider.prepararContexto(atleta);

        // DECISÃO INTERVALADO — avaliação determinística pré-LLM (5 portões fisiológicos)
        RecomendacaoIntervalado recomIntervalado = intervaladoElegibilidadeService.avaliar(
                atleta, metaDados, ctx.treinosUltimas4Semanas(), ctx.dataReferencia());

        // 1. Dados básicos do atleta

        String provas = null;
        if(provaAlvo != null) {
            provas = periodizacaoPromptFormatter.formatarProvas(provaAlvo, ctx.provasPreparatorias());
        }
        // 2. ALERTAS OBRIGATÓRIOS NO TOPO (prioridade máxima)
        int maxDiasConsecutivos = disponibilidadePromptFormatter.calcularMaxDiasConsecutivos(metaDados, atleta);
        String alertasObrigatorios = alertasPromptFormatter.gerarAlertasObrigatorios(
                atleta, metaDados, maxDiasConsecutivos, ctx.treinosUltimas4Semanas(), ctx.dataReferencia());

        // 3. HIERARQUIA DE DECISÃO (para resolver conflitos)
        String hierarquiaDecisao = alertasPromptFormatter.gerarHierarquiaDecisao(metaDados, atleta);

        // 4. EVENTO COMPETITIVO NA SEMANA (regra mandatória baseada na semana planejada)
        String eventoCompetitivoSemana = periodizacaoPromptFormatter.formatarEventoCompetitivoSemana(
                provaAlvo, ctx.provasPreparatorias(), inicioSemana);

        // 5. RESTRIÇÕES E LESÕES
        String restricoesLesoes = alertasPromptFormatter.formatarRestricoesLesoes(atleta, ctx.dataReferencia());

        // 6. VALIDAÇÃO E FALLBACKS DE DADOS
        String fallbacksDados = validarEFallbacksDadosFisiologicos(atleta);

        // 7. Análises de pré-planejamento
        var analiseEstimulos = variabilidadePromptFormatter.analisarEstimulosRecentes(
                ctx.treinosUltimas4Semanas(), ctx.dataReferencia());
        var volumeUltimas3Semanas = variabilidadePromptFormatter.calcularVolumeMedioUltimasTresSemanas(
                ctx.treinosUltimas3Semanas(), ctx.dataReferencia());
        var matrizVariabilidade = variabilidadePromptFormatter.identificarMatrizVariabilidade(
                ctx.treinosUltimas4Semanas(), ctx.dataReferencia());
        var alertasVariabilidade = variabilidadePromptFormatter.gerarAlertasVariabilidade(
                ctx.treinosUltimas4Semanas(), ctx.dataReferencia());
        var instrucoesRecuperacao = recuperacaoPromptFormatter.detalharRecuperacao(
                atleta, metaDados, ctx.treinosUltimaSemana());

        // 7. Compilar contexto histórico consolidado (sem repetição)
        StringBuilder historicoCompleto = new StringBuilder();

        // ETAPA 1: Dados fisiológicos e zonas (com fallbacks se necessário)
        historicoCompleto.append("## 📊 DADOS FISIOLÓGICOS E ZONAS DE TREINO\n\n");
        historicoCompleto.append(formatarDadosFisiologicos(atleta)).append("\n\n");
        if (!fallbacksDados.isEmpty()) {
            historicoCompleto.append(fallbacksDados);
        }

        // Ajuste de pace por TSB (Fase 2): nota explícita ao LLM sobre penalidade de fadiga
        String avisoTsb = paceZoneCalculator.formatarAvisoAjuste(metaDados != null ? metaDados.getTsbAtual() : null);
        if (!avisoTsb.isEmpty()) {
            historicoCompleto.append(avisoTsb).append("\n");
        }

        // ETAPA 2: Métricas de carga e fadiga (consolidadas)
        historicoCompleto.append(metricasPromptFormatter.formatarMetricas(metaDados)).append("\n\n");

        // ETAPA 3: Histórico recente de treinos
        historicoCompleto.append(formatarHistoricoTreinos(ctx.treinosUltimos14Dias())).append("\n\n");

        // ETAPA 3.5: Pace demonstrado por tipo nas últimas 4 semanas (Fase 1)
        historicoCompleto.append(paceHistoricoFormatter.formatarHistoricoPace(ctx.treinosUltimas4Semanas())).append("\n\n");

        // ETAPA 3.6: Teto obrigatório de pace por tipo (Fase 3)
        Map<TipoTreino, BigDecimal> tetoPorTipo = paceHistoricoFormatter.calcularTetoPorTipo(ctx.treinosUltimas4Semanas());
        if (!tetoPorTipo.isEmpty()) {
            historicoCompleto.append(paceHistoricoFormatter.formatarTetoPace(tetoPorTipo)).append("\n\n");
        }

        // ETAPA 3.7: Aviso de paceLimiar desatualizado (Fase 5)
        String avisoPaceLimiar = paceHistoricoFormatter.verificarPaceLimiarAtualizado(atleta);
        if (!avisoPaceLimiar.isEmpty()) {
            historicoCompleto.append(avisoPaceLimiar).append("\n\n");
        }

        // ETAPA 4: Disponibilidade e padrões de treino
        historicoCompleto.append(disponibilidadePromptFormatter.formatarDisponibilidade(atleta, metaDados, inicioSemana, diasEfetivos)).append("\n\n");

        // ETAPA 5: Análise de estímulos recentes
        historicoCompleto.append(analiseEstimulos).append("\n\n");

        // ETAPA 6: Volume das últimas 3 semanas (consolidado)
        historicoCompleto.append("## 📈 VOLUME MÉDIO DAS ÚLTIMAS 3 SEMANAS\n");
        historicoCompleto.append(String.format("- **Volume Médio:** %.1f km\n", volumeUltimas3Semanas.get("volumeMedioKm")));
        historicoCompleto.append(String.format("- **Tendência:** %s\n", volumeUltimas3Semanas.get("tendencia")));
        historicoCompleto.append(String.format("- **Semana Mais Recente:** %.1f km\n", volumeUltimas3Semanas.get("volumeSemanaMaisRecente")));
        historicoCompleto.append(String.format("- **Semana Anterior:** %.1f km\n", volumeUltimas3Semanas.get("volumeSemanaAnterior")));
        historicoCompleto.append(String.format("- **2 Semanas Atrás:** %.1f km\n\n", volumeUltimas3Semanas.get("volumeDuasSemanas")));

        // ETAPA 7: Matriz de variabilidade
        historicoCompleto.append(matrizVariabilidade).append("\n");
        historicoCompleto.append(alertasVariabilidade).append("\n");

        // ETAPA 8: Instruções de recuperação
        historicoCompleto.append(instrucoesRecuperacao).append("\n\n");

        // ETAPA 9: Periodização estruturada
        historicoCompleto.append("## 📅 PERIODIZAÇÃO E FASE ATUAL\n\n");
        historicoCompleto.append(periodizacaoPromptFormatter.formatarPeriodizacaoProva(provaAlvo)).append("\n\n");

        // ETAPA 10: METAS CALCULADAS PARA ESTA SEMANA (ajustadas e consistentes)
        int tssAlvo = periodizacaoPromptFormatter.calcularTssAlvoAjustado(metaDados, atleta);

        // Determinar tipo de semana baseado nas recomendações
        String tipoSemana = periodizacaoPromptFormatter.determinarTipoSemana(metaDados, atleta, tssAlvo);

        historicoCompleto.append("## 🎯 METAS PARA ESTA SEMANA\n\n");
        historicoCompleto.append(String.format("**Tipo de Semana:** %s\n\n", tipoSemana));
        historicoCompleto.append(String.format("- **TSS Alvo Semanal:** %d pontos", tssAlvo));

        // Explicar se houve ajuste por regeneração
        Double tsb = metaDados != null ? metaDados.getTsbAtual() : null;
        int tssAlvoBase = periodizacaoPromptFormatter.calcularTssAlvo(metaDados);
        if (tssAlvo < tssAlvoBase) {
            int reducaoPercentual = (int) Math.round((1.0 - (double) tssAlvo / tssAlvoBase) * 100);
            historicoCompleto.append(String.format(" (reduzido %d%% por semana regenerativa)", reducaoPercentual));
        }
        historicoCompleto.append("\n");

        historicoCompleto.append(String.format("  - Baseado em CTL atual: %.1f\n", metaDados != null && metaDados.getCtlAtual() != null ? metaDados.getCtlAtual() : 0.0));
        historicoCompleto.append(String.format("  - Ajustado por TSB: %.1f (%s)\n",
                tsb != null ? tsb : 0.0,
                metaDados != null && metaDados.getInterpretacaoTsb() != null ?
                metricasPromptFormatter.interpretarTsb(metaDados != null ? metaDados.getTsbAtual() : null): "normal"));
        historicoCompleto.append(String.format("  - Ramp Rate atual: %.1f pts/sem (%s)\n\n",
                metaDados != null && metaDados.getRampRateAtual() != null ? metaDados.getRampRateAtual() : 0.0,
                metricasPromptFormatter.interpretarRampRate(metaDados != null ? metaDados.getRampRateAtual() : null)));
        historicoCompleto.append(String.format("  - Recomendação Ramp Rate:  (%s)",
                metricasPromptFormatter.getRecomendacaoRampRate(metaDados!= null ? metaDados.getRampRateAtual() : null)));
        historicoCompleto.append(String.format("- **Máximo de Dias Consecutivos Recomendado:** %d dias\n", maxDiasConsecutivos));
        historicoCompleto.append(String.format("  - Atual: %d dias consecutivos\n",
                metaDados != null && metaDados.getDiasConsecutivosTreino() != null ? metaDados.getDiasConsecutivosTreino() : 0));
        historicoCompleto.append("  - Baseado em TSB, experiência e histórico\n\n");
        historicoCompleto.append(String.format("- **Distribuição Semanal Sugerida:** %s\n\n",
                disponibilidadePromptFormatter.sugerirDistribuicaoSemanal(metaDados, atleta)));

        // 8. Fallbacks para dados nulos
        String diaPreferidoLongo = atleta.getDiaPreferidoLongo() != null ?
                atleta.getDiaPreferidoLongo().toString() : "SABADO";

        // 9. Montar histórico completo com todas as seções na ordem correta
        StringBuilder historicoFinal = new StringBuilder();

        // Ordem prioritária: alertas primeiro, depois dados, depois regras
        historicoFinal.append(alertasObrigatorios);
        historicoFinal.append(hierarquiaDecisao);
        historicoFinal.append(eventoCompetitivoSemana);
        historicoFinal.append(restricoesLesoes);
        historicoFinal.append(formatarDecisaoIntervalado(recomIntervalado));
        historicoFinal.append(historicoCompleto);

        historicoFinal.append(String.format("** STATUS GERAL ** \n"));
        historicoFinal.append(String.format("  - ** Status geral: (%s)", metricasPromptFormatter.avaliarStatusGeral(metaDados)));

        // 10. Carregar e formatar o novo template otimizado
        return templateLoader.loadAndFormat(
                "plano-treino-otimizado-claude.txt",
                atleta.getNome(),                                                                              // %s - Nome
                atleta.getIdade(),                                                                             // %d - Idade
                atleta.getObjetivo() != null ? atleta.getObjetivo() : "Melhorar condicionamento",             // %s - Objetivo
                atleta.getNivelExperiencia() != null ? atleta.getNivelExperiencia().toString() : "INTERMEDIARIO", // %s - Experiência
                disponibilidadePromptFormatter.formatarDias(atleta.getDiasDisponiveis()),                       // %s - Dias disponíveis
                diaPreferidoLongo,                                                                             // %s - Dia preferido longo
                provas,                                                                                        // %s - Provas
                historicoFinal.toString()                                                                      // %s - Histórico completo (com alertas no topo)
        );
    }

    // ======================== MÉTODOS AUXILIARES (mantidos) ========================

    /**
     * Converte o resultado do motor de elegibilidade em uma seção de texto estruturada
     * que é injetada no prompt como instrução mandatória para o LLM.
     *
     * <p>Usa pattern matching switch sobre o sealed type para garantir exaustividade
     * em tempo de compilação (Java 21). Texto em ASCII sem emojis para evitar problemas
     * de encoding no {@code templateLoader}.</p>
     */
    private String formatarDecisaoIntervalado(RecomendacaoIntervalado recomendacao) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n## DECISAO INTERVALADO - INSTRUCAO OBRIGATORIA\n\n");

        switch (recomendacao) {
            case RecomendacaoIntervalado.Substituido sub -> {
                sb.append("[PROIBIDO] Nenhuma forma de treino intensivo esta semana.\n");
                sb.append("Tipo substituto obrigatorio: ").append(sub.tipoFallback().getLabel()).append("\n");
                sb.append("Fundamento: ").append(sub.motivo()).append("\n");
                sb.append("Instrucao: ").append(sub.instrucaoParaLlm()).append("\n");
            }
            case RecomendacaoIntervalado.Degradado deg -> {
                sb.append("[DEGRADADO] Versao segura de intensidade autorizada.\n");
                sb.append("Categoria segura: ").append(deg.categoriaSegura().name())
                  .append(" — ").append(deg.categoriaSegura().getNome()).append("\n");
                sb.append("Fundamento: ").append(deg.motivo()).append("\n");
                sb.append("Instrucao: ").append(deg.instrucaoParaLlm()).append("\n");
                sb.append("Descricao: ").append(deg.categoriaSegura().getDescricao()).append("\n");
            }
            case RecomendacaoIntervalado.Elegivel el -> {
                sb.append("[AUTORIZADO] Intervalado elegivel esta semana.\n");
                sb.append("Categoria: ").append(el.categoria().name())
                  .append(" — ").append(el.categoria().getNome()).append("\n");
                sb.append("Fundamento: ").append(el.motivo()).append("\n");
                sb.append("Instrucao: ").append(el.instrucaoParaLlm()).append("\n");
                sb.append("Descricao: ").append(el.categoria().getDescricao()).append("\n");
            }
        }

        sb.append("\nATENCAO: Esta decisao e deterministica e baseada em dados reais do atleta. ");
        sb.append("Ela SUBSTITUI qualquer raciocinio independente sobre intensidade. ");
        sb.append("Nao inclua INTERVALADO se marcado [PROIBIDO].\n\n");

        return sb.toString();
    }

    private String formatarHistoricoTreinos(List<TreinoRealizado> treinosRecentes) {
        if (treinosRecentes == null || treinosRecentes.isEmpty()) {
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

        BigDecimal volumeMedio = volumeTotal.divide(
                BigDecimal.valueOf(totalTreinos),
                2,
                RoundingMode.HALF_UP
        );

        int tssMedio = tssTotal / totalTreinos;

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

    private String formatarDadosFisiologicos(Atleta atleta) {
        if (atleta.getFcLimiar() == null && atleta.getPaceLimiar() == null) {
            return """
                    **ATENÇÃO:** Atleta sem dados fisiológicos cadastrados!
                    - Usar valores estimados: FC Limiar ~85%% FCmáx, Pace conservador
                    - Recomendar teste de limiar urgente
                    """;
        }

        List<ZonaCompleta> zonas = zonaTreinoService.calcularZonas(atleta);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
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
                        atleta.getDataUltimoTestePace().toString() : "Nunca testado"
        ));

        for (ZonaCompleta zona : zonas) {
            sb.append(String.format("- Z%d (%s): %s-%s min/km | %d-%d bpm\n",
                    zona.numero(),
                    zona.nome(),
                    zona.pace().paceMin(),
                    zona.pace().paceMax(),
                    zona.fc().fcMin(),
                    zona.fc().fcMax()));
        }

        return sb.toString();
    }

    private String validarEFallbacksDadosFisiologicos(Atleta atleta) {
        StringBuilder fallbacks = new StringBuilder();
        boolean temProblema = false;

        if (atleta.getPaceLimiar() == null || atleta.getPaceLimiar().compareTo(BigDecimal.ZERO) == 0) {
            temProblema = true;
            fallbacks.append("⚠️ **Pace Limiar inválido/zerado** → Usar estimativa por nível\n");
            String paceEstimado = estimarPacePorNivel(atleta);
            fallbacks.append("  - Pace estimado: ").append(paceEstimado).append(" min/km\n");
            fallbacks.append("  - **USAR APENAS FC para prescrição, não pace\n");
            fallbacks.append("⚠️ **Zonas de pace inutilizáveis** → Usar apenas FC (Z1-Z5 por percentual FCmax)\n");
        }

        if (atleta.getFcLimiarCalculada() == null || atleta.getFcLimiarCalculada() <= 0) {
            temProblema = true;
            Integer fcMax = atleta.getFcMaximaCalculada();
            if (fcMax != null && fcMax > 0) {
                int fcLimiarEstimada = (int) Math.round(fcMax * 0.85);
                fallbacks.append("⚠️ **FC Limiar não disponível** → Usar estimativa: ").append(fcLimiarEstimada).append(" bpm (85% FCmax)\n");
            } else {
                fallbacks.append("⚠️ **FC Limiar e FC Max não disponíveis** → Usar valores conservadores\n");
                fallbacks.append("  - Estimar FC Limiar: ~150-160 bpm\n");
            }
        }

        if (temProblema) {
            StringBuilder sb = new StringBuilder();
            sb.append("## ⚠️ FALLBACKS PARA DADOS INCOMPLETOS\n\n");
            sb.append(fallbacks);
            sb.append("\n**Ao prescrever treinos:**\n");
            sb.append("- Se pace inválido: usar formato \"Z2 (140-160 bpm)\" em vez de incluir pace\n");
            sb.append("- Se FC disponível: priorizar FC sobre pace estimado\n");
            sb.append("- Adicionar na justificativa: \"Zonas estimadas - recomenda-se teste de limiar\"\n\n");
            return sb.toString();
        }

        return "";
    }

    private String estimarPacePorNivel(Atleta atleta) {
        if (atleta.getNivelExperiencia() == null) {
            return "5:30-6:00";
        }

        return switch (atleta.getNivelExperiencia()) {
            case INICIANTE -> "6:30-7:00";
            case INTERMEDIARIO -> "5:30-6:00";
            case AVANCADO -> "4:30-5:00";
            case ELITE -> "3:30-4:30";
        };
    }
}
