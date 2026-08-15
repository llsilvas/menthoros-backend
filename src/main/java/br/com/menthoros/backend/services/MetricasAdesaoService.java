package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.output.AdesaoSemanalDto;
import br.com.menthoros.backend.dto.output.SemanaAdesaoDto;
import br.com.menthoros.backend.dto.output.AdesaoDiariaDto;
import br.com.menthoros.backend.dto.output.SemanaAdesaoDiariaDto;
import br.com.menthoros.backend.dto.output.DiaAdesaoDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.exception.ResourceNotFoundException;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.multitenancy.TenantContext;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import br.com.menthoros.backend.entity.TreinoPlanejado;
import br.com.menthoros.backend.entity.TreinoRealizado;
import java.util.UUID;

@Service
public class MetricasAdesaoService {

    private final AtletaRepository atletaRepository;
    private final TreinoPlanejadoRepository treinoPlanejadoRepository;
    private final TreinoRealizadoRepository treinoRealizadoRepository;

    public MetricasAdesaoService(AtletaRepository atletaRepository,
                                 TreinoPlanejadoRepository treinoPlanejadoRepository,
                                 TreinoRealizadoRepository treinoRealizadoRepository) {
        this.atletaRepository = atletaRepository;
        this.treinoPlanejadoRepository = treinoPlanejadoRepository;
        this.treinoRealizadoRepository = treinoRealizadoRepository;
    }

    public AdesaoSemanalDto getAdesaoSemanal(String atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(UUID.fromString(atletaId), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));

        LocalDate hoje = LocalDate.now();

        // Get current week
        SemanaAdesaoDto semanaAtual = calcularSemana(atleta, hoje);

