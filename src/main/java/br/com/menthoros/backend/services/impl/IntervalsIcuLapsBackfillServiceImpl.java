package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.output.BackfillEtapasOutputDto;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.IntervalsIcuLapsBackfillService;
import br.com.menthoros.backend.services.helper.IntervalsIcuActivityMapper;
import br.com.menthoros.backend.services.helper.IntervalsIcuLapsBackfillPersister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orquestrador NÃO transacional do backfill de etapas (D9) — mesma separação do import: a chamada
 * HTTP fica fora de qualquer transação, e a persistência de cada treino é delegada ao
 * {@link IntervalsIcuLapsBackfillPersister}, em transação própria e curta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntervalsIcuLapsBackfillServiceImpl implements IntervalsIcuLapsBackfillService {

    private final IntervalsIcuConnectionService intervalsIcuConnectionService;
    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final IntervalsIcuClient intervalsIcuClient;
    private final IntervalsIcuActivityMapper intervalsIcuActivityMapper;
    private final IntervalsIcuLapsBackfillPersister persister;

    @Override
    public BackfillEtapasOutputDto backfillEtapas(UUID atletaId, UUID tenantId) {
        if (atletaId == null || tenantId == null) {
            throw new IllegalArgumentException("atletaId e tenantId não podem ser nulos");
        }

        IntegracaoExterna conexao = intervalsIcuConnectionService.conexaoAtiva(atletaId, tenantId)
                .orElseThrow(() -> new DomainConflictException("Atleta não tem conexão intervals.icu ativa"));

        List<TreinoRealizado> candidatos =
                treinoRealizadoRepository.findSemEtapasByAtletaAndFonte(tenantId, atletaId, FonteDados.INTERVALS_ICU);
        log.info("Backfill de etapas intervals.icu: atletaId={}, candidatos={}", atletaId, candidatos.size());

        int atualizados = 0;
        int semIntervalos = 0;
        int falhas = 0;

        for (TreinoRealizado treino : candidatos) {
            try {
                IcuActivityDto dto =
                        intervalsIcuClient.buscarAtividade(conexao.getAccessToken(), treino.getExternalId(), true);
                List<EtapaRealizada> etapas = intervalsIcuActivityMapper.mapEtapas(dto);
                if (etapas.isEmpty()) {
                    semIntervalos++;
                    continue;
                }
                persister.gravarEtapas(treino.getId(), etapas, tenantId);
                atualizados++;
            } catch (RuntimeException e) {
                // Falha de um treino não arrasta os demais. Nada é marcado: o treino continua sem
                // etapas, logo continua candidato na próxima execução.
                falhas++;
                log.warn("Backfill: falha ao completar o treino {} (externalId={}): {}",
                        treino.getId(), treino.getExternalId(), e.toString());
            }
        }

        log.info("Backfill concluído: atletaId={}, candidatos={}, atualizados={}, semIntervalos={}, falhas={}",
                atletaId, candidatos.size(), atualizados, semIntervalos, falhas);
        return new BackfillEtapasOutputDto(candidatos.size(), atualizados, semIntervalos, falhas);
    }
}
