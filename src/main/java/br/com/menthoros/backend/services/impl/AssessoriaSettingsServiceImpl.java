package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AssessoriaPatchInputDto;
import br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.exception.DomainRuleViolationException;
import jakarta.persistence.OptimisticLockException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaLogoRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.AssessoriaSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessoriaSettingsServiceImpl implements AssessoriaSettingsService {

    /** Rota do próprio produto. Nunca uma URL de terceiro — ver D1 no design da change. */
    static final String LOGO_PATH = "/api/v1/assessorias/me/logo";

    private final AssessoriaRepository assessoriaRepository;
    private final AssessoriaLogoRepository logoRepository;
    private final AtletaRepository atletaRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public AssessoriaMeOutputDto buscarDoTenantCorrente() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.debug("Buscando configuração da assessoria: tenantId={}", tenantId);

        return montarSaida(carregarDoTenant(tenantId), tenantId);
    }

    @Override
    @Transactional
    public AssessoriaMeOutputDto atualizarDoTenantCorrente(AssessoriaPatchInputDto input) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        log.info("Atualizando assessoria: tenantId={}, versaoInformada={}", tenantId, input.version());

        Assessoria assessoria = carregarDoTenant(tenantId);

        // Checagem explícita antes de escrever: dá o 409 sem depender de o flush acontecer, e a
        // @Version continua sendo a rede para a corrida entre esta leitura e o commit.
        if (!Objects.equals(input.version(), assessoria.getVersion())) {
            log.info("Versão obsoleta no PATCH: tenantId={}, informada={}, atual={}",
                    tenantId, input.version(), assessoria.getVersion());
            throw new OptimisticLockException(
                    "A assessoria foi alterada por outra sessão. Recarregue antes de salvar.");
        }

        // Defesa em profundidade: o Bean Validation do controller já barra, mas o serviço não
        // confia no DTO (ver Service Standards).
        String nome = normalizar(input.nome());
        if (nome.isBlank()) {
            throw new DomainRuleViolationException("Nome da assessoria não pode ser vazio");
        }

        assessoria.setNome(nome);
        assessoriaRepository.save(assessoria);
        log.info("Assessoria atualizada: tenantId={}", tenantId);

        return montarSaida(assessoria, tenantId);
    }

    private Assessoria carregarDoTenant(UUID tenantId) {
        return assessoriaRepository.findById(tenantId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "Assessoria não encontrada para o tenant corrente"));
    }

    /** Colapsa espaços internos e apara as bordas — "  A   B " vira "A B". */
    private String normalizar(String valor) {
        return valor == null ? "" : valor.strip().replaceAll("\\s+", " ");
    }

    private AssessoriaMeOutputDto montarSaida(Assessoria assessoria, UUID tenantId) {
        boolean temLogo = logoRepository.existsByAssessoriaId(tenantId);

        // Agregações no banco: a coleção Assessoria.atletas traria todas as linhas só para contar.
        long atletas = atletaRepository.countAtivosByTenantId(tenantId);
        // O dono entra aqui: ele permanece TECNICO em `role`, e é isso que mantém a contagem
        // do plano correta (ver UserRole.PROPRIETARIO).
        long tecnicos = usuarioRepository.countByTenantIdAndRoleAndAtivoTrue(tenantId, UserRole.TECNICO);

        return new AssessoriaMeOutputDto(
                assessoria.getId(),
                assessoria.getNome(),
                temLogo,
                temLogo ? LOGO_PATH : null,
                assessoria.getPlano(),
                new AssessoriaMeOutputDto.Uso(
                        atletas,
                        assessoria.getMaxAtletas(),
                        tecnicos,
                        assessoria.getMaxTecnicos()),
                assessoria.getVersion());
    }
}
