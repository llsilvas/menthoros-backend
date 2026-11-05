package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.input.AthleteZoneAnchorsInputDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.exception.InvalidZoneAnchorsException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

/**
 * Único validador das âncoras: a simulação e o PUT passam por ele, então nada que a prévia aceita é
 * rejeitado ao salvar.
 *
 * <p>FC máxima: a informada ou, se vazia, a <b>gravada</b> precisa ser {@code >= round(limiar × 1,06)} —
 * topo da Z5, que {@code ZonaTreinoService} calcula sem olhar a máxima. Máxima estimada nunca valida
 * nem bloqueia: os dois fallbacks de {@code Atleta} divergem (180 x 190), e rejeitar por um número que
 * o treinador não digitou o travaria.
 *
 * <p><b>Idempotente:</b> YES. <b>Side Effects:</b> NONE. <b>Tenant-aware:</b> NO — opera sobre o
 * atleta que o chamador já resolveu por tenant.
 */
@Component
@RequiredArgsConstructor
public class AthleteZoneAnchorsValidator {

    private static final LocalDate DATA_MINIMA = LocalDate.of(2000, 1, 1);
    private static final double TOPO_Z5 = 1.06;

    private final Validator beanValidator;
    private final AtletaHojeResolver atletaHojeResolver;

    /**
     * @throws InvalidZoneAnchorsException com o caminho de cada campo inválido
     */
    public void validar(AthleteZoneAnchorsInputDto input, Atleta atleta) {
        if (input == null || atleta == null) {
            throw new IllegalArgumentException("Input e atleta são obrigatórios");
        }
        Map<String, String> erros = new TreeMap<>();
        if (input.fc() == null && input.pace() == null) {
            erros.put("body", "Informe ao menos um grupo: fc ou pace");
        }
        beanValidator.validate(input).forEach(v -> erros.put(v.getPropertyPath().toString(), v.getMessage()));

        LocalDate hoje = atletaHojeResolver.hojeDe(atleta);
        if (input.fc() != null) {
            validarData("fc.dataTeste", input.fc().dataTeste(), hoje, erros);
            validarMaxima(input.fc(), atleta, erros);
        }
        if (input.pace() != null) {
            validarData("pace.dataTeste", input.pace().dataTeste(), hoje, erros);
        }
        if (!erros.isEmpty()) {
            throw new InvalidZoneAnchorsException(erros);
        }
    }

    private static void validarData(String campo, LocalDate data, LocalDate hoje, Map<String, String> erros) {
        if (data == null || erros.containsKey(campo)) {
            return;
        }
        if (data.isAfter(hoje)) {
            erros.put(campo, "Data do teste não pode estar no futuro");
        } else if (data.isBefore(DATA_MINIMA)) {
            erros.put(campo, "Data do teste não pode ser anterior a 2000-01-01");
        }
    }

    private static void validarMaxima(AthleteZoneAnchorsInputDto.Fc fc, Atleta atleta, Map<String, String> erros) {
        if (fc.limiar() == null || erros.containsKey("fc.limiar") || erros.containsKey("fc.maxima")) {
            return;
        }
        Integer gravada = atleta.getFcMaxima() != null && atleta.getFcMaxima() > 0 ? atleta.getFcMaxima() : null;
        Integer maxima = fc.maxima() != null ? fc.maxima() : gravada;
        int minimo = (int) Math.round(fc.limiar() * TOPO_Z5);
        if (maxima != null && maxima < minimo) {
            erros.put("fc.maxima", "FC máxima deve ser no mínimo " + minimo + " bpm (topo da Z5, 106% do limiar)");
        }
    }
}
