package br.com.menthoros.backend.controller;

import br.com.menthoros.backend.dto.output.StatusOutputDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class StatusControllerTest {

    private static final String APPLICATION = "menthoros-test";
    private static final String VERSION = "9.9.9";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-13T12:00:00Z");

    @Nested
    @DisplayName("getStatus")
    class GetStatus {

        @Test
        @DisplayName("retorna 200 com application, version e timestamp do Clock injetado")
        void retornaStatusComClockFixo() {
            // Arrange
            Clock clockFixo = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
            StatusController controller = new StatusController(APPLICATION, VERSION, clockFixo);

            // Act
            var response = controller.getStatus();

            // Assert
            assertEquals(200, response.getStatusCode().value());
            StatusOutputDto body = response.getBody();
            assertNotNull(body, "ResponseEntity.getBody() não deve ser nulo");
            assertEquals(APPLICATION, body.application());
            assertEquals(VERSION, body.version());
            assertEquals(OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC), body.timestamp());
        }

        @Test
        @DisplayName("timestamp reflete o Clock injetado, não o relógio do sistema")
        void timestampVemDoClock() {
            // Arrange
            Instant outroInstante = Instant.parse("2020-01-01T00:00:00Z");
            Clock clockFixo = Clock.fixed(outroInstante, ZoneOffset.UTC);
            StatusController controller = new StatusController(APPLICATION, VERSION, clockFixo);

            // Act
            StatusOutputDto body = controller.getStatus().getBody();

            // Assert
            assertNotNull(body, "ResponseEntity.getBody() não deve ser nulo");
            assertEquals(OffsetDateTime.ofInstant(outroInstante, ZoneOffset.UTC), body.timestamp());
        }
    }
}
