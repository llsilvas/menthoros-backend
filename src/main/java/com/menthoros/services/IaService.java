package com.menthoros.services;

import com.menthoros.dto.output.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class IaService {

    private final ChatClient chatClient;

    public IaService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public PlanoSemanalOutputDto gerarPlano(AtletaOutputDto atletaOutputDto, List<TreinoRealizadoOutputDto> treinoRealizadoOutputDtoList) {

        return chatClient.prompt()
                .user(buildRequest(atletaOutputDto, treinoRealizadoOutputDtoList))
                .call()
                .entity(PlanoSemanalOutputDto.class);
    }

    public Map<Long, PlanoTreinoOutputDto> gerarPlanosEmLote(Map<AtletaOutputDto, List<TreinoRealizadoOutputDto>> atletaDtoListMap) {
        return null;
    }

    public String buildRequest(AtletaOutputDto atleta, List<TreinoRealizadoOutputDto> treinosRecentes) {
        return String.format("""
        Você é um treinador de corrida especialista.

        Com base no perfil detalhado do atleta e nos treinos realizados nas últimas semanas, gere um plano de treino semanal com foco no objetivo do atleta, respeitando sua disponibilidade de dias, nível de experiência e preferências.

        ### Perfil do Atleta:
        - Nome: %s
        - Idade: %d anos
        - Objetivo: %s
        - Nível de Experiência: %s
        - Dias disponíveis para treinar: %s
        - Dia preferido para treino longo: %s
        - Provas cadastradas: %s

        ### Histórico de treinos realizados recentemente:
        %s

        ### Regras para o plano de treino:
        - Incluir de 3 a 5 treinos
        - Variar os tipos de treinos (regenerativo, intervalado, longo, etc.)
        - Respeitar a distribuição dos dias disponíveis
        - O treino longo deve preferencialmente cair no dia preferido
        - Os treinos devem estar adaptados ao nível do atleta

        ### Para cada treino, gere os seguintes campos:
        - diaSemana (ex: SEGUNDA)
        - tipo (ex: INTERVALADO, LONGO, REGENERATIVO, etc.)
        - descricao: explicação detalhada do treino
        - fc_alvo: faixa de frequência cardíaca (ex: 70–80%% FCmáx)
        - duracao_min: duração total estimada em minutos
        - distancia_km: distância estimada em km
        - ritmo_alvo: ex: 5:45-6:15/km
        - observacao: orientações e dicas do treinador

        Gere **apenas o JSON** como resposta. Não inclua nenhuma explicação adicional.
        """,
                atleta.nome(),
                atleta.idade(),
                atleta.objetivo(),
                atleta.nivelExperiencia(),
                atleta.diasDisponiveis(),
                atleta.diaPreferidoLongo(),
                formatarProvas(atleta.provas()),
                formatarTreinos(treinosRecentes)
        );
    }


    private String formatarProvas(List<ProvaOutputDto> provas){

        if(provas == null || provas.isEmpty()){
            return "Nenhuma prova cadastrada";
        }

        return provas.stream()
                .map(p -> String.format("- %s em %s (%.1f km)",
                        p.nome(),
                        p.data().toString(),
                        p.distancia())).collect(Collectors.joining("\n"));

    }

    private String formatarTreinos(List<TreinoRealizadoOutputDto> treinos) {
        if (treinos == null || treinos.isEmpty()) {
            return "Nenhum treino recente encontrado.";
        }

        return treinos.stream()
                .map(t -> String.format("""
                - %s
                  Tipo: %s
                  Distância: %.1f km | Duração: %d min | Ritmo alvo: %s | FC alvo: %s
                  Observações: %s
                """,
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
