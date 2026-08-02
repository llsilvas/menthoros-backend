package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuActivityDto;
import br.com.menthoros.backend.dto.output.BackfillEtapasOutputDto;
import br.com.menthoros.backend.entity.EtapaRealizada;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.IntervalsIcuApiException;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.IntervalsIcuLapsBackfillService;
import br.com.menthoros.backend.services.helper.IntervalsIcuActivityMapper;
import br.com.menthoros.backend.services.helper.IntervalsIcuLapsBackfillPersister;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
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

    /** Teto de chamadas externas por execução — ver o comentário no loop. */
    private static final int MAX_POR_EXECUCAO = 50;

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

        List<TreinoRealizado> todos =
                treinoRealizadoRepository.findSemEtapasByAtletaAndFonte(tenantId, atletaId, FonteDados.INTERVALS_ICU);

        // Cada candidato custa uma chamada externa BLOQUEANTE, em serie, dentro de um request HTTP
        // sincrono. Sem teto, um atleta com muito passivo estoura o timeout do endpoint e o rate
        // limit do intervals.icu. O corte nunca e silencioso: volta em `restantes`.
        boolean truncado = todos.size() > MAX_POR_EXECUCAO;
        List<TreinoRealizado> candidatos = truncado ? todos.subList(0, MAX_POR_EXECUCAO) : todos;
        log.info("Backfill de etapas intervals.icu: atletaId={}, semEtapas={}, processando={}",
                atletaId, todos.size(), candidatos.size());

        int atualizados = 0;
        int semIntervalos = 0;
        int falhas = 0;
        int processados = 0;

        for (TreinoRealizado treino : candidatos) {
            processados++;
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
            } catch (IntervalsIcuApiException e) {
                // Credencial morta vale para TODOS os candidatos seguintes: insistir so multiplica
                // latencia e queima rate limit. Aborta com a mesma mensagem acionavel do import.
                if (credencialInvalida(e)) {
                    log.warn("Backfill interrompido: credencial intervals.icu inválida (atletaId={}, após {} de {})",
                            atletaId, processados, candidatos.size());
                    throw new DomainConflictException("Credencial intervals.icu inválida — reconecte a integração");
                }
                // Falha pontual desta activity: nao arrasta os demais, e nada e marcado no treino —
                // ele continua sem etapas, logo continua candidato na proxima execucao.
                falhas++;
                log.warn("Backfill: falha ao completar o treino {} (externalId={}): {}",
                        treino.getId(), treino.getExternalId(), e.toString());
            }
            // RuntimeException nao vinda do transporte e BUG (ex.: NPE no mapper) — deixar propagar.
            // Conta-lo como "falha" de negocio esconderia defeito real atras de estatistica.
        }

        int restantes = todos.size() - candidatos.size();
        log.info("Backfill concluído: atletaId={}, candidatos={}, atualizados={}, semIntervalos={}, falhas={}, restantes={}",
                atletaId, candidatos.size(), atualizados, semIntervalos, falhas, restantes);
        return new BackfillEtapasOutputDto(candidatos.size(), atualizados, semIntervalos, falhas, restantes);
    }

    private boolean credencialInvalida(IntervalsIcuApiException e) {
        HttpStatusCode status = e.getStatus();
        return status != null && (status.value() == 401 || status.value() == 403);
    }
}
