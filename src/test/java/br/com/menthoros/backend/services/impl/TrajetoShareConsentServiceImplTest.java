package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.lgpd.LgpdProperties;
import br.com.menthoros.backend.dto.output.TrajetoShareConsentOutputDto;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.entity.UsuarioTrajetoShareConsent;
import br.com.menthoros.backend.enums.TrajetoShareConsentEvento;
import br.com.menthoros.backend.exception.ConsentVersionStaleException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.repository.UsuarioTrajetoShareConsentRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrajetoShareConsentServiceImpl")
class TrajetoShareConsentServiceImplTest {

    private static final String VIGENTE = "2026-11-09";

    @Mock private UsuarioTrajetoShareConsentRepository repository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuthenticatedPrincipalResolver principalResolver;

    private TrajetoShareConsentServiceImpl service;
    private final UUID tenantId = UUID.randomUUID();
    private final Usuario usuario = Usuario.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        LgpdProperties props = new LgpdProperties();
        props.setPolicyVersion(VIGENTE);
        service = new TrajetoShareConsentServiceImpl(repository, usuarioRepository, principalResolver, props);
        TenantContext.setTenantId(tenantId);
        // lenient: o caminho de versão defasada recusa antes de resolver o usuário.
        lenient().when(principalResolver.getCurrentSubject()).thenReturn("kc-1");
        lenient().when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("kc-1", tenantId)).thenReturn(Optional.of(usuario));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void ultimoEvento(TrajetoShareConsentEvento evento, String versao) {
        UsuarioTrajetoShareConsent e = UsuarioTrajetoShareConsent.builder()
                .usuario(usuario).tenantId(tenantId).policyVersion(versao).evento(evento)
                .registradoEm(Instant.parse("2026-11-09T10:00:00Z")).build();
        when(repository.findFirstByUsuario_IdAndTenantIdOrderByRegistradoEmDesc(usuario.getId(), tenantId))
                .thenReturn(Optional.of(e));
    }

    private void semEventos() {
        when(repository.findFirstByUsuario_IdAndTenantIdOrderByRegistradoEmDesc(usuario.getId(), tenantId))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("sem eventos: não concedido, com a versão vigente para o aviso apresentar")
    void semEventos_naoConcedido() {
        semEventos();
        TrajetoShareConsentOutputDto dto = service.estadoAtual();
        assertThat(dto.granted()).isFalse();
        assertThat(dto.policyVersion()).isEqualTo(VIGENTE);
        assertThat(dto.registradoEm()).isNull();
    }

    @Test
    @DisplayName("concessão em versão anterior não vale: a Política mudou, o aviso reaparece")
    void concessaoEmVersaoAntiga_naoVale() {
        ultimoEvento(TrajetoShareConsentEvento.CONCEDIDO, "2026-10-26");
        assertThat(service.estadoAtual().granted()).isFalse();
    }

    @Test
    @DisplayName("conceder grava evento CONCEDIDO com a versão vigente (CA3)")
    void conceder_grava() {
        semEventos();
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TrajetoShareConsentOutputDto dto = service.conceder(VIGENTE);

        ArgumentCaptor<UsuarioTrajetoShareConsent> cap = ArgumentCaptor.forClass(UsuarioTrajetoShareConsent.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getEvento()).isEqualTo(TrajetoShareConsentEvento.CONCEDIDO);
        assertThat(cap.getValue().getPolicyVersion()).isEqualTo(VIGENTE);
        assertThat(cap.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(dto.granted()).isTrue();
    }

    @Test
    @DisplayName("versão apresentada diferente da vigente: 409 e nada é gravado (CA6)")
    void conceder_versaoDefasada() {
        assertThatThrownBy(() -> service.conceder("2026-10-26")).isInstanceOf(ConsentVersionStaleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("conceder de novo na versão vigente é idempotente (CA9)")
    void conceder_idempotente() {
        ultimoEvento(TrajetoShareConsentEvento.CONCEDIDO, VIGENTE);
        assertThat(service.conceder(VIGENTE).granted()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("revogar grava evento REVOGADO e o estado vira não concedido (CA7)")
    void revogar_grava() {
        ultimoEvento(TrajetoShareConsentEvento.CONCEDIDO, VIGENTE);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TrajetoShareConsentOutputDto dto = service.revogar();

        ArgumentCaptor<UsuarioTrajetoShareConsent> cap = ArgumentCaptor.forClass(UsuarioTrajetoShareConsent.class);
        verify(repository).save(cap.capture());
        assertThat(cap.getValue().getEvento()).isEqualTo(TrajetoShareConsentEvento.REVOGADO);
        assertThat(dto.granted()).isFalse();
    }

    @Test
    @DisplayName("revogar sem nunca ter concedido, ou já revogado, não grava")
    void revogar_idempotente() {
        semEventos();
        service.revogar();
        ultimoEvento(TrajetoShareConsentEvento.REVOGADO, VIGENTE);
        service.revogar();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("conceder depois de revogar, na mesma versão, grava nova concessão")
    void concederDepoisDeRevogar() {
        ultimoEvento(TrajetoShareConsentEvento.REVOGADO, VIGENTE);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.conceder(VIGENTE).granted()).isTrue();
        verify(repository).save(any());
    }

    @Test
    @DisplayName("usuário do token fora do tenant: não encontrado, nada é gravado")
    void usuarioForaDoTenant() {
        when(usuarioRepository.findByKeycloakIdAndAssessoria_Id("kc-1", tenantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.conceder(VIGENTE)).isInstanceOf(DomainNotFoundException.class);
        verify(repository, never()).save(any());
    }
}
