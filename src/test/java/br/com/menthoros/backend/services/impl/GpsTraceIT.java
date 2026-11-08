package br.com.menthoros.backend.services.impl;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.TreinoRealizado;
import br.com.menthoros.backend.entity.Usuario;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.UserRole;
import br.com.menthoros.backend.repository.AssessoriaRepository;
import br.com.menthoros.backend.repository.AtletaRepository;
import br.com.menthoros.backend.repository.TreinoRealizadoRepository;
import br.com.menthoros.backend.repository.UsuarioRepository;
import br.com.menthoros.backend.services.helper.GpsTraceCodec;
import br.com.menthoros.backend.services.helper.GpsTraceSimplifier;
import com.garmin.fit.DateTime;
import com.garmin.fit.File;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.Sport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trajeto GPS de ponta a ponta (add-activity-gps-trace, CA1, CA2, CA4): upload de FIT real →
 * {@code gps_trace} gravado pela V113 no Postgres → endpoint do atleta; esteira sem posição e
 * outro atleta do mesmo tenant respondem 404.
 */
@AutoConfigureMockMvc
@DisplayName("Trajeto GPS do treino realizado — ponta a ponta")
class GpsTraceIT extends AbstractIntegrationTest {

    private static final String FIT = "/fit/corrida-15km-16laps.fit";

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TreinoRealizadoRepository treinoRepository;

    @TempDir Path tempDir;

    private Assessoria assessoria;
    private Atleta atleta;
    private UUID subjectAtleta;
    private UUID subjectOutroAtleta;

    @BeforeEach
    void seed() {
        assessoria = new Assessoria();
        assessoria.setNome("Assessoria GPS IT");
        assessoria.setDominio("gps-it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        subjectAtleta = UUID.randomUUID();
        atleta = atletaComUsuario("Atleta GPS IT", subjectAtleta);
        subjectOutroAtleta = UUID.randomUUID();
        atletaComUsuario("Outro Atleta GPS IT", subjectOutroAtleta);
    }

    @Test
    @DisplayName("FIT outdoor: gps_trace gravado, endpoint devolve o trajeto, outro atleta recebe 404")
    void outdoorGravaEExpoeTrajeto() throws Exception {
        mockMvc.perform(multipart("/api/v1/atletas/me/treinos/importar-fit")
                        .file(new MockMultipartFile("arquivo", "corrida.fit",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, arquivoFit()))
                        .with(jwtDe(subjectAtleta)))
                .andExpect(status().isCreated());

        TreinoRealizado treino = unicoTreinoDe(atleta);
        assertThat(treino.getGpsTrace()).as("gps_trace persistido pela V113").isNotNull();
        var payload = GpsTraceCodec.fromJson(treino.getGpsTrace());
        assertThat(payload.n()).isBetween(2, GpsTraceSimplifier.MAX_POINTS);
        assertThat(treino.getGpsTrace().length()).as("bytes do JSON").isLessThan(8 * 1024);

        mockMvc.perform(get("/api/v1/atletas/me/realizados/{id}/trajeto", treino.getId()).with(jwtDe(subjectAtleta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.v").value(1))
                .andExpect(jsonPath("$.n", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.n", lessThanOrEqualTo(GpsTraceSimplifier.MAX_POINTS)))
                .andExpect(jsonPath("$.polyline", not(emptyString())))
                .andExpect(jsonPath("$.tSeg[0]").value(0))
                .andExpect(jsonPath("$.fc").isArray());

        // CA4 — outro atleta do MESMO tenant: 404, sem revelar que o treino existe.
        mockMvc.perform(get("/api/v1/atletas/me/realizados/{id}/trajeto", treino.getId()).with(jwtDe(subjectOutroAtleta)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FIT de esteira (sem posição): treino criado sem gps_trace; trajeto responde 404")
    void esteiraSemTrajeto() throws Exception {
        mockMvc.perform(multipart("/api/v1/atletas/me/treinos/importar-fit")
                        .file(new MockMultipartFile("arquivo", "esteira.fit",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, fitSemGps()))
                        .with(jwtDe(subjectAtleta)))
                .andExpect(status().isCreated());

        TreinoRealizado treino = unicoTreinoDe(atleta);
        assertThat(treino.getGpsTrace()).isNull();

        mockMvc.perform(get("/api/v1/atletas/me/realizados/{id}/trajeto", treino.getId()).with(jwtDe(subjectAtleta)))
                .andExpect(status().isNotFound());
    }

    private Atleta atletaComUsuario(String nome, UUID subject) {
        Atleta a = new Atleta();
        a.setNome(nome);
        a.setEmail("gps-it-" + UUID.randomUUID() + "@menthoros.test");
        a.setObjetivo("Correr 15km");
        a.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        a.setAtivo(AtletaStatus.ATIVO);
        a.setAssessoria(assessoria);
        a = atletaRepository.save(a);
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .id(subject)
                .keycloakId(subject.toString())
                .assessoria(assessoria)
                .email(subject + "@menthoros.test")
                .nome("Usuario " + subject)
                .role(UserRole.ATLETA)
                .ativo(true)
                .build());
        a.setUsuario(usuario);
        return atletaRepository.save(a);
    }

    private TreinoRealizado unicoTreinoDe(Atleta dono) {
        List<TreinoRealizado> treinos = treinoRepository.findByAtletaIdOrderByDataTreinoAsc(dono.getId());
        assertThat(treinos).hasSize(1);
        return treinos.get(0);
    }

    private RequestPostProcessor jwtDe(UUID subject) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ATLETA"))
                .jwt(j -> j.subject(subject.toString())
                        .claim("tenant_id", assessoria.getId().toString())
                        .claim("email", subject + "@menthoros.test")
                        .claim("roles", List.of("ATLETA")));
    }

    private static byte[] arquivoFit() throws IOException {
        try (InputStream in = GpsTraceIT.class.getResourceAsStream(FIT)) {
            assertThat(in).as("fixture %s", FIT).isNotNull();
            return in.readAllBytes();
        }
    }

    /** Corrida de 30 min com records de FC e sem posição — o que uma esteira produz. */
    private byte[] fitSemGps() throws IOException {
        Instant inicio = Instant.now().minusSeconds(7200);
        FileIdMesg fileId = new FileIdMesg();
        fileId.setType(File.ACTIVITY);
        fileId.setManufacturer(1);
        fileId.setSerialNumber(424242L);
        fileId.setTimeCreated(new DateTime(inicio));

        SessionMesg session = new SessionMesg();
        session.setStartTime(new DateTime(inicio));
        session.setSport(Sport.RUNNING);
        session.setTotalElapsedTime(1800f);
        session.setTotalTimerTime(1800f);
        session.setTotalDistance(5000f);
        session.setAvgHeartRate((short) 150);
        session.setMaxHeartRate((short) 170);

        Path arquivo = tempDir.resolve("esteira-" + System.nanoTime() + ".fit");
        FileEncoder encoder = new FileEncoder(arquivo.toFile());
        encoder.write(fileId);
        for (int i = 0; i < 1800; i += 10) {
            RecordMesg r = new RecordMesg();
            r.setTimestamp(new DateTime(inicio.plusSeconds(i)));
            r.setHeartRate((short) (140 + (i % 30)));
            r.setDistance(i * 2.78f);
            encoder.write(r);
        }
        encoder.write(session);
        encoder.close();
        return Files.readAllBytes(arquivo);
    }
}
