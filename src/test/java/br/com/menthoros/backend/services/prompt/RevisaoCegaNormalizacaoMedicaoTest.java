package br.com.menthoros.backend.services.prompt;

import br.com.menthoros.backend.dto.llm.EtapaTreinoLlmDto;
import br.com.menthoros.backend.dto.llm.PlanoSemanalLlmDto;
import br.com.menthoros.backend.dto.llm.TreinoPlanejadoLlmDto;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.services.helper.ContextoNormalizacao;
import br.com.menthoros.backend.services.helper.EtapaFcValidator;
import br.com.menthoros.backend.services.helper.NormalizacaoDeTreino;
import br.com.menthoros.backend.services.helper.PaceValidator;
import br.com.menthoros.backend.services.helper.PlanoEstruturaReparador;
import br.com.menthoros.backend.services.helper.TreinoNormalizador;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mede, sem chamar LLM, quantas etapas dos planos da revisão cega ({@code target/revisao-cega/}, gerados
 * por {@link RevisaoCegaPlanosRealTest}) têm pace implícito fora do {@code ritmoAlvo} (±10%) ou distância
 * zero — na resposta bruta e depois da {@link NormalizacaoDeTreino} de produção
 * (add-etapa-pace-validator, task 0.3). Aproximação: sem zonas de FC e sem teto/piso de pace históricos
 * no contexto; gates que reprovam o treino (turno de reparo em produção) contam como "rejeitado".
 *
 * <pre>./mvnw test -Peval -Dtest=RevisaoCegaNormalizacaoMedicaoTest</pre>
 */
@Tag("eval")
@DisplayName("Revisão cega — medição de pace implícito antes/depois da normalização")
class RevisaoCegaNormalizacaoMedicaoTest {

    private static final Pattern FAIXA = Pattern.compile("^(\\d{1,2}):(\\d{2})-(\\d{1,2}):(\\d{2})(/km)?$");
    private static final Pattern UNICO = Pattern.compile("^(\\d{1,2}):(\\d{2})(/km)?$");

    @Test
    @DisplayName("conta etapas incoerentes por modelo e tipo de treino")
    void mede() throws Exception {
        Path dir = Path.of("target", "revisao-cega");
        assertThat(dir).isDirectory();
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var normalizacao = new NormalizacaoDeTreino(new TreinoNormalizador(new PaceValidator()), new EtapaFcValidator(),
                new PlanoEstruturaReparador(new SimpleMeterRegistry()), new PaceValidator(), new SimpleMeterRegistry());
        Map<String, Atleta> atletas = new TreeMap<>();
        EvalCandidateFixtures.todos().forEach(c -> atletas.put(c.arquetipo().nome(), c.arquetipo().atleta()));

        Map<String, int[]> placar = new TreeMap<>(); // chave modelo|tipo → [etapas, brutoFora, bruto0, normFora, norm0, rejeitados]
        StringBuilder detalhe = new StringBuilder();
        try (Stream<Path> arquivos = Files.list(dir)) {
            for (Path p : arquivos.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                String[] partes = p.getFileName().toString().split("\\.");
                String arq = partes[0];
                String modelo = partes[1] + (partes.length > 4 ? "." + partes[2] : "");
                Atleta atleta = atletas.get(arq);
                var ctx = new ContextoNormalizacao(atleta, atleta.getId(), null, Map.of(), Map.of());
                PlanoSemanalLlmDto plano = mapper.readValue(Files.readString(p), PlanoSemanalLlmDto.class);
                for (TreinoPlanejadoLlmDto bruto : plano.treinosPlanejados()) {
                    int[] c = placar.computeIfAbsent(modelo + "|" + bruto.tipoTreino(), k -> new int[6]);
                    TreinoPlanejadoLlmDto norm = null;
                    try {
                        norm = normalizacao.normalizar(bruto, ctx);
                    } catch (RuntimeException e) {
                        c[5]++;
                    }
                    for (EtapaTreinoLlmDto e : etapas(bruto)) {
                        c[0]++;
                        String r = avaliar(e);
                        if ("fora".equals(r)) c[1]++;
                        if ("zero".equals(r)) c[2]++;
                    }
                    if (norm != null) {
                        for (EtapaTreinoLlmDto e : etapas(norm)) {
                            String r = avaliar(e);
                            if ("fora".equals(r)) c[3]++;
                            if ("zero".equals(r)) c[4]++;
                            if (r != null) {
                                detalhe.append(String.format("- %s %s %s %s: %s %s em %s min (ritmo %s)%n", p.getFileName(),
                                        bruto.diaSemana(), bruto.tipoTreino(), e.tipoEtapa(), r, e.distanciaKm(),
                                        e.duracaoMin(), e.ritmoAlvo()));
                            }
                        }
                    }
                }
            }
        }
        StringBuilder md = new StringBuilder("| modelo | tipo | etapas | bruto: pace fora | bruto: 0 km | normalizado: pace fora | normalizado: 0 km | treino rejeitado por gate |\n|---|---|---|---|---|---|---|---|\n");
        placar.forEach((k, c) -> md.append(String.format("| %s | %s | %d | %d | %d | %d | %d | %d |%n",
                k.split("\\|")[0], k.split("\\|")[1], c[0], c[1], c[2], c[3], c[4], c[5])));
        md.append("\n### Etapas que continuam incoerentes depois da normalização\n\n").append(detalhe);
        Files.writeString(dir.resolve("medicao-normalizacao.md"), md);
        System.out.println(md);
    }

    private static List<EtapaTreinoLlmDto> etapas(TreinoPlanejadoLlmDto t) {
        return t.etapas() == null ? List.of() : t.etapas();
    }

    /** "fora": pace implícito fora da faixa ±10%; "zero": duração e ritmo sem distância; null: ok ou não avaliável. */
    static String avaliar(EtapaTreinoLlmDto e) {
        double[] faixa = faixa(e.ritmoAlvo());
        if (faixa == null || e.duracaoMin() == null || e.duracaoMin() <= 0) return null;
        if (e.distanciaKm() == null || e.distanciaKm() <= 0) return "zero";
        double pace = e.duracaoMin() / e.distanciaKm();
        return pace < faixa[0] * 0.9 || pace > faixa[1] * 1.1 ? "fora" : null;
    }

    private static double[] faixa(String ritmo) {
        if (ritmo == null) return null;
        String s = ritmo.trim().replace(" ", "");
        Matcher m = FAIXA.matcher(s);
        if (m.matches()) {
            double a = Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2)) / 60.0;
            double b = Integer.parseInt(m.group(3)) + Integer.parseInt(m.group(4)) / 60.0;
            return new double[]{Math.min(a, b), Math.max(a, b)};
        }
        m = UNICO.matcher(s);
        if (m.matches()) {
            double a = Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2)) / 60.0;
            return new double[]{a, a};
        }
        return null;
    }
}
