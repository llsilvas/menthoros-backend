package com.menthoros.services.impl;

import com.menthoros.entity.PlanoMetaDados;
import com.menthoros.entity.TreinoRealizado;
import com.menthoros.repository.PlanoMetadadosRepository;
import com.menthoros.repository.TreinoRealizadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TsbServiceImpl {

    private final TreinoRealizadoRepository treinoRealizadoRepository;
    private final PlanoMetadadosRepository planoMetaDadosRepository;

    @Transactional
    public void atualizarTsb(UUID atletaId) {
        // 1 - Obter histórico de treinos ordenado por data

        List<TreinoRealizado> treinos = treinoRealizadoRepository.findByAtletaIdOrderByDataTreinoAsc(atletaId);

        double atl = 0.0;
        double ctl = 0.0;

        for (TreinoRealizado treino : treinos) {
            double tss = calcularTss(treino);

            // Fórmula de média exponencial
            atl += (tss - atl) * (1.0 / 7.0);   // ATL (7 dias)
            ctl += (tss - ctl) * (1.0 / 42.0);  // CTL (42 dias)
        }

        double tsb = ctl - atl;

        // 2 - Atualizar na tabela PlanoMetaDados
        PlanoMetaDados metaDados = planoMetaDadosRepository.findByAtletaId(atletaId)
                .orElseThrow(() -> new IllegalArgumentException("MetaDados não encontrado para atleta: " + atletaId));

        metaDados.setAtl(BigDecimal.valueOf(round(atl)));
        metaDados.setCtl(BigDecimal.valueOf(round(ctl)));
        metaDados.setTsb(BigDecimal.valueOf(round(tsb)));

        planoMetaDadosRepository.save(metaDados);
    }

    private double calcularTss(TreinoRealizado treino) {
        // Exemplo simples: duração em horas * (IF^2) * 100
        double duracaoHoras = treino.getDuracaoMin() / 60.0;
        double ifator = treino.getPercepcaoEsforco() != null ? treino.getPercepcaoEsforco() : 1.0;
        return duracaoHoras * Math.pow(ifator, 2) * 100;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

