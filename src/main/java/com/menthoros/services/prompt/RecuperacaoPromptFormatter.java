package com.menthoros.services.prompt;

import com.menthoros.entity.Atleta;
import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.enums.FaixaTsb;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Formatador de instruções de recuperação e regeneração para prompts de IA.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Detalhamento de instruções de recuperação baseadas em fadiga</li>
 *   <li>Treinos regenerativos recomendados por nível de fadiga</li>
 *   <li>Parâmetros técnicos para zonas Z1/Z2</li>
 *   <li>Recomendações de recuperação ativa, sono e nutrição</li>
 * </ul>
 */
@Component
public class RecuperacaoPromptFormatter {

    /**
     * Gera instruções completas de recuperação/regeneração.
     * Recebe treinos pré-carregados (sem acesso a repository).
     */
    public String detalharRecuperacao(Atleta atleta, PlanoMetaDados metaDados,
                                       List<TreinoRealizado> treinosUltimaSemana) {
        if (atleta == null) {
            return "⚠️ Atleta não informado. Não foi possível gerar instruções de recuperação.";
        }

        StringBuilder recuperacao = new StringBuilder();
        recuperacao.append("**INSTRUÇÕES DE RECUPERAÇÃO/REGENERAÇÃO:**\n\n");

        Integer fcRepouso = atleta.getFcRepouso() != null ? atleta.getFcRepouso() : 60;
        Integer fcMax = atleta.getFcMaximaComFallback();

        int fcZ1Min = Math.round(fcMax * 0.50f);
        int fcZ1Max = Math.round(fcMax * 0.60f);

        Double tsb = metaDados != null && metaDados.getTsbAtual() != null ? metaDados.getTsbAtual() : 0.0;
        Double atl = metaDados != null && metaDados.getAtlAtual() != null ? metaDados.getAtlAtual() : 0.0;
        Double ctl = metaDados != null && metaDados.getCtlAtual() != null ? metaDados.getCtlAtual() : 0.0;
        Double rampRate = metaDados != null && metaDados.getRampRateAtual() != null ? metaDados.getRampRateAtual() : 0.0;

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
        gerarRecomendacoesSonoNutricao(recuperacao, tsb.intValue(), atl);

        return recuperacao.toString();
    }

    public String determinaNivelFadiga(Double tsb, Double atl, Double ctl) {
        FaixaTsb faixa = FaixaTsb.classificar(tsb);
        if (faixa == null) return "Sem dados";

        return switch (faixa) {
            case FADIGA_EXCESSIVA -> "CRÍTICA 🔴 (Overtraining)";
            case FADIGA_ALTA -> "ALTA ⚠️ (Muito Cansado)";
            case FADIGA_MODERADA, ACUMULANDO_FADIGA, FATIGADO -> "MODERADA 🟡 (Preparado)";
            case RECUPERANDO, FORMA_IDEAL, DESCANSADO, MUITO_DESCANSADO -> "BAIXA ✅ (Fresco)";
        };
    }

    public String determinaStatusRecuperacao(Integer tsb) {
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

    private void gerarParametrosTecnicos(StringBuilder sb, Atleta atleta, int fcZ1Min, int fcZ1Max, int fcRepouso) {
        sb.append("**Zona Z1 (Regenerativa)**:\n");
        sb.append(String.format("- FC Alvo: %d-%d bpm (50-60%% da FCMax)\n", fcZ1Min, fcZ1Max));
        sb.append("- Pace Estimado: ~7:00-7:30 /km\n");
        sb.append("- RPE (Escala 1-10): 2-3 (Muito Fácil)\n");
        sb.append("- Respiração: Nasal, leve, ritmo natural\n");
        sb.append("- Conversa: Conversa fácil, sem ofegância\n");
        sb.append("- Sensação: Leve, movimento fluido, sem tensão\n\n");

        Integer fcMaxima = atleta.getFcMaxima();
        sb.append("**Zona Z2 (Base Aeróbica)**:\n");
        sb.append(String.format("- FC Alvo: %d-%d bpm (60-70%% da FCMax)\n",
                fcZ1Max, fcMaxima != null ? Math.round(fcMaxima * 0.70f) : fcZ1Max + 20));
        sb.append("- Pace Estimado: ~6:30-7:00 /km\n");
        sb.append("- RPE: 3-4 (Fácil com esforço moderado)\n");
        sb.append("- Respiração: Nasal predominante, ritmo controlado\n");
        sb.append("- Conversa: Conversa possível com pequenos ajustes respiratórios\n");
    }

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

    private void gerarRecomendacoesSonoNutricao(StringBuilder sb, Integer tsb, Double atl) {
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
}
