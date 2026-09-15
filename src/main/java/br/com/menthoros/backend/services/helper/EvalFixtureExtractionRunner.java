package br.com.menthoros.backend.services.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runner MANUAL, fora do fluxo de produção e fora do CI (plan-generation-eval-set, task 1.3) —
 * nunca instanciado pelo Spring (sem {@code @Component}, conecta via JDBC puro). Lê gerações reais
 * do ledger, estratifica e redige via {@link EvalFixtureExtractor}/{@link EvalPiiRedactor}, e grava
 * as fixtures de auditoria + {@code manifest.sha256} em
 * {@code src/test/resources/eval/plan-generation/auditoria/}.
 *
 * <p><b>Uso</b> (variáveis de ambiente, nunca hardcoded):
 * <pre>
 * EVAL_DB_URL=jdbc:postgresql://HOST:5432/DB EVAL_DB_USER=... EVAL_DB_PASSWORD=... \
 *   ./mvnw -q compile dependency:build-classpath -Dmdep.outputFile=/tmp/eval-cp.txt &amp;&amp; \
 *   java -cp target/classes:$(cat /tmp/eval-cp.txt) \
 *   br.com.menthoros.backend.services.helper.EvalFixtureExtractionRunner
 * </pre>
 *
 * <p>Heurísticas de rótulo (documentadas, não exatas — aproximação aceitável para auditoria, ver
 * proposal.md "Open Questions"): arquétipo = {@code nivel_experiencia} + sufixo {@code _LESAO}
 * quando {@code tem_lesao}; cold-start = {@code tsb_inicio IS NULL} no plano (sem baseline de
 * carga calculado ainda); veredito = {@code review_status} do plano.
 */
public final class EvalFixtureExtractionRunner {

    private static final int ALVO_FIXTURES = 50;
    private static final Path DESTINO =
            Path.of("src/test/resources/eval/plan-generation/auditoria");

    private EvalFixtureExtractionRunner() {
    }

    public static void main(String[] args) throws Exception {
        String url = requireEnv("EVAL_DB_URL");
        String user = requireEnv("EVAL_DB_USER");
        String password = requireEnv("EVAL_DB_PASSWORD");

        List<EvalFixtureExtractor.CandidatoAmostra> candidatos;
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            candidatos = buscarCandidatos(conn);
        }

        var extractor = new EvalFixtureExtractor(new EvalPiiRedactor());
        List<EvalFixtureExtractor.FixtureAuditoria> fixtures =
                extractor.estratificarERedigir(candidatos, ALVO_FIXTURES);

        Files.createDirectories(DESTINO);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        StringBuilder manifesto = new StringBuilder();
        int n = 0;
        for (var fixture : fixtures) {
            n++;
            String nomeArquivo = fixture.arquetipo().toLowerCase() + "-" + n + ".json";
            Path destino = DESTINO.resolve(nomeArquivo);
            String conteudo = mapper.writeValueAsString(fixture);
            Files.writeString(destino, conteudo, StandardCharsets.UTF_8);
            manifesto.append(nomeArquivo).append(' ').append(sha256(conteudo)).append('\n');
        }
        Files.writeString(DESTINO.resolve("manifest.sha256"), manifesto.toString(), StandardCharsets.UTF_8);

        System.out.printf("Extraídas %d fixtures de auditoria em %s%n", fixtures.size(), DESTINO);
    }

    private static List<EvalFixtureExtractor.CandidatoAmostra> buscarCandidatos(Connection conn) throws SQLException {
        String sql = """
                SELECT lc.generation_request_id, lc.response_json, lc.schema_version, lc.prompt_version,
                       ps.id AS plano_semanal_id, ps.review_status, ps.tsb_inicio,
                       a.nivel_experiencia, a.tem_lesao, a.fc_maxima, a.fc_limiar, a.pace_limiar,
                       a.nome, a.data_nascimento
                FROM tb_llm_call lc
                JOIN tb_plano_semanal ps ON ps.generation_request_id = lc.generation_request_id
                JOIN tb_atleta a ON a.id = ps.atleta_id
                WHERE lc.result = 'SUCCESS' AND lc.response_json IS NOT NULL
                ORDER BY lc.created_at DESC
                LIMIT 500
                """;
        List<EvalFixtureExtractor.CandidatoAmostra> resultado = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID generationRequestId = (UUID) rs.getObject("generation_request_id");
                UUID planoSemanalId = (UUID) rs.getObject("plano_semanal_id");
                boolean temLesao = rs.getBoolean("tem_lesao");
                String arquetipo = rs.getString("nivel_experiencia") + (temLesao ? "_LESAO" : "");
                boolean coldStart = rs.getObject("tsb_inicio") == null;
                String veredito = rs.getString("review_status");
                Integer fcMaxima = (Integer) rs.getObject("fc_maxima");
                Integer fcLimiar = (Integer) rs.getObject("fc_limiar");
                BigDecimal paceLimiar = rs.getBigDecimal("pace_limiar");
                String nomeAtleta = rs.getString("nome");
                LocalDate nascimento = rs.getObject("data_nascimento", LocalDate.class);
                Integer idade = nascimento != null ? Period.between(nascimento, LocalDate.now()).getYears() : null;

                String planoFinalJson = buscarPlanoFinalJson(conn, planoSemanalId);
                var piiAlvo = new EvalPiiRedactor.PiiAlvo(nomeAtleta, idade, null, null, null);

                resultado.add(new EvalFixtureExtractor.CandidatoAmostra(
                        generationRequestId, rs.getString("response_json"), rs.getString("schema_version"),
                        rs.getString("prompt_version"), planoFinalJson, fcMaxima, fcLimiar, paceLimiar,
                        arquetipo, coldStart, veredito, piiAlvo));
            }
        }
        return resultado;
    }

    private static String buscarPlanoFinalJson(Connection conn, UUID planoSemanalId) throws SQLException {
        String sql = """
                SELECT data_treino, tipo_treino, zona_alvo, tss_planejado, justificativa_ia, editado_pelo_coach
                FROM tb_treino_planejado
                WHERE plano_semanal_id = ?
                ORDER BY data_treino
                """;
        List<Map<String, Object>> treinos = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, planoSemanalId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> treino = new LinkedHashMap<>();
                    treino.put("dataTreino", String.valueOf(rs.getObject("data_treino")));
                    treino.put("tipoTreino", rs.getString("tipo_treino"));
                    treino.put("zonaAlvo", rs.getString("zona_alvo"));
                    treino.put("tssPlanejado", rs.getObject("tss_planejado"));
                    treino.put("justificativaIa", rs.getString("justificativa_ia"));
                    treino.put("editadoPeloCoach", rs.getBoolean("editado_pelo_coach"));
                    treinos.add(treino);
                }
            }
        }
        try {
            return new ObjectMapper().writeValueAsString(treinos);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao serializar plano final para JSON", e);
        }
    }

    private static String requireEnv(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória ausente: " + nome);
        }
        return valor;
    }

    private static String sha256(String conteudo) throws java.security.NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(conteudo.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
