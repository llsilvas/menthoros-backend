package br.com.menthoros.backend.ai.output;

import br.com.menthoros.backend.dto.llm.AnaliseWorkoutRawDto;
import br.com.menthoros.backend.enums.PrimaryAnalysisCause;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DuplicateKeyTolerantOutputConverterTest {

    private static final String SEM_DUPLICATA = """
            {"summary":"s","technical_interpretation":"ti","primary_cause":"PACING_ERROR",
             "recommendation":"r","tags":["a","b"],"execution_score":7,"rationale":"ra"}
            """;

    private final DuplicateKeyTolerantOutputConverter<AnaliseWorkoutRawDto> converter =
            new DuplicateKeyTolerantOutputConverter<>(AnaliseWorkoutRawDto.class);

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUpLog() {
        logger = (Logger) LoggerFactory.getLogger(DuplicateKeyTolerantOutputConverter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDownLog() {
        logger.detachAppender(appender);
    }

    @Nested
    @DisplayName("convert")
    class Convert {

        @Test
        @DisplayName("aceita chaves repetidas e fica com o último valor de cada uma")
        void aceitaDuplicataComUltimoValor() {
            String json = """
                    {"summary":"s","primary_cause":"NORMAL","execution_score":6,
                     "rationale":"ra","primary_cause":"PACING_ERROR","execution_score":8}
                    """;

            AnaliseWorkoutRawDto dto = converter.convert(json);

            assertThat(dto.primaryCause()).isEqualTo(PrimaryAnalysisCause.PACING_ERROR);
            assertThat(dto.executionScore()).isEqualTo(8);
            assertThat(dto.summary()).isEqualTo("s");
            assertThat(dto.rationale()).isEqualTo("ra");
        }

        @Test
        @DisplayName("aceita chave repetida dentro de cerca ```json")
        void aceitaDuplicataEmCercaMarkdown() {
            String json = "```json\n{\"execution_score\":5,\"execution_score\":9}\n```";

            AnaliseWorkoutRawDto dto = converter.convert(json);

            assertThat(dto.executionScore()).isEqualTo(9);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "```json\n", "```\n"})
        @DisplayName("sem duplicata, resultado idêntico ao do BeanOutputConverter")
        void semDuplicataIgualAoBeanOutputConverter(String abertura) {
            String texto = abertura.isEmpty() ? SEM_DUPLICATA : abertura + SEM_DUPLICATA + "```";

            AnaliseWorkoutRawDto esperado = new BeanOutputConverter<>(AnaliseWorkoutRawDto.class).convert(texto);
            AnaliseWorkoutRawDto obtido = converter.convert(texto);

            assertThat(obtido).isEqualTo(esperado);
        }

        @Test
        @DisplayName("texto que não é JSON lança exceção")
        void naoJsonLancaExcecao() {
            assertThatThrownBy(() -> converter.convert("desculpe, não consegui analisar"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("erro de parse não carrega o conteúdo da resposta na mensagem")
        void erroNaoCarregaConteudo() {
            // A mensagem vai para log e para AnaliseWorkout.errorMessage; a resposta pode trazer
            // dado do atleta.
            assertThatThrownBy(() -> converter.convert("{\"summary\":\"dado-sensivel\",\"execution_score\":}"))
                    .satisfies(e -> {
                        for (Throwable t = e; t != null; t = t.getCause()) {
                            assertThat(String.valueOf(t.getMessage())).doesNotContain("dado-sensivel");
                        }
                    });
        }

        @Test
        @DisplayName("registra WARN com o campo repetido quando há duplicata, sem logar o corpo")
        void registraWarnNaDuplicata() {
            converter.convert("{\"summary\":\"segredo\",\"execution_score\":5,\"execution_score\":9}");

            assertThat(appender.list)
                    .filteredOn(e -> e.getLevel() == Level.WARN)
                    .singleElement()
                    .satisfies(e -> {
                        assertThat(e.getFormattedMessage()).contains("execution_score");
                        assertThat(e.getFormattedMessage()).doesNotContain("segredo");
                    });
        }

        @Test
        @DisplayName("não registra WARN em resposta sem duplicata")
        void semWarnSemDuplicata() {
            converter.convert(SEM_DUPLICATA);

            assertThat(appender.list).noneMatch(e -> e.getLevel() == Level.WARN);
        }
    }

    @Nested
    @DisplayName("getFormat")
    class GetFormat {

        @Test
        @DisplayName("envia as mesmas instruções de formato do BeanOutputConverter")
        void mesmoFormatoDoBeanOutputConverter() {
            String esperado = new BeanOutputConverter<>(AnaliseWorkoutRawDto.class).getFormat();

            assertThat(converter.getFormat()).isEqualTo(esperado);
        }
    }
}
