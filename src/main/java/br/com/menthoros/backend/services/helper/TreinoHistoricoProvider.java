package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.CheckinProntidao;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.enums.NivelProntidao;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.CheckinProntidaoRepository;
import br.com.menthoros.backend.repository.ProvaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
    private final CheckinProntidaoRepository checkinProntidaoRepository;
    private final Clock clock;

    public TreinoHistoricoProvider(TreinoRealizadoRepository treinoRealizadoRepository,
                                   ProvaRepository provaRepository,
                                   CheckinProntidaoRepository checkinProntidaoRepository,
                                   Clock clock) {
        this.treinoRealizadoRepository = treinoRealizadoRepository;
        this.provaRepository = provaRepository;
        this.checkinProntidaoRepository = checkinProntidaoRepository;
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

        // Checkins de prontidão dos últimos 7 dias (inclui hoje)
        List<CheckinProntidao> checkinsUltimos7Dias = checkinProntidaoRepository
                .findByAtletaIdAndDataBetween(atleta.getId(), hoje.minusDays(6), hoje, TenantContext.getRequiredTenantId());

        return new ContextoTreino(hoje, treinosUltimas4Semanas, provasPreparatorias, checkinsUltimos7Dias);
    }

    /**
     * Record imutável contendo todos os dados históricos pré-carregados.
     * Métodos utilitários filtram subconjuntos sem novas queries.
     */
    public record ContextoTreino(
            LocalDate dataReferencia,
            List<TreinoRealizado> treinosUltimas4Semanas,
            List<Prova> provasPreparatorias,
            List<CheckinProntidao> checkinsUltimos7Dias
    ) {
        /**
         * Nível de prontidão do dia de referência, ou {@code null} se não houver checkin.
         */
        @Nullable
        public NivelProntidao nivelProntidaoHoje() {
            return checkinsUltimos7Dias.stream()
                    .filter(c -> dataReferencia.equals(c.getData()))
                    .map(CheckinProntidao::getNivelProntidao)
                    .findFirst()
                    .orElse(null);
        }

        /**
         * Score de prontidão do dia de referência, ou {@code null} se não houver checkin.
         */
        @Nullable
        public BigDecimal readinessScoreHoje() {
            return checkinsUltimos7Dias.stream()
                    .filter(c -> dataReferencia.equals(c.getData()))
                    .map(CheckinProntidao::getReadinessScore)
                    .findFirst()
                    .orElse(null);
        }

        /**
         * Sequência dos últimos 7 dias (do mais antigo ao mais recente), um valor por dia —
         * {@code null} nos dias sem checkin registrado.
         */
        public List<@Nullable NivelProntidao> sequenciaUltimos7Dias() {
            List<NivelProntidao> sequencia = new java.util.ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate dia = dataReferencia.minusDays(i);
                NivelProntidao nivel = checkinsUltimos7Dias.stream()
                        .filter(c -> dia.equals(c.getData()))
                        .map(CheckinProntidao::getNivelProntidao)
                        .findFirst()
                        .orElse(null);
                sequencia.add(nivel);
            }
            return sequencia;
        }
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
