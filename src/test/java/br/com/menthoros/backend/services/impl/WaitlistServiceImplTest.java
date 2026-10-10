package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.config.lgpd.LgpdProperties;
import br.com.menthoros.backend.dto.input.WaitlistInputDto;
import br.com.menthoros.backend.entity.Waitlist;
import br.com.menthoros.backend.enums.FaixaAtletas;
import br.com.menthoros.backend.enums.PerfilWaitlist;
import br.com.menthoros.backend.enums.WatchBrand;
import br.com.menthoros.backend.events.WaitlistLeadCreatedEvent;
import br.com.menthoros.backend.repository.WaitlistRepository;
import br.com.menthoros.backend.services.WaitlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceImplTest {

    private static final String POLICY_VERSION = "2026-08-03";

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WaitlistServiceImpl waitlistService;

    @BeforeEach
    void setUp() {
        LgpdProperties lgpdProperties = new LgpdProperties();
        lgpdProperties.setPolicyVersion(POLICY_VERSION);
        waitlistService = new WaitlistServiceImpl(waitlistRepository, eventPublisher, lgpdProperties);
    }

    @Nested
    @DisplayName("registrar — criação")
    class Registrar {

        @Test
        @DisplayName("persiste novo e-mail e retorna CRIADO")
        void novoEmailRetornaCriado() {
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.empty());
            when(waitlistRepository.saveAndFlush(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));

            WaitlistService.Resultado resultado = waitlistService.registrar(
                    dto("Maria@Exemplo.com", PerfilWaitlist.TREINADOR, FaixaAtletas.DE_11_A_30, null));

            assertThat(resultado).isEqualTo(WaitlistService.Resultado.CRIADO);
            verify(waitlistRepository).saveAndFlush(any(Waitlist.class));
        }

        @Test
        @DisplayName("lead criado publica WaitlistLeadCreatedEvent com o id salvo")
        void criadoPublicaEvento() {
            UUID id = UUID.randomUUID();
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.empty());
            when(waitlistRepository.saveAndFlush(any(Waitlist.class)))
                    .thenAnswer(inv -> ((Waitlist) inv.getArgument(0)).toBuilder().id(id).build());

            waitlistService.registrar(dto("maria@exemplo.com", PerfilWaitlist.TREINADOR, FaixaAtletas.DE_11_A_30, null));

            ArgumentCaptor<WaitlistLeadCreatedEvent> captor = ArgumentCaptor.forClass(WaitlistLeadCreatedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().waitlistId()).isEqualTo(id);
        }

        @Test
        @DisplayName("executor de notificação saturado (publishEvent lança) não derruba o cadastro")
        void falhaAoPublicarEventoNaoDerrubaCadastro() {
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.empty());
            when(waitlistRepository.saveAndFlush(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));
            org.mockito.Mockito.doThrow(new org.springframework.core.task.TaskRejectedException("pool saturado"))
                    .when(eventPublisher).publishEvent(any());

            WaitlistService.Resultado resultado = waitlistService.registrar(
                    dto("maria@exemplo.com", PerfilWaitlist.TREINADOR, FaixaAtletas.DE_11_A_30, null));

            assertThat(resultado).isEqualTo(WaitlistService.Resultado.CRIADO);
        }

        @Test
        @DisplayName("honeypot não publica evento")
        void honeypotNaoPublicaEvento() {
            waitlistService.registrar(dto("bot@exemplo.com", PerfilWaitlist.TREINADOR, null, "http://spam.example"));

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("grava e-mail normalizado (trim + lowercase) preservando o original")
        void normalizaEmail() {
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.empty());
            when(waitlistRepository.saveAndFlush(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);

            waitlistService.registrar(dto("  Maria@Exemplo.com  ", PerfilWaitlist.TREINADOR, FaixaAtletas.DE_11_A_30, null));

            verify(waitlistRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getEmailNormalized()).isEqualTo("maria@exemplo.com");
            assertThat(captor.getValue().getEmail()).isEqualTo("Maria@Exemplo.com");
        }

        @Test
        @DisplayName("corrida: violação do índice único é convertida em JA_INSCRITO")
        void corridaRetornaJaInscrito() {
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.empty());
            when(waitlistRepository.saveAndFlush(any(Waitlist.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_waitlist_email_normalized"));

            WaitlistService.Resultado resultado = waitlistService.registrar(
                    dto("maria@exemplo.com", PerfilWaitlist.ATLETA, null, null));

            assertThat(resultado).isEqualTo(WaitlistService.Resultado.JA_INSCRITO);
        }

        @Test
        @DisplayName("honeypot preenchido retorna IGNORADO sem persistir nem consultar")
        void honeypotRetornaIgnorado() {
            WaitlistService.Resultado resultado = waitlistService.registrar(
                    dto("bot@exemplo.com", PerfilWaitlist.TREINADOR, null, "http://spam.example"));

            assertThat(resultado).isEqualTo(WaitlistService.Resultado.IGNORADO);
            verify(waitlistRepository, never()).saveAndFlush(any());
            verify(waitlistRepository, never()).findByEmailNormalized(any());
        }

        @Test
        @DisplayName("faixa de atletas é ignorada quando o perfil é ATLETA")
        void atletaNaoGravaQtdAtletas() {
            when(waitlistRepository.findByEmailNormalized("joao@exemplo.com")).thenReturn(Optional.empty());
            when(waitlistRepository.saveAndFlush(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);

            waitlistService.registrar(dto("joao@exemplo.com", PerfilWaitlist.ATLETA, FaixaAtletas.MAIS_DE_100, null));

            verify(waitlistRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getQtdAtletas()).isNull();
        }

        @Test
        @DisplayName("PROPRIETARIO grava qtdAtletas e watchBrand, igual a TREINADOR")
        void proprietarioTratadoComoTreinador() {
            when(waitlistRepository.findByEmailNormalized("ana@exemplo.com")).thenReturn(Optional.empty());
            when(waitlistRepository.saveAndFlush(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);

            WaitlistInputDto dto = new WaitlistInputDto("Ana Proprietária", "ana@exemplo.com", null,
                    PerfilWaitlist.PROPRIETARIO, FaixaAtletas.DE_11_A_30, WatchBrand.GARMIN, true, null,
                    null, null, null, null, null, null);

            waitlistService.registrar(dto);

            verify(waitlistRepository).saveAndFlush(captor.capture());
            Waitlist salvo = captor.getValue();
            assertThat(salvo.getQtdAtletas()).isEqualTo(FaixaAtletas.DE_11_A_30);
            assertThat(salvo.getWatchBrand()).isEqualTo(WatchBrand.GARMIN);
        }

        @Test
        @DisplayName("dto nulo lança IllegalArgumentException")
        void dtoNuloLanca() {
            assertThatThrownBy(() -> waitlistService.registrar(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nulo");
            verify(waitlistRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("e-mail em branco lança IllegalArgumentException")
        void emailEmBrancoLanca() {
            WaitlistInputDto dto = new WaitlistInputDto("Maria", "   ", null, PerfilWaitlist.ATLETA, null, null,
                    true, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> waitlistService.registrar(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("E-mail");
            verify(waitlistRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("nome em branco lança IllegalArgumentException")
        void nomeEmBrancoLanca() {
            WaitlistInputDto dto = new WaitlistInputDto("  ", "joao@exemplo.com", null, PerfilWaitlist.ATLETA, null,
                    null, true, null, null, null, null, null, null, null);
            assertThatThrownBy(() -> waitlistService.registrar(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Nome");
            verify(waitlistRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("grava os 4 campos UTM e landingPath/referrer quando enviados")
        void gravaUtmQuandoEnviado() {
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.empty());
            when(waitlistRepository.saveAndFlush(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);

            WaitlistInputDto dto = new WaitlistInputDto("Maria Treinadora", "maria@exemplo.com", null,
                    PerfilWaitlist.TREINADOR, FaixaAtletas.DE_11_A_30, WatchBrand.GARMIN, true, null,
                    "instagram", "social", "turma-fundadora", "bio-link", "/waitlist", "https://instagram.com");

            waitlistService.registrar(dto);

            verify(waitlistRepository).saveAndFlush(captor.capture());
            Waitlist salvo = captor.getValue();
            assertThat(salvo.getUtmSource()).isEqualTo("instagram");
            assertThat(salvo.getUtmMedium()).isEqualTo("social");
            assertThat(salvo.getUtmCampaign()).isEqualTo("turma-fundadora");
            assertThat(salvo.getUtmContent()).isEqualTo("bio-link");
            assertThat(salvo.getLandingPath()).isEqualTo("/waitlist");
            assertThat(salvo.getReferrer()).isEqualTo("https://instagram.com");
        }

        @Test
        @DisplayName("ausência de UTM grava as 4 colunas como null (cliente antigo intocado)")
        void semUtmGravaNull() {
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.empty());
            when(waitlistRepository.saveAndFlush(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);

            waitlistService.registrar(dto("maria@exemplo.com", PerfilWaitlist.TREINADOR, FaixaAtletas.DE_11_A_30, null));

            verify(waitlistRepository).saveAndFlush(captor.capture());
            Waitlist salvo = captor.getValue();
            assertThat(salvo.getUtmSource()).isNull();
            assertThat(salvo.getUtmMedium()).isNull();
            assertThat(salvo.getUtmCampaign()).isNull();
            assertThat(salvo.getUtmContent()).isNull();
        }

        @Test
        @DisplayName("policyVersion é sempre carimbado de LgpdProperties, nunca aceito do DTO")
        void policyVersionVemDoServidor() {
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.empty());
            when(waitlistRepository.saveAndFlush(any(Waitlist.class))).thenAnswer(inv -> inv.getArgument(0));
            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);

            waitlistService.registrar(dto("maria@exemplo.com", PerfilWaitlist.TREINADOR, FaixaAtletas.DE_11_A_30, null));

            verify(waitlistRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getPolicyVersion()).isEqualTo(POLICY_VERSION);
        }
    }

    @Nested
    @DisplayName("registrar — upsert em reenvio")
    class Upsert {

        @Test
        @DisplayName("e-mail já inscrito atualiza contato/atribuição e retorna JA_INSCRITO, sem publicar evento")
        void reenvioAtualizaERetornaJaInscrito() {
            UUID id = UUID.randomUUID();
            Waitlist existente = Waitlist.builder()
                    .id(id).nome("Maria").email("maria@exemplo.com").emailNormalized("maria@exemplo.com")
                    .perfil(PerfilWaitlist.TREINADOR).qtdAtletas(FaixaAtletas.ATE_10).aceiteLgpd(true)
                    .policyVersion(POLICY_VERSION)
                    .utmSource("instagram").utmContent("bio-link-antigo")
                    .build();
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.of(existente));

            WaitlistInputDto dto = new WaitlistInputDto("Maria Treinadora", "maria@exemplo.com", "+55 11 90000-0000",
                    PerfilWaitlist.TREINADOR, FaixaAtletas.DE_11_A_30, WatchBrand.COROS, true, null,
                    "google", "cpc", "reenvio", "outro-link", null, null);

            WaitlistService.Resultado resultado = waitlistService.registrar(dto);

            assertThat(resultado).isEqualTo(WaitlistService.Resultado.JA_INSCRITO);
            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);
            verify(waitlistRepository).save(captor.capture());
            Waitlist atualizado = captor.getValue();
            assertThat(atualizado.getNome()).isEqualTo("Maria Treinadora");
            assertThat(atualizado.getTelefone()).isEqualTo("+55 11 90000-0000");
            assertThat(atualizado.getQtdAtletas()).isEqualTo(FaixaAtletas.DE_11_A_30);
            assertThat(atualizado.getWatchBrand()).isEqualTo(WatchBrand.COROS);
            verify(waitlistRepository, never()).saveAndFlush(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("reenvio NUNCA sobrescreve perfil, aceiteLgpd ou policyVersion de linha existente (anti-forjamento)")
        void reenvioNaoSobrescrevePerfilNemConsentimento() {
            Waitlist existente = Waitlist.builder()
                    .id(UUID.randomUUID()).nome("Maria").email("maria@exemplo.com").emailNormalized("maria@exemplo.com")
                    .perfil(PerfilWaitlist.ATLETA).aceiteLgpd(true).policyVersion("2025-01-01")
                    .build();
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.of(existente));

            // Alguém que só sabe o e-mail de "maria" tenta virar o perfil dela para TREINADOR.
            WaitlistInputDto dto = new WaitlistInputDto("Outro Nome", "maria@exemplo.com", null,
                    PerfilWaitlist.TREINADOR, FaixaAtletas.DE_11_A_30, WatchBrand.GARMIN, true, null,
                    null, null, null, null, null, null);

            waitlistService.registrar(dto);

            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);
            verify(waitlistRepository).save(captor.capture());
            Waitlist atualizado = captor.getValue();
            assertThat(atualizado.getPerfil()).isEqualTo(PerfilWaitlist.ATLETA);
            assertThat(atualizado.isAceiteLgpd()).isTrue();
            assertThat(atualizado.getPolicyVersion()).isEqualTo("2025-01-01");
            // Perfil ficou ATLETA (congelado) — watchBrand/qtdAtletas do reenvio são ignorados.
            assertThat(atualizado.getQtdAtletas()).isNull();
            assertThat(atualizado.getWatchBrand()).isNull();
        }

        @Test
        @DisplayName("reenvio não sobrescreve o UTM original (first-touch)")
        void reenvioPreservaUtmOriginal() {
            Waitlist existente = Waitlist.builder()
                    .id(UUID.randomUUID()).nome("Maria").email("maria@exemplo.com").emailNormalized("maria@exemplo.com")
                    .perfil(PerfilWaitlist.ATLETA).aceiteLgpd(true)
                    .utmSource("instagram").utmMedium("social").utmCampaign("turma-fundadora").utmContent("bio-link")
                    .build();
            when(waitlistRepository.findByEmailNormalized("maria@exemplo.com")).thenReturn(Optional.of(existente));

            // Reenvio sem nenhum UTM (ex.: pessoa voltando direto ao site) não pode apagar a origem real.
            WaitlistInputDto dto = new WaitlistInputDto("Maria", "maria@exemplo.com", null,
                    PerfilWaitlist.ATLETA, null, null, true, null, null, null, null, null, null, null);

            waitlistService.registrar(dto);

            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);
            verify(waitlistRepository).save(captor.capture());
            Waitlist atualizado = captor.getValue();
            assertThat(atualizado.getUtmSource()).isEqualTo("instagram");
            assertThat(atualizado.getUtmMedium()).isEqualTo("social");
            assertThat(atualizado.getUtmCampaign()).isEqualTo("turma-fundadora");
            assertThat(atualizado.getUtmContent()).isEqualTo("bio-link");
        }
    }

    private WaitlistInputDto dto(String email, PerfilWaitlist perfil, FaixaAtletas faixa, String website) {
        return new WaitlistInputDto("Maria Treinadora", email, "+55 11 99999-9999", perfil, faixa, null, true,
                website, null, null, null, null, null, null);
    }
}
