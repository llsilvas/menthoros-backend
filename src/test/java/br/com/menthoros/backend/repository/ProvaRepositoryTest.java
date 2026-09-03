package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.AbstractIntegrationTest;
import br.com.menthoros.backend.entity.Assessoria;
import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.Prova;
import br.com.menthoros.backend.enums.AtletaStatus;
import br.com.menthoros.backend.enums.DistanciaProva;
import br.com.menthoros.backend.enums.NivelExperiencia;
import br.com.menthoros.backend.enums.PlanoAssessoria;
import br.com.menthoros.backend.enums.ProvaStatus;
import br.com.menthoros.backend.enums.TipoProva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProvaRepository}.
 */
@Transactional
class ProvaRepositoryTest extends AbstractIntegrationTest {

    @Autowired private ProvaRepository provaRepository;
    @Autowired private AssessoriaRepository assessoriaRepository;
    @Autowired private AtletaRepository atletaRepository;

    private Assessoria assessoria;
    private Atleta atleta;

    @BeforeEach
    void setup() {
        assessoria = new Assessoria();
        assessoria.setNome("Assessoria Test Prova");
        assessoria.setDominio("prova-test-" + UUID.randomUUID());
        assessoria.setPlano(PlanoAssessoria.BASIC);
        assessoria = assessoriaRepository.save(assessoria);

        atleta = new Atleta();
        atleta.setNome("Atleta Prova");
        atleta.setEmail("atleta-prova-" + UUID.randomUUID() + "@test.com");
        atleta.setObjetivo("Meia maratona sub-1h45");
        atleta.setNivelExperiencia(NivelExperiencia.INTERMEDIARIO);
        atleta.setAtivo(AtletaStatus.ATIVO);
        atleta.setAssessoria(assessoria);
        atleta = atletaRepository.save(atleta);
    }

    @Nested
    @DisplayName("findProvasRealizadasRecentes")
    class FindProvasRealizadasRecentes {

        @Test
        @DisplayName("retorna prova realizada válida independente de distanciaKm ser nulo")
        void retornaProvaValidaComDistanciaKmNulo() {
            Prova prova = criarProva("Meia SP", LocalDate.now().minusDays(10), DistanciaProva.KM_21, null,
                    true, LocalTime.of(1, 45, 0));
            provaRepository.save(prova);

            List<Prova> resultado = provaRepository.findProvasRealizadasRecentes(
                    atleta.getId(), assessoria.getId(), LocalDate.now().minusDays(90));

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getNomeProva()).isEqualTo("Meia SP");
        }

