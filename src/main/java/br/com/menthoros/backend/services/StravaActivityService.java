package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.StravaSyncResponseDto;
import br.com.menthoros.backend.dto.output.StravaSyncStatusDto;
import br.com.menthoros.backend.dto.strava.StravaActivityDto;
import br.com.menthoros.backend.dto.strava.StravaSplitDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StravaActivityService {

    List<StravaActivityDto> fetchActivities(String accessToken, Instant after, int page);

    List<StravaSplitDto> fetchActivityLaps(String accessToken, Long activityId);

    TreinoRealizado mapToTreinoRealizado(StravaActivityDto activity, Atleta atleta);

    EtapaRealizada mapToEtapaRealizada(StravaSplitDto split);

    int syncActivities(UUID atletaId);

    void syncSingleActivityById(Atleta atleta, IntegracaoExterna integracao, Long activityId);

    StravaSyncResponseDto syncActivitiesForAtleta(UUID atletaId, UUID tenantId);

    StravaSyncStatusDto getSyncStatus(UUID atletaId, UUID tenantId);
}
