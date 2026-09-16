package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Grader de concordância do eval set (plan-generation-eval-set, fatia 1) — roda **só em modo
 * auditoria**: compara a resposta histórica da LLM contra o plano final persistido para o mesmo
 * {@code generation_request_id}. **Limite conhecido, documentado no proposal**: sem histórico de
 * edição por campo, a comparação é por posição na lista de treinos (não por dia/tipo casado
 * semanticamente) — quando as duas listas têm tamanhos diferentes, a comparação fica limitada aos
 * N primeiros treinos e isso é reportado, não escondido.
 *
 * <p>Despacha por {@code schemaVersion} como {@link EvalDeterministicGrader}: {@code schema-v2}
 * resolve via {@link SessionResolver} antes de comparar.
 *
 * <p>Idempotent: YES. Side Effects: NONE. Tenant-aware: NÃO.
 *
 * <p>Deliberadamente sem {@code @Component} — nunca instanciada pelo Spring (achado do /qa).
 */
public class EvalAgreementGrader {

    private final ObjectMapper objectMapper;
    private final SessionResolver sessionResolver;

    public EvalAgreementGrader(ObjectMapper objectMapper, SessionResolver sessionResolver) {
        this.objectMapper = objectMapper;
        this.sessionResolver = sessionResolver;
    }

    /** Um campo estrutural que divergiu entre a resposta da LLM e o plano final persistido. */
    public record Divergencia(int posicao, String campo, @Nullable Object valorResposta, @Nullable Object valorFinal) {
    }

    public record Resultado(int totalTreinosComparados, int treinosNaRespostaNaoComparados,
                             List<Divergencia> divergencias) {

        /** % de campos estruturais divergentes entre os treinos efetivamente comparados. */
        public double percentualDivergencia(int camposPorTreino) {
            int totalCampos = totalTreinosComparados * camposPorTreino;
            return totalCampos == 0 ? 0.0 : (double) divergencias.size() / totalCampos;
        }
    }

    private static final int CAMPOS_POR_TREINO = 2; // tipoTreino, tssPlanejado

    public Resultado avaliar(String respostaHistoricaJson, @Nullable String schemaVersion,
                              AthleteZones zonasAtleta, String planoFinalPersistidoJson) {
        PlanoSemanalLlmDto plano = EvalPlanoJsonParser.parsear(objectMapper, sessionResolver,
                respostaHistoricaJson, schemaVersion, zonasAtleta);
        List<Map<String, Object>> finais = parsearPlanoFinal(planoFinalPersistidoJson);
        List<TreinoPlanejadoLlmDto> daResposta = plano.treinosPlanejados() != null
                ? plano.treinosPlanejados() : List.of();

        int comparaveis = Math.min(daResposta.size(), finais.size());
        List<Divergencia> divergencias = new ArrayList<>();
        for (int i = 0; i < comparaveis; i++) {
            TreinoPlanejadoLlmDto daRespostaAtual = daResposta.get(i);
            Map<String, Object> finalAtual = finais.get(i);

            compararCampo(i, "tipoTreino", daRespostaAtual.tipoTreino(), finalAtual.get("tipoTreino"), divergencias);
            compararCampo(i, "tssPlanejado", daRespostaAtual.tssPlanejado(), finalAtual.get("tssPlanejado"), divergencias);
        }

        int naoComparados = Math.abs(daResposta.size() - finais.size());
        return new Resultado(comparaveis, naoComparados, divergencias);
    }

    private void compararCampo(int posicao, String campo, @Nullable Object valorResposta,
                                @Nullable Object valorFinal, List<Divergencia> destino) {
        if (!Objects.equals(String.valueOf(valorResposta), String.valueOf(valorFinal))) {
            destino.add(new Divergencia(posicao, campo, valorResposta, valorFinal));
        }
    }

    private List<Map<String, Object>> parsearPlanoFinal(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "planoFinalPersistidoJson não é um JSON válido: " + e.getMessage(), e);
        }
    }

    static int camposPorTreino() {
        return CAMPOS_POR_TREINO;
    }
}
