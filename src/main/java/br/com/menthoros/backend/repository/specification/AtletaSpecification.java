package br.com.menthoros.backend.repository.specification;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.enums.NivelExperiencia;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class AtletaSpecification {

    private AtletaSpecification() {
        // Utility class
    }

    public static Specification<Atleta> byTenant(UUID tenantId) {
        return (root, query, cb) ->
            cb.equal(root.get("assessoria").get("id"), tenantId);
    }

    public static Specification<Atleta> active() {
        return (root, query, cb) ->
            cb.equal(root.get("ativo"), AtletaStatus.ATIVO);
    }

    public static Specification<Atleta> inactive() {
        return (root, query, cb) ->
            cb.equal(root.get("ativo"), AtletaStatus.INATIVO);
    }

    public static Specification<Atleta> withStravaConnected() {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var subRoot = sub.from(IntegracaoExterna.class);
            sub.select(subRoot.get("atleta").get("id"))
               .where(
                   cb.equal(subRoot.get("plataforma"), FonteDados.STRAVA),
                   cb.isTrue(subRoot.get("ativo")),
                   cb.isNotNull(subRoot.get("accessToken"))
               );
            return root.get("id").in(sub);
        };
    }

    public static Specification<Atleta> withoutStravaConnected() {
        return (root, query, cb) -> {
            Subquery<UUID> sub = query.subquery(UUID.class);
            var subRoot = sub.from(IntegracaoExterna.class);
            sub.select(subRoot.get("atleta").get("id"))
               .where(
                   cb.equal(subRoot.get("plataforma"), FonteDados.STRAVA),
                   cb.isTrue(subRoot.get("ativo")),
                   cb.isNotNull(subRoot.get("accessToken"))
               );
            return root.get("id").in(sub).not();
        };
    }

    public static Specification<Atleta> withNameContaining(String name) {
        return (root, query, cb) -> {
            var nomeExpression = root.<String>get("nome");
            return cb.like(cb.lower(nomeExpression), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<Atleta> withNivelExperiencia(NivelExperiencia nivel) {
        return (root, query, cb) -> cb.equal(root.get("nivelExperiencia"), nivel);
    }

    public static Specification<Atleta> withTemLesao(Boolean temLesao) {
        return (root, query, cb) -> cb.equal(root.get("temLesao"), temLesao);
    }
}
