package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.KudosInputDto;
import br.com.menthoros.backend.dto.output.KudosOutputDto;
import br.com.menthoros.backend.dto.output.KudosRecenteOutputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Kudos;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DuplicateResourceException;
import br.com.menthoros.backend.mapper.KudosMapper;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.KudosRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.security.AuthenticatedPrincipalResolver;
import br.com.menthoros.backend.services.KudosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KudosServiceImpl implements KudosService {

    private final KudosRepository kudosRepository;
    private final AtletaRepository atletaRepository;
    private final UsuarioRepository usuarioRepository;
    private final KudosMapper kudosMapper;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final Clock clock;

    @Override
    @Transactional
    public KudosOutputDto registrar(UUID atletaId, KudosInputDto input) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate hoje = LocalDate.now(clock);

        Atleta atleta = atletaRepository.findByIdAndTenantId(atletaId, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado no tenant atual"));

        String sub = principalResolver.getCurrentSubject();
        Usuario coach = usuarioRepository.findByKeycloakIdAndAssessoria_Id(sub, tenantId)
                .orElseThrow(() -> new DomainNotFoundException("Usuário autenticado não encontrado no tenant"));

        if (kudosRepository.existsByAtletaIdAndCoachIdAndMotivoAndData(atletaId, coach.getId(), input.motivo(), hoje)) {
            throw new DuplicateResourceException(
                    "Você já reconheceu a " + input.motivo().getLabel().toLowerCase() + " deste atleta hoje.");
        }

        Kudos kudos = Kudos.builder()
                .atleta(atleta)
                .coach(coach)
                .motivo(input.motivo())
                .data(hoje)
                .tenantId(tenantId)
                .build();

        kudos = kudosRepository.save(kudos);
        log.info("Kudo registrado: atletaId={}, coachId={}, motivo={}", atletaId, coach.getId(), input.motivo());

        return kudosMapper.toOutputDto(kudos);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KudosRecenteOutputDto> listarRecentes(UUID atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Instant desde = Instant.now(clock).minus(7, ChronoUnit.DAYS);
        return kudosRepository.findRecentesByAtletaIdAndTenantId(atletaId, tenantId, desde).stream()
                .map(kudosMapper::toRecenteOutputDto)
                .toList();
    }
}
