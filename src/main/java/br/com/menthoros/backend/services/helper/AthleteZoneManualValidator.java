package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto;
import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto.Eixo;
import br.com.menthoros.backend.dto.input.AthleteZonesManualInputDto.Faixa;
import br.com.menthoros.backend.enums.ModoZonas;
import br.com.menthoros.backend.exception.InvalidZoneAnchorsException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Regras das faixas manuais (design D4). O índice do caminho ({@code fc.faixas[2].min}) é o do array, que
 * chega ordenado por zona. Borda compartilhada e lacuna são aceitas; sobreposição aponta a zona de cima.
 *
 * <p><b>Idempotente:</b> YES. <b>Side Effects:</b> NONE. <b>Tenant-aware:</b> NO.
 */
@Component
public class AthleteZoneManualValidator {

    private record Regras(String eixo, int zonas, int minimo, int maximo, String unidade, boolean pace) {}

    private static final Regras FC = new Regras("fc", 5, 40, 230, "bpm", false);
    private static final Regras PACE = new Regras("pace", 6, 120, 900, "s/km", true);

    /**
     * @throws InvalidZoneAnchorsException com o caminho de cada campo inválido
     */
    public void validar(AthleteZonesManualInputDto input) {
        if (input == null) {
            throw new IllegalArgumentException("Input é obrigatório");
        }
        Map<String, String> erros = new TreeMap<>();
        if (input.fc() == null && input.pace() == null) {
            erros.put("body", "Informe ao menos um grupo: fc ou pace");
        }
        validarEixo(input.fc(), FC, erros);
        validarEixo(input.pace(), PACE, erros);
        if (!erros.isEmpty()) {
            throw new InvalidZoneAnchorsException(erros);
        }
    }

    private static void validarEixo(Eixo eixo, Regras r, Map<String, String> erros) {
        if (eixo == null) {
            return;
        }
        if (eixo.modo() == null) {
            erros.put(r.eixo() + ".modo", "Modo é obrigatório: LIMIAR ou MANUAL");
            return;
        }
        if (eixo.modo() != ModoZonas.MANUAL) {
            return;
        }
        List<Faixa> faixas = eixo.faixas();
        if (faixas == null || faixas.size() != r.zonas()) {
            erros.put(r.eixo() + ".faixas", "Informe as " + r.zonas() + " zonas, de Z1 a Z" + r.zonas());
            return;
        }
        Faixa anterior = null;
        for (int i = 0; i < faixas.size(); i++) {
            Faixa f = faixas.get(i);
            Faixa valida = validarFaixa(f, i, r, erros) ? f : null;
            if (valida != null && anterior != null) {
                validarVizinha(anterior, valida, i, r, erros);
            }
            anterior = valida;
        }
    }

    /** @return true quando a faixa está completa e válida sozinha — só então entra na regra de vizinhas */
    private static boolean validarFaixa(Faixa f, int i, Regras r, Map<String, String> erros) {
        String base = r.eixo() + ".faixas[" + i + "]";
        if (f == null) {
            erros.put(base, "Faixa da Z" + (i + 1) + " é obrigatória");
            return false;
        }
        if (f.zona() == null || f.zona() != i + 1) {
            erros.put(base + ".zona", "Esperada a Z" + (i + 1) + ": uma faixa por zona, em ordem");
            return false;
        }
        boolean ok = limite(f.min(), base + ".min", r, erros) & limite(f.max(), base + ".max", r, erros);
        if (ok && f.min() >= f.max()) {
            erros.put(base + ".max", r.pace()
                    ? "O limite mais lento deve ser maior que o mais rápido"
                    : "\"Até\" deve ser maior que \"De\"");
            return false;
        }
        return ok;
    }

    private static boolean limite(Integer valor, String campo, Regras r, Map<String, String> erros) {
        if (valor == null) {
            erros.put(campo, "Valor é obrigatório");
            return false;
        }
        if (valor < r.minimo() || valor > r.maximo()) {
            erros.put(campo, "Deve estar entre " + r.minimo() + " e " + r.maximo() + " " + r.unidade());
            return false;
        }
        return true;
    }

    /** FC sobe de zona para zona; pace desce (mais rápido = menos s/km). Borda igual é aceita. */
    private static void validarVizinha(Faixa anterior, Faixa atual, int i, Regras r, Map<String, String> erros) {
        String base = r.eixo() + ".faixas[" + i + "]";
        String msg = "Sobrepõe a Z" + i;
        if (r.pace()) {
            if (atual.max() > anterior.min()) {
                erros.put(base + ".max", msg);
            }
        } else if (atual.min() < anterior.max()) {
            erros.put(base + ".min", msg);
        }
    }
}
