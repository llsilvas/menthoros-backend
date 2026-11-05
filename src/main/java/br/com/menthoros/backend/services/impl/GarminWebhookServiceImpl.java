package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminPingService;
import br.com.menthoros.backend.services.GarminWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class GarminWebhookServiceImpl implements GarminWebhookService {

    private final IntegracaoExternaRepository integracaoRepository;
    private final GarminPingService pingService;

    /** Teto por chave e por requisição: o Garmin manda lotes pequenos; mais que isso é abuso ou erro. */
    static final int MAX_ITENS_POR_CHAVE = 100;

    /**
     * Idempotent: YES — reaplicar o mesmo ping chega ao mesmo estado.
     * Side Effects: Database update por userId (cross-tenant de propósito: o ping não traz tenant).
     * Tenant-aware: NO.
     *
     * Deliberadamente SEM @Transactional no lote: cada item grava em transação própria (o
     * {@code save} do repositório), então um item com erro não desfaz os outros nem devolve 500 ao
     * Garmin, que reenviaria o lote inteiro. Exceção: {@code activityFiles}, cuja falha de gravação
     * propaga (ver {@link GarminPingService#registrar}).
     */
    @Override
    public void processar(Map<String, List<JsonNode>> corpo) {
        if (corpo == null) {
            return;
        }
        porItem("deregistrations", corpo, this::desregistrar);
        porItem("userPermissionsChange", corpo, this::atualizarPermissoes);
        // Fora do porItem de propósito: engolir a falha aqui perderia o arquivo para sempre (o link
        // só vale 24 h e não há outro aviso). Propaga → 500 → o Garmin reenvia; o dedup absorve.
        List<JsonNode> arquivos = limitar("activityFiles", corpo);
        if (!arquivos.isEmpty()) {
            pingService.registrar(arquivos);
        }
    }

    private List<JsonNode> limitar(String chave, Map<String, List<JsonNode>> corpo) {
        List<JsonNode> itens = corpo.getOrDefault(chave, List.of());
        if (itens.size() > MAX_ITENS_POR_CHAVE) {
            log.warn("Webhook Garmin com {} itens em '{}'; processando só os {} primeiros", itens.size(), chave,
                    MAX_ITENS_POR_CHAVE);
            return itens.subList(0, MAX_ITENS_POR_CHAVE);
        }
        return itens;
    }

    private void porItem(String chave, Map<String, List<JsonNode>> corpo, Consumer<JsonNode> acao) {
        limitar(chave, corpo).forEach(item -> {
            try {
                acao.accept(item);
            } catch (RuntimeException e) {
                // Só o tipo: a mensagem de uma violação de constraint pode carregar valores da linha.
                log.error("Falha ao processar item '{}' do webhook Garmin: {}", chave, e.getClass().getSimpleName());
            }
        });
    }

    private void desregistrar(JsonNode item) {
        conexaoAtiva(item).ifPresent(c -> {
            c.revogarCredenciais("Autorização removida pelo Garmin; reconecte a conta");
            integracaoRepository.save(c);
            log.info("Garmin desregistrou o usuário: atletaId={}", c.getAtleta().getId());
        });
    }

    private void atualizarPermissoes(JsonNode item) {
        conexaoAtiva(item).ifPresent(c -> {
            List<String> permissoes = new ArrayList<>();
            item.path("permissions").forEach(p -> permissoes.add(p.asText()));
            c.setScopes(String.join(GarminConnectionServiceImpl.SEPARADOR_SCOPES, permissoes));
            integracaoRepository.save(c);
            log.info("Garmin alterou permissões: atletaId={}, total={}", c.getAtleta().getId(), permissoes.size());
        });
    }

    private Optional<IntegracaoExterna> conexaoAtiva(JsonNode item) {
        String userId = item.path("userId").asText("");
        if (userId.isBlank()) {
            return Optional.empty();
        }
        return integracaoRepository.findActiveByExternalAthleteIdAndPlataforma(userId, FonteDados.GARMIN);
    }
}
