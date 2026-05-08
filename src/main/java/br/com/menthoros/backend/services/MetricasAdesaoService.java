package br.com.menthoros.backend.services;

import com.menthoros.api.dtos.AdesaoSemanalDto;
import com.menthoros.api.dtos.SemanaAdesaoDto;
import com.menthoros.api.dtos.AdesaoDiariaDto;
import com.menthoros.api.dtos.SemanaAdesaoDiariaDto;
import com.menthoros.api.dtos.DiaAdesaoDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.multitenancy.TenantContext;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
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
        Atleta atleta = atletaRepository.findById(java.util.UUID.fromString(atletaId))
            .orElseThrow(() -> new RuntimeException("Atleta not found: " + atletaId));

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
            .mapToDouble(s -> s.getPercentualRealizacao() != null ? s.getPercentualRealizacao() : 0.0)
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

    public AdesaoDiariaDto getAdesaoDiaria(String atletaId) {
        Atleta atleta = atletaRepository.findById(java.util.UUID.fromString(atletaId))
            .orElseThrow(() -> new RuntimeException("Atleta not found: " + atletaId));

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

            int week = semanaData.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            int year = semanaData.get(IsoFields.WEEK_BASED_YEAR);

            List<DiaAdesaoDto> dias = new ArrayList<>();
            int totalPlanejados = 0;
            int totalRealizados = 0;

            for (int j = 0; j < 7; j++) {
                LocalDate data = startOfWeek.plusDays(j);
                int planejadosNoDia = planejadosPorDia.getOrDefault(data, 0);
                int realizadosNoDia = realizadosPorDia.getOrDefault(data, 0);
                double percentual = planejadosNoDia > 0 ? (realizadosNoDia * 100.0 / planejadosNoDia) : 0.0;

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

            double percentualGeral = totalPlanejados > 0 ? (totalRealizados * 100.0 / totalPlanejados) : 0.0;

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
        List<Atleta> atletas = atletaRepository.findAllByTenantIdOrderByNome(tenantId);

        if (atletas.isEmpty()) {
            return new AdesaoDiariaDto(tenantId.toString(), "Assessoria", new ArrayList<>());
        }

        List<SemanaAdesaoDiariaDto> semanas = new ArrayList<>();

        for (int i = 4; i >= 0; i--) {
            LocalDate semanaData = hoje.minusWeeks(i);
            LocalDate startOfWeek = semanaData.with(WeekFields.of(Locale.ENGLISH).dayOfWeek(), 1);
            LocalDate endOfWeek = startOfWeek.plusDays(6);

            int week = semanaData.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            int year = semanaData.get(IsoFields.WEEK_BASED_YEAR);

            List<DiaAdesaoDto> dias = new ArrayList<>();

            for (int j = 0; j < 7; j++) {
                LocalDate data = startOfWeek.plusDays(j);

                double totalPercentual = 0.0;
                int atletasComDados = 0;

                for (Atleta atleta : atletas) {
                    int planejadosNoDia = treinoPlanejadoRepository
                        .findByAtletaIdAndDataBetween(atleta.getId(), data, data)
                        .size();
                    int realizadosNoDia = treinoRealizadoRepository
                        .findByAtletaIdAndDataTreinoBetween(atleta.getId(), data, data)
                        .size();

                    if (planejadosNoDia > 0) {
                        double percentualAtleta = (realizadosNoDia * 100.0 / planejadosNoDia);
                        totalPercentual += percentualAtleta;
                        atletasComDados++;
                    }
                }

                double percentualMedio = atletasComDados > 0 ? (totalPercentual / atletasComDados) : 0.0;

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

            double percentualGeralSemana = dias.stream()
                .mapToDouble(DiaAdesaoDto::percentual)
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

        int week = data.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = data.get(IsoFields.WEEK_BASED_YEAR);

        Integer planejados = treinoPlanejadoRepository.countPlannedTrainings(atleta.getId(), startOfWeek, endOfWeek);
        Integer realizados = treinoRealizadoRepository.countRealizedTrainings(atleta.getId(), startOfWeek, endOfWeek);

        double percentual = planejados > 0 ? (realizados.doubleValue() / planejados) * 100 : 0.0;

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
