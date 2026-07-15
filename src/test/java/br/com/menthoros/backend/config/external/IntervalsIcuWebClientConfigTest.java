package br.com.menthoros.backend.config.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntervalsIcuWebClientConfigTest {

    @Test
    @DisplayName("cria WebClient com baseUrl das properties")
    void criaWebClientComBaseUrl() {
        IntervalsIcuProperties props = new IntervalsIcuProperties();
        props.setBaseUrl("https://intervals.icu");

        IntervalsIcuWebClientConfig config = new IntervalsIcuWebClientConfig(props);

        assertThat(config.intervalsIcuWebClient()).isNotNull();
    }
}
