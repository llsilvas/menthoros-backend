package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.output.RecommendationExplanation;
import br.com.menthoros.backend.dto.output.SugestaoCoachOutputDto;
import br.com.menthoros.backend.entity.SugestaoCoach;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SugestaoCoachMapper {

    private final ObjectMapper objectMapper;

    public SugestaoCoachOutputDto toOutputDto(SugestaoCoach entity) {
        if (entity == null) throw new IllegalArgumentException("SugestaoCoach entity cannot be null");

        return new SugestaoCoachOutputDto(
                entity.getId(),
                entity.getAtleta().getId(),
                nomeCompleto(entity),
                entity.getTipo(),
                entity.getStatus(),
                entity.getConfidence(),
                entity.getSummary(),
                deserializarReasoning(entity.getReasoningJson()),
                entity.getCreatedAt(),
                entity.getReviewedAt(),
                entity.getExpiresAt()
        );
    }

    private String nomeCompleto(SugestaoCoach entity) {
        String sobrenome = entity.getAtleta().getSobrenome();
        return sobrenome != null && !sobrenome.isBlank()
                ? entity.getAtleta().getNome() + " " + sobrenome
                : entity.getAtleta().getNome();
    }

    private RecommendationExplanation deserializarReasoning(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, RecommendationExplanation.class);
        } catch (JsonProcessingException e) {
            log.warn("SugestaoCoachMapper: falha ao deserializar reasoning: {}", e.getMessage());
            return null;
        }
    }
}
