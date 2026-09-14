package br.com.menthoros.backend.services.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida a separação {@code plano-treino-system.txt} / {@code plano-treino-user.txt}
 * (system-user-prompt-split, CA2 e CA3).
 */
@DisplayName("Templates plano-treino-system.txt e plano-treino-user.txt")
class PlanoTreinoPromptTemplatesTest {

    private static final String SYSTEM_TEMPLATE = "prompts/plano-treino-system.txt";
    private static final String USER_TEMPLATE = "prompts/plano-treino-user.txt";

    /** Placeholders de {@code String.format}: %s, %d, %%, ou posicional (%3$s). */
    private static final Pattern PLACEHOLDER_FORMATO = Pattern.compile("%\\d*\\$?[sd]|%%");

    private static final List<String> SECOES_SYSTEM = List.of(
            "### ANÁLISE OBRIGATÓRIA PRÉ-PLANEJAMENTO",
            "### PRIORIZAÇÃO POR OBJETIVO",
            "### MATRIZ DE VARIABILIDADE DE ESTÍMULOS",
            "### REGRAS DO PLANO",
            "### ESTRUTURA OBRIGATÓRIA DO TREINO INTERVALADO",
            "### ESTRUTURA OBRIGATÓRIA DE OUTROS TIPOS DE TREINO",
            "## ESTRUTURA DE ETAPAS PARA TREINO FARTLEK",
            "### ENUMS (STRING, UPPERCASE - APENAS ESTES VALORES)",
            "### CAMPOS OBRIGATÓRIOS (TOPO DO JSON / PLANO SEMANAL)",
            "### CAMPOS OBRIGATÓRIOS (POR TREINO)",
            "### CAMPOS OBRIGATÓRIOS (POR ETAPA)",
            "### REGRAS DE CONSISTÊNCIA E QUALIDADE",
            "### VALIDAÇÃO FINAL (CHECKLIST INTERNO)",
            "### INSTRUÇÕES CRÍTICAS - FORMATO DE SAÍDA"
    );

    private static final List<String> SECOES_USER = List.of(
            "### PERFIL DO ATLETA",
            "### HISTÓRICO RECENTE"
    );

    @Nested
    @DisplayName("plano-treino-system.txt")
    class System {

        @Test
        @DisplayName("não contém placeholder de String.format (CA3)")
        void semPlaceholderDeFormato() throws IOException {
            String conteudo = lerRecurso(SYSTEM_TEMPLATE);
            assertThat(PLACEHOLDER_FORMATO.matcher(conteudo).find())
                    .as("system.txt é lido cru (loadTemplate, sem String.format); qualquer %%s/%%d/%%%%"
                            + " ou placeholder posicional (%%3$s) indica conteúdo dinâmico vazado pro bloco"
                            + " estático")
                    .isFalse();
        }

        @ParameterizedTest(name = "contém a seção \"{0}\"")
        @ValueSource(strings = {
                "### ANÁLISE OBRIGATÓRIA PRÉ-PLANEJAMENTO",
                "### PRIORIZAÇÃO POR OBJETIVO",
                "### MATRIZ DE VARIABILIDADE DE ESTÍMULOS",
                "### REGRAS DO PLANO",
                "### ESTRUTURA OBRIGATÓRIA DO TREINO INTERVALADO",
                "### ESTRUTURA OBRIGATÓRIA DE OUTROS TIPOS DE TREINO",
                "## ESTRUTURA DE ETAPAS PARA TREINO FARTLEK",
                "### ENUMS (STRING, UPPERCASE - APENAS ESTES VALORES)",
                "### CAMPOS OBRIGATÓRIOS (TOPO DO JSON / PLANO SEMANAL)",
                "### CAMPOS OBRIGATÓRIOS (POR TREINO)",
                "### CAMPOS OBRIGATÓRIOS (POR ETAPA)",
                "### REGRAS DE CONSISTÊNCIA E QUALIDADE",
                "### VALIDAÇÃO FINAL (CHECKLIST INTERNO)",
                "### INSTRUÇÕES CRÍTICAS - FORMATO DE SAÍDA"
        })
        @DisplayName("preserva as seções do template original (CA2)")
        void preservaSecoes(String secao) throws IOException {
            assertThat(lerRecurso(SYSTEM_TEMPLATE)).contains(secao);
        }

        @Test
        @DisplayName("mantém a regra de objetivo sem placeholder posicional")
        void regraDeObjetivoSemPlaceholder() throws IOException {
            String conteudo = lerRecurso(SYSTEM_TEMPLATE);
            assertThat(conteudo)
                    .as("linha 167 do original tinha %%3$s — placeholder real dentro do bloco tratado"
                            + " como estático; corrigido para texto genérico")
                    .contains("O objetivo do atleta determina o treino-chave da semana")
                    .doesNotContain("%3$s");
        }

        @Test
        @DisplayName("não sobrou nenhum %%% de escape do String.format legado")
        void semTriploPercentual() throws IOException {
            assertThat(lerRecurso(SYSTEM_TEMPLATE)).doesNotContain("%%%");
        }
    }

    @Nested
    @DisplayName("plano-treino-user.txt")
    class User {

        @ParameterizedTest(name = "contém a seção \"{0}\"")
        @ValueSource(strings = {"### PERFIL DO ATLETA", "### HISTÓRICO RECENTE"})
        @DisplayName("preserva as seções dinâmicas do template original (CA2)")
        void preservaSecoes(String secao) throws IOException {
            assertThat(lerRecurso(USER_TEMPLATE)).contains(secao);
        }

        @Test
        @DisplayName("tem os 8 placeholders na ordem Nome, Idade, Objetivo, Nível, Dias, DiaPreferido, Provas, Histórico")
        void oitoPlaceholdersNaOrdem() throws IOException {
            String conteudo = lerRecurso(USER_TEMPLATE);
            assertThat(conteudo)
                    .contains("- Nome: %s")
                    .contains("- Idade: %d anos")
                    .contains("- Objetivo Principal: %s")
                    .contains("- Nível: %s")
                    .contains("- Dias disponíveis: %s")
                    .contains("- Dia preferido do longo: %s")
                    .contains("- Provas planejadas: %s");
            // 8º placeholder (Histórico) fica sozinho na linha "**Dados:**\n%s"
            assertThat(conteudo).contains("**Dados:**");
        }
    }

    private static String lerRecurso(String nome) throws IOException {
        ClassPathResource resource = new ClassPathResource(nome);
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
