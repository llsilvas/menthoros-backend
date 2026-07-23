package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.input.AssinaturaInputDto;
import br.com.menthoros.backend.dto.input.AssinaturaTierInputDto;
import br.com.menthoros.backend.dto.output.AssinaturaOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Assinatura;
import br.com.menthoros.backend.enums.StatusAssinatura;
import br.com.menthoros.backend.exception.DomainConflictException;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.mapper.AssinaturaMapper;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AssinaturaRepository;
import br.com.menthoros.backend.services.AsaasGateway;
import br.com.menthoros.backend.services.AsaasGateway.AsaasAssinaturaCriada;
import br.com.menthoros.backend.services.AssinaturaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssinaturaServiceImpl implements AssinaturaService {

    private final AssinaturaRepository assinaturaRepository;
    private final AssessoriaRepository assessoriaRepository;
    private final AsaasGateway asaasGateway;
    private final AssinaturaMapper assinaturaMapper;

    /**
     * <strong>Deliberadamente NÃO {@code @Transactional}</strong> (design.md Decisão 9): a âncora
     * {@code PENDENTE} precisa sobreviver a uma falha do Asaas — se o método inteiro fosse uma
     * transação, o rollback apagaria a âncora. Cada {@code save} auto-commita.
     */
    @Override
    public AssinaturaOutputDto criar(UUID assessoriaId, AssinaturaInputDto input) {
        if (input == null) {
            throw new IllegalArgumentException("AssinaturaInputDto cannot be null");
        }
        log.info("Criando assinatura: assessoriaId={}, tier={}, nextDueDate={}",
                assessoriaId, input.plano(), input.nextDueDate());

        Assessoria assessoria = buscarAssessoria(assessoriaId);

        // Idempotência (CA14): reaproveita a PENDENTE; conflita se já houver assinatura ativa.
        Assinatura assinatura = assinaturaRepository.findByAssessoriaId(assessoriaId).orElse(null);
        if (assinatura != null && assinatura.getStatus() != StatusAssinatura.PENDENTE) {
            throw new DomainConflictException(
                    "Assessoria já possui assinatura (status=" + assinatura.getStatus() + ")");
        }

        // Passo 1: âncora local PENDENTE (auto-commit em transação própria).
        if (assinatura == null) {
            assinatura = new Assinatura();
            assinatura.setAssessoriaId(assessoriaId);
            assinatura.setStatus(StatusAssinatura.PENDENTE);
            assinatura.setValor(input.valor());
            assinatura.setDataProximaCobranca(toInstant(input.nextDueDate()));
            assinatura = assinaturaRepository.save(assinatura);
            log.info("Assinatura PENDENTE criada: id={}, assessoriaId={}", assinatura.getId(), assessoriaId);
        }

        // Passo 2: Asaas (fora de transação — falha aqui deixa a assinatura PENDENTE como âncora).
        AsaasAssinaturaCriada criada = asaasGateway.criarClienteEAssinatura(
                assessoria, input.creditCardToken(), input.nextDueDate(), input.valor());

        // Passo 3: confirma ATIVA + grava o tier vendido (CA12).
        assinatura.setAsaasCustomerId(criada.asaasCustomerId());
        assinatura.setAsaasSubscriptionId(criada.asaasSubscriptionId());
        assinatura.setStatus(StatusAssinatura.ATIVA);
        assinatura = assinaturaRepository.save(assinatura);

        assessoria.setPlano(input.plano());
        assessoriaRepository.save(assessoria);

        log.info("Assinatura ATIVA confirmada: id={}, assessoriaId={}", assinatura.getId(), assessoriaId);
        return assinaturaMapper.toOutputDto(assinatura, assessoria.getPlano());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssinaturaOutputDto atualizarTier(UUID assessoriaId, AssinaturaTierInputDto input) {
        if (input == null) {
            throw new IllegalArgumentException("AssinaturaTierInputDto cannot be null");
        }
        log.info("Trocando tier da assinatura: assessoriaId={}, novoTier={}", assessoriaId, input.plano());

        Assessoria assessoria = buscarAssessoria(assessoriaId);
        Assinatura assinatura = buscarAssinatura(assessoriaId);
        if (assinatura.getAsaasSubscriptionId() == null) {
            throw new DomainConflictException(
                    "Assinatura sem subscription no Asaas (status=" + assinatura.getStatus() + ")");
        }

        // Local primeiro; Asaas por último — falha externa reverte o lado local (CA15).
        assessoria.setPlano(input.plano());
        assinatura.setValor(input.valor());
        assessoriaRepository.save(assessoria);
        assinaturaRepository.save(assinatura);

        asaasGateway.atualizarValor(assinatura.getAsaasSubscriptionId(), input.valor());

        log.info("Tier trocado: assessoriaId={}, tier={}", assessoriaId, input.plano());
        return assinaturaMapper.toOutputDto(assinatura, assessoria.getPlano());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelar(UUID assessoriaId) {
        log.info("Cancelando assinatura: assessoriaId={}", assessoriaId);

        Assessoria assessoria = buscarAssessoria(assessoriaId);
        Assinatura assinatura = buscarAssinatura(assessoriaId);

        // Local primeiro; Asaas por último — falha externa reverte o lado local (CA7/CA15).
        assinatura.setStatus(StatusAssinatura.CANCELADA);
        assessoria.setAtivo(false);
        assinaturaRepository.save(assinatura);
        assessoriaRepository.save(assessoria);

        if (assinatura.getAsaasSubscriptionId() != null) {
            asaasGateway.cancelarAssinatura(assinatura.getAsaasSubscriptionId());
        }
        log.info("Assinatura cancelada: assessoriaId={}", assessoriaId);
    }

    private Assessoria buscarAssessoria(UUID assessoriaId) {
        return assessoriaRepository.findById(assessoriaId)
                .orElseThrow(() -> new DomainNotFoundException("Assessoria não encontrada: " + assessoriaId));
    }

    private Assinatura buscarAssinatura(UUID assessoriaId) {
        return assinaturaRepository.findByAssessoriaId(assessoriaId)
                .orElseThrow(() -> new DomainNotFoundException(
                        "Assinatura não encontrada para a assessoria: " + assessoriaId));
    }

    private static java.time.Instant toInstant(LocalDate data) {
        return data.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
