package br.com.menthoros.backend.domain.entity;

import br.com.menthoros.backend.domain.audit.AuditableEntity;
import br.com.menthoros.backend.entity.Atleta;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying that {@link Atleta} correctly inherits the audit trail
 * from {@link AuditableEntity}.
 *
 * <p>These tests are pure reflection-based unit tests that do not require a
 * database or Spring context. They confirm the structural requirement that
 * {@code Atleta} participates in the JPA audit mechanism.
 */
@DisplayName("Atleta - AuditableEntity inheritance")
class AtletaAuditTest {

    @Test
    @DisplayName("Atleta must extend AuditableEntity")
    void atleta_must_extend_auditable_entity() {
        assertThat(AuditableEntity.class).isAssignableFrom(Atleta.class);
    }

    @Test
    @DisplayName("AuditableEntity must be annotated with @MappedSuperclass")
    void auditable_entity_must_be_mapped_superclass() {
        assertThat(AuditableEntity.class).hasAnnotation(MappedSuperclass.class);
    }

    @Test
    @DisplayName("AuditableEntity must register the JPA AuditingEntityListener")
    void auditable_entity_must_register_auditing_listener() {
        EntityListeners listeners = AuditableEntity.class.getAnnotation(EntityListeners.class);
        assertThat(listeners).isNotNull();
        assertThat(listeners.value())
            .contains(org.springframework.data.jpa.domain.support.AuditingEntityListener.class);
    }

    @Test
    @DisplayName("createdAt field must exist on AuditableEntity with correct type and annotations")
    void created_at_field_must_be_present_and_annotated() throws NoSuchFieldException {
        Field field = AuditableEntity.class.getDeclaredField("createdAt");
        assertThat(field.getType()).isEqualTo(LocalDateTime.class);
        assertThat(field.getAnnotation(CreatedDate.class)).isNotNull();
        Column col = field.getAnnotation(Column.class);
        assertThat(col).isNotNull();
        assertThat(col.nullable()).isFalse();
        assertThat(col.updatable()).isFalse();
    }

    @Test
    @DisplayName("updatedAt field must exist on AuditableEntity with correct type and annotations")
    void updated_at_field_must_be_present_and_annotated() throws NoSuchFieldException {
        Field field = AuditableEntity.class.getDeclaredField("updatedAt");
        assertThat(field.getType()).isEqualTo(LocalDateTime.class);
        assertThat(field.getAnnotation(LastModifiedDate.class)).isNotNull();
        Column col = field.getAnnotation(Column.class);
        assertThat(col).isNotNull();
        assertThat(col.nullable()).isFalse();
    }

    @Test
    @DisplayName("createdBy field must exist on AuditableEntity with correct type and annotations")
    void created_by_field_must_be_present_and_annotated() throws NoSuchFieldException {
        Field field = AuditableEntity.class.getDeclaredField("createdBy");
        assertThat(field.getType()).isEqualTo(String.class);
        assertThat(field.getAnnotation(CreatedBy.class)).isNotNull();
    }

    @Test
    @DisplayName("updatedBy field must exist on AuditableEntity with correct type and annotations")
    void updated_by_field_must_be_present_and_annotated() throws NoSuchFieldException {
        Field field = AuditableEntity.class.getDeclaredField("updatedBy");
        assertThat(field.getType()).isEqualTo(String.class);
        assertThat(field.getAnnotation(LastModifiedBy.class)).isNotNull();
    }

    @Test
    @DisplayName("Atleta inherits getCreatedAt() from AuditableEntity")
    void atleta_inherits_get_created_at() throws NoSuchMethodException {
        assertThat(Atleta.class.getMethod("getCreatedAt").getReturnType())
            .isEqualTo(LocalDateTime.class);
    }

    @Test
    @DisplayName("Atleta inherits getCreatedBy() from AuditableEntity")
    void atleta_inherits_get_created_by() throws NoSuchMethodException {
        assertThat(Atleta.class.getMethod("getCreatedBy").getReturnType())
            .isEqualTo(String.class);
    }
}
