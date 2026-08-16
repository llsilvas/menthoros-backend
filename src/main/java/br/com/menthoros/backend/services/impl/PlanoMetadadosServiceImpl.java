package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementação do serviço de gerenciamento de metadados de planos de treino.
 *
 * <p>Esta classe é responsável por buscar e criar metadados associados aos planos
 * semanais dos atletas. Utiliza cache para otimizar o acesso aos dados frequentemente
 * consultados.
 *
 * <p><strong>Cache:</strong> Os metadados são armazenados em cache com a chave baseada
 * no ID do atleta, evitando consultas desnecessárias ao banco de dados.
 *
 * @see PlanoMetaDados
 * @see Atleta
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanoMetadadosServiceImpl implements PlanoMetadadosService {

    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final AtletaRepository atletaRepository;
    private final AssessoriaRepository assessoriaRepository;

    private static final String TENANT_KEY =
            "T(br.com.menthoros.backend.multitenancy.TenantContext).getTenantId()";
    private static final String HAS_TENANT =
            "T(br.com.menthoros.backend.multitenancy.TenantContext).hasTenant()";

    /**
     * {@inheritDoc}
     *
     * <p>Este método é cacheado para melhorar a performance. O cache é invalidado
     * automaticamente quando os metadados são atualizados.
     *
     * <p><strong>Idempotent:</strong> YES — retorna metadados existentes ou cria novos se ausentes.
     * <strong>Side Effects:</strong> Database insert (apenas na primeira chamada por atleta).
     * <strong>Tenant-aware:</strong> YES — popula assessoria a partir do TenantContext quando disponível.
     */
    /**
     * <b>Deliberadamente SEM {@code @Cacheable}</b> — foi removido em 2026-08-15 depois de um
     * incidente em produção.
     *
     * <p>Este método <b>escreve</b>: quando não encontra, cria e salva dentro da transação do
     * chamador. O Spring popula o cache <i>antes</i> do commit, então uma transação que reverte
     * — o que é provável em {@code gerarPlanoTreino}, que chama LLM e leva dezenas de segundos —
     * apagava o {@code INSERT} do banco e deixava o objeto no cache com um ID que nunca existiu.
     * A partir daí, toda tentativa lia o ID fantasma, falhava no {@code findByIdAndTenantId} e
     * revertia de novo: <b>o erro não se resolvia sozinho, nem reiniciando a tentativa</b>.
     *
     * <p>Cachear o resultado de um método que escreve é inseguro por construção. O ganho aqui era
     * uma consulta indexada num fluxo que faz uma chamada de LLM — desprezível perto do risco.
     * {@code PlanoMetadadosCacheIT} guarda esse comportamento.
     */
    @Override
    public PlanoMetaDados buscarOuCriarMetadados(Atleta atleta) {
        Objects.requireNonNull(atleta, "Atleta não pode ser nulo");
        Objects.requireNonNull(atleta.getId(), "ID do atleta não pode ser nulo");

        return planoMetadadosRepository.findLatestByAtletaId(atleta.getId())
                .orElseGet(() -> criarMetadadosIniciais(atleta));
    }

    @Override
    public PlanoMetaDados buscarPorAtletaId(UUID atletaId) {
        Objects.requireNonNull(atletaId, "atletaId não pode ser nulo");

        Atleta atleta = atletaRepository.findById(atletaId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado: " + atletaId));

        return buscarOuCriarMetadados(atleta);
    }

    /**
     * Cria metadados iniciais para um atleta que ainda não possui histórico.
     *
     * <p>Os metadados iniciais incluem informações básicas extraídas do perfil
     * do atleta, como o dia preferido para treinos longos, e o tenant (assessoria)
     * resolvido via TenantContext para garantir isolamento multi-tenant.
     *
     * <p><strong>Idempotent:</strong> NO — cria nova entidade cada vez que é chamado.
     * <strong>Side Effects:</strong> Database insert (nova entidade criada).
     * <strong>Tenant-aware:</strong> YES — popula assessoria via TenantContext quando disponível.
     *
     * @param atleta o atleta para o qual os metadados serão criados
     * @return os metadados criados e persistidos
     */
    private PlanoMetaDados criarMetadadosIniciais(Atleta atleta) {
        log.info("Criando metadados iniciais para o atleta: {}", atleta.getId());

        PlanoMetaDados.PlanoMetaDadosBuilder builder = PlanoMetaDados.builder()
                .atleta(atleta)
                .diaPreferidoLongo(atleta.getDiaPreferidoLongo())
                .dataCriacao(LocalDateTime.now());

        // A assessoria é OBRIGATÓRIA, não best-effort. A leitura é tenant-scoped
        // (`findByIdAndTenantId`), então metadados sem tenant seriam invisíveis para sempre — o
        // mesmo sintoma do incidente do cache, só que persistido no banco em vez de em memória.
        if (TenantContext.hasTenant()) {
            UUID tenantId = TenantContext.getTenantId();
            builder.assessoria(assessoriaRepository.getReferenceById(tenantId));
            log.debug("Metadados criados com tenant: {}", tenantId);
        } else if (atleta.getAssessoria() != null) {
            builder.assessoria(atleta.getAssessoria());
            log.debug("Metadados criados com assessoria do atleta: {}", atleta.getAssessoria().getId());
        } else {
            throw new IllegalStateException(
                    "Não é possível criar metadados sem tenant: nem o contexto nem o atleta "
                            + atleta.getId() + " têm assessoria");
        }

        PlanoMetaDados metaDados = builder.build();
        return planoMetadadosRepository.save(metaDados);
    }
}