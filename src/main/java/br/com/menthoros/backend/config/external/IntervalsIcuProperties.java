package br.com.menthoros.backend.config.external;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.intervals-icu")
public class IntervalsIcuProperties {

    private String baseUrl = "https://intervals.icu";
}
