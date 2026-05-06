package br.com.menthoros.backend.repository.specification;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.enums.AtletaStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtletaSpecificationTest {

    @Mock
    private Root<Atleta> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Mock
    private Predicate predicate;

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("byTenant: should build equal predicate on assessoria.id")
    void byTenant_shouldBuildEqualPredicateOnAssessoriaId() {
        UUID tenantId = UUID.randomUUID();

        Path<Object> assessoriaPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        when(root.get("assessoria")).thenReturn(assessoriaPath);
        when(assessoriaPath.get("id")).thenReturn(idPath);
        when(cb.equal(idPath, tenantId)).thenReturn(predicate);

        Specification<Atleta> spec = AtletaSpecification.byTenant(tenantId);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(root).get("assessoria");
        verify(assessoriaPath).get("id");
        verify(cb).equal(idPath, tenantId);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("active: should build equal predicate with AtletaStatus.ATIVO")
    void active_shouldBuildEqualPredicateWithAtletaStatusAtivo() {
        Path<Object> ativoPath = mock(Path.class);
        when(root.get("ativo")).thenReturn(ativoPath);
        when(cb.equal(ativoPath, AtletaStatus.ATIVO)).thenReturn(predicate);

        Specification<Atleta> spec = AtletaSpecification.active();
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(root).get("ativo");
        verify(cb).equal(ativoPath, AtletaStatus.ATIVO);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("inactive: should build equal predicate with AtletaStatus.INATIVO")
    void inactive_shouldBuildEqualPredicateWithAtletaStatusInativo() {
        Path<Object> ativoPath = mock(Path.class);
        when(root.get("ativo")).thenReturn(ativoPath);
        when(cb.equal(ativoPath, AtletaStatus.INATIVO)).thenReturn(predicate);

        Specification<Atleta> spec = AtletaSpecification.inactive();
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(root).get("ativo");
        verify(cb).equal(ativoPath, AtletaStatus.INATIVO);
    }

    @Test
    @DisplayName("withStravaConnected: should build IN subquery predicate")
    @SuppressWarnings("unchecked")
    void withStravaConnected_shouldBuildInSubqueryPredicate() {
        Subquery<UUID> subquery = mock(Subquery.class);
        Root<Object> subRoot = mock(Root.class);
        Path<Object> subAtletaPath = mock(Path.class);
        Path<Object> subAtletaIdPath = mock(Path.class);
        Path<Object> subAtivoPath = mock(Path.class);
        Path<Object> subAccessTokenPath = mock(Path.class);
        Path<Object> rootIdPath = mock(Path.class);
        Predicate inPredicate = mock(Predicate.class);

        when(query.subquery(UUID.class)).thenReturn(subquery);
        when(subquery.from(any(Class.class))).thenReturn(subRoot);
        when(subRoot.get("atleta")).thenReturn(subAtletaPath);
        when(subAtletaPath.get("id")).thenReturn(subAtletaIdPath);
        when(subRoot.get("plataforma")).thenReturn(mock(Path.class));
        when(subRoot.get("ativo")).thenReturn(subAtivoPath);
        when(subRoot.get("accessToken")).thenReturn(subAccessTokenPath);
        when(subquery.select(any())).thenReturn(subquery);
        when(subquery.where(any(Predicate[].class))).thenReturn(subquery);
        lenient().when(cb.equal(any(), any())).thenReturn(predicate);
        when(cb.isTrue(any())).thenReturn(predicate);
        when(cb.isNotNull(any())).thenReturn(predicate);
        when(root.get("id")).thenReturn(rootIdPath);
        when(rootIdPath.in(subquery)).thenReturn(inPredicate);

        Specification<Atleta> spec = AtletaSpecification.withStravaConnected();
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(query).subquery(UUID.class);
    }

    @Test
    @DisplayName("withNameContaining: should build LIKE predicate on nome (case-insensitive)")
    @SuppressWarnings("unchecked")
    void withNameContaining_shouldBuildLikePredicateOnNome() {
        Path<String> nomePath = mock(Path.class);
        when(root.<String>get("nome")).thenReturn(nomePath);
        Expression<String> lowerExpr = mock(Expression.class);
        when(cb.lower(any(Expression.class))).thenReturn(lowerExpr);
        when(cb.like(any(Expression.class), any(String.class))).thenReturn(predicate);

        Specification<Atleta> spec = AtletaSpecification.withNameContaining("João");
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(cb).lower(nomePath);
        verify(cb).like(lowerExpr, "%joão%");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("should combine byTenant and active specifications with and()")
    void shouldCombineByTenantAndActiveSpecifications() {
        UUID tenantId = UUID.randomUUID();

        Path<Object> assessoriaPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        Path<Object> ativoPath = mock(Path.class);
        when(root.get("assessoria")).thenReturn(assessoriaPath);
        when(assessoriaPath.get("id")).thenReturn(idPath);
        when(root.get("ativo")).thenReturn(ativoPath);
        when(cb.equal(idPath, tenantId)).thenReturn(predicate);
        when(cb.equal(ativoPath, AtletaStatus.ATIVO)).thenReturn(predicate);
        when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

        Specification<Atleta> combined = AtletaSpecification.byTenant(tenantId)
                .and(AtletaSpecification.active());

        assertThat(combined).isNotNull();

        Predicate result = combined.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
    }
}
