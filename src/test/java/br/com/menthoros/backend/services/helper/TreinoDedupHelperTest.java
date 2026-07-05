package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreinoDedupHelperTest {

    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @InjectMocks private TreinoDedupHelper helper;

    private UUID atletaId;

    @BeforeEach
    void setUp() {
        atletaId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("saveIdempotent")
    class SaveIdempotent {

        @Test
        @DisplayName("persiste normalmente quando não há conflito — retorna a mesma instância")
        void persisteSemConflito() {
            String externalId = "ext-1";
            TreinoRealizado treino = new TreinoRealizado();
            when(treinoRealizadoRepository.save(treino)).thenReturn(treino);

            TreinoRealizado salvo = helper.saveIdempotent(treino, externalId, atletaId);

            assertThat(salvo).isSameAs(treino);
        }

        @Test
        @DisplayName("sob conflito de constraint, retorna o registro já existente (vencedor da corrida)")
        void retornaVencedorSobConflito() {
            String externalId = "ext-2";
            TreinoRealizado treino = new TreinoRealizado();
            TreinoRealizado vencedor = new TreinoRealizado();
            when(treinoRealizadoRepository.save(treino)).thenThrow(new DataIntegrityViolationException("duplicate"));
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, atletaId))
                    .thenReturn(Optional.of(vencedor));

            TreinoRealizado salvo = helper.saveIdempotent(treino, externalId, atletaId);

            assertThat(salvo).isSameAs(vencedor).isNotSameAs(treino);
            verify(treinoRealizadoRepository).findByExternalIdAndAtletaId(externalId, atletaId);
        }

        @Test
        @DisplayName("conflito sem registro encontrado no retry propaga a exceção original")
        void propagaExcecaoQuandoNaoEncontraNoRetry() {
            String externalId = "ext-3";
            TreinoRealizado treino = new TreinoRealizado();
            DataIntegrityViolationException original = new DataIntegrityViolationException("duplicate");
            when(treinoRealizadoRepository.save(treino)).thenThrow(original);
            when(treinoRealizadoRepository.findByExternalIdAndAtletaId(externalId, atletaId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> helper.saveIdempotent(treino, externalId, atletaId))
                    .isSameAs(original);
        }
    }
}
