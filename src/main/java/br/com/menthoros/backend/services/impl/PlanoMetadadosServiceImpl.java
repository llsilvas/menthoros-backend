package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;
import br.com.menthoros.backend.exception.DomainNotFoundException;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.PlanoMetadadosRepository;
import br.com.menthoros.backend.services.PlanoMetadadosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementação do serviço de gerenciamento de metadados de planos de treino.
 *
 * <p>Esta classe é responsável por buscar e criar metadados associados aos planos
 * semanais dos atletas. Utiliza cache para otimizar o acesso aos dados frequentemente
 * consultados.
 *
 * <p><strong>Cache:</strong> Os metadados são armazenados em cache com a chave baseada
 * no ID do atleta, evitando consultas desnecessárias ao banco de dados.
 *
 * @see PlanoMetaDados
 * @see Atleta
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanoMetadadosServiceImpl implements PlanoMetadadosService {

    private final PlanoMetadadosRepository planoMetadadosRepository;
    private final AtletaRepository atletaRepository;

    /**
     * {@inheritDoc}
     *
     * <p>Este método é cacheado para melhorar a performance. O cache é invalidado
     * automaticamente quando os metadados são atualizados.
     */
    @Override
    @Cacheable(value = "metadados-atleta", key = "#atleta.id")
    public PlanoMetaDados buscarOuCriarMetadados(Atleta atleta) {
        Objects.requireNonNull(atleta, "Atleta não pode ser nulo");
        Objects.requireNonNull(atleta.getId(), "ID do atleta não pode ser nulo");

        return planoMetadadosRepository.findLatestByAtletaId(atleta.getId())
                .orElseGet(() -> criarMetadadosIniciais(atleta));
    }

    @Override
    public PlanoMetaDados buscarPorAtletaId(UUID atletaId) {
        Objects.requireNonNull(atletaId, "atletaId não pode ser nulo");

        Atleta atleta = atletaRepository.findById(atletaId)
                .orElseThrow(() -> new DomainNotFoundException("Atleta não encontrado: " + atletaId));

        return buscarOuCriarMetadados(atleta);
    }

    /**
     * Cria metadados iniciais para um atleta que ainda não possui histórico.
     *
     * <p>Os metadados iniciais incluem apenas informações básicas extraídas do perfil
     * do atleta, como o dia preferido para treinos longos.
     *
     * @param atleta o atleta para o qual os metadados serão criados
     * @return os metadados criados e persistidos
     */
    private PlanoMetaDados criarMetadadosIniciais(Atleta atleta) {
        log.info("Criando metadados iniciais para o atleta: {}", atleta.getId());

        PlanoMetaDados metaDados = PlanoMetaDados.builder()
                .atleta(atleta)
                .diaPreferidoLongo(atleta.getDiaPreferidoLongo())
                .dataCriacao(LocalDateTime.now())
                .build();

        return planoMetadadosRepository.save(metaDados);
    }
}