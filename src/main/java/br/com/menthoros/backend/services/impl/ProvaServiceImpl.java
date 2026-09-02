package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.ProvaAtletaInputDto;
import br.com.menthoros.backend.dto.input.ProvaInputDto;
import br.com.menthoros.backend.dto.output.ProvaOutputDto;
import br.com.menthoros.backend.dto.output.ProvaProximaDto;
import br.com.menthoros.backend.dto.output.ProvasProximasResponseDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.MotivoRevisaoProva;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.ProvaRealizadaImutavelException;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.mapper.ProvaMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.security.AuthenticatedAtletaResolver;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.ProvaService;
import br.com.menthoros.backend.services.helper.ProvaEnricher;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvaServiceImpl implements ProvaService {

    private final ProvaRepository provaRepository;
    private final AtletaRepository atletaRepository;
    private final AssessoriaRepository assessoriaRepository;
    private final ProvaMapper provaMapper;
    private final ProvaEnricher provaEnricher;
    private final AuthenticatedAtletaResolver atletaResolver;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final Validator validator;
    private final Clock clock;

    /**
     * Resolve o atleta pelo ID garantindo isolamento por tenant.
     * Usa TenantContext.getRequiredTenantId() — lança IllegalStateException se tenant ausente.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId()
     *
     * @param atletaId ID do atleta
     * @return Atleta pertencente ao tenant atual
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se o atleta não for encontrado no tenant
     */
    private Atleta resolveAtleta(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));
    }

    /**
     * Posse: um principal que atua só como atleta opera exclusivamente no próprio {@code atletaId}.
     * A violação responde como "não encontrado" (404), indistinguível de atleta inexistente — mesmo
     * padrão do isolamento de tenant (spec prova-crud, "Isolamento multi-tenancy").
     */
    private Atleta resolveAtletaComPosse(UUID atletaId) {
        Atleta atleta = resolveAtleta(atletaId);
        if (atletaResolver.atuaComoAtleta() && !atletaId.equals(atletaResolver.resolverAtletaIdAtual())) {
            log.warn("Atleta tentou operar sobre prova de outro atleta: atletaId={}", atletaId);
            throw new ResourceNotFoundException("Atleta não encontrado: " + atletaId);
        }
        return atleta;
    }

    private Prova resolveProva(Atleta atleta, UUID provaId) {
        // tenant-aware: usa assessoria do atleta como tenant para garantir isolamento cross-tenant
        UUID tenantId = atleta.getAssessoria().getId();
        return provaRepository.findByIdAndTenantId(provaId, tenantId)
                .filter(p -> p.getAtleta().getId().equals(atleta.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Prova não encontrada: " + provaId));
    }

    /**
     * Cria uma prova para o atleta dentro do tenant da requisição atual. Deriva os campos de
     * preparação e, se a prova nasce como alvo, desmarca a alvo anterior do atleta. Para o
     * atleta, só o subconjunto {@link ProvaAtletaInputDto} é aceito (data futura obrigatória).
     *
     * Idempotent: NO — Cria nova entidade a cada chamada.
     * Side Effects: Database insert (nova Prova criada); update na prova-alvo anterior, se houver
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @param atletaId ID do atleta para quem a prova será criada
     * @param dto dados da prova
     * @return ProvaOutputDto com os dados da prova criada
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se o atleta não for encontrado no tenant ou não for o do principal atleta
     * @throws ConstraintViolationException se o atleta enviar data não futura ou distância customizada sem km
     */
    @Override
    @Transactional
    public ProvaOutputDto criarProva(UUID atletaId, ProvaInputDto dto) {
        Atleta atleta = resolveAtletaComPosse(atletaId);
        boolean atorAtleta = atletaResolver.atuaComoAtleta();
        Prova prova = atorAtleta
                ? provaMapper.toEntity(validarAtleta(ProvaAtletaInputDto.from(dto)))
                : provaMapper.toEntity(dto);
        prova.setAtleta(atleta);
        prova.setAssessoria(atleta.getAssessoria());
        if (prova.getStatusProva() == null) {
            prova.setStatusProva(ProvaStatus.PLANEJADA);
        }
        if (prova.getFoiRealizada() == null) {
            prova.setFoiRealizada(false);
        }
        provaEnricher.aplicarDerivados(prova);
        garantirAlvoUnica(atleta, prova);
        if (atorAtleta) {
            marcarPendente(prova, MotivoRevisaoProva.NOVA, null);
        }
        Prova salva = provaRepository.save(prova);
        log.info("Prova criada: id={}, atletaId={}, alvo={}, atorAtleta={}",
                salva.getId(), atletaId, salva.isProvaAlvo(), atorAtleta);
        return provaMapper.toOutputDto(salva);
    }

    /**
     * Lista as provas de um atleta dentro do tenant da requisição atual.
     *
     * Idempotent: YES — Operação de leitura, sem alteração de estado.
     * Side Effects: NONE
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @param atletaId ID do atleta cujas provas serão listadas
     * @return lista de ProvaOutputDto com as provas do atleta
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se o atleta não for encontrado no tenant ou não for o do principal atleta
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProvaOutputDto> listarProvas(UUID atletaId) {
        Atleta atleta = resolveAtletaComPosse(atletaId);
        return provaRepository.findByAtletaOrderByDataProvaAsc(atleta)
                .stream()
                .map(provaMapper::toOutputDto)
                .toList();
    }

    /**
     * Busca uma prova específica de um atleta dentro do tenant da requisição atual.
     *
     * Idempotent: YES — Operação de leitura, sem alteração de estado.
     * Side Effects: NONE
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @param atletaId ID do atleta proprietário da prova
     * @param provaId ID da prova a ser buscada
     * @return ProvaOutputDto com os dados da prova
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se atleta ou prova não forem encontrados no tenant
     */
    @Override
    @Transactional(readOnly = true)
    public ProvaOutputDto buscarProvaPorId(UUID atletaId, UUID provaId) {
        Atleta atleta = resolveAtletaComPosse(atletaId);
        return provaMapper.toOutputDto(resolveProva(atleta, provaId));
    }

    /**
     * Atualiza uma prova de um atleta dentro do tenant da requisição atual. Recalcula os campos
     * derivados e mantém a prova-alvo única. Para o atleta, aplica só o subconjunto
     * {@link ProvaAtletaInputDto} e recusa prova já realizada.
     *
     * Idempotent: YES — Atualizar com os mesmos dados produz o mesmo resultado.
     * Side Effects: Database update (Prova atualizada; prova-alvo anterior desmarcada, se houver)
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @param atletaId ID do atleta proprietário da prova
     * @param provaId ID da prova a ser atualizada
     * @param dto novos dados da prova
     * @return ProvaOutputDto com os dados atualizados
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se atleta ou prova não forem encontrados no tenant
     * @throws ProvaRealizadaImutavelException se o atleta tentar alterar prova realizada (409)
     */
    @Override
    @Transactional
    public ProvaOutputDto atualizarProva(UUID atletaId, UUID provaId, ProvaInputDto dto) {
        Atleta atleta = resolveAtletaComPosse(atletaId);
        Prova prova = resolveProva(atleta, provaId);
        boolean atorAtleta = atletaResolver.atuaComoAtleta();
        EstadoRelevante antes = EstadoRelevante.de(prova);
        if (atorAtleta) {
            exigirNaoRealizada(prova);
            provaMapper.updateEntity(validarAtleta(ProvaAtletaInputDto.from(dto)), prova);
        } else {
            provaMapper.updateEntity(dto, prova);
        }
        provaEnricher.aplicarDerivados(prova);
        Optional<String> alvoSubstituida = garantirAlvoUnica(atleta, prova);
        if (atorAtleta) {
            registrarMudancaDoAtleta(prova, antes, alvoSubstituida);
        }
        return provaMapper.toOutputDto(provaRepository.save(prova));
    }

    /**
     * Remove fisicamente uma prova de um atleta dentro do tenant da requisição atual.
     *
     * Idempotent: YES — Deletar uma prova já removida é seguro (já não existe).
     * Side Effects: Database delete (Prova removida)
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @param atletaId ID do atleta proprietário da prova
     * @param provaId ID da prova a ser removida
     * @throws IllegalStateException se o tenant não estiver configurado (ausência de JWT)
     * @throws ResourceNotFoundException se atleta ou prova não forem encontrados no tenant
     */
    @Override
    @Transactional
    public void deletarProva(UUID atletaId, UUID provaId) {
        Atleta atleta = resolveAtletaComPosse(atletaId);
        Prova prova = resolveProva(atleta, provaId);
        provaRepository.delete(prova);
        log.info("Prova removida fisicamente: id={}, atletaId={}", provaId, atletaId);
    }

    /**
     * Cancela (soft) uma prova: {@code statusProva = CANCELADA}. A prova some das listagens e do
     * planejamento, mas fica preservada. O atleta não pode cancelar prova já realizada.
     *
     * Idempotent: YES — cancelar prova já cancelada mantém o estado.
     * Side Effects: Database update (status)
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @throws ProvaRealizadaImutavelException se o atleta tentar cancelar prova realizada (409)
     */
    @Override
    @Transactional
    public void cancelarProva(UUID atletaId, UUID provaId) {
        Atleta atleta = resolveAtletaComPosse(atletaId);
        Prova prova = resolveProva(atleta, provaId);
        if (atletaResolver.atuaComoAtleta()) {
            exigirNaoRealizada(prova);
            marcarPendente(prova, MotivoRevisaoProva.CANCELADA, null);
        }
        prova.setStatusProva(ProvaStatus.CANCELADA);
        provaRepository.save(prova);
        log.info("Prova cancelada: id={}, atletaId={}", provaId, atletaId);
    }

    /**
     * DELETE do recurso: ADMIN remove fisicamente; atleta e treinador cancelam.
     *
     * Idempotent: YES — segue a operação delegada.
     * Side Effects: Database delete ou update, conforme o papel
     * Tenant-aware: YES
     */
    @Override
    @Transactional
    public void removerProva(UUID atletaId, UUID provaId) {
        if (principalResolver.hasRole(UserRole.ADMIN)) {
            deletarProva(atletaId, provaId);
        } else {
            cancelarProva(atletaId, provaId);
        }
    }

    /**
     * Registra a ciência do coach sobre a última mudança do atleta: {@code revisadaPeloCoach = true},
     * motivo e alvo anterior limpos. Vale também para prova cancelada.
     *
     * Idempotent: YES — prova já revisada permanece igual.
     * Side Effects: Database update (flag e campos de motivo)
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     *
     * @throws ResourceNotFoundException se atleta ou prova não forem encontrados no tenant
     */
    @Override
    @Transactional
    public ProvaOutputDto marcarCiente(UUID atletaId, UUID provaId) {
        Atleta atleta = resolveAtleta(atletaId);
        Prova prova = resolveProva(atleta, provaId);
        if (!prova.isRevisadaPeloCoach()) {
            prova.setRevisadaPeloCoach(true);
            prova.setMotivoRevisao(null);
            prova.setAlvoAnteriorNome(null);
            prova = provaRepository.save(prova);
            log.info("Ciência do coach registrada: provaId={}, atletaId={}", provaId, atletaId);
        }
        return provaMapper.toOutputDto(prova);
    }

    /**
     * Provas do atleta pendentes de ciência do coach (futuras ou canceladas). Lê direto do
     * repositório para não depender do corte da fila de atenção.
     *
     * Idempotent: YES — leitura pura.
     * Side Effects: NONE
     * Tenant-aware: YES — usa TenantContext.getRequiredTenantId() via resolveAtleta()
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProvaOutputDto> listarPendentesRevisao(UUID atletaId) {
        Atleta atleta = resolveAtleta(atletaId);
        return provaRepository.findPendentesRevisaoByAtleta(atleta.getId(), LocalDate.now(clock))
                .stream()
                .map(provaMapper::toOutputDto)
                .toList();
    }

    /**
     * Retorna as provas próximas dos próximos 15 dias de todos os atletas.
     * Operação global — não filtra por tenant, pois é usada para monitoramento de assessoria.
     *
     * Idempotent: YES — Operação de leitura, sem alteração de estado.
     * Side Effects: NONE
     * Tenant-aware: YES — retorna apenas provas de atletas da assessoria atual
     * (TenantContext). A versão anterior era global e vazava dados cross-tenant.
     *
     * @return ProvasProximasResponseDto com provas dos próximos 15 dias do tenant
     */
    @Override
    @Transactional(readOnly = true)
    public ProvasProximasResponseDto getProvasProximas() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate endDate = LocalDate.now().plusDays(15);
        List<Prova> provas = provaRepository.findUpcomingProvasNext15DaysByTenant(endDate, tenantId);

        List<ProvaProximaDto> dtoList = provas.stream()
            .map(p -> {
                LocalDate dataProva = p.getDataProva();
                long diasFaltando = ChronoUnit.DAYS.between(LocalDate.now(), dataProva);

                return new ProvaProximaDto(
                    p.getId(),
                    p.getAtleta().getId(),
                    p.getAtleta().getNome(),
                    p.getNomeProva(),
                    p.getDataProva().toString(),
                    p.getTipoProva().toString(),
                    p.getDistancia().toString(),
                    p.getDistanciaKm() != null ? p.getDistanciaKm().doubleValue() : null,
                    p.getTempoObjetivo() != null ? p.getTempoObjetivo().toString() : null,
                    p.getStatusProva().toString(),
                    Math.toIntExact(diasFaltando)
                );
            })
            .toList();

        return new ProvasProximasResponseDto(
            dtoList,
            dtoList.size(),
            LocalDateTime.now().toString()
        );
    }

    /**
     * Se {@code prova} é alvo, desmarca as demais provas-alvo não canceladas do atleta.
     *
     * @return nome da alvo substituída, quando havia outra prova como alvo
     */
    private Optional<String> garantirAlvoUnica(Atleta atleta, Prova prova) {
        if (!prova.isProvaAlvo()) {
            return Optional.empty();
        }
        String alvoAnterior = null;
        for (Prova outra : provaRepository.findByAtletaAndProvaAlvoTrue(atleta)) {
            if (prova.getId() != null && prova.getId().equals(outra.getId())) {
                continue;
            }
            outra.setProvaAlvo(false);
            provaRepository.save(outra);
            alvoAnterior = outra.getNomeProva();
        }
        return Optional.ofNullable(alvoAnterior);
    }

    /**
     * O DTO completo passa pelo {@code @Valid} do controller; o recorte do atleta tem regras
     * próprias (data futura, km da distância livre), validadas aqui para responder 400 com o campo.
     */
    private ProvaAtletaInputDto validarAtleta(ProvaAtletaInputDto dto) {
        Set<ConstraintViolation<ProvaAtletaInputDto>> violacoes = validator.validate(dto);
        if (!violacoes.isEmpty()) {
            throw new ConstraintViolationException(violacoes);
        }
        return dto;
    }

    /**
     * Regra da spec prova-atencao-coach: só data, distância, quilometragem e prova-alvo zeram a
     * flag; nome e tempo objetivo não. Troca de alvo prevalece sobre mudança de data.
     */
    private void registrarMudancaDoAtleta(Prova prova, EstadoRelevante antes, Optional<String> alvoSubstituida) {
        if (antes.provaAlvo() != prova.isProvaAlvo()) {
            marcarPendente(prova, MotivoRevisaoProva.ALVO_TROCADA, alvoSubstituida.orElse(null));
        } else if (!antes.mesmaDataEDistancia(prova)) {
            marcarPendente(prova, MotivoRevisaoProva.DATA_ALTERADA, null);
        }
    }

    private void marcarPendente(Prova prova, MotivoRevisaoProva motivo, String alvoAnteriorNome) {
        prova.setRevisadaPeloCoach(false);
        prova.setMotivoRevisao(motivo);
        prova.setAlvoAnteriorNome(alvoAnteriorNome);
    }

    /** Foto dos campos cuja mudança pelo atleta exige ciência do coach. */
    private record EstadoRelevante(LocalDate dataProva, DistanciaProva distancia, BigDecimal distanciaKm,
                                   boolean provaAlvo) {
        static EstadoRelevante de(Prova prova) {
            return new EstadoRelevante(prova.getDataProva(), prova.getDistancia(), prova.getDistanciaKm(),
                    prova.isProvaAlvo());
        }

        boolean mesmaDataEDistancia(Prova prova) {
            return Objects.equals(dataProva, prova.getDataProva())
                    && distancia == prova.getDistancia()
                    && (distanciaKm == null ? prova.getDistanciaKm() == null
                        : prova.getDistanciaKm() != null && distanciaKm.compareTo(prova.getDistanciaKm()) == 0);
        }
    }

    private void exigirNaoRealizada(Prova prova) {
        if (Boolean.TRUE.equals(prova.getFoiRealizada())) {
            throw new ProvaRealizadaImutavelException("Prova já realizada não pode ser alterada ou cancelada pelo atleta");
        }
    }
}
