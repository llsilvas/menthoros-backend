package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provedor centralizado de dados históricos de treino para construção de prompts.
 *
 * <p>Busca os dados uma única vez e os disponibiliza via {@link ContextoTreino},
 * eliminando queries repetidas ao banco durante a montagem do prompt.</p>
 */
@Component
public class TreinoHistoricoProvider {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final ProvaRepository provaRepository;
    private final Clock clock;

    public TreinoHistoricoProvider(TreinoRealizadoRepository treinoRealizadoRepository,
                                   ProvaRepository provaRepository,
                                   Clock clock) {
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.provaRepository = provaRepository;
        this.clock = clock;
    }

    /**
     * Prepara todo o contexto de dados necessário para construção do prompt.
     * Uma única chamada busca tudo; os formatadores consomem sem acessar o banco.
     */
    public ContextoTreino prepararContexto(Atleta atleta) {
        LocalDate hoje = LocalDate.now(clock);

        // Busca abrangente: 4 semanas (cobre todos os casos de uso)
        LocalDate quatroSemanas = hoje.minusWeeks(4);
        List<TreinoRealizado> treinosUltimas4Semanas = treinoRealizadoRepository
                .findByAtletaAndDataTreinoGreaterThanEqualOrderByDataTreinoDesc(atleta, quatroSemanas);

        // Provas preparatórias (próximos 6 meses, excluindo prova alvo)
        List<Prova> provasPreparatorias = provaRepository
                .findByAtletaAndDataProvaBetweenOrderByDataProvaAsc(atleta, hoje, hoje.plusMonths(6))
                .stream()
                .filter(p -> !p.isProvaAlvo())
                .toList();

        return new ContextoTreino(hoje, treinosUltimas4Semanas, provasPreparatorias);
    }

    /**
     * Record imutável contendo todos os dados históricos pré-carregados.
     * Métodos utilitários filtram subconjuntos sem novas queries.
     */
    public record ContextoTreino(
            LocalDate dataReferencia,
            List<TreinoRealizado> treinosUltimas4Semanas,
            List<Prova> provasPreparatorias
    ) {
        /**
         * Treinos dos últimos 14 dias (usado por formatarHistoricoTreinos).
         */
        public List<TreinoRealizado> treinosUltimos14Dias() {
            LocalDate limite = dataReferencia.minusDays(16);
            return treinosUltimas4Semanas.stream()
                    .filter(t -> !t.getDataTreino().isBefore(limite))
                    .collect(Collectors.toList());
        }

        /**
         * Treinos da última semana (usado por detalharRecuperacao).
         */
        public List<TreinoRealizado> treinosUltimaSemana() {
            LocalDate limite = dataReferencia.minusWeeks(1);
            return treinosUltimas4Semanas.stream()
                    .filter(t -> !t.getDataTreino().isBefore(limite))
                    .collect(Collectors.toList());
        }

        /**
         * Treinos das últimas 3 semanas (usado por calcularVolumeMedio).
         */
        public List<TreinoRealizado> treinosUltimas3Semanas() {
            LocalDate limite = dataReferencia.minusWeeks(3);
            return treinosUltimas4Semanas.stream()
                    .filter(t -> !t.getDataTreino().isBefore(limite))
                    .collect(Collectors.toList());
        }

        /**
         * Filtra treinos de uma semana específica (segunda a domingo).
         */
        public List<TreinoRealizado> treinosDaSemana(LocalDate inicioSemana, LocalDate fimSemana) {
            return treinosUltimas4Semanas.stream()
                    .filter(t -> !t.getDataTreino().isBefore(inicioSemana) &&
                            !t.getDataTreino().isAfter(fimSemana))
                    .collect(Collectors.toList());
        }
    }
}
