package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.dto.strava.StravaActivityDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.FonteDados;
import br.com.menthoros.backend.events.TreinoRegistradoEvent;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import br.com.menthoros.backend.mapper.TreinoMapper;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.IntegracaoExternaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import br.com.menthoros.backend.services.impl.StravaActivityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StravaActivityServiceImpl.enriquecerTreinoComStrava.
 *
 * Verifies: RPE auto-fill from perceived_exertion, event publishing gate,
 * immutability of existing RPE, and validation guards.
 */
@ExtendWith(MockitoExtension.class)
class EnriquecerStravaServiceTest {

    @Mock private AtletaRepository atletaRepository;
    @Mock private TreinoRealizadoRepository treinoRealizadoRepository;
    @Mock private IntegracaoExternaRepository integracaoExternaRepository;
    @Mock private StravaOAuthService stravaOAuthService;
    @Mock private TsbService tsbService;
    @Mock private TreinoMapper treinoMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private WebClient stravaWebClient;
    @Mock private TreinoDedupHelper treinoDedupHelper;

    @Mock private WebClient.RequestHeadersUriSpec<?> uriSpec;
    @Mock private WebClient.RequestHeadersSpec<?> headersSpec;
    @Mock private WebClient.ResponseSpec responseSpec;

    private StravaActivityServiceImpl service;
    private UUID treinoId;
    private UUID tenantId;
    private TreinoRealizado treinoStrava;

    @BeforeEach
    void setUp() {
        service = new StravaActivityServiceImpl(
                atletaRepository,
                treinoRealizadoRepository,
                integracaoExternaRepository,
                stravaOAuthService,
                tsbService,
                treinoMapper,
                eventPublisher,
                stravaWebClient,
                treinoDedupHelper
        );

        treinoId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        Assessoria assessoria = new Assessoria();
        assessoria.setId(tenantId);

        Atleta atleta = new Atleta();
        atleta.setId(UUID.randomUUID());
        atleta.setAssessoria(assessoria);

        treinoStrava = new TreinoRealizado();
        treinoStrava.setId(treinoId);
        treinoStrava.setTenantId(tenantId);
        treinoStrava.setFonteDados(FonteDados.STRAVA);
        treinoStrava.setExternalId("987654321");
        treinoStrava.setAtleta(atleta);
    }

    // -------------------------------------------------------------------------
    // RPE auto-fill
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deve preencher percepcaoEsforco quando Strava retorna perceived_exertion e RPE está nulo")
    void enriquecer_rpeNulo_preencheRpeEPublicaEvento() {
        treinoStrava.setPercepcaoEsforco(null);

        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(treinoStrava));
        when(stravaOAuthService.getValidToken(any())).thenReturn("access-token");
        stubStravaCall(stravaActivity(8.0));
        when(treinoRealizadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mockOutputDto());

        service.enriquecerTreinoComStrava(treinoId, tenantId);

