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
import com.garmin.fit.DateTime;
import com.garmin.fit.File;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.Sport;
import com.garmin.fit.SubSport;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flag de esteira de ponta a ponta (add-activity-treadmill-flag, CA1, CA2, CA4): upload de FIT →
 * coluna {@code esteira} da V115 no Postgres → {@code GET /me/treinos}.
 */
@AutoConfigureMockMvc
@DisplayName("Flag de esteira do treino realizado — ponta a ponta")
class TreadmillFlagIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TreinoRealizadoRepository treinoRepository;

    @TempDir Path tempDir;

    private Assessoria assessoria;
    private Atleta atleta;
    private UUID subjectAtleta;

    @BeforeEach
    void seed() {
        assessoria = new Assessoria();
        assessoria.setNome("Assessoria Esteira IT");
        assessoria.setDominio("esteira-it-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        subjectAtleta = UUID.randomUUID();
        atleta = atletaComUsuario(subjectAtleta);
    }

    @Test
    @DisplayName("FIT com subSport TREADMILL: esteira gravada e exposta em GET /me/treinos")
    void treadmillGravaEExpoe() throws Exception {
        importar(fit(SubSport.TREADMILL));

        assertThat(unicoTreinoDe(atleta).isEsteira()).isTrue();
        mockMvc.perform(get("/api/v1/atletas/me/treinos").param("dias", "7").with(jwtDe(subjectAtleta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].esteira").value(true));
    }

    @Test
    @DisplayName("FIT com subSport INDOOR_RUNNING: esteira false no banco e no contrato")
    void indoorRunningNaoMarca() throws Exception {
        importar(fit(SubSport.INDOOR_RUNNING));

        assertThat(unicoTreinoDe(atleta).isEsteira()).isFalse();
        mockMvc.perform(get("/api/v1/atletas/me/treinos").param("dias", "7").with(jwtDe(subjectAtleta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].esteira").value(false));
    }

    private void importar(byte[] arquivo) throws Exception {
        mockMvc.perform(multipart("/api/v1/atletas/me/treinos/importar-fit")
                        .file(new MockMultipartFile("arquivo", "treino.fit",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, arquivo))
                        .with(jwtDe(subjectAtleta)))
                .andExpect(status().isCreated());
    }

    private Atleta atletaComUsuario(UUID subject) {
        Atleta a = new Atleta();
        a.setNome("Atleta Esteira IT");
        a.setEmail("esteira-it-" + UUID.randomUUID() + "@menthoros.test");
        a.setObjetivo("Correr 10km");
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

    /** Corrida de 30 min sem posição, com o subSport informado. */
    private byte[] fit(SubSport subSport) throws IOException {
        Instant inicio = Instant.now().minusSeconds(7200);
        FileIdMesg fileId = new FileIdMesg();
        fileId.setType(File.ACTIVITY);
        fileId.setManufacturer(1);
        fileId.setSerialNumber(515151L);
        fileId.setTimeCreated(new DateTime(inicio));

        SessionMesg session = new SessionMesg();
        session.setStartTime(new DateTime(inicio));
        session.setSport(Sport.RUNNING);
        session.setSubSport(subSport);
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
