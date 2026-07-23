package br.com.menthoros.backend.mapper;

import br.com.menthoros.backend.dto.input.AtletaOnboardingInputDto;
import br.com.menthoros.backend.dto.output.AtletaOnboardingOutputDto;
import br.com.menthoros.backend.dto.output.CalibracaoStatusOutputDto;
import br.com.menthoros.backend.dto.output.OnboardingConclusaoOutputDto;
import br.com.menthoros.backend.entity.PerfilOnboardingAtleta;
import br.com.menthoros.backend.services.onboarding.CalibrationStatusResult;
import br.com.menthoros.backend.services.onboarding.OnboardingConclusionResult;
import br.com.menthoros.backend.services.onboarding.OnboardingDraftInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OnboardingMapper {

    private final ProvaMapper provaMapper;

    public OnboardingDraftInput toDraftInput(AtletaOnboardingInputDto dto, boolean preenchidoPorCoach) {
        if (dto == null) {
            throw new IllegalArgumentException("AtletaOnboardingInputDto cannot be null");
        }
        return new OnboardingDraftInput(
                dto.objetivo(),
                dto.nivelExperiencia(),
                dto.diasDisponiveis(),
                dto.volumeSemanalMax(),
                dto.temLesao(),
                dto.descricaoLesao(),
                dto.dataUltimaLesao(),
                dto.historicoLesoes(),
                dto.maiorTreinoRecenteKm(),
                dto.duracaoDisponivelMin(),
                dto.restricoes(),
                dto.modalidade(),
                dto.percepcaoCondicionamento(),
                preenchidoPorCoach,
                dto.canalIntegracao(),
                dto.dispositivoMarca(),
                dto.dispositivoModelo()
        );
    }

    public AtletaOnboardingOutputDto toOutputDto(PerfilOnboardingAtleta entity) {
        if (entity == null) {
            throw new IllegalArgumentException("PerfilOnboardingAtleta entity cannot be null");
        }
        return new AtletaOnboardingOutputDto(
                entity.getId(),
                entity.getStatus(),
                entity.getObjetivo(),
                entity.getNivelExperiencia(),
                entity.getDiasDisponiveis(),
                entity.getVolumeSemanalMax(),
                entity.getTemLesao(),
                entity.getDescricaoLesao(),
                entity.getDataUltimaLesao(),
                entity.getHistoricoLesoes(),
                entity.getMaiorTreinoRecenteKm(),
                entity.getDuracaoDisponivelMin(),
                entity.getRestricoes(),
                entity.getModalidade(),
                entity.getPercepcaoCondicionamento(),
                entity.isPreenchidoPorCoach(),
                entity.getCanalIntegracao(),
                entity.getDispositivoMarca(),
                entity.getDispositivoModelo(),
                entity.getCriadoEm(),
                entity.getAtualizadoEm()
        );
    }

    public OnboardingConclusaoOutputDto toConclusaoOutputDto(OnboardingConclusionResult result) {
        if (result == null) {
            throw new IllegalArgumentException("OnboardingConclusionResult cannot be null");
        }
        return new OnboardingConclusaoOutputDto(
                result.perfil().getStatus(),
                provaMapper.toOutputDto(result.provaAlvo()),
                result.context().baseline().ctlEstimado(),
                result.context().baseline().dataEstimativa(),
                result.context().confidenceScore(),
                result.confidenceTier()
        );
    }

    public CalibracaoStatusOutputDto toCalibracaoStatusOutputDto(CalibrationStatusResult result) {
        if (result == null) {
            throw new IllegalArgumentException("CalibrationStatusResult cannot be null");
        }
        return new CalibracaoStatusOutputDto(
                result.phase(), result.stage(), result.weekNumber(), result.confidenceScore());
    }
}
