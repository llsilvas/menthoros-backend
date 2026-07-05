package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.dto.fit.FitSessionData;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.services.FitParseService;
import br.com.menthoros.backend.services.helper.FitTreinoPersister;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comportamento de persistência (dedup, side effects, mapeamento de campos) está em
 * {@link br.com.menthoros.backend.services.helper.FitTreinoPersisterTest} — este teste cobre só
 * a orquestração (parse → delegar para o persister).
 */
@ExtendWith(MockitoExtension.class)
class FitUploadServiceImplTest {

    @Mock private FitParseService fitParseService;
    @Mock private FitTreinoPersister fitTreinoPersister;

    private FitUploadServiceImpl service;

    @Nested
    @DisplayName("importar")
    class Importar {

        @Test
        @DisplayName("faz o parse e delega a persistência ao FitTreinoPersister, retornando seu resultado")
        void parseiaEDelegaPersistencia() {
            service = new FitUploadServiceImpl(fitParseService, fitTreinoPersister);

            UUID atletaId = UUID.randomUUID();
            FitSessionData dados = new FitSessionData(1L, LocalDate.of(2026, 7, 1), 1751360400L,
                    Duration.ofMinutes(30), 5.0, 150, 175, 62, true, "RUNNING", List.of());
            when(fitParseService.parse(any())).thenReturn(dados);
            FitImportResultado resultadoEsperado = new FitImportResultado(mock(TreinoRealizadoOutputDto.class), true);
            when(fitTreinoPersister.persistir(eq(atletaId), eq(dados))).thenReturn(resultadoEsperado);

            FitImportResultado resultado = service.importar(atletaId, new ByteArrayInputStream(new byte[0]));

            assertThat(resultado).isSameAs(resultadoEsperado);
            verify(fitTreinoPersister).persistir(atletaId, dados);
        }
    }
}
