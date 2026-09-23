package br.com.menthoros.backend.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.dto.output.EtapaTreinoDto;
import br.com.menthoros.backend.enums.DiaSemana;
import br.com.menthoros.backend.enums.TipoTreino;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TreinoPlanejadoLlmDto(
        String diaSemana,
        String tipoTreino,
        String fcAlvo,
        Integer tssPlanejado,
        Double intensidadePlanejada,
        Integer percepcaoEsforcoEsperada,
        String justificativaIa,
        String duracaoMin,
        Double distanciaKm,
        String ritmoAlvo,
        List<EtapaTreinoLlmDto> etapas,
        // Os três a seguir nunca vêm do LLM (prova-no-plano-semanal): ProvaNoPlanoService os
        // preenche ao construir o TreinoPlanejadoLlmDto do tipo PROVA, para o TreinoMapper copiar
        // descrição, zona alvo e o vínculo direto para a entidade.
        String descricao,
        String zonaAlvo,
        UUID provaId
) {
    // Overload de compatibilidade: preserva as ~20 chamadas existentes (testes e pipeline de
    // normalização da LLM) que nunca lidam com prova. Os três campos novos nascem nulos.
    public TreinoPlanejadoLlmDto(String diaSemana, String tipoTreino, String fcAlvo,
                                  Integer tssPlanejado, Double intensidadePlanejada,
                                  Integer percepcaoEsforcoEsperada, String justificativaIa,
                                  String duracaoMin, Double distanciaKm, String ritmoAlvo,
                                  List<EtapaTreinoLlmDto> etapas) {
        this(diaSemana, tipoTreino, fcAlvo, tssPlanejado, intensidadePlanejada,
                percepcaoEsforcoEsperada, justificativaIa, duracaoMin, distanciaKm, ritmoAlvo,
                etapas, null, null, null);
    }

    // Withers (pipeline-normalizacao-treino, seção 1): copiam SEMPRE pelo construtor canônico de 14
    // campos — o overload de 11 acima zeraria descricao/zonaAlvo/provaId em silêncio.

    public TreinoPlanejadoLlmDto comEtapas(List<EtapaTreinoLlmDto> novasEtapas) {
        return new TreinoPlanejadoLlmDto(diaSemana, tipoTreino, fcAlvo, tssPlanejado, intensidadePlanejada,
                percepcaoEsforcoEsperada, justificativaIa, duracaoMin, distanciaKm, ritmoAlvo,
                novasEtapas, descricao, zonaAlvo, provaId);
    }

    public TreinoPlanejadoLlmDto comRitmo(String novoRitmoAlvo) {
        return new TreinoPlanejadoLlmDto(diaSemana, tipoTreino, fcAlvo, tssPlanejado, intensidadePlanejada,
                percepcaoEsforcoEsperada, justificativaIa, duracaoMin, distanciaKm, novoRitmoAlvo,
                etapas, descricao, zonaAlvo, provaId);
    }

    public TreinoPlanejadoLlmDto comDistancia(Double novaDistanciaKm) {
        return new TreinoPlanejadoLlmDto(diaSemana, tipoTreino, fcAlvo, tssPlanejado, intensidadePlanejada,
                percepcaoEsforcoEsperada, justificativaIa, duracaoMin, novaDistanciaKm, ritmoAlvo,
                etapas, descricao, zonaAlvo, provaId);
    }

    /** Troca o dia da semana — usado pela âncora do longo (add-descanso-explicito-por-fadiga). */
    public TreinoPlanejadoLlmDto comDiaSemana(String novoDiaSemana) {
        return new TreinoPlanejadoLlmDto(novoDiaSemana, tipoTreino, fcAlvo, tssPlanejado, intensidadePlanejada,
                percepcaoEsforcoEsperada, justificativaIa, duracaoMin, distanciaKm, ritmoAlvo,
                etapas, descricao, zonaAlvo, provaId);
    }

    public TreinoPlanejadoLlmDto comDuracao(String novaDuracaoMin) {
        return new TreinoPlanejadoLlmDto(diaSemana, tipoTreino, fcAlvo, tssPlanejado, intensidadePlanejada,
                percepcaoEsforcoEsperada, justificativaIa, novaDuracaoMin, distanciaKm, ritmoAlvo,
                etapas, descricao, zonaAlvo, provaId);
    }
}
