package br.com.menthoros.backend.repository.custom;

import br.com.menthoros.backend.entity.Atleta;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * JPA implementation of {@link AtletaRepositoryCustom}.
 *
 * <p>Uses a programmatically built {@link EntityGraph} to control fetch behaviour per call,
 * avoiding N+1 queries for collection-heavy use-cases while keeping light-weight queries
 * fast for the {@code "basic"} strategy.
 *
 * <p>Spring Data JPA resolves this class automatically because its name follows the
 * {@code <RepositoryInterface>Impl} convention required by the framework.
 */
public class AtletaRepositoryImpl implements AtletaRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Atleta> findAtletasWithFetchGraph(UUID tenantId, String fetchType, Pageable pageable) {

        long total = getTotalCount(tenantId);

        if ("all".equals(fetchType)) {
            // Hibernate cannot simultaneously fetch two bag-typed collections in a
            // single query (MultipleBagFetchException). Fetching each collection in its
            // own query lets the persistence context merge them transparently.
            fetchBagQuery(tenantId, "diasDisponiveis", pageable);
            fetchBagQuery(tenantId, "provas", pageable);

            // Re-fetch the page with no graph so the already-initialised collections are
            // returned as managed entities within the same persistence context.
            TypedQuery<Atleta> baseQuery = em.createQuery(
                    "SELECT a FROM Atleta a WHERE a.assessoria.id = :tenantId",
                    Atleta.class
            );
            baseQuery.setParameter("tenantId", tenantId);
            baseQuery.setFirstResult((int) pageable.getOffset());
            baseQuery.setMaxResults(pageable.getPageSize());
            return new PageImpl<>(baseQuery.getResultList(), pageable, total);
        }

        EntityGraph<Atleta> graph = em.createEntityGraph(Atleta.class);

        switch (fetchType) {
            case "dias"  -> graph.addAttributeNodes("diasDisponiveis");
            case "provas" -> graph.addAttributeNodes("provas");
            default -> {
                // "basic" — no additional attribute nodes; only base entity is fetched
            }
        }

        TypedQuery<Atleta> query = em.createQuery(
                "SELECT a FROM Atleta a WHERE a.assessoria.id = :tenantId",
                Atleta.class
        );
        query.setHint("jakarta.persistence.fetchgraph", graph);
        query.setParameter("tenantId", tenantId);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(query.getResultList(), pageable, total);
    }

    /**
     * Execute a focused fetch query for a single bag-typed collection attribute.
     * Results are discarded; the goal is to populate the persistence-context cache
     * so the collection is initialised for entities already loaded in this session.
     */
    private void fetchBagQuery(UUID tenantId, String attributeName, Pageable pageable) {
        EntityGraph<Atleta> graph = em.createEntityGraph(Atleta.class);
        graph.addAttributeNodes(attributeName);

        TypedQuery<Atleta> q = em.createQuery(
                "SELECT a FROM Atleta a WHERE a.assessoria.id = :tenantId",
                Atleta.class
        );
        q.setHint("jakarta.persistence.fetchgraph", graph);
        q.setParameter("tenantId", tenantId);
        q.setFirstResult((int) pageable.getOffset());
        q.setMaxResults(pageable.getPageSize());
        q.getResultList(); // side-effect: warms the persistence-context cache
    }

    private long getTotalCount(UUID tenantId) {
        TypedQuery<Long> countQuery = em.createQuery(
                "SELECT COUNT(a) FROM Atleta a WHERE a.assessoria.id = :tenantId",
                Long.class
        );
        countQuery.setParameter("tenantId", tenantId);
        return countQuery.getSingleResult();
    }
}
