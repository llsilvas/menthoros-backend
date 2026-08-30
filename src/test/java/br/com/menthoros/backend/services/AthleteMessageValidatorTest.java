package br.com.menthoros.backend.services;

import br.com.menthoros.backend.dto.llm.AthleteMessageDto;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AthleteMessageValidatorTest {

    private final AthleteMessageValidator validator = new AthleteMessageValidator();

    private static AthleteMessageDto valido() {
        return new AthleteMessageDto(
                "Você segurou o ritmo nos dois blocos de tempo.",
                "Saiu como planejado: 58 min contra 61 previstos, com os blocos dentro da faixa.",
                "Um 7 num treino previsto como 6 — pesou um pouco mais que o esperado.",
                "Capriche no sono hoje e vale comentar com seu coach como você acorda amanhã.");
    }

    @Test
    void aceita_bloco_valido_em_pt() {
        assertThat(validator.validar(valido())).isEmpty();
    }

    @Test
    void bloqueia_jargao_de_treinador() {
        AthleteMessageDto dto = new AthleteMessageDto(
                valido().recognition(),
                "Seu TSB está em -28, sinal de fadiga acumulada.",
                valido().effortReading(),
                valido().nextWorkoutTip());

        assertThat(validator.validar(dto)).contains(AthleteMessageValidator.MOTIVO_JARGAO);
    }

    @Test
    void bloqueia_prescricao_de_pular_treino() {
        AthleteMessageDto dto = new AthleteMessageDto(
                valido().recognition(),
                valido().howItWent(),
                valido().effortReading(),
                "Melhor pular o treino de quinta e descansar.");

        assertThat(validator.validar(dto)).contains(AthleteMessageValidator.MOTIVO_JARGAO);
    }

    @Test
    void nao_bloqueia_palavras_que_contem_os_tokens() {
        // "atleta" contém "atl", "descanso" contém "sc" — o \b protege contra falso positivo.
        AthleteMessageDto dto = new AthleteMessageDto(
                "Você correu como um atleta consistente hoje.",
                valido().howItWent(),
                valido().effortReading(),
                valido().nextWorkoutTip());

        assertThat(validator.validar(dto)).isEmpty();
    }

    @Test
    void bloqueia_campo_acima_de_240_chars() {
        AthleteMessageDto dto = new AthleteMessageDto(
                valido().recognition(),
                "Você foi muito bem e " + "de novo ".repeat(40),
                valido().effortReading(),
                valido().nextWorkoutTip());

        assertThat(validator.validar(dto)).contains(AthleteMessageValidator.MOTIVO_TAMANHO);
    }

    @Test
    void bloqueia_texto_em_ingles() {
        AthleteMessageDto dto = new AthleteMessageDto(
                "Great job! You held the pace on both blocks.",
                "The workout went as planned and you finished strong.",
                "It was harder than expected but you managed it well.",
                "Get good sleep and talk to your coach tomorrow.");

        assertThat(validator.validar(dto)).contains(AthleteMessageValidator.MOTIVO_IDIOMA);
    }

    @Test
    void bloco_incompleto_nao_e_bloqueio() {
        AthleteMessageDto dto = new AthleteMessageDto(valido().recognition(), null, " ", valido().nextWorkoutTip());

        assertThat(validator.completo(dto)).isFalse();
        assertThat(validator.completo(valido())).isTrue();
    }

    @Test
    void motivos_sao_estaveis() {
        // Persistidos em atleta_bloqueado_motivo (VARCHAR 40) — mudar o valor quebra histórico.
        assertThat(Optional.of(AthleteMessageValidator.MOTIVO_JARGAO)).contains("JARGAO_OU_PRESCRICAO");
        assertThat(AthleteMessageValidator.MOTIVO_TAMANHO).isEqualTo("TAMANHO");
        assertThat(AthleteMessageValidator.MOTIVO_IDIOMA).isEqualTo("IDIOMA");
    }
}
