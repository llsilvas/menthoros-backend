package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.StravaSyncResponseDto;
import br.com.menthoros.backend.dto.output.StravaSyncStatusDto;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.dto.strava.StravaActivityDto;
import br.com.menthoros.backend.dto.strava.StravaSplitDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.services.helper.PullResultado;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StravaActivityService {

    List<StravaActivityDto> fetchActivities(String accessToken, Instant after, int page);

    List<StravaSplitDto> fetchActivityLaps(String accessToken, Long activityId);

    TreinoRealizado mapToTreinoRealizado(StravaActivityDto activity, Atleta atleta);

    EtapaRealizada mapToEtapaRealizada(StravaSplitDto split);

    /**
     * Pull agendado sobre {@code pull_cursor} (fix-sync-cursor-data-loss D3). Só o scheduler chama.
     * Não lança depois de carregar a integração; o resultado diz o que foi commitado.
     */
    PullResultado pullAgendado(UUID atletaId);

    void syncSingleActivityById(Atleta atleta, IntegracaoExterna integracao, Long activityId);

    StravaSyncResponseDto syncActivitiesForAtleta(UUID atletaId, UUID tenantId);

    StravaSyncStatusDto getSyncStatus(UUID atletaId, UUID tenantId);

    /**
     * Busca o detalhe completo de uma atividade Strava sob demanda e enriquece o TreinoRealizado.
     * Preenche perceived_exertion → percepcaoEsforco se ainda não estiver definido.
     */
    TreinoRealizadoOutputDto enriquecerTreinoComStrava(UUID treinoRealizadoId, UUID tenantId);
}
