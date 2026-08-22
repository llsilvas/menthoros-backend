package br.com.menthoros.backend.config;

import br.com.menthoros.backend.domain.audit.AuditableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:auditdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // Este teste sobe o contexto sem profile, entao herda o application.yml real, que deixa
    // client-id/client-secret vazios (vem de env var em runtime). IntervalsIcuProperties e
    // @NotBlank e sem isto o contexto nao sobe. Ver D11 da change intervals-icu-oauth2.
    "app.intervals-icu.client-id=test-intervals-client-id",
    "app.intervals-icu.client-secret=test-intervals-client-secret"
})
class AuditConfigTest {

    @Autowired
    private AuditingHandler auditingHandler;

    @Autowired
    private EntityManager entityManager;

    @Test
    @WithMockUser(username = "testuser")
    void should_have_auditing_enabled() {
        assertThat(auditingHandler).isNotNull();
    }

    @Test
    @Transactional
    @WithMockUser(username = "alice@example.com")
    void should_populate_created_by_from_security_context() {
        AuditTestEntity entity = new AuditTestEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("test");

        entityManager.persist(entity);
        entityManager.flush();

        AuditTestEntity saved = entityManager.find(AuditTestEntity.class, entity.getId());

        assertThat(saved.getCreatedBy()).isEqualTo("alice@example.com");
        assertThat(saved.getUpdatedBy()).isEqualTo("alice@example.com");
    }

    @Test
    @Transactional
    void should_fallback_to_system_when_no_authentication() {
        AuditTestEntity entity = new AuditTestEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("no-auth");

        entityManager.persist(entity);
        entityManager.flush();

        AuditTestEntity saved = entityManager.find(AuditTestEntity.class, entity.getId());

        assertThat(saved.getCreatedBy()).isEqualTo("system");
        assertThat(saved.getUpdatedBy()).isEqualTo("system");
    }

    @Entity
    @Table(name = "audit_test_entity")
    static class AuditTestEntity extends AuditableEntity {

        @Id
        private UUID id;

        private String name;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