        @Test
        @DisplayName("retorna prova realizada válida com distanciaKm customizado preenchido")
        void retornaProvaValidaComDistanciaKmCustomizado() {
            Prova prova = criarProva("10K Custom", LocalDate.now().minusDays(5), DistanciaProva.KM_10,
                    BigDecimal.valueOf(10.5), true, LocalTime.of(0, 50, 0));
            provaRepository.save(prova);

            List<Prova> resultado = provaRepository.findProvasRealizadasRecentes(
                    atleta.getId(), assessoria.getId(), LocalDate.now().minusDays(90));

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).getDistanciaKm()).isEqualByComparingTo(BigDecimal.valueOf(10.5));
        }

        @Test
        @DisplayName("exclui prova fora da janela de dias")
        void excluiProvaForaDaJanela() {
            Prova prova = criarProva("Prova Antiga", LocalDate.now().minusDays(120), DistanciaProva.KM_10, null,
                    true, LocalTime.of(0, 50, 0));
            provaRepository.save(prova);

            List<Prova> resultado = provaRepository.findProvasRealizadasRecentes(
                    atleta.getId(), assessoria.getId(), LocalDate.now().minusDays(90));

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("exclui prova sem foiRealizada")
        void excluiProvaSemFoiRealizada() {
            Prova prova = criarProva("Prova Futura", LocalDate.now().minusDays(5), DistanciaProva.KM_10, null,
                    false, LocalTime.of(0, 50, 0));
            provaRepository.save(prova);

            List<Prova> resultado = provaRepository.findProvasRealizadasRecentes(
                    atleta.getId(), assessoria.getId(), LocalDate.now().minusDays(90));

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("exclui prova com statusProva CANCELADA (mesmo com foiRealizada/tempoRealizado preenchidos)")
        void excluiProvaCancelada() {
            Prova prova = criarProva("Prova Cancelada", LocalDate.now().minusDays(5), DistanciaProva.KM_10, null,
                    true, LocalTime.of(0, 50, 0));
            prova.setStatusProva(ProvaStatus.CANCELADA);
            provaRepository.save(prova);

            List<Prova> resultado = provaRepository.findProvasRealizadasRecentes(
                    atleta.getId(), assessoria.getId(), LocalDate.now().minusDays(90));

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("exclui prova sem tempoRealizado")
        void excluiProvaSemTempoRealizado() {
            Prova prova = criarProva("Prova Sem Tempo", LocalDate.now().minusDays(5), DistanciaProva.KM_10, null,
                    true, null);
            provaRepository.save(prova);

            List<Prova> resultado = provaRepository.findProvasRealizadasRecentes(
                    atleta.getId(), assessoria.getId(), LocalDate.now().minusDays(90));

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("nunca retorna prova de outro tenant")
        void nuncaRetornaProvaDeOutroTenant() {
            Assessoria outraAssessoria = new Assessoria();
            outraAssessoria.setNome("Outra Assessoria");
            outraAssessoria.setDominio("outra-prova-test-" + UUID.randomUUID());
            outraAssessoria.setPlano(PlanoAssessoria.BASIC);
            outraAssessoria = assessoriaRepository.save(outraAssessoria);

            Atleta outroAtleta = new Atleta();
            outroAtleta.setNome("Atleta Outro Tenant");
            outroAtleta.setEmail("outro-atleta-" + UUID.randomUUID() + "@test.com");
            outroAtleta.setObjetivo("5K");
            outroAtleta.setNivelExperiencia(NivelExperiencia.INICIANTE);
            outroAtleta.setAtivo(AtletaStatus.ATIVO);
            outroAtleta.setAssessoria(outraAssessoria);
            outroAtleta = atletaRepository.save(outroAtleta);

            Prova provaOutroTenant = criarProva("Prova Outro Tenant", LocalDate.now().minusDays(5),
                    DistanciaProva.KM_10, null, true, LocalTime.of(0, 50, 0));
            provaOutroTenant.setAtleta(outroAtleta);
            provaOutroTenant.setAssessoria(outraAssessoria);
            provaRepository.save(provaOutroTenant);

            List<Prova> resultado = provaRepository.findProvasRealizadasRecentes(
                    atleta.getId(), assessoria.getId(), LocalDate.now().minusDays(90));

            assertThat(resultado).isEmpty();
        }

        @Test
        @DisplayName("múltiplas provas válidas retornam ordenadas por dataProva DESC")
        void multiplasProvasOrdenadasPorDataDesc() {
            Prova maisAntiga = criarProva("Prova Mais Antiga", LocalDate.now().minusDays(60),
                    DistanciaProva.KM_10, null, true, LocalTime.of(0, 50, 0));
            Prova maisRecente = criarProva("Prova Mais Recente", LocalDate.now().minusDays(5),
                    DistanciaProva.KM_21, null, true, LocalTime.of(1, 45, 0));
            provaRepository.save(maisAntiga);
            provaRepository.save(maisRecente);

            List<Prova> resultado = provaRepository.findProvasRealizadasRecentes(
                    atleta.getId(), assessoria.getId(), LocalDate.now().minusDays(90));

            assertThat(resultado).hasSize(2);
            assertThat(resultado.get(0).getNomeProva()).isEqualTo("Prova Mais Recente");
            assertThat(resultado.get(1).getNomeProva()).isEqualTo("Prova Mais Antiga");
        }
    }

    @Nested
    @DisplayName("findPendentesRevisao")
    class FindPendentesRevisao {

        @Test
        @DisplayName("inclui futura pendente e cancelada pendente; exclui passada, revisada e de outro tenant")
        void filtraPendentes() {
            LocalDate hoje = LocalDate.now();
            Prova futuraPendente = pendente("Futura", hoje.plusWeeks(10), ProvaStatus.PLANEJADA);
            Prova canceladaPendente = pendente("Cancelada", hoje.plusWeeks(4), ProvaStatus.CANCELADA);
            Prova passadaPendente = pendente("Passada", hoje.minusDays(1), ProvaStatus.CONCLUIDA);
            Prova futuraRevisada = pendente("Revisada", hoje.plusWeeks(6), ProvaStatus.PLANEJADA);
            futuraRevisada.setRevisadaPeloCoach(true);
            provaRepository.saveAll(List.of(futuraPendente, canceladaPendente, passadaPendente, futuraRevisada));

            Assessoria outra = new Assessoria();
            outra.setNome("Outra");
            outra.setDominio("outra-" + UUID.randomUUID());
            outra.setPlano(PlanoAssessoria.BASIC);
            outra = assessoriaRepository.save(outra);
            Atleta atletaOutra = new Atleta();
            atletaOutra.setNome("Outro");
            atletaOutra.setEmail("outro-" + UUID.randomUUID() + "@test.com");
            atletaOutra.setObjetivo("x");
            atletaOutra.setNivelExperiencia(NivelExperiencia.INICIANTE);
            atletaOutra.setAtivo(AtletaStatus.ATIVO);
            atletaOutra.setAssessoria(outra);
            atletaOutra = atletaRepository.save(atletaOutra);
            Prova deOutroTenant = pendente("Outro tenant", hoje.plusWeeks(8), ProvaStatus.PLANEJADA);
            deOutroTenant.setAtleta(atletaOutra);
            deOutroTenant.setAssessoria(outra);
            provaRepository.save(deOutroTenant);

            List<Prova> porTenant = provaRepository.findPendentesRevisaoByAssessoria(assessoria.getId(), hoje);
            List<Prova> porAtleta = provaRepository.findPendentesRevisaoByAtleta(atleta.getId(), assessoria.getId(), hoje);

            assertThat(porTenant).extracting(Prova::getNomeProva).containsExactly("Cancelada", "Futura");
            assertThat(porAtleta).extracting(Prova::getNomeProva).containsExactly("Cancelada", "Futura");
        }

        @Test
        @DisplayName("prova gravada sem tocar na flag nasce revisada (default true)")
        void defaultRevisada() {
            Prova prova = criarProva("Meia SP", LocalDate.now().plusWeeks(12), DistanciaProva.KM_21, null,
                    false, null);
            provaRepository.saveAndFlush(prova);

            assertThat(provaRepository.findPendentesRevisaoByAtleta(atleta.getId(), assessoria.getId(), LocalDate.now())).isEmpty();
        }

        private Prova pendente(String nome, LocalDate data, ProvaStatus status) {
            Prova prova = criarProva(nome, data, DistanciaProva.KM_21, null, false, null);
            prova.setStatusProva(status);
            prova.setRevisadaPeloCoach(false);
            prova.setMotivoRevisao(br.com.menthoros.backend.enums.MotivoRevisaoProva.NOVA);
            return prova;
        }
    }

    private Prova criarProva(String nome, LocalDate dataProva, DistanciaProva distancia, BigDecimal distanciaKm,
                              boolean foiRealizada, LocalTime tempoRealizado) {
        Prova prova = new Prova();
        prova.setNomeProva(nome);
        prova.setDataProva(dataProva);
        prova.setDistancia(distancia);
        prova.setDistanciaKm(distanciaKm);
        prova.setTipoProva(TipoProva.CORRIDA_RUA);
        prova.setStatusProva(ProvaStatus.CONCLUIDA);
        prova.setFoiRealizada(foiRealizada);
        prova.setTempoRealizado(tempoRealizado);
        prova.setAtleta(atleta);
        prova.setAssessoria(assessoria);
        return prova;
    }
}
