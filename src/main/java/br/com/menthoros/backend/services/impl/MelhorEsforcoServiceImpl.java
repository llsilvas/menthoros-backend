package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.dto.intervalsicu.IcuPaceCurveDto;
import br.com.menthoros.backend.dto.output.MelhorEsforcoDto;
import br.com.menthoros.backend.dto.output.MelhoresEsforcosOutputDto;
import br.com.menthoros.backend.entity.IntegracaoExterna;
import br.com.menthoros.backend.multitenancy.TenantContext;
import br.com.menthoros.backend.services.IntervalsIcuClient;
import br.com.menthoros.backend.services.IntervalsIcuConnectionService;
import br.com.menthoros.backend.services.MelhorEsforcoService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Extrai, do curve de pace do intervals.icu, o melhor tempo contínuo do atleta em cada distância
 * de referência — diferente de {@code AtletaProgressServiceImpl.getRecordes} (PR de treino
 * inteiro): distâncias curtas acontecem DENTRO de um treino maior, e só o intervals.icu (com o
 * stream real) sabe achar esse trecho.
 */
@Service
@RequiredArgsConstructor
public class MelhorEsforcoServiceImpl implements MelhorEsforcoService {

    /** Distâncias-alvo, em metros, na mesma ordem exibida (design.md §5). */
    private record Alvo(String label, double metros) {}

    private static final List<Alvo> ALVOS = List.of(
            new Alvo("400m", 400.0),
            new Alvo("800m", 800.0),
            new Alvo("1.5k", 1500.0),
            new Alvo("1mi", 1609.34),
            new Alvo("3k", 3000.0),
            new Alvo("5k", 5000.0),
            new Alvo("10k", 10000.0)
    );

    /**
     * Validado contra a API real (task 1.1): o curve traz pontos exatos nas distâncias-alvo — 1%
     * é folga de arredondamento, não compensação de amostragem esparsa.
     */
    private static final double TOLERANCIA_RELATIVA = 0.01;

    private static final String HAS_TENANT =
            "T(br.com.menthoros.backend.multitenancy.TenantContext).hasTenant()";
    private static final String TENANT_KEY =
            "T(br.com.menthoros.backend.multitenancy.TenantContext).getTenantId()";

    private final IntervalsIcuConnectionService connectionService;
    private final IntervalsIcuClient intervalsIcuClient;

    /**
     * Cache Caffeine próprio (`CacheConfig`, TTL padrão 30min). <b>Independente</b> do cache de
     * {@link #buscarParaAtleta} — nomes de cache diferentes, um por formato de retorno. Coach e
     * atleta abrindo a mesma janela dentro do TTL disparam 2 chamadas ao intervals.icu, não 1; não
     * é dedupe cross-caller, só evita repetir a chamada nas re-aberturas do mesmo caminho (achado
     * de review, 2026-09-18 — documentado, não corrigido: dedupe cross-caller exigiria um cache
     * compartilhado por chave `atletaId+janela` sozinho, sem o formato de retorno na chave, o que
     * complica a invalidação entre os dois shapes).
     */
    @Override
    @Cacheable(value = "melhores-esforcos",
            key = "#atletaId + '_' + #janela + '_' + " + TENANT_KEY, condition = HAS_TENANT)
    public List<MelhorEsforcoDto> buscar(UUID atletaId, String janela) {
        return buscarMarcas(atletaId, janela);
    }

    /** Cache próprio — ver nota de {@link #buscar} sobre os dois caches não serem compartilhados. */
    @Override
    @Cacheable(value = "melhores-esforcos-atleta",
            key = "#atletaId + '_' + #janela + '_' + " + TENANT_KEY, condition = HAS_TENANT)
    public MelhoresEsforcosOutputDto buscarParaAtleta(UUID atletaId, String janela) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Optional<IntegracaoExterna> conexao = connectionService.conexaoAtiva(atletaId, tenantId);
        if (conexao.isEmpty()) {
            return new MelhoresEsforcosOutputDto(List.of(), false);
        }
        return new MelhoresEsforcosOutputDto(buscarMarcasComConexao(conexao.get(), janela), true);
    }

    /** Lógica comum a {@link #buscar} e {@link #buscarParaAtleta}: resolve conexão, busca e extrai. */
    private List<MelhorEsforcoDto> buscarMarcas(UUID atletaId, String janela) {
        UUID tenantId = TenantContext.getRequiredTenantId();
        Optional<IntegracaoExterna> conexao = connectionService.conexaoAtiva(atletaId, tenantId);
        if (conexao.isEmpty()) {
            return List.of();
        }
        return buscarMarcasComConexao(conexao.get(), janela);
    }

    private List<MelhorEsforcoDto> buscarMarcasComConexao(IntegracaoExterna integracao, String janela) {
        IcuPaceCurveDto curva = intervalsIcuClient.buscarPaceCurves(
                integracao.getAccessToken(), integracao.getExternalAthleteId(), janela);
        return extrairMarcas(curva);
    }

    private List<MelhorEsforcoDto> extrairMarcas(IcuPaceCurveDto curva) {
        if (curva.list() == null || curva.list().isEmpty()) {
            return List.of();
        }
        IcuPaceCurveDto.Curva primeira = curva.list().getFirst();
        List<Double> distancias = primeira.distance();
        List<Integer> valores = primeira.values();
        if (distancias == null || valores == null || distancias.isEmpty()
                || distancias.size() != valores.size()) {
            return List.of();
        }

        List<MelhorEsforcoDto> marcas = new ArrayList<>();
        for (Alvo alvo : ALVOS) {
            int melhorIndice = -1;
            double melhorDesvio = Double.MAX_VALUE;
            for (int i = 0; i < distancias.size(); i++) {
                double desvio = Math.abs(distancias.get(i) - alvo.metros()) / alvo.metros();
                if (desvio < melhorDesvio) {
                    melhorDesvio = desvio;
                    melhorIndice = i;
                }
            }
            if (melhorIndice >= 0 && melhorDesvio <= TOLERANCIA_RELATIVA) {
                double distanciaMetros = distancias.get(melhorIndice);
                int tempoSegundos = valores.get(melhorIndice);
                marcas.add(new MelhorEsforcoDto(
                        alvo.label(), distanciaMetros, tempoSegundos,
                        formatarPace(tempoSegundos, distanciaMetros)));
            }
        }
        return marcas;
    }

    private String formatarPace(int tempoSegundos, double distanciaMetros) {
        double paceSecPerKm = tempoSegundos / (distanciaMetros / 1000.0);
        long total = Math.round(paceSecPerKm);
        return String.format("%d:%02d/km", total / 60, total % 60);
    }
}
