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
import org.springframework.cache.annotation.Cacheable;
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
    @Override
    @Cacheable(value = "metadados-atleta", key = "#atleta.id + '_' + " + TENANT_KEY, condition = HAS_TENANT)
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

        // Popula assessoria (tenant) se disponível no contexto ou via atleta
        if (TenantContext.hasTenant()) {
            UUID tenantId = TenantContext.getTenantId();
            Assessoria assessoria = assessoriaRepository.getReferenceById(tenantId);
            builder.assessoria(assessoria);
            log.debug("Metadados criados com tenant: {}", tenantId);
        } else if (atleta.getAssessoria() != null) {
            builder.assessoria(atleta.getAssessoria());
            log.debug("Metadados criados com assessoria do atleta: {}", atleta.getAssessoria().getId());
        }

        PlanoMetaDados metaDados = builder.build();
        return planoMetadadosRepository.save(metaDados);
    }
}