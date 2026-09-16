package br.com.menthoros.backend.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.menthoros.backend.enums.TipoEtapa;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EtapaTreinoLlmDto(
        Integer ordem,

        String tipoEtapa,

        String descricaoEtapa,

        Integer duracaoMin,

        Double distanciaKm,

        String fcAlvoEtapa,

        Integer repeticoes,

        String ritmoAlvo
) {
    // Withers (pipeline-normalizacao-treino, seção 1): cópia de record existente mudando um campo.
    // Criações genuínas de etapa (expansão de série, tiro+rec sintetizados, aquec/desaq do reparo)
    // continuam pelo construtor canônico — não há record de origem.

    public EtapaTreinoLlmDto comOrdem(Integer novaOrdem) {
        return new EtapaTreinoLlmDto(novaOrdem, tipoEtapa, descricaoEtapa, duracaoMin, distanciaKm,
                fcAlvoEtapa, repeticoes, ritmoAlvo);
    }

    public EtapaTreinoLlmDto comDuracao(Integer novaDuracaoMin) {
        return new EtapaTreinoLlmDto(ordem, tipoEtapa, descricaoEtapa, novaDuracaoMin, distanciaKm,
                fcAlvoEtapa, repeticoes, ritmoAlvo);
    }

    public EtapaTreinoLlmDto comDistancia(Double novaDistanciaKm) {
        return new EtapaTreinoLlmDto(ordem, tipoEtapa, descricaoEtapa, duracaoMin, novaDistanciaKm,
                fcAlvoEtapa, repeticoes, ritmoAlvo);
    }

    public EtapaTreinoLlmDto comFc(String novoFcAlvoEtapa) {
        return new EtapaTreinoLlmDto(ordem, tipoEtapa, descricaoEtapa, duracaoMin, distanciaKm,
                novoFcAlvoEtapa, repeticoes, ritmoAlvo);
    }
}
