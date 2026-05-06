package br.com.menthoros.backend.repository.projection;

import br.com.menthoros.backend.enums.AtletaStatus;

import java.util.UUID;

public interface AtletaListProjection {
    UUID getId();
    String getNome();
    String getEmail();
    AtletaStatus getAtivo();
}
