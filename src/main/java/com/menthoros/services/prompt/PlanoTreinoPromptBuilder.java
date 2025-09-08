package com.menthoros.services.prompt;

import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.ProvaOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PlanoTreinoPromptBuilder {

    private final String promptTemplate;

    public PlanoTreinoPromptBuilder(@Value("classpath:prompts/plano-treino-prompt.txt") Resource promptResource) {
        try {
            this.promptTemplate = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível carregar o template do prompt.", e);
        }
    }

    public String buildRequest(AtletaOutputDto atleta, List<TreinoRealizadoOutputDto> treinosRecentes, PlanoSemanalOutputDto planoSemanalOutputDto) {
        return String.format(promptTemplate,
                atleta.nome(),
                atleta.idade(),
                atleta.objetivo(),
                atleta.nivelExperiencia(),  
                atleta.diasDisponiveis(),
                atleta.diaPreferidoLongo(),
                formatarProvas(atleta.provas()),
                formatarTreinos(treinosRecentes), 
                formatarHistorico(planoSemanalOutputDto)
        );
    }

    private String formatarHistorico(PlanoSemanalOutputDto planoSemanalOutputDto) {
        if (planoSemanalOutputDto == null) {
            return "### Volume da semana anterior:\n- Nenhum plano concluído encontrado.\n";
        }

        Double volumeAlvo = planoSemanalOutputDto.volumeAlvoKm();
        Double volumePlanejado = planoSemanalOutputDto.volumePlanejadoKm();
        Double volumeRealizado = planoSemanalOutputDto.volumeRealizadoKm();

        return String.format("""
            ### Volume da semana anterior:
            - volume alvo: %.1f km
            - volume planejado: %.1f km
            - volume realizado: %.1f km
            """,
                volumeAlvo != null ? volumeAlvo : 0.0,
                volumePlanejado != null ? volumePlanejado : 0.0,
                volumeRealizado != null ? volumeRealizado : 0.0
        );
    }



    private String formatarProvas(List<ProvaOutputDto> provas){

        if(provas == null || provas.isEmpty()){
            return "Nenhuma prova cadastrada";
        }

        return provas.stream()
                .map(p -> String.format("- %s em %s (%.1f km)",
                        p.nomeProva(),
                        p.dataProva().toString(),
                        p.distanciaKm().doubleValue())).collect(Collectors.joining("\n"));

    }

    private String formatarTreinos(List<TreinoRealizadoOutputDto> treinos) {
        if (treinos == null || treinos.isEmpty()) {
            return "Nenhum treino recente encontrado.";
        }

        return treinos.stream()
                .map(t -> String.format("- %s\n Tipo: %s\n Distância: %.1f km | Duração: %d min | Ritmo alvo: %s | FC alvo: %s\n Observações: %s\n",
                        t.dataTreino().toString(),
                        t.tipoTreino(),
                        t.distanciaKm(),
                        t.duracaoMin(),
                        t.ritmoMedio() != null ? t.ritmoMedio() : "N/A",
                        t.fcMedia() != null ? t.fcMedia() : "N/A",
                        t.comentario() != null ? t.comentario() : "Sem observações")
                )
                .collect(Collectors.joining("\n"));
    }
}