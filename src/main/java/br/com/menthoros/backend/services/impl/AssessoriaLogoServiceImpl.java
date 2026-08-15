package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.output.AssessoriaMeOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.AssessoriaLogo;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaLogoRepository;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.services.AssessoriaLogoService;
import br.com.menthoros.backend.services.AssessoriaSettingsService;
import br.com.menthoros.backend.services.helper.LogoImagemValidator;
import br.com.menthoros.backend.services.helper.TenantCoerenciaGuard;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssessoriaLogoServiceImpl implements AssessoriaLogoService {

    private final AssessoriaRepository assessoriaRepository;
    private final AssessoriaLogoRepository logoRepository;
    private final LogoImagemValidator validator;
    private final AssessoriaSettingsService settingsService;
    private final TenantCoerenciaGuard tenantCoerenciaGuard;

    @Override
    @Transactional
    public AssessoriaMeOutputDto substituir(byte[] bytes, Long version) {
        UUID tenantId = tenantCoerenciaGuard.exigirCoerencia();
        Assessoria assessoria = carregarConferindoVersao(tenantId, version);

        // Validar ANTES de qualquer escrita: um arquivo recusado não pode deixar rastro nem
        // consumir a versão da assessoria.
        LogoImagemValidator.LogoValidada validada = validator.validar(bytes);

        // Upsert: uma linha por assessoria (a PK é a FK), então substituir é UPDATE.
        AssessoriaLogo logo = logoRepository.findById(tenantId)
                .orElseGet(() -> AssessoriaLogo.builder().assessoriaId(tenantId).build());
        logo.setContent(bytes);
        logo.setContentType(validada.contentType());
        logo.setSizeBytes(validada.tamanhoBytes());
        logo.setEtag(validada.etag());
        logo.setUpdatedAt(OffsetDateTime.now());
        logoRepository.save(logo);

        // Bump da versão na MESMA transação: bytes e ponteiro commitam juntos, então uma falha
        // aqui reverte o upload inteiro — não há objeto órfão a limpar depois.
        tocar(assessoria);

        log.info("Logo da assessoria substituída: tenantId={}, bytes={}, tipo={}",
                tenantId, validada.tamanhoBytes(), validada.contentType());

        return settingsService.buscarDoTenantCorrente();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LogoBinario> buscar() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        return logoRepository.findById(tenantId)
                .map(logo -> new LogoBinario(logo.getContent(), logo.getContentType(), logo.getEtag()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> buscarEtag() {
        return logoRepository.findEtagByAssessoriaId(TenantContext.getRequiredTenantId());
    }

    @Override
    @Transactional
    public void remover(Long version) {
        UUID tenantId = tenantCoerenciaGuard.exigirCoerencia();
        Assessoria assessoria = carregarConferindoVersao(tenantId, version);

        logoRepository.deleteById(tenantId);
        tocar(assessoria);

        log.info("Logo da assessoria removida: tenantId={}", tenantId);
    }

    /**
     * Carrega a assessoria e recusa versão obsoleta antes de qualquer escrita — o mesmo contrato do
     * PATCH. Sem isto no DELETE, uma aba antiga apagaria a logo que outra acabou de enviar, sem
     * conflito e com perda de dado.
     */
    private Assessoria carregarConferindoVersao(UUID tenantId, Long version) {
        Assessoria assessoria = assessoriaRepository.findById(tenantId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "Assessoria não encontrada para o tenant corrente"));

        if (!Objects.equals(version, assessoria.getVersion())) {
            log.info("Versão obsoleta em operação de logo: tenantId={}, informada={}, atual={}",
                    tenantId, version, assessoria.getVersion());
            throw new OptimisticLockException(
                    "A assessoria foi alterada por outra sessão. Recarregue antes de salvar.");
        }
        return assessoria;
    }

    /**
     * Força o incremento da {@code @Version} da assessoria. A logo vive em outra tabela, então
     * mexer nela não sujaria a entidade sozinha — sem este toque, duas abas poderiam enviar logos
     * diferentes com a mesma versão e nenhuma receberia {@code 409}.
     */
    private void tocar(Assessoria assessoria) {
        assessoria.setUpdatedAt(java.time.LocalDateTime.now());
        assessoriaRepository.saveAndFlush(assessoria);
    }
}
