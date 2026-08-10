package br.com.menthoros.backend.dto.input;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CoachSignupInputDto — validação e normalização do cadastro público")
class CoachSignupInputDtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrirValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fecharValidator() {
        factory.close();
    }

    private static CoachSignupInputDto valido() {
        return new CoachSignupInputDto(
                "Maria Treinadora",
                "maria@exemplo.com",
                "senha-forte-o-suficiente",
                "Assessoria Corrida na Serra",
                "corridasserra",
                null);
    }

    /** Nomes das propriedades que violaram alguma restrição. */
    private static Set<String> violacoes(CoachSignupInputDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("payload válido não produz violação")
    void payloadValido() {
        assertThat(violacoes(valido())).isEmpty();
    }

    @Nested
    @DisplayName("Normalização")
    class Normalizacao {

        @Test
        @DisplayName("e-mail e slug são normalizados para minúsculas e sem espaços nas bordas")
        void normalizaEmailESlug() {
            var dto = new CoachSignupInputDto(
                    "  Maria Treinadora  ", "  MARIA@Exemplo.COM  ", "senha-forte-o-suficiente",
                    "  Assessoria Corrida na Serra  ", "  CorridaSerra  ", null);

            assertThat(dto.email()).isEqualTo("maria@exemplo.com");
            assertThat(dto.slug()).isEqualTo("corridaserra");
            assertThat(dto.nome()).isEqualTo("Maria Treinadora");
            assertThat(dto.nomeAssessoria()).isEqualTo("Assessoria Corrida na Serra");
        }

        @Test
        @DisplayName("a senha NÃO é normalizada — trim alteraria o segredo escolhido pelo usuário")
        void naoNormalizaSenha() {
            var comEspacos = "  senha com bordas  ";
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", comEspacos, "Assessoria", "corridasserra", null);

            assertThat(dto.senha()).isEqualTo(comEspacos);
        }

        @Test
        @DisplayName("campos nulos não quebram a normalização — quem reporta o erro é a validação")
        void toleraNulos() {
            var dto = new CoachSignupInputDto(null, null, null, null, null, null);

            assertThat(violacoes(dto)).contains("nome", "email", "senha", "nomeAssessoria", "slug");
        }
    }

    @Nested
    @DisplayName("Slug")
    class Slug {

        @ParameterizedTest(name = "\"{0}\" é rejeitado")
        @ValueSource(strings = {
                "Corrida Serra",   // espaço
                "corrida_serra",   // underscore
                "corrida.serra",   // ponto
                "-corridaserra",   // hífen na borda
                "corridaserra-",
                "corrida--serra",  // hífen duplicado
                "ab",              // curto demais
                "acentuaç",        // acento
                "CorridaSerra "    // normalizado vira válido, mas com espaço interno não
        })
        void slugsInvalidos(String slug) {
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "senha-forte-o-suficiente", "Assessoria", slug, null);

            // "CorridaSerra " normaliza para "corridaserra", que é válido — os demais devem falhar.
            if ("corridaserra".equals(dto.slug())) {
                assertThat(violacoes(dto)).doesNotContain("slug");
            } else {
                assertThat(violacoes(dto)).contains("slug");
            }
        }

        @ParameterizedTest(name = "\"{0}\" é aceito")
        @ValueSource(strings = {"corridasserra", "team-x", "abc", "a1b2c3", "assessoria-da-maria-2026"})
        void slugsValidos(String slug) {
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "senha-forte-o-suficiente", "Assessoria", slug, null);

            assertThat(violacoes(dto)).doesNotContain("slug");
        }

        @Test
        @DisplayName("\"default\" é reservado — é o tenant semeado pela V2, e tomá-lo colide com dado existente")
        void slugDefaultEhReservado() {
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "senha-forte-o-suficiente", "Assessoria", "default", null);

            assertThat(violacoes(dto)).contains("slugPermitido");
        }

        @ParameterizedTest(name = "\"{0}\" é reservado")
        @ValueSource(strings = {"api", "admin", "www", "app", "auth", "login", "menthoros"})
        void slugsReservados(String slug) {
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "senha-forte-o-suficiente", "Assessoria", slug, null);

            assertThat(violacoes(dto)).contains("slugPermitido");
        }

        @Test
        @DisplayName("a reserva é checada após a normalização — \"ADMIN\" não escapa por caixa alta")
        void reservaResisteACaixaAlta() {
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "senha-forte-o-suficiente", "Assessoria", " ADMIN ", null);

            assertThat(violacoes(dto)).contains("slugPermitido");
        }

        @Test
        @DisplayName("não excede o varchar(100) de tb_assessoria.dominio")
        void slugRespeitaAColuna() {
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "senha-forte-o-suficiente", "Assessoria",
                    "a".repeat(101), null);

            assertThat(violacoes(dto)).contains("slug");
        }
    }

    @Nested
    @DisplayName("Senha")
    class Senha {

        @Test
        @DisplayName("senha curta é rejeitada — o realm não tem passwordPolicy, então este é o único portão")
        void senhaCurta() {
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "curta", "Assessoria", "corridasserra", null);

            assertThat(violacoes(dto)).contains("senha");
        }

        @Test
        @DisplayName("senha absurdamente longa é rejeitada antes de chegar ao Keycloak")
        void senhaLonga() {
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "a".repeat(129), "Assessoria", "corridasserra", null);

            assertThat(violacoes(dto)).contains("senha");
        }
    }

    @Nested
    @DisplayName("Demais campos")
    class DemaisCampos {

        @Test
        @DisplayName("e-mail malformado é rejeitado")
        void emailInvalido() {
            var dto = new CoachSignupInputDto(
                    "Maria", "nao-e-email", "senha-forte-o-suficiente", "Assessoria", "corridasserra", null);

            assertThat(violacoes(dto)).contains("email");
        }

        @Test
        @DisplayName("nome da assessoria não excede o varchar(200) da coluna")
        void nomeAssessoriaRespeitaAColuna() {
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "senha-forte-o-suficiente",
                    "a".repeat(201), "corridasserra", null);

            assertThat(violacoes(dto)).contains("nomeAssessoria");
        }

        @Test
        @DisplayName("honeypot preenchido é sinalizado pelo DTO, não pela validação — a resposta precisa ser indistinguível")
        void honeypotNaoViraErroDeValidacao() {
            var dto = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "senha-forte-o-suficiente", "Assessoria", "corridasserra",
                     "http://spam.example");

            assertThat(violacoes(dto)).isEmpty();
            assertThat(dto.honeypotPreenchido()).isTrue();
        }

        @Test
        @DisplayName("honeypot vazio ou em branco não é considerado preenchido")
        void honeypotVazio() {
            assertThat(valido().honeypotPreenchido()).isFalse();
            var comEspacos = new CoachSignupInputDto(
                    "Maria", "maria@exemplo.com", "senha-forte-o-suficiente", "Assessoria", "corridasserra",
                     "   ");
            assertThat(comEspacos.honeypotPreenchido()).isFalse();
        }
    }
}
