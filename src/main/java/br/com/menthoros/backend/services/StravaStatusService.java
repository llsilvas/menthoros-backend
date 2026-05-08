package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.StravaStatusGlobalDto;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import org.springframework.stereotype.Service;

@Service
public class StravaStatusService {

    private final IntegracaoExternaRepository integracaoExternaRepository;
    private final AtletaRepository atletaRepository;

    public StravaStatusService(IntegracaoExternaRepository integracaoExternaRepository,
                               AtletaRepository atletaRepository) {
        this.integracaoExternaRepository = integracaoExternaRepository;
        this.atletaRepository = atletaRepository;
    }

    public StravaStatusGlobalDto getStatusGlobal() {
        Integer totalAtletas = atletaRepository.countAllAthletes();
        Integer atletasConectados = integracaoExternaRepository.countAthletesWithActiveStrava();

        Double percentualConectado = totalAtletas > 0
            ? (atletasConectados.doubleValue() / totalAtletas) * 100
            : 0.0;

        return new StravaStatusGlobalDto(totalAtletas, atletasConectados, percentualConectado);
    }
}
