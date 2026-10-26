package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.services.GarminWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GarminWebhookServiceImpl implements GarminWebhookService {

    private final IntegracaoExternaRepository integracaoRepository;

    @Override
    @Transactional
    public void processar(Map<String, List<JsonNode>> corpo) {
        if (corpo == null) {
            return;
        }
        corpo.getOrDefault("deregistrations", List.of()).forEach(this::desregistrar);
        corpo.getOrDefault("userPermissionsChange", List.of()).forEach(this::atualizarPermissoes);
    }

    private void desregistrar(JsonNode item) {
        conexaoAtiva(item).ifPresent(c -> {
            c.setAtivo(false);
            c.setAccessToken(null);
            c.setRefreshToken(null);
            c.setTokenExpiraEm(null);
            c.setRefreshTokenExpiraEm(null);
            c.setScopes(null);
            c.setLastSyncError("Autorização removida pelo Garmin; reconecte a conta");
            integracaoRepository.save(c);
            log.info("Garmin desregistrou o usuário: atletaId={}", c.getAtleta().getId());
        });
    }

    private void atualizarPermissoes(JsonNode item) {
        conexaoAtiva(item).ifPresent(c -> {
            List<String> permissoes = new ArrayList<>();
            item.path("permissions").forEach(p -> permissoes.add(p.asText()));
            c.setScopes(String.join(",", permissoes));
            integracaoRepository.save(c);
            log.info("Garmin alterou permissões: atletaId={}, total={}", c.getAtleta().getId(), permissoes.size());
        });
    }

    private java.util.Optional<IntegracaoExterna> conexaoAtiva(JsonNode item) {
        String userId = item.path("userId").asText("");
        if (userId.isBlank()) {
            return java.util.Optional.empty();
        }
        return integracaoRepository.findActiveByExternalAthleteIdAndPlataforma(userId, FonteDados.GARMIN);
    }
}
