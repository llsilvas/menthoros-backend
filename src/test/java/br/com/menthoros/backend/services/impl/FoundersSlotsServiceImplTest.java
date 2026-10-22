package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.FoundersSlotsOutputDto;
import br.com.menthoros.backend.repository.FoundingInviteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoundersSlotsServiceImplTest {

    @Mock
    private FoundingInviteRepository foundingInviteRepository;

    private FoundersSlotsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FoundersSlotsServiceImpl(foundingInviteRepository, 10, Duration.ofSeconds(30));
    }

    @Test
    void calculaVagasRestantesAPartirDosConvitesNaoInvalidados() {
        when(foundingInviteRepository.countByInvalidatedAtIsNull()).thenReturn(3L);

        FoundersSlotsOutputDto resultado = service.obterVagas();

        assertThat(resultado).isEqualTo(new FoundersSlotsOutputDto(10, 3, 7, true));
    }

    @Test
    void vagasEsgotadasNuncaFicamNegativas() {
        when(foundingInviteRepository.countByInvalidatedAtIsNull()).thenReturn(15L);

        FoundersSlotsOutputDto resultado = service.obterVagas();

        assertThat(resultado.remaining()).isZero();
        assertThat(resultado.open()).isFalse();
        assertThat(resultado.taken()).isEqualTo(15);
    }

    @Test
    void exatamenteNoLimiteFechaAsVagas() {
        when(foundingInviteRepository.countByInvalidatedAtIsNull()).thenReturn(10L);

        FoundersSlotsOutputDto resultado = service.obterVagas();

        assertThat(resultado.remaining()).isZero();
        assertThat(resultado.open()).isFalse();
    }

    @Test
    void chamadasRepetidasDentroDoTtlUsamOCache() {
        when(foundingInviteRepository.countByInvalidatedAtIsNull()).thenReturn(1L);

        service.obterVagas();
        service.obterVagas();

        verify(foundingInviteRepository, org.mockito.Mockito.times(1)).countByInvalidatedAtIsNull();
    }
}
