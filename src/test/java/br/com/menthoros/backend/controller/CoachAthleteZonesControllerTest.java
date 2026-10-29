package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.AthleteTrainingZonesOutputDto;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.services.CoachAthleteZonesService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoachAthleteZonesController")
class CoachAthleteZonesControllerTest {

    @Mock private CoachAthleteZonesService service;
    @InjectMocks private CoachAthleteZonesController controller;

    private final UUID atletaId = UUID.randomUUID();

    @Test
    @DisplayName("GET /{atletaId}/zonas → 200 com o DTO do serviço")
    void retornaZonas() {
        AthleteTrainingZonesOutputDto dto = new AthleteTrainingZonesOutputDto(
                "LTHR_FRIEL", null, null, null, null, List.of());
        when(service.buscarZonas(atletaId)).thenReturn(dto);

        ResponseEntity<AthleteTrainingZonesOutputDto> resp = controller.buscarZonas(atletaId);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(dto);
    }

    @Test
    @DisplayName("atleta fora do tenant → DomainNotFoundException propagada (404 pelo handler)")
    void atletaNaoEncontrado() {
        when(service.buscarZonas(atletaId)).thenThrow(new DomainNotFoundException("Atleta não encontrado"));

        assertThatThrownBy(() -> controller.buscarZonas(atletaId)).isInstanceOf(DomainNotFoundException.class);
    }
}