        assertThat(treinoStrava.getPercepcaoEsforco()).isEqualTo(8);
        verify(eventPublisher).publishEvent(any(TreinoRegistradoEvent.class));
    }

    @Test
    @DisplayName("deve arredondar perceived_exertion fracionado para o inteiro mais próximo")
    void enriquecer_perceivedExertionFracionado_arrondaMath() {
        treinoStrava.setPercepcaoEsforco(null);

        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(treinoStrava));
        when(stravaOAuthService.getValidToken(any())).thenReturn("token");
        stubStravaCall(stravaActivity(7.6));
        when(treinoRealizadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mockOutputDto());

        service.enriquecerTreinoComStrava(treinoId, tenantId);

        assertThat(treinoStrava.getPercepcaoEsforco()).isEqualTo(8);
    }

    // -------------------------------------------------------------------------
    // Preservação de RPE existente
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("não deve sobrescrever percepcaoEsforco quando já definido pelo coach")
    void enriquecer_rpeJaDefinido_preservaValorExistente() {
        treinoStrava.setPercepcaoEsforco(7);

        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(treinoStrava));
        when(stravaOAuthService.getValidToken(any())).thenReturn("token");
        stubStravaCall(stravaActivity(9.0));
        when(treinoRealizadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mockOutputDto());

        service.enriquecerTreinoComStrava(treinoId, tenantId);

        assertThat(treinoStrava.getPercepcaoEsforco()).isEqualTo(7);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("não deve publicar evento quando Strava não retorna perceived_exertion")
    void enriquecer_semPerceivedExertion_naoPublicaEvento() {
        treinoStrava.setPercepcaoEsforco(null);

        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(treinoStrava));
        when(stravaOAuthService.getValidToken(any())).thenReturn("token");
        stubStravaCall(stravaActivity(null));
        when(treinoRealizadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(treinoMapper.toOutputDto(any(TreinoRealizado.class))).thenReturn(mockOutputDto());

        service.enriquecerTreinoComStrava(treinoId, tenantId);

        assertThat(treinoStrava.getPercepcaoEsforco()).isNull();
        verify(eventPublisher, never()).publishEvent(any());
    }

    // -------------------------------------------------------------------------
    // Guards de validação
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("lança DomainNotFoundException quando treino não é encontrado")
    void enriquecer_treinoNaoEncontrado_lancaNotFound() {
        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enriquecerTreinoComStrava(treinoId, tenantId))
                .isInstanceOf(DomainNotFoundException.class);

        verifyNoInteractions(stravaOAuthService, eventPublisher);
    }

    @Test
    @DisplayName("lança DomainRuleViolationException quando fonteDados não é STRAVA")
    void enriquecer_fonteDadosNaoStrava_lancaRuleViolation() {
        treinoStrava.setFonteDados(FonteDados.GARMIN);

        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(treinoStrava));

        assertThatThrownBy(() -> service.enriquecerTreinoComStrava(treinoId, tenantId))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("Strava");

        verifyNoInteractions(stravaOAuthService, eventPublisher);
    }

    @Test
    @DisplayName("lança DomainRuleViolationException quando externalId é nulo")
    void enriquecer_externalIdNulo_lancaRuleViolation() {
        treinoStrava.setExternalId(null);

        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(treinoStrava));

        assertThatThrownBy(() -> service.enriquecerTreinoComStrava(treinoId, tenantId))
                .isInstanceOf(DomainRuleViolationException.class);

        verifyNoInteractions(stravaOAuthService, eventPublisher);
    }

    @Test
    @DisplayName("lança DomainRuleViolationException quando externalId não é numérico")
    void enriquecer_externalIdInvalido_lancaRuleViolation() {
        treinoStrava.setExternalId("nao-numerico");

        when(treinoRealizadoRepository.findByIdAndTenantId(treinoId, tenantId))
                .thenReturn(Optional.of(treinoStrava));

        // parseLong falha antes de getValidToken ser chamado
        assertThatThrownBy(() -> service.enriquecerTreinoComStrava(treinoId, tenantId))
                .isInstanceOf(DomainRuleViolationException.class);

        verifyNoInteractions(stravaOAuthService, eventPublisher);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubStravaCall(StravaActivityDto response) {
        // Raw-type casts are necessary to bypass WebClient's complex nested generics in unit tests.
        WebClient.RequestHeadersUriSpec rawUri = uriSpec;
        WebClient.RequestHeadersSpec rawHeaders = headersSpec;
        doReturn(rawUri).when(stravaWebClient).get();
        doReturn(rawHeaders).when(rawUri).uri(any(java.util.function.Function.class));
        doReturn(rawHeaders).when(rawHeaders).header(anyString(), anyString());
        doReturn(responseSpec).when(rawHeaders).retrieve();
        doReturn(Mono.just(response)).when(responseSpec).bodyToMono(StravaActivityDto.class);
    }

    private StravaActivityDto stravaActivity(Double perceivedExertion) {
        return new StravaActivityDto(
                987654321L, "Corrida", "Run", "2026-05-18T07:00:00Z",
                10000d, 3600, 3700, 50d, 2.77d, 155d, 180d,
                true, 65, perceivedExertion, "Treino forte",
                false, 0, 87d, "Garmin", null, List.of()
        );
    }

    private TreinoRealizadoOutputDto mockOutputDto() {
        return new TreinoRealizadoOutputDto(
                treinoId, null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, // intensidadeReal
                null, // runningDynamics
                null, null, null,
                null, null, null, null,
                null, null, null, null
        );
    }
}