        // Get last 4 weeks
        List<SemanaAdesaoDto> ultimas4Semanas = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            LocalDate semanaData = hoje.minusWeeks(i);
            ultimas4Semanas.add(calcularSemana(atleta, semanaData));
        }

        // Calculate average
        double mediaUltimas4Semanas = ultimas4Semanas.stream()
            .mapToDouble(s -> s.percentualRealizacao() != null ? s.percentualRealizacao() : 0.0)
            .average()
            .orElse(0.0);

        return new AdesaoSemanalDto(
            atleta.getId().toString(),
            atleta.getNome(),
            semanaAtual,
            ultimas4Semanas,
            mediaUltimas4Semanas
        );
    }

    /**
     * Aderencia de uma semana especifica, identificada por qualquer data dentro dela — nao
     * necessariamente a semana corrente. Aditivo (athlete-onboarding-baseline, design.md Decisao 5):
     * {@link #getAdesaoSemanal(String)} sempre usa {@code LocalDate.now()}, o que nao serve para
     * avaliar "a semana mais recente de calibracao" quando ela nao e a semana corrente (ex.: job de
     * encerramento avaliando D+1). Nao altera o metodo existente.
     *
     * <p>Idempotente: SIM — leitura, sem efeito colateral.
     * Efeitos colaterais: NENHUM.
     * Tenant-aware: SIM — via {@code TenantContext.getRequiredTenantId()}.
     *
     * @param atletaId ID do atleta
     * @param dataReferencia qualquer data dentro da semana desejada
     * @return aderencia da semana que contem {@code dataReferencia}
     * @throws ResourceNotFoundException se o atleta nao existir no tenant corrente
     */
    public SemanaAdesaoDto getAdesaoSemana(String atletaId, LocalDate dataReferencia) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(UUID.fromString(atletaId), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));

        return calcularSemana(atleta, dataReferencia);
    }

    public AdesaoDiariaDto getAdesaoDiaria(String atletaId) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Atleta atleta = atletaRepository.findByIdAndTenantId(UUID.fromString(atletaId), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Atleta não encontrado: " + atletaId));

        LocalDate hoje = LocalDate.now();
        LocalDate inicioJanela = hoje.with(WeekFields.of(Locale.ENGLISH).dayOfWeek(), 1).minusWeeks(4);
        LocalDate fimJanela = hoje.with(WeekFields.of(Locale.ENGLISH).dayOfWeek(), 1).plusDays(6);

        List<TreinoPlanejado> planejados = treinoPlanejadoRepository
            .findByAtletaIdAndDataBetween(atleta.getId(), inicioJanela, fimJanela);
        List<TreinoRealizado> realizados = treinoRealizadoRepository
            .findByAtletaIdAndDataTreinoBetween(atleta.getId(), inicioJanela, fimJanela);

        Map<LocalDate, Integer> planejadosPorDia = new HashMap<>();
        Map<LocalDate, Integer> realizadosPorDia = new HashMap<>();

        for (TreinoPlanejado tp : planejados) {
            planejadosPorDia.merge(tp.getDataTreino(), 1, Integer::sum);
        }

        for (TreinoRealizado tr : realizados) {
            realizadosPorDia.merge(tr.getDataTreino(), 1, Integer::sum);
        }

        List<SemanaAdesaoDiariaDto> semanas = new ArrayList<>();

        for (int i = 4; i >= 0; i--) {
            LocalDate semanaData = hoje.minusWeeks(i);
            LocalDate startOfWeek = semanaData.with(WeekFields.of(Locale.ENGLISH).dayOfWeek(), 1);
            LocalDate endOfWeek = startOfWeek.plusDays(6);

            int week = semanaData.get(WeekFields.of(Locale.ENGLISH).weekOfWeekBasedYear());
            int year = semanaData.get(WeekFields.of(Locale.ENGLISH).weekBasedYear());

            List<DiaAdesaoDto> dias = new ArrayList<>();
            int totalPlanejados = 0;
            int totalRealizados = 0;

            for (int j = 0; j < 7; j++) {
                LocalDate data = startOfWeek.plusDays(j);
                int planejadosNoDia = planejadosPorDia.getOrDefault(data, 0);
                int realizadosNoDia = realizadosPorDia.getOrDefault(data, 0);
                double percentual = planejadosNoDia > 0 ? Math.min(100.0, realizadosNoDia * 100.0 / planejadosNoDia) : 0.0;

                String[] diasSemana = {"DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SAB"};
                String diaSemanaStr = diasSemana[data.getDayOfWeek().getValue() % 7];

                dias.add(new DiaAdesaoDto(
                    data.toString(),
                    diaSemanaStr,
                    planejadosNoDia,
                    realizadosNoDia,
                    percentual
                ));

                totalPlanejados += planejadosNoDia;
                totalRealizados += realizadosNoDia;
            }

            double percentualGeral = totalPlanejados > 0 ? Math.min(100.0, totalRealizados * 100.0 / totalPlanejados) : 0.0;

            semanas.add(new SemanaAdesaoDiariaDto(
                String.format("%04d-W%02d", year, week),
                startOfWeek.toString(),
                endOfWeek.toString(),
                percentualGeral,
                dias
            ));
        }

        return new AdesaoDiariaDto(
            atleta.getId().toString(),
            atleta.getNome(),
            semanas
        );
    }

    public AdesaoDiariaDto getAdesaoDiariaAssessoria() {
        UUID tenantId = TenantContext.getRequiredTenantId();
        LocalDate hoje = LocalDate.now();
        List<Atleta> atletas = atletaRepository.findAtivosByTenantIdOrderByNome(tenantId);

        if (atletas.isEmpty()) {
            return new AdesaoDiariaDto(tenantId.toString(), "Assessoria", new ArrayList<>());
        }

        // Janela das 5 semanas exibidas (domingo da semana mais antiga até sábado da semana atual).
        LocalDate inicioJanela = hoje.minusWeeks(4).with(WeekFields.of(Locale.ENGLISH).dayOfWeek(), 1);
        LocalDate fimJanela = hoje.with(WeekFields.of(Locale.ENGLISH).dayOfWeek(), 1).plusDays(6);

        // Duas consultas por atleta para a janela inteira (evita N+1 por dia).
        Map<UUID, Map<LocalDate, Integer>> planejadosPorAtleta = new HashMap<>();
        Map<UUID, Map<LocalDate, Integer>> realizadosPorAtleta = new HashMap<>();
        for (Atleta atleta : atletas) {
            Map<LocalDate, Integer> planejados = new HashMap<>();
            for (TreinoPlanejado tp : treinoPlanejadoRepository
                    .findByAtletaIdAndDataBetween(atleta.getId(), inicioJanela, fimJanela)) {
                planejados.merge(tp.getDataTreino(), 1, Integer::sum);
            }
            planejadosPorAtleta.put(atleta.getId(), planejados);

            Map<LocalDate, Integer> realizados = new HashMap<>();
            for (TreinoRealizado tr : treinoRealizadoRepository
                    .findByAtletaIdAndDataTreinoBetween(atleta.getId(), inicioJanela, fimJanela)) {
                realizados.merge(tr.getDataTreino(), 1, Integer::sum);
            }
            realizadosPorAtleta.put(atleta.getId(), realizados);
        }

        List<SemanaAdesaoDiariaDto> semanas = new ArrayList<>();

        for (int i = 4; i >= 0; i--) {
            LocalDate semanaData = hoje.minusWeeks(i);
            LocalDate startOfWeek = semanaData.with(WeekFields.of(Locale.ENGLISH).dayOfWeek(), 1);
            LocalDate endOfWeek = startOfWeek.plusDays(6);

            int week = semanaData.get(WeekFields.of(Locale.ENGLISH).weekOfWeekBasedYear());
            int year = semanaData.get(WeekFields.of(Locale.ENGLISH).weekBasedYear());

            List<DiaAdesaoDto> dias = new ArrayList<>();
            List<Double> percentuaisDiasAtivos = new ArrayList<>();

            for (int j = 0; j < 7; j++) {
                LocalDate data = startOfWeek.plusDays(j);

                double totalPercentual = 0.0;
                int atletasComDados = 0;

                for (Atleta atleta : atletas) {
                    int planejadosNoDia = planejadosPorAtleta.get(atleta.getId()).getOrDefault(data, 0);
                    int realizadosNoDia = realizadosPorAtleta.get(atleta.getId()).getOrDefault(data, 0);

                    if (planejadosNoDia > 0) {
                        double percentualAtleta = Math.min(100.0, realizadosNoDia * 100.0 / planejadosNoDia);
                        totalPercentual += percentualAtleta;
                        atletasComDados++;
                    }
                }

                double percentualMedio = atletasComDados > 0 ? (totalPercentual / atletasComDados) : 0.0;
                if (atletasComDados > 0) {
                    percentuaisDiasAtivos.add(percentualMedio);
                }

                String[] diasSemana = {"DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SAB"};
                String diaSemanaStr = diasSemana[data.getDayOfWeek().getValue() % 7];

                dias.add(new DiaAdesaoDto(
                    data.toString(),
                    diaSemanaStr,
                    0,
                    0,
                    percentualMedio
                ));
            }

            // Média apenas sobre os dias com treino planejado, evitando diluição por dias sem dados.
            double percentualGeralSemana = percentuaisDiasAtivos.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

            semanas.add(new SemanaAdesaoDiariaDto(
                String.format("%04d-W%02d", year, week),
                startOfWeek.toString(),
                endOfWeek.toString(),
                percentualGeralSemana,
                dias
            ));
        }

        return new AdesaoDiariaDto(
            tenantId.toString(),
            "Assessoria",
            semanas
        );
    }

    private SemanaAdesaoDto calcularSemana(Atleta atleta, LocalDate data) {
        LocalDate startOfWeek = data.with(WeekFields.of(Locale.ENGLISH).dayOfWeek(), 1);
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        int week = data.get(WeekFields.of(Locale.ENGLISH).weekOfWeekBasedYear());
        int year = data.get(WeekFields.of(Locale.ENGLISH).weekBasedYear());

        Integer planejados = treinoPlanejadoRepository.countPlannedTrainings(atleta.getId(), startOfWeek, endOfWeek);
        Integer realizados = treinoRealizadoRepository.countRealizedTrainings(atleta.getId(), startOfWeek, endOfWeek);

        double percentual = planejados > 0 ? Math.min(100.0, (realizados.doubleValue() / planejados) * 100) : 0.0;

        return new SemanaAdesaoDto(
            String.format("%04d-W%02d", year, week),
            startOfWeek.toString(),
            endOfWeek.toString(),
            planejados,
            realizados,
            percentual,
            Math.toIntExact(treinoRealizadoRepository.findRealizedTrainingsByWeek(atleta.getId(), startOfWeek, endOfWeek)
                .stream()
                .map(tr -> tr.getDataTreino().getDayOfWeek())
                .distinct()
                .count())
        );
    }
}
