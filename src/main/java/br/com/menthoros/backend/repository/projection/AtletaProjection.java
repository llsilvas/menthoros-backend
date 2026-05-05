package br.com.menthoros.backend.repository.projection;

import java.util.UUID;

public interface AtletaProjection {
    UUID getId();
    String getNome();
    String getEmail();
}
