package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.dto.fit.FitImportResultado;
import br.com.menthoros.backend.dto.output.TreinoRealizadoOutputDto;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TempoEmZonas;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.services.TreinoService;
import br.com.menthoros.backend.services.impl.FitParseServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Import real do .fit até a leitura do detalhe (coach-ver-treino-realizado, CA9, task 2.4): o tempo
 * em zonas fica com as zonas vigentes no import, mesmo depois que o limiar do atleta muda.
 */
class FitTempoEmZonasHistoricoIT extends AbstractIntegrationTest {

    @Autowired private FitTreinoPersister fitTreinoPersister;
    @Autowired private TreinoService treinoService;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("mudar o limiar depois do import não altera o tempoEmZonas do treino")
    void zonasMudamHistoricoNao() throws Exception {
        Atleta atleta = seedAtleta(165);
        TenantContext.setTenantId(atleta.getAssessoria().getId());

        FitImportResultado importado;
        try (InputStream in = getClass().getResourceAsStream("/fit/corrida-15km-16laps.fit")) {
            importado = fitTreinoPersister.persistir(atleta.getId(), new FitParseServiceImpl().parse(in));
        }
        UUID treinoId = importado.treino().id();
        TempoEmZonas noImport = treinoService.getTreinoById(treinoId).tempoEmZonas();
        assertThat(noImport).isNotNull();
        assertThat(noImport.zonas().stream().mapToInt(TempoEmZonas.Zona::segundos).sum()).isEqualTo(5639);

        // Dentro de transação, por dirty checking: save() do Atleta desanexado faria merge da lista
        // vazia de treinos e o orphanRemoval apagaria o treino importado.
        transactionTemplate.executeWithoutResult(tx ->
                atletaRepository.findById(atleta.getId()).orElseThrow().setFcLimiar(150));

        TreinoRealizadoOutputDto depois = treinoService.getTreinoById(treinoId);
        assertThat(depois.tempoEmZonas()).isEqualTo(noImport);
        assertThat(depois.tempoEmZonas().zonas().get(0).bpmMin()).isEqualTo(noImport.zonas().get(0).bpmMin());
    }

    private Atleta seedAtleta(int fcLimiar) {
        Assessoria assessoria = new Assessoria();
        assessoria.setNome("Assessoria Zonas Historico");
        assessoria.setDominio("zonas-historico-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        Atleta atleta = new Atleta();
        atleta.setNome("Atleta Zonas");
        atleta.setEmail("zonas-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Correr 21km");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta.setFcLimiar(fcLimiar);
        return atletaRepository.save(atleta);
    }
}
