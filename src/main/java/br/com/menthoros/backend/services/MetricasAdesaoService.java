package br.com.menthoros.backend.services;

import com.menthoros.api.dtos.AdesaoSemanalDto;
import com.menthoros.api.dtos.SemanaAdesaoDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoPlanejadoRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private SemanaAdesaoDto calcularSemana(Atleta atleta, LocalDate data) {
        LocalDate startOfWeek = data.with(WeekFields.of(Locale.ENGLISH).dayOfWeek(), 1);
        LocalDate endOfWeek = startOfWeek.plusDays(6);

        int week = data.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = data.get(IsoFields.WEEK_BASED_YEAR);

        Integer planejados = treinoPlanejadoRepository.countPlannedTrainings(atleta.getId(), startOfWeek);
        Integer realizados = treinoRealizadoRepository.countRealizedTrainings(atleta.getId(), startOfWeek);

        double percentual = planejados > 0 ? (realizados.doubleValue() / planejados) * 100 : 0.0;

        return new SemanaAdesaoDto(
            String.format("%04d-W%02d", year, week),
            startOfWeek.toString(),
            endOfWeek.toString(),
            planejados,
            realizados,
            percentual,
            Math.toIntExact(treinoRealizadoRepository.findRealizedTrainingsByWeek(atleta.getId(), startOfWeek)
                .stream()
                .map(tr -> tr.getDataTreino().getDayOfWeek())
                .distinct()
                .count())
        );
    }
}
