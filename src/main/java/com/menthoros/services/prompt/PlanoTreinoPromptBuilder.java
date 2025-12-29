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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PlanoTreinoPromptBuilder {

    private final String promptTemplate;
    private final ProvaRepository provaRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final PromptTemplateLoader templateLoader;

    public PlanoTreinoPromptBuilder(@Value("classpath:prompts/plano-treino-prompt.txt") Resource promptResource,
                                   ProvaRepository provaRepository,
                                   TreinoRealizadoRepository treinoRealizadoRepository,
                                   PromptTemplateLoader templateLoader) {
        this.provaRepository = provaRepository;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.templateLoader = templateLoader;
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

        // Carregar e formatar o template externalizado
        return templateLoader.loadAndFormat(
                "plano-treino-enhanced.txt",
                // Parâmetros do String.format (na ordem correta dos placeholders)
                atleta.getNome(),                                                                              // %1$s
                atleta.getIdade(),                                                                             // %2$d
                atleta.getObjetivo() != null ? atleta.getObjetivo() : "Melhorar condicionamento",             // %3$s
                atleta.getNivelExperiencia() != null ? atleta.getNivelExperiencia().toString() : "INTERMEDIARIO", // %4$s
                formatarDias(atleta.getDiasDisponiveis()),                                                     // %5$s
                diaPreferidoLongo,                                                                             // %6$s
                provas,                                                                                        // %7$s
                dadosFisiologicos,                                                                             // %8$s
                historicoTreinos,                                                                              // %9$s
                metricas,                                                                                      // %10$s
                diasFormatados,                                                                                // %11$s
                // PARÂMETROS CALCULADOS PARA ESTA SEMANA
                tssAlvo,                                                                                       // %12$d
                tssMedio,                                                                                      // %13$d
                rampRate,                                                                                      // %14$.1f
                interpretarRampRate(rampRate),                                                                 // %15$s
                diasConsecutivos,                                                                              // %16$d
                maxDiasConsecutivos,                                                                           // %17$d
                gerarStatusDiasConsecutivos(diasConsecutivos, maxDiasConsecutivos),                           // %18$s
                periodizacao,                                                                                  // %19$s
                alertas,                                                                                       // %20$s
                // Repetidos nas REGRAS DO PLANO
                tssAlvo,                                                                                       // %21$d
                maxDiasConsecutivos                                                                            // %22$d
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

    /**
     * Analisa estímulos recentes (últimas 4 semanas) e retorna análise formatada
     * para o prompt otimizado.
     *
     * Calcula:
     * 1. Tipos de treino ausentes há >14 dias
     * 2. Volume semanal detalhado (últimas 3 semanas)
     * 3. Distribuição de intensidade por zona (Z1-Z5)
     * 4. Sinais de sobrecarga (treinos cancelados, RPE médio)
     */
    public String analisarEstimulosRecentes(Atleta atleta, LocalDate inicioSemana) {
        LocalDate hoje = LocalDate.now();
        LocalDate quadroSemanas = hoje.minusWeeks(4);

        // 1. Buscar treinos das últimas 4 semanas
        List<TreinoRealizado> treinosRecentes = treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(atleta, quadroSemanas);

        if (treinosRecentes.isEmpty()) {
            return "**ANÁLISE PRÉ-PLANEJAMENTO:**\n\n" +
                    "❌ Nenhum treino realizado nos últimos 28 dias.\n" +
                    "⚠️ RECOMENDAÇÃO: Iniciar com volume conservador (progressão lenta).\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**ANÁLISE PRÉ-PLANEJAMENTO (últimas 4 semanas):**\n\n");

        // 2. ANÁLISE 1: Tipos de treino e gaps
        sb.append("### 1. Padrão de Estímulos\n");
        analisarTiposTreinoEGaps(treinosRecentes, hoje, sb);

        // 3. ANÁLISE 2: Volume semanal
        sb.append("\n### 2. Volume Semanal Realizado\n");
        analisarVolumeSemanal(treinosRecentes, sb);

        // 4. ANÁLISE 3: Distribuição de intensidade
        sb.append("\n### 3. Padrão de Intensidade (Distribuição de Zonas)\n");
        analisarIntensidadeZonas(treinosRecentes, sb);

        // 5. ANÁLISE 4: Sinais de sobrecarga
        sb.append("\n### 4. Sinais de Fadiga/Sobrecarga\n");
        analisarSobreCarga(treinosRecentes, sb);

        sb.append("\n---\n");
        return sb.toString();
    }

    /**
     * Analisa tipos de treino realizados e identifica gaps (ausências)
     */
    private void analisarTiposTreinoEGaps(List<TreinoRealizado> treinos, LocalDate hoje, StringBuilder sb) {
        // Agrupar por tipo de treino e encontrar última data
        Map<String, LocalDate> ultimaDataPorTipo = new HashMap<>();

        treinos.forEach(t -> {
            String tipo = t.getTipoTreino() != null ? t.getTipoTreino().toString() : "DESCONHECIDO";
            LocalDate ultimaData = ultimaDataPorTipo.getOrDefault(tipo, t.getDataTreino());

            if (t.getDataTreino().isAfter(ultimaData)) {
                ultimaDataPorTipo.put(tipo, t.getDataTreino());
            } else {
                ultimaDataPorTipo.put(tipo, ultimaData);
            }
        });

        // Tipos padrão esperados em um programa de treino
        List<String> tiposEsperados = Arrays.asList(
                "REGENERATIVO", "CONTINUO", "INTERVALADO", "LONGO", "TEMPO_RUN", "FARTLEK"
        );

        sb.append("**Tipos de treino realizados:**\n");
        tiposEsperados.forEach(tipo -> {
            if (ultimaDataPorTipo.containsKey(tipo)) {
                LocalDate ultima = ultimaDataPorTipo.get(tipo);
                long diasDesde = java.time.temporal.ChronoUnit.DAYS.between(ultima, hoje);

                if (diasDesde <= 7) {
                    sb.append(String.format("- ✅ %s: realizado há %d dias%n", tipo, diasDesde));
                } else if (diasDesde <= 14) {
                    sb.append(String.format("- 🟡 %s: realizado há %d dias (considerar reintroduzir)%n", tipo, diasDesde));
                } else {
                    sb.append(String.format("- 🔴 %s: ausente há %d dias (REINTRODUZIR ESTA SEMANA)%n", tipo, diasDesde));
                }
            } else {
                sb.append(String.format("- ⚠️ %s: NUNCA realizado%n", tipo));
            }
        });
    }

    /**
     * Analisa volume semanal das últimas 3 semanas
     */
    private void analisarVolumeSemanal(List<TreinoRealizado> treinos, StringBuilder sb) {
        LocalDate hoje = LocalDate.now();

        // Calcular semanas (últimas 3)
        for (int semana = 0; semana < 3; semana++) {
            LocalDate fimSemana = hoje.minusWeeks(semana);
            LocalDate inicioSemana = fimSemana.minusDays(6); // Segunda a domingo

            BigDecimal volumeSemana = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSemana) &&
                               !t.getDataTreino().isAfter(fimSemana))
                    .map(t -> t.getDistanciaKm() != null ? t.getDistanciaKm() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int tssSemana = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSemana) &&
                               !t.getDataTreino().isAfter(fimSemana))
                    .mapToInt(t -> t.getTssCalculado() != null ? t.getTssCalculado() : 0)
                    .sum();

            long treinosSemana = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSemana) &&
                               !t.getDataTreino().isAfter(fimSemana))
                    .count();

            String tendencia = semana == 2 ? "📊 Base" : (semana == 1 ? "📈 Comparação" : "📊 Atual");
            sb.append(String.format("- **Semana %s (-7 dias):** %.1f km | %d TSS | %d treinos%n",
                    tendencia, volumeSemana.doubleValue(), tssSemana, treinosSemana));
        }
    }

    /**
     * Analisa distribuição de intensidade por zona (Z1-Z5)
     */
    private void analisarIntensidadeZonas(List<TreinoRealizado> treinos, StringBuilder sb) {
        // Calcular TSS por zona baseado em zonas alvo
        int tssZ1 = 0, tssZ2 = 0, tssZ3 = 0, tssZ4 = 0, tssZ5 = 0;
        int totalTss = 0;

        for (TreinoRealizado t : treinos) {
            Integer tss = t.getTssCalculado() != null ? t.getTssCalculado() : 0;
            totalTss += tss;

            String zona = t.getZonaAlvo() != null ? t.getZonaAlvo().toUpperCase() : "DESCONHECIDA";

            // Distribuir TSS conforme zona alvo
            if (zona.contains("Z1")) {
                tssZ1 += tss;
            } else if (zona.contains("Z2")) {
                tssZ2 += tss;
            } else if (zona.contains("Z3")) {
                tssZ3 += tss;
            } else if (zona.contains("Z4")) {
                tssZ4 += tss;
            } else if (zona.contains("Z5")) {
                tssZ5 += tss;
            }
        }

        // Calcular percentuais
        double pctZ1 = totalTss > 0 ? (tssZ1 * 100.0 / totalTss) : 0;
        double pctZ2 = totalTss > 0 ? (tssZ2 * 100.0 / totalTss) : 0;
        double pctZ3 = totalTss > 0 ? (tssZ3 * 100.0 / totalTss) : 0;
        double pctZ4 = totalTss > 0 ? (tssZ4 * 100.0 / totalTss) : 0;
        double pctZ5 = totalTss > 0 ? (tssZ5 * 100.0 / totalTss) : 0;

        sb.append(String.format("- **Z1 (Recuperação):** %.0f TSS (%.0f%%)%n", (double) tssZ1, pctZ1));
        sb.append(String.format("- **Z2 (Base Aeróbica):** %.0f TSS (%.0f%%) %s%n",
                (double) tssZ2, pctZ2, pctZ2 >= 50 ? "✅" : "⚠️"));
        sb.append(String.format("- **Z3 (Contínuo Moderado):** %.0f TSS (%.0f%%)%n", (double) tssZ3, pctZ3));
        sb.append(String.format("- **Z4 (Threshold):** %.0f TSS (%.0f%%)%n", (double) tssZ4, pctZ4));
        sb.append(String.format("- **Z5 (VO2max):** %.0f TSS (%.0f%%)%n", (double) tssZ5, pctZ5));
    }

    /**
     * Analisa sinais de sobrecarga: treinos cancelados, RPE elevado, etc
     */
    private void analisarSobreCarga(List<TreinoRealizado> treinos, StringBuilder sb) {
        long treinos14dias = treinos.stream()
                .filter(t -> !t.getDataTreino().isBefore(LocalDate.now().minusDays(14)))
                .count();

        double rpeMedia = treinos.stream()
                .filter(t -> t.getPercepcaoEsforco() != null)
                .mapToInt(TreinoRealizado::getPercepcaoEsforco)
                .average()
                .orElse(0);

        sb.append(String.format("- **Treinos nos últimos 14 dias:** %d%n", treinos14dias));
        sb.append(String.format("- **RPE médio:** %.1f/10 %s%n",
                rpeMedia,
                rpeMedia >= 7 ? "⚠️ (atleta relatando esforço elevado)" : "✅"));

        // Verificar padrão de treinos com esforço muito alto
        long treinosIntensivos = treinos.stream()
                .filter(t -> t.getPercepcaoEsforco() != null && t.getPercepcaoEsforco() >= 8)
                .count();

        if (treinosIntensivos > treinos14dias * 0.5) {
            sb.append("- 🔴 Mais de 50% dos treinos com RPE ≥8 (REDUZIR INTENSIDADE ESTA SEMANA)\\n");
        } else if (treinosIntensivos > treinos14dias * 0.3) {
            sb.append("- 🟡 Entre 30-50% com alta intensidade (monitorar sinais de fadiga)\\n");
        } else {
            sb.append("- ✅ Distribuição de intensidade adequada\\n");
        }
    }

    /**
     * Identifica a matriz de variabilidade de treinos (últimas 4 semanas)
     * e recomenda qual categoria de intervalado usar esta semana.
     *
     * Categorias de Intervalado:
     * - A: VO2max curto (200m, 400m, 600m)
     * - B: VO2max longo (3-5 min)
     * - C: Threshold (4-6 min no pace limiar)
     * - D: Tempo Run (contínuo em Z3)
     * - E: Fartlek estruturado (variado)
     *
     * Retorna string formatada com análise e recomendação
     */
    public String identificarMatrizVariabilidade(Atleta atleta, LocalDate inicioSemana) {
        LocalDate hoje = LocalDate.now();
        LocalDate quatroSemanas = hoje.minusWeeks(4);

        // Buscar treinos das últimas 4 semanas
        List<TreinoRealizado> treinos = treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(atleta, quatroSemanas);

        if (treinos.isEmpty()) {
            return """
                    **MATRIZ DE VARIABILIDADE:**

                    ⚠️ Nenhum treino realizado nos últimos 28 dias.
                    ✅ RECOMENDAÇÃO: Começar com INTERVALADO Categoria A ou B (VO2max)
                    """;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**MATRIZ DE VARIABILIDADE (últimas 4 semanas):**\n\n");

        // Analisar cada semana
        Map<Integer, String> categoriaPorSemana = new HashMap<>();
        Map<Integer, String> descricaoPorSemana = new HashMap<>();

        for (int semana = 0; semana < 4; semana++) {
            LocalDate fimSemana = hoje.minusWeeks(semana);
            LocalDate inicioSem = fimSemana.minusDays(6);

            // Encontrar treino intervalado dessa semana
            TreinoRealizado treinoIntervalado = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSem) &&
                               !t.getDataTreino().isAfter(fimSemana) &&
                               t.getTipoTreino() != null &&
                               (t.getTipoTreino().toString().contains("INTERVALADO") ||
                                t.getTipoTreino().toString().contains("FARTLEK")))
                    .findFirst()
                    .orElse(null);

            String categoria = "NENHUM";
            String descricao = "Sem intervalado";

            if (treinoIntervalado != null) {
                // Tentar identificar a categoria pela descrição ou observações
                categoria = identificarCategoriaIntervalado(treinoIntervalado);
                descricao = treinoIntervalado.getObservacao() != null ?
                        treinoIntervalado.getObservacao().substring(0, Math.min(50, treinoIntervalado.getObservacao().length())) :
                        "Intervalado";
            }

            categoriaPorSemana.put(semana, categoria);
            descricaoPorSemana.put(semana, descricao);

            String semanaLabel = switch (semana) {
                case 0 -> "Semana atual (hoje -0 dias)";
                case 1 -> "Semana passada (hoje -7 dias)";
                case 2 -> "2 semanas atrás (hoje -14 dias)";
                case 3 -> "3 semanas atrás (hoje -21 dias)";
                default -> "Semana " + semana;
            };

            sb.append(String.format("**Semana %d:** %s\n", semana, semanaLabel));
            sb.append(String.format("  └─ Categoria: %s | %s\n\n", categoria, descricao));
        }

        // Analisar padrão e fazer recomendação
        String recomendacao = recomendarCategoriaIntervalado(categoriaPorSemana);
        sb.append("✅ **RECOMENDAÇÃO PARA ESTA SEMANA:**\n");
        sb.append(recomendacao);

        // Avisos de repetição
        String avisos = gerarAvisosRepetidosIntervalados(categoriaPorSemana);
        if (!avisos.isEmpty()) {
            sb.append("\n⚠️ **AVISOS DE REPETIÇÃO:**\n");
            sb.append(avisos);
        }

        sb.append("\n---\n");
        return sb.toString();
    }

    /**
     * Tenta identificar a categoria de intervalado baseado na descrição ou tipo
     */
    private String identificarCategoriaIntervalado(TreinoRealizado treino) {
        String obs = treino.getObservacao() != null ? treino.getObservacao().toUpperCase() : "";
        String tipo = treino.getTipoTreino() != null ? treino.getTipoTreino().toString() : "";

        // Procurar por padrões na observação
        if (obs.contains("200M") || obs.contains("400M") || obs.contains("CURTO")) {
            return "A (VO2max curto)";
        } else if (obs.contains("3MIN") || obs.contains("4MIN") || obs.contains("5MIN") || obs.contains("LONGO")) {
            return "B (VO2max longo)";
        } else if (obs.contains("THRESHOLD") || obs.contains("LIMIAR") || obs.contains("4-6 MIN")) {
            return "C (Threshold)";
        } else if (obs.contains("TEMPO") || obs.contains("CONTÍNUO") || obs.contains("Z3")) {
            return "D (Tempo Run)";
        } else if (tipo.contains("FARTLEK") || obs.contains("FARTLEK") || obs.contains("VARIADO")) {
            return "E (Fartlek)";
        }

        // Se não conseguir identificar, retornar genérico
        return "Indeterminada";
    }

    /**
     * Recomenda qual categoria usar esta semana baseado no padrão das 4 semanas anteriores
     */
    private String recomendarCategoriaIntervalado(Map<Integer, String> categoriaPorSemana) {
        String categoriaUltimaSemanab = categoriaPorSemana.getOrDefault(1, "NENHUM");
        String categoriaSemanaAtual = categoriaPorSemana.getOrDefault(0, "NENHUM");

        // Lógica de rotação: alternar categorias, evitar repetir
        String recomendacao;

        if (categoriaSemanaAtual.equals("NENHUM") || categoriaSemanaAtual.equals("Indeterminada")) {
            // Se semana passada teve algo, alternar
            recomendacao = switch (categoriaUltimaSemanab) {
                case "A (VO2max curto)" -> "→ Use **Categoria B** (VO2max longo - 3-5 min)\n  Razão: Alternar com semana anterior (A→B)";
                case "B (VO2max longo)" -> "→ Use **Categoria C** (Threshold - 4-6 min)\n  Razão: Alternar com semana anterior (B→C)";
                case "C (Threshold)" -> "→ Use **Categoria D** (Tempo Run - Z3)\n  Razão: Alternar com semana anterior (C→D)";
                case "D (Tempo Run)" -> "→ Use **Categoria A** (VO2max curto - 200m/400m)\n  Razão: Alternar com semana anterior (D→A)";
                case "E (Fartlek)" -> "→ Use **Categoria A** (VO2max curto)\n  Razão: Alternar com Fartlek (E→A)";
                default -> "→ Use **Categoria A** (VO2max curto - 200m/400m)\n  Razão: Começar com base sólida";
            };
        } else {
            // Se semana atual teve intervalado, considerar incluir outra categoria
            recomendacao = switch (categoriaSemanaAtual) {
                case "A (VO2max curto)" -> "→ Considerou **Categoria B** (VO2max longo) na próxima\n  Razão: Progressão natural (A→B)";
                case "B (VO2max longo)" -> "→ Considerou **Categoria C** (Threshold) na próxima\n  Razão: Progressão natural (B→C)";
                case "C (Threshold)" -> "→ Considerou **Categoria D** (Tempo Run) na próxima\n  Razão: Progressão natural (C→D)";
                case "D (Tempo Run)" -> "→ Use **Categoria A** (VO2max curto)\n  Razão: Volta ao ciclo (D→A)";
                case "E (Fartlek)" -> "→ Use **Categoria B** (VO2max longo)\n  Razão: Alternar após Fartlek (E→B)";
                default -> "→ Use **Categoria A** (VO2max curto)\n  Razão: Recomendação padrão";
            };
        }

        return recomendacao;
    }

    /**
     * Gera avisos se houver repetição de mesma categoria em semanas consecutivas
     */
    private String gerarAvisosRepetidosIntervalados(Map<Integer, String> categoriaPorSemana) {
        StringBuilder avisos = new StringBuilder();

        String categoriaSemanaAtual = categoriaPorSemana.getOrDefault(0, "NENHUM");
        String categoriaUltimaSemanab = categoriaPorSemana.getOrDefault(1, "NENHUM");

        // Aviso 1: Mesma categoria repetida
        if (!categoriaSemanaAtual.equals("NENHUM") &&
            !categoriaSemanaAtual.equals("Indeterminada") &&
            categoriaSemanaAtual.equals(categoriaUltimaSemanab)) {
            avisos.append(String.format("🟡 Mesma categoria (%s) em 2 semanas consecutivas\n", categoriaSemanaAtual));
            avisos.append("   → MUDAR para outra categoria esta semana\n\n");
        }

        // Aviso 2: Categoria ausente há muito tempo
        boolean tempoJaAusente = false;
        for (int i = 0; i < 4; i++) {
            String cat = categoriaPorSemana.getOrDefault(i, "NENHUM");
            if (!cat.equals("NENHUM") && !cat.equals("Indeterminada")) {
                tempoJaAusente = true;
                break;
            }
        }

        if (!tempoJaAusente && categoriaPorSemana.values().stream()
                .noneMatch(c -> c.equals("NENHUM") || c.equals("Indeterminada"))) {
            // Todas as semanas tiveram alguma coisa
            avisos.append("✅ Boa rotação de categorias observada nos últimos 28 dias\n");
        }

        return avisos.toString();
    }

    /**
     * Gera alertas de variabilidade de treinos (repetição de estímulos, gaps, etc)
     * para serem incluídos no prompt otimizado.
     *
     * Verifica:
     * 1. Tipos de treino não realizados há >14 dias
     * 2. Mesma categoria de intervalado repetida 2+ semanas
     * 3. Gaps grandes sem estímulo específico
     * 4. Padrões de repetição desnecessária
     */
    public String gerarAlertasVariabilidade(Atleta atleta, LocalDate inicioSemana) {
        LocalDate hoje = LocalDate.now();
        LocalDate quatroSemanas = hoje.minusWeeks(4);

        // Buscar treinos das últimas 4 semanas
        List<TreinoRealizado> treinos = treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(atleta, quatroSemanas);

        StringBuilder alertas = new StringBuilder();
        boolean temAlertas = false;

        if (treinos.isEmpty()) {
            return "✅ Sem alertas de variabilidade (nenhum treino realizado).\n";
        }

        // ALERTA 1: Tipos de treino ausentes há >14 dias
        alertas.append("### Estímulos Ausentes Há Mais de 14 Dias\n");
        Map<String, LocalDate> ultimaDataPorTipo = extrairUltimaDataPorTipo(treinos);
        List<String> tiposEsperados = Arrays.asList(
                "REGENERATIVO", "CONTINUO", "INTERVALADO", "LONGO", "TEMPO_RUN", "FARTLEK"
        );

        for (String tipo : tiposEsperados) {
            if (!ultimaDataPorTipo.containsKey(tipo)) {
                long diasDesde = ChronoUnit.DAYS.between(hoje.minusYears(1), hoje); // Nunca feito
                alertas.append(String.format("🔴 **%s:** NUNCA realizado - REINTRODUZIR URGENTEMENTE\n", tipo));
                temAlertas = true;
            } else {
                LocalDate ultimaData = ultimaDataPorTipo.get(tipo);
                long diasDesde = ChronoUnit.DAYS.between(ultimaData, hoje);

                if (diasDesde > 14 && diasDesde <= 21) {
                    alertas.append(String.format("🟡 **%s:** Ausente há %d dias (considerar reintroduzir)\n", tipo, diasDesde));
                    temAlertas = true;
                } else if (diasDesde > 21) {
                    alertas.append(String.format("🔴 **%s:** Ausente há %d dias (REINTRODUZIR ESTA SEMANA)\n", tipo, diasDesde));
                    temAlertas = true;
                }
            }
        }

        if (!temAlertas) {
            alertas.append("✅ Nenhum estímulo ausente há >14 dias\n");
        }

        // ALERTA 2: Repetição de mesma categoria de intervalado
        alertas.append("\n### Repetição de Categorias de Intervalado\n");
        Map<Integer, String> categoriaPorSemana = extrairCategoriasPorSemana(treinos);
        boolean temRepetidos = false;

        String cat0 = categoriaPorSemana.getOrDefault(0, "NENHUM");
        String cat1 = categoriaPorSemana.getOrDefault(1, "NENHUM");
        String cat2 = categoriaPorSemana.getOrDefault(2, "NENHUM");

        if (!cat0.equals("NENHUM") && cat0.equals(cat1)) {
            alertas.append(String.format("🟡 **Categoria repetida:** %s está sendo usada 2 semanas consecutivas\n", cat0));
            alertas.append("   → Recomendação: ALTERNAR para outra categoria esta semana\n");
            temRepetidos = true;
        }

        if (!cat1.equals("NENHUM") && cat1.equals(cat2)) {
            alertas.append(String.format("⚠️ **Padrão observado:** %s também foi usado há 2 semanas\n", cat1));
            temRepetidos = true;
        }

        if (!temRepetidos && !cat0.equals("NENHUM")) {
            alertas.append("✅ Boa rotação entre categorias de intervalado\n");
        }

        // ALERTA 3: Gaps entre treinos de qualidade
        alertas.append("\n### Frequência de Treinos Intensivos\n");
        long treinosIntensivos = treinos.stream()
                .filter(t -> t.getTipoTreino() != null &&
                           (t.getTipoTreino().toString().contains("INTERVALADO") ||
                            t.getTipoTreino().toString().contains("TEMPO_RUN") ||
                            t.getTipoTreino().toString().contains("FARTLEK")))
                .count();

        long totalTreinos = treinos.size();
        double pctIntensivos = totalTreinos > 0 ? (treinosIntensivos * 100.0 / totalTreinos) : 0;

        if (pctIntensivos < 15) {
            alertas.append(String.format("🟡 Baixa frequência de treinos intensivos (%.0f%% dos treinos)\n", pctIntensivos));
            alertas.append("   → Considerar aumentar frequência de intervalados/tempo runs\n");
        } else if (pctIntensivos > 40) {
            alertas.append(String.format("🟡 Alta frequência de treinos intensivos (%.0f%% dos treinos)\n", pctIntensivos));
            alertas.append("   → Considerar aumentar treinos regenerativos para equilíbrio\n");
        } else {
            alertas.append(String.format("✅ Frequência adequada de intensivos (%.0f%% dos treinos)\n", pctIntensivos));
        }

        // ALERTA 4: Variabilidade geral
        alertas.append("\n### Variabilidade Geral de Treinos\n");
        long tiposDiferentes = ultimaDataPorTipo.keySet().stream()
                .filter(tipo -> ultimaDataPorTipo.containsKey(tipo))
                .count();

        if (tiposDiferentes >= 5) {
            alertas.append("✅ Excelente variabilidade - treinos de múltiplos tipos sendo realizados\n");
        } else if (tiposDiferentes >= 3) {
            alertas.append("🟡 Variabilidade moderada - considerar incluir mais tipos de treino\n");
        } else {
            alertas.append("🔴 Variabilidade baixa - apenas " + tiposDiferentes + " tipo(s) de treino realizado(s)\n");
            alertas.append("   → Adicionar treinos regenerativos e/ou variações de intensidade\n");
        }

        return alertas.toString();
    }

    /**
     * Extrai a última data de cada tipo de treino
     */
    private Map<String, LocalDate> extrairUltimaDataPorTipo(List<TreinoRealizado> treinos) {
        Map<String, LocalDate> mapa = new HashMap<>();

        treinos.forEach(t -> {
            String tipo = t.getTipoTreino() != null ? t.getTipoTreino().toString() : "DESCONHECIDO";
            LocalDate ultimaData = mapa.getOrDefault(tipo, t.getDataTreino());

            if (t.getDataTreino().isAfter(ultimaData)) {
                mapa.put(tipo, t.getDataTreino());
            } else {
                mapa.put(tipo, ultimaData);
            }
        });

        return mapa;
    }

    /**
     * Extrai a categoria de intervalado para cada semana
     */
    private Map<Integer, String> extrairCategoriasPorSemana(List<TreinoRealizado> treinos) {
        Map<Integer, String> categorias = new HashMap<>();
        LocalDate hoje = LocalDate.now();

        for (int semana = 0; semana < 4; semana++) {
            LocalDate fimSemana = hoje.minusWeeks(semana);
            LocalDate inicioSem = fimSemana.minusDays(6);

            TreinoRealizado treinoIntervalado = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSem) &&
                               !t.getDataTreino().isAfter(fimSemana) &&
                               t.getTipoTreino() != null &&
                               (t.getTipoTreino().toString().contains("INTERVALADO") ||
                                t.getTipoTreino().toString().contains("FARTLEK")))
                    .findFirst()
                    .orElse(null);

            if (treinoIntervalado != null) {
                String categoria = identificarCategoriaIntervalado(treinoIntervalado);
                categorias.put(semana, categoria);
            } else {
                categorias.put(semana, "NENHUM");
            }
        }

        return categorias;
    }

    /**
     * Gera instruções detalhadas de recuperação/regeneração baseadas no estado atual do atleta
     */
    public String detalharRecuperacao(Atleta atleta, PlanoMetaDados metaDados, LocalDate inicioSemana) {
        LocalDate hoje = LocalDate.now();
        LocalDate umaSemana = hoje.minusWeeks(1);

        List<TreinoRealizado> trenosUltimaSemana = treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(atleta, umaSemana);

        StringBuilder recuperacao = new StringBuilder();
        recuperacao.append("**INSTRUÇÕES DE RECUPERAÇÃO/REGENERAÇÃO:**\n\n");

        // Dados fisiológicos
        Integer fcRepouso = atleta.getFcRepouso() != null ? atleta.getFcRepouso() : 60;
        Integer fcLimiar = atleta.getFcLimiarCalculada() != null ? atleta.getFcLimiarCalculada() : 150;
        Integer fcMax = atleta.getFcMaxima() != null ? atleta.getFcMaxima() : 200;

        // Zona Z1 (Regenerativa): 50-60% FCMax
        int fcZ1Min = Math.round(fcMax * 0.50f);
        int fcZ1Max = Math.round(fcMax * 0.60f);

        // TSB e estado de fadiga
        Double tsb = metaDados.getTsbAtual() != null ? metaDados.getTsbAtual() : 0.0;
        Double atl = metaDados.getAtlAtual() != null ? metaDados.getAtlAtual() : 0.0;
        Double ctl = metaDados.getCtlAtual() != null ? metaDados.getCtlAtual() : 0.0;
        Double rampRate = metaDados.getRampRateAtual() != null ? metaDados.getRampRateAtual() : 0.0;

        // Determinar nível de fadiga
        String nivelFadiga = determinaNivelFadiga(tsb, atl, ctl);
        String statusRecuperacao = determinaStatusRecuperacao(tsb.intValue());

        recuperacao.append("### 1. Estado de Recuperação\n");
        recuperacao.append(String.format("- **TSB:** %.0f (Status: %s)\n", tsb, statusRecuperacao));
        recuperacao.append(String.format("- **Fadiga (ATL):** %.1f (Nível: %s)\n", atl, nivelFadiga));
        recuperacao.append(String.format("- **Forma (CTL):** %.1f\n", ctl));
        recuperacao.append(String.format("- **Ramp Rate:** %.1f%% (Progressão: %s)\n", rampRate,
                rampRate > 10 ? "AGRESSIVA ⚠️" : (rampRate > 5 ? "MODERADA" : "CONSERVADORA")));
        recuperacao.append("\n");

        recuperacao.append("### 2. Treinos Regenerativos Recomendados\n");
        gerarTreinosRegenerativos(recuperacao, fcZ1Min, fcZ1Max, fcRepouso, nivelFadiga);
        recuperacao.append("\n");

        recuperacao.append("### 3. Parâmetros Técnicos para Zona Z1\n");
        gerarParametrosTecnicos(recuperacao, atleta, fcZ1Min, fcZ1Max, fcRepouso);
        recuperacao.append("\n");

        recuperacao.append("### 4. Recuperação Ativa\n");
        gerarRecuperacaoAtiva(recuperacao, nivelFadiga);
        recuperacao.append("\n");

        recuperacao.append("### 5. Recomendações de Sono e Nutrição\n");
        gerarRecomendacoesSonoNutrição(recuperacao, tsb.intValue(), atl);

        return recuperacao.toString();
    }

    /**
     * Determina o nível de fadiga baseado em ATL
     */
    private String determinaNivelFadiga(Double tsb, Double atl, Double ctl) {
        if (tsb < -10) {
            return "CRÍTICA 🔴 (Overtraining)";
        } else if (tsb < -5) {
            return "ALTA ⚠️ (Muito Cansado)";
        } else if (tsb < 5) {
            return "MODERADA 🟡 (Preparado)";
        } else {
            return "BAIXA ✅ (Fresco)";
        }
    }

    /**
     * Determina o status de recuperação baseado em TSB
     */
    private String determinaStatusRecuperacao(Integer tsb) {
        if (tsb >= 25) {
            return "COMPLETAMENTE RECUPERADO (Risco: perda de forma)";
        } else if (tsb >= 10) {
            return "MUITO BEM RECUPERADO (Ótimo para treinos duro)";
        } else if (tsb >= 5) {
            return "BEM RECUPERADO (Bom para sessões mistas)";
        } else if (tsb >= -5) {
            return "MODERADAMENTE RECUPERADO (Recomendado regenerativo)";
        } else if (tsb >= -10) {
            return "POUCO RECUPERADO (Enfatizar recuperação)";
        } else {
            return "CRITICAMENTE FATIGADO (Descanso completo recomendado)";
        }
    }

    /**
     * Gera recomendações de treinos regenerativos
     */
    private void gerarTreinosRegenerativos(StringBuilder sb, int fcZ1Min, int fcZ1Max, int fcRepouso, String nivelFadiga) {
        if (nivelFadiga.contains("CRÍTICA") || nivelFadiga.contains("ALTA")) {
            sb.append("**PRIORIDADE: Descanso completo ou treino muito leve**\n\n");
            sb.append("- **Caminhada fácil (20-30 min)**: FC = ").append(fcRepouso).append("-").append(fcZ1Min).append(" bpm\n");
            sb.append("  - Ritmo conversável, muito leve\n");
            sb.append("  - Objetivo: movimento leve, não treino\n\n");
            sb.append("- **Natação regenerativa (20-30 min)**: FC = ").append(fcRepouso).append("-").append(fcZ1Min).append(" bpm\n");
            sb.append("  - Nado leve contínuo ou alternado com flutuação\n");
            sb.append("  - Objetivo: recuperação ativa do sistema aeróbico\n\n");
        } else if (nivelFadiga.contains("MODERADA")) {
            sb.append("**RECOMENDAÇÃO: Treinos Z1 com duração moderada**\n\n");
            sb.append("- **Corrida Fácil Curta (30-40 min)**: FC = ").append(fcZ1Min).append("-").append(fcZ1Max).append(" bpm\n");
            sb.append("  - Conversa fácil mantida, ritmo muito relaxado\n\n");
            sb.append("- **Corrida Fácil com Estímulos (40 min total)**:\n");
            sb.append("  - 10 min aquecimento em Z1\n");
            sb.append("  - 4-6 x [1 min em Z2 + 2-3 min em Z1]\n");
            sb.append("  - 5 min arremate em Z1\n\n");
        } else {
            sb.append("**RECOMENDAÇÃO: Treinos regenerativos estruturados**\n\n");
            sb.append("- **Corrida Longa Fácil (50-70 min)**: FC = ").append(fcZ1Min).append("-").append(fcZ1Max).append(" bpm\n");
            sb.append("  - Base aeróbica com foco em durabilidade\n\n");
            sb.append("- **Corrida com Variações (45-60 min)**:\n");
            sb.append("  - 10 min aquecimento em Z1\n");
            sb.append("  - 25-35 min em Z2 (conversável com esforço leve)\n");
            sb.append("  - 10 min arremate em Z1\n\n");
        }
    }

    /**
     * Gera parâmetros técnicos para treinos regenerativos
     */
    private void gerarParametrosTecnicos(StringBuilder sb, Atleta atleta, int fcZ1Min, int fcZ1Max, int fcRepouso) {
        String paceLimiar = atleta.getPaceLimiar() != null ? atleta.getPaceLimiar().toString() : "5:30";

        // Estimar pace Z1 (aproximadamente 70-80% do pace limiar)
        // Se limiar é 5:30, Z1 seria ~7:00-7:30 por km
        String paceZ1Estimado = "~7:00-7:30";

        sb.append("**Zona Z1 (Regenerativa)**:\n");
        sb.append(String.format("- FC Alvo: %d-%d bpm (50-60%% da FCMax)\n", fcZ1Min, fcZ1Max));
        sb.append(String.format("- Pace Estimado: %s /km\n", paceZ1Estimado));
        sb.append("- RPE (Escala 1-10): 2-3 (Muito Fácil)\n");
        sb.append("- Respiração: Nasal, leve, ritmo natural\n");
        sb.append("- Conversa: Conversa fácil, sem ofegância\n");
        sb.append("- Sensação: Leve, movimento fluido, sem tensão\n\n");

        sb.append("**Zona Z2 (Base Aeróbica)**:\n");
        sb.append(String.format("- FC Alvo: %d-%d bpm (60-70%% da FCMax)\n", fcZ1Max, Math.round(atleta.getFcMaxima() * 0.70f)));
        sb.append("- Pace Estimado: ~6:30-7:00 /km\n");
        sb.append("- RPE: 3-4 (Fácil com esforço moderado)\n");
        sb.append("- Respiração: Nasal predominante, ritmo controlado\n");
        sb.append("- Conversa: Conversa possível com pequenos ajustes respiratórios\n");
    }

    /**
     * Gera recomendações de recuperação ativa
     */
    private void gerarRecuperacaoAtiva(StringBuilder sb, String nivelFadiga) {
        if (nivelFadiga.contains("CRÍTICA") || nivelFadiga.contains("ALTA")) {
            sb.append("- **Alongamento dinâmico** (10 min): Movimentos suaves, sem força\n");
            sb.append("- **Espuma/Auto-massagem**: Apenas nas áreas de tensão, muito suave\n");
            sb.append("- **Yoga regenerativo** (15-20 min): Foco em respiração e mobilidade\n");
            sb.append("- **Descanso passivo**: Elevar pernas, relaxamento muscular ativo\n");
        } else if (nivelFadiga.contains("MODERADA")) {
            sb.append("- **Alongamento estático** (15 min): Pós-treino, mantendo 20-30s cada\n");
            sb.append("- **Espuma de rolagem** (10 min): Quadríceps, glúteos, panturrilhas, TFL\n");
            sb.append("- **Banho frio/morno** (5-10 min): Promove vasodilatação e circulação\n");
            sb.append("- **Mobilidade dinâmica** (10 min): Squats, lunges, rotações\n");
        } else {
            sb.append("- **Alongamento completo** (20 min): Trabalho de flexibilidade\n");
            sb.append("- **Espuma de rolagem agressiva** (15 min): Trabalho miofascial profundo\n");
            sb.append("- **Contraste de temperatura** (água quente/fria): Estímulo à circulação\n");
            sb.append("- **Foam rolling + alongamento** (20 min): Sessão combinada de mobilidade\n");
        }
    }

    /**
     * Gera recomendações de sono e nutrição
     */
    private void gerarRecomendacoesSonoNutrição(StringBuilder sb, Integer tsb, Double atl) {
        sb.append("**Sono**:\n");
        if (tsb < -5) {
            sb.append("- Prioridade máxima: 8-9h por noite\n");
            sb.append("- Cochilos: Sim, 20-30 min entre 14:00-16:00\n");
            sb.append("- Higiene do sono: Escuro, silencioso, fresco (18-19°C)\n");
        } else if (tsb < 5) {
            sb.append("- Alvo: 7-8h por noite\n");
            sb.append("- Cochilos: Opcional, se sentir fadiga\n");
        } else {
            sb.append("- Alvo: 7h por noite (risco de recuperação excessiva)\n");
            sb.append("- Cochilos: Evitar excesso\n");
        }

        sb.append("\n**Nutrição**:\n");
        if (atl != null && atl > 100) {
            sb.append("- Aumentar ingestão: +200-300 kcal/dia\n");
            sb.append("- Foco em: Carboidratos (50-60%), Proteína (1.2-1.6g/kg)\n");
            sb.append("- Hidratação: Mínimo 3L/dia\n");
            sb.append("- Minerais: Magnésio (abacate, banana, chocolate), Zinco (carne, ovos)\n");
        } else {
            sb.append("- Manutenção calórica normal\n");
            sb.append("- Equilíbrio: 50% CHO, 20% Proteína, 30% Gordura\n");
            sb.append("- Hidratação: 2.5-3L/dia\n");
        }
    }

    /**
     * Constrói o prompt otimizado usando o novo template plano-treino-otimizado-claude.txt
     * Orquestra todos os métodos de análise pré-planejamento para criar um contexto completo
     */
    public String buildOptimizedPrompt(Atleta atleta, PlanoMetaDados metaDados, Prova provaAlvo, LocalDate inicioSemana) {

        // 1. Dados básicos do atlevar provas = formatarProvas(atleta, provaAlvo);

        // 2. Análises de pré-planejamento - NOVOS MÉTODOS
        var analiseEstimulos = analisarEstimulosRecentes(atleta, inicioSemana);
        var volumeUltimas3Semanas = calcularVolumeMedioUltimasTresSemanas(atleta);
        var matrizVariabilidade = identificarMatrizVariabilidade(atleta, inicioSemana);
        var alertasVariabilidade = gerarAlertasVariabilidade(atleta, inicioSemana);
        var instrucoesRecuperacao = detalharRecuperacao(atleta, metaDados, inicioSemana);
        var provas = formatarProvas(atleta, provaAlvo);

        // 3. Compilar todo o contexto em um histórico completo
        StringBuilder historicoCompleto = new StringBuilder();
        historicoCompleto.append(formatarHistoricoTreinos(atleta)).append("\n\n");
        historicoCompleto.append(formatarMetricas(metaDados)).append("\n\n");
        historicoCompleto.append(analiseEstimulos).append("\n\n");
        historicoCompleto.append("## VOLUME MÉDIO DAS ÚLTIMAS 3 SEMANAS\n");
        historicoCompleto.append(String.format("- **Volume Médio:** %.1f km\n", volumeUltimas3Semanas.get("volumeMedioKm")));
        historicoCompleto.append(String.format("- **Tendência:** %s\n", volumeUltimas3Semanas.get("tendencia")));
        historicoCompleto.append(String.format("- **Semana Mais Recente:** %.1f km\n\n", volumeUltimas3Semanas.get("volumeSemanaMaisRecente")));
        historicoCompleto.append(matrizVariabilidade).append("\n");
        historicoCompleto.append(alertasVariabilidade).append("\n");
        historicoCompleto.append(instrucoesRecuperacao);

        // 4. Fallbacks para dados nulos
        String diaPreferidoLongo = atleta.getDiaPreferidoLongo() != null ?
                atleta.getDiaPreferidoLongo().toString() : "SABADO";

        // 5. Carregar e formatar o novo template otimizado (apenas com os 9 placeholders existentes)
        return templateLoader.loadAndFormat(
                "plano-treino-otimizado-claude.txt",
                atleta.getNome(),                                                                              // %s - Nome
                atleta.getIdade(),                                                                             // %d - Idade
                atleta.getObjetivo() != null ? atleta.getObjetivo() : "Melhorar condicionamento",             // %s - Objetivo
                atleta.getNivelExperiencia() != null ? atleta.getNivelExperiencia().toString() : "INTERMEDIARIO", // %s - Experiência
                formatarDias(atleta.getDiasDisponiveis()),                                                     // %s - Dias disponíveis
                diaPreferidoLongo,                                                                             // %s - Dia preferido longo
                provas,                                                                                        // %s - Provas
                historicoCompleto.toString(),                                                                  // %s - Histórico completo com todas as análises
                atleta.getObjetivo() != null ? atleta.getObjetivo() : "Melhorar condicionamento"              // %s - Objetivo (repetido para linha 148)
        );
    }

    /**
     * Calcula volume médio das últimas 3 semanas com análise de tendência
     * Retorna Map com dados estruturados para uso no prompt otimizado
     */
    public Map<String, Object> calcularVolumeMedioUltimasTresSemanas(Atleta atleta) {
        LocalDate hoje = LocalDate.now();
        Map<String, Object> resultado = new HashMap<>();

        // Buscar treinos das últimas 3 semanas
        LocalDate tresSemanas = hoje.minusWeeks(3);
        List<TreinoRealizado> treinos = treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(atleta, tresSemanas);

        // Se não há treinos, retornar zeros
        if (treinos.isEmpty()) {
            resultado.put("volumeMedioKm", 0.0);
            resultado.put("volumeMinimoKm", 0.0);
            resultado.put("volumeMaximoKm", 0.0);
            resultado.put("tendencia", "SEM DADOS");
            resultado.put("tssMedioPorSemana", 0);
            resultado.put("treinosPorSemana", 0.0);
            resultado.put("volumeSemanaMaisRecente", 0.0);
            resultado.put("volumeSemanaAnterior", 0.0);
            resultado.put("volumeDuasSemanas", 0.0);
            return resultado;
        }

        // Calcular volume para cada semana (últimas 3)
        List<Double> volumesPorSemana = new ArrayList<>();
        List<Integer> tssPorSemana = new ArrayList<>();
        List<Long> treinosPorSemana = new ArrayList<>();

        for (int semana = 0; semana < 3; semana++) {
            LocalDate fimSemana = hoje.minusWeeks(semana);
            LocalDate inicioSemana = fimSemana.minusDays(6); // Segunda a domingo

            // Filtrar treinos desta semana
            List<TreinoRealizado> treinosSemana = treinos.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSemana) &&
                               !t.getDataTreino().isAfter(fimSemana))
                    .collect(Collectors.toList());

            // Volume total da semana
            BigDecimal volumeSemana = treinosSemana.stream()
                    .map(t -> t.getDistanciaKm() != null ? t.getDistanciaKm() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // TSS total da semana
            int tssSemana = treinosSemana.stream()
                    .mapToInt(t -> t.getTssCalculado() != null ? t.getTssCalculado() : 0)
                    .sum();

            // Contar treinos
            long contTreinos = treinosSemana.size();

            volumesPorSemana.add(volumeSemana.doubleValue());
            tssPorSemana.add(tssSemana);
            treinosPorSemana.add(contTreinos);
        }

        // Calcular volume total e média
        double volumeTotal = volumesPorSemana.stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        double volumeMedio = volumeTotal / 3.0;

        // Encontrar mínimo e máximo
        double volumeMinimo = volumesPorSemana.stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(0.0);
        double volumeMaximo = volumesPorSemana.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        // Calcular TSS médio por semana
        int tssMedioSemanal = (int) tssPorSemana.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        // Calcular treinos médios por semana
        double treinosMedioSemanal = treinosPorSemana.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        // Determinar tendência (CRESCENTE, DECRESCENTE, VARIÁVEL, ESTÁVEL)
        String tendencia = "ESTÁVEL";
        double semanaMaisRecente = volumesPorSemana.get(0);
        double semanaAnterior = volumesPorSemana.get(1);
        double duasSemanas = volumesPorSemana.get(2);

        if (semanaMaisRecente > semanaAnterior && semanaAnterior > duasSemanas) {
            tendencia = "CRESCENTE";
        } else if (semanaMaisRecente < semanaAnterior && semanaAnterior < duasSemanas) {
            tendencia = "DECRESCENTE";
        } else if (Math.abs(semanaMaisRecente - semanaAnterior) > 10 ||
                   Math.abs(semanaAnterior - duasSemanas) > 10) {
            tendencia = "VARIÁVEL";
        } else {
            tendencia = "ESTÁVEL";
        }

        // Montar resultado
        resultado.put("volumeMedioKm", Math.round(volumeMedio * 10.0) / 10.0);
        resultado.put("volumeMinimoKm", Math.round(volumeMinimo * 10.0) / 10.0);
        resultado.put("volumeMaximoKm", Math.round(volumeMaximo * 10.0) / 10.0);
        resultado.put("tendencia", tendencia);
        resultado.put("tssMedioPorSemana", tssMedioSemanal);
        resultado.put("treinosPorSemana", Math.round(treinosMedioSemanal * 10.0) / 10.0);
        resultado.put("volumeSemanaMaisRecente", Math.round(semanaMaisRecente * 10.0) / 10.0);
        resultado.put("volumeSemanaAnterior", Math.round(semanaAnterior * 10.0) / 10.0);
        resultado.put("volumeDuasSemanas", Math.round(duasSemanas * 10.0) / 10.0);

        return resultado;
    }
}