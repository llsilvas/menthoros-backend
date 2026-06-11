package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioSyncServiceImplLinkTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AssessoriaRepository assessoriaRepository;
    @Mock private AtletaRepository atletaRepository;

    @InjectMocks private UsuarioSyncServiceImpl service;

    private Jwt jwtAtleta() {
        return Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("email", "ana@teste.com")
                .claim("given_name", "Ana")
                .claim("family_name", "Atleta")
                .claim("email_verified", true)
                .claim("realm_access", Map.of("roles", List.of("ATLETA")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private void mockTenantECriacao(UUID tenantId) {
        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);
        when(assessoriaRepository.findById(tenantId)).thenReturn(Optional.of(assessoria));
        when(usuarioRepository.findByKeycloakId(any())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
    }

    @Test
    void vinculaAtletaQuandoRoleAtletaEEmailBate() {
        UUID tenantId = UUID.randomUUID();
        mockTenantECriacao(tenantId);

        Atleta atletaSemVinculo = new Atleta();
        atletaSemVinculo.setId(UUID.randomUUID());
        atletaSemVinculo.setEmail("ana@teste.com");
        when(atletaRepository.findByEmailAndAssessoria_Id("ana@teste.com", tenantId))
                .thenReturn(Optional.of(atletaSemVinculo));

        service.syncUsuarioFromJwt(jwtAtleta(), tenantId);

        ArgumentCaptor<Atleta> captor = ArgumentCaptor.forClass(Atleta.class);
        verify(atletaRepository).save(captor.capture());
        assertThat(captor.getValue().getUsuario()).isNotNull();
    }

    @Test
    void naoVinculaQuandoAtletaJaTemUsuario() {
        UUID tenantId = UUID.randomUUID();
        mockTenantECriacao(tenantId);

        Usuario usuarioExistente = new Usuario();
        usuarioExistente.setId(UUID.randomUUID());

        Atleta atletaComVinculo = new Atleta();
        atletaComVinculo.setId(UUID.randomUUID());
        atletaComVinculo.setEmail("ana@teste.com");
        atletaComVinculo.setUsuario(usuarioExistente);
        when(atletaRepository.findByEmailAndAssessoria_Id("ana@teste.com", tenantId))
                .thenReturn(Optional.of(atletaComVinculo));

        service.syncUsuarioFromJwt(jwtAtleta(), tenantId);

        verify(atletaRepository, never()).save(any(Atleta.class));
    }
}
