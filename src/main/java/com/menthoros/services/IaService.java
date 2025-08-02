package com.menthoros.services;

import com.menthoros.dto.AtletaDto;
import com.menthoros.dto.PlanoDto;
import com.menthoros.dto.PlanoTreinoDto;
import com.menthoros.dto.TreinoRealizadoDto;
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

    public PlanoDto gerarPlano(AtletaDto atletaDto, PlanoTreinoDto planoDto, List<TreinoRealizadoDto> treinoRealizadoDtoList){

        return chatClient.prompt()
                .user(buildRequest(atletaDto, treinoRealizadoDtoList))
                .call()
                .entity(PlanoDto.class);
    }

    public Map<Long, PlanoDto> gerarPlanosEmLote(Map<AtletaDto, List<TreinoRealizadoDto>> atletaDtoListMap){
        return null;
    }

    public String buildRequest(AtletaDto atletaDto, List<TreinoRealizadoDto> treinoRealizadoDtoList){

        return String.format(
                """
                Você é um treinador de corrida especialista.
                Com base nas informações abaixo, gere um plano de treino semanal no formato JSON estruturado.
                Informações do atleta: %s
                Histórico de treinos: %s
                """,
                atletaDto,
                treinoRealizadoDtoList
        );

    }
}
