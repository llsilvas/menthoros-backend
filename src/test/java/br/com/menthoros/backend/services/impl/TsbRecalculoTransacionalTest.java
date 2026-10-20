package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.repository.MetricasDiariasRepository;
import br.com.menthoros.backend.services.helper.PaceLimiarResolvido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TsbRecalculoTransacional — lock, fecha-buraco e laço (design.md R1/R2/D1)")
class TsbRecalculoTransacionalTest {

    @Mock private MetricasDiariasRepository metricasDiariasRepository;
    @Mock private TsbDiaPersister tsbDiaPersister;

    private static final UUID ATLETA_ID = UUID.randomUUID();
    private static final LocalDate D0 = LocalDate.of(2026, 10, 1);

    private TsbRecalculoTransacional bean() {
        return new TsbRecalculoTransacional(metricasDiariasRepository, tsbDiaPersister);
    }

    @Test
    @DisplayName("é transacional: lock e laço compartilham a mesma transação")
    void ehTransacional() throws NoSuchMethodException {
        Method m = TsbRecalculoTransacional.class.getMethod("recalcularIntervalo",
                UUID.class, LocalDate.class, LocalDate.class, boolean.class, PaceLimiarResolvido.class);

        assertThat(m.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    @DisplayName("obtém o lock do atleta antes de ler métricas e antes de gravar qualquer dia")
    void lockAntesDeTudo() {
        when(metricasDiariasRepository.findDataUltimaMetrica(ATLETA_ID)).thenReturn(null);

        bean().recalcularIntervalo(ATLETA_ID, D0, D0, true, null);

        InOrder ordem = inOrder(metricasDiariasRepository, tsbDiaPersister);
        ordem.verify(metricasDiariasRepository).lockAtleta(ATLETA_ID);
        ordem.verify(metricasDiariasRepository).findDataUltimaMetrica(ATLETA_ID);
        ordem.verify(tsbDiaPersister).atualizarDiaTransacional(eq(ATLETA_ID), eq(D0), eq(true), any());
    }

    @Nested
    @DisplayName("início efetivo (fecha o buraco para trás)")
    class InicioEfetivo {

        @Test
        @DisplayName("atleta sem métrica: começa em inicio")
        void semMetrica() {
            when(metricasDiariasRepository.findDataUltimaMetrica(ATLETA_ID)).thenReturn(null);

            bean().recalcularIntervalo(ATLETA_ID, D0, D0.plusDays(1), true, null);

            verify(tsbDiaPersister, times(2)).atualizarDiaTransacional(eq(ATLETA_ID), any(), any(Boolean.class), any());
            verify(tsbDiaPersister).atualizarDiaTransacional(ATLETA_ID, D0, false, null);
        }

        @Test
        @DisplayName("retroativo (inicio anterior à última métrica): começa em inicio")
        void retroativo() {
            when(metricasDiariasRepository.findDataUltimaMetrica(ATLETA_ID)).thenReturn(D0.plusDays(5));

            bean().recalcularIntervalo(ATLETA_ID, D0, D0.plusDays(5), true, null);

            verify(tsbDiaPersister).atualizarDiaTransacional(ATLETA_ID, D0, false, null);
            verify(tsbDiaPersister, times(6)).atualizarDiaTransacional(eq(ATLETA_ID), any(), any(Boolean.class), any());
        }

        @Test
        @DisplayName("inicio logo após a última métrica (sem buraco): começa em inicio")
        void semBuraco() {
            when(metricasDiariasRepository.findDataUltimaMetrica(ATLETA_ID)).thenReturn(D0.minusDays(1));

            bean().recalcularIntervalo(ATLETA_ID, D0, D0, true, null);

            verify(tsbDiaPersister).atualizarDiaTransacional(ATLETA_ID, D0, true, null);
            verify(tsbDiaPersister, times(1)).atualizarDiaTransacional(eq(ATLETA_ID), any(), any(Boolean.class), any());
        }

        @Test
        @DisplayName("pós-buraco: começa em ultimaMetrica+1; intermediários sem metadados, só o último com")
        void posBuraco() {
            LocalDate ultima = D0.minusDays(4);
            when(metricasDiariasRepository.findDataUltimaMetrica(ATLETA_ID)).thenReturn(ultima);
            PaceLimiarResolvido pace = new PaceLimiarResolvido(null, null, null);

            bean().recalcularIntervalo(ATLETA_ID, D0, D0, true, pace);

            InOrder ordem = inOrder(tsbDiaPersister);
            ordem.verify(tsbDiaPersister).atualizarDiaTransacional(ATLETA_ID, ultima.plusDays(1), false, null);
            ordem.verify(tsbDiaPersister).atualizarDiaTransacional(ATLETA_ID, ultima.plusDays(2), false, null);
            ordem.verify(tsbDiaPersister).atualizarDiaTransacional(ATLETA_ID, ultima.plusDays(3), false, null);
            ordem.verify(tsbDiaPersister).atualizarDiaTransacional(ATLETA_ID, D0, true, pace);
            verify(tsbDiaPersister, times(4)).atualizarDiaTransacional(eq(ATLETA_ID), any(), any(Boolean.class), any());
        }
    }

    @Test
    @DisplayName("gravaMetadados=false (bloco intermediário): nenhum dia grava metadados")
    void semMetadados() {
        when(metricasDiariasRepository.findDataUltimaMetrica(ATLETA_ID)).thenReturn(null);

        bean().recalcularIntervalo(ATLETA_ID, D0, D0.plusDays(2), false, null);

        verify(tsbDiaPersister, times(3)).atualizarDiaTransacional(eq(ATLETA_ID), any(), eq(false), any());
        verify(tsbDiaPersister, never()).atualizarDiaTransacional(any(), any(), eq(true), any());
    }
}
