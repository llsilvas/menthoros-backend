package br.com.menthoros.backend.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
class AuditConfigTest {

    @Autowired
    private AuditingHandler auditingHandler;

    @Test
    @WithMockUser(username = "testuser")
    void should_have_auditing_enabled() {
        assertThat(auditingHandler).isNotNull();
    }
}
