package com.menthoros.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class EnumJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDiaSemanaSerialization() throws Exception {
        String json = objectMapper.writeValueAsString(DiaSemana.SEGUNDA);
        System.out.println("DiaSemana.SEGUNDA: " + json);
        
        json = objectMapper.writeValueAsString(DiaSemana.SABADO);
        System.out.println("DiaSemana.SABADO: " + json);
    }

    @Test
    public void testTipoTreinoEnumSerialization() throws Exception {
        String json = objectMapper.writeValueAsString(TipoTreino.INTERVALADO);
        System.out.println("TipoTreino.INTERVALADO: " + json);
        
        json = objectMapper.writeValueAsString(TipoTreino.LONGO);
        System.out.println("TipoTreino.LONGO: " + json);
    }

    @Test
    public void testNivelExperienciaEnumSerialization() throws Exception {
        String json = objectMapper.writeValueAsString(NivelExperiencia.INICIANTE);
        System.out.println("NivelExperiencia.INICIANTE: " + json);
        
        json = objectMapper.writeValueAsString(NivelExperiencia.AVANCADO);
        System.out.println("NivelExperiencia.AVANCADO: " + json);
    }

    @Test
    public void testPlanoStatusEnumSerialization() throws Exception {
        String json = objectMapper.writeValueAsString(PlanoStatus.EM_ANDAMENTO);
        System.out.println("PlanoStatus.EM_ANDAMENTO: " + json);
        
        json = objectMapper.writeValueAsString(PlanoStatus.CONCLUIDO);
        System.out.println("PlanoStatus.CONCLUIDO: " + json);
    }

    @Test
    public void testTipoEtapaEnumSerialization() throws Exception {
        String json = objectMapper.writeValueAsString(TipoEtapa.AQUECIMENTO);
        System.out.println("TipoEtapa.AQUECIMENTO: " + json);
        
        json = objectMapper.writeValueAsString(TipoEtapa.PRINCIPAL);
        System.out.println("TipoEtapa.PRINCIPAL: " + json);
    }
}