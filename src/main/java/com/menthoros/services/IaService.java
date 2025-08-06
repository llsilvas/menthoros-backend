package com.menthoros.services;

import com.menthoros.dto.output.AtletaOutputDto;
import com.menthoros.dto.output.PlanoSemanalOutputDto;
import com.menthoros.dto.output.PlanoTreinoOutputDto;
import com.menthoros.dto.output.TreinoRealizadoOutputDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
                        
                        Com base no perfil do atleta e nos treinos realizados nas últimas semanas, gere um plano de treino semanal no formato JSON estruturado, com foco no objetivo atual do atleta.
                                                
                        Informações do atleta:
                        %s
                        
                        Histórico de treinos realizados:
                        %s
                        
                        O plano deve conter de 3 a 5 treinos com os seguintes campos:
                        - diaSemana
                        - tipo
                        - descricao do treino detalhada
                        - fc_alvo
                        - duracao_min
                        - distancia_km
                        - ritmo_alvo
                        - observacao do treinador
                        
                        Gere **apenas o JSON** como resposta. Não inclua nenhuma explicação adicional.
                        """,
                atleta,
                treinosRecentes
        );
    }

}
