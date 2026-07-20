package br.com.menthoros.backend.repository;

import br.com.menthoros.backend.entity.AtividadeProvenienciaDescartada;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface AtividadeProvenienciaDescartadaRepository extends CrudRepository<AtividadeProvenienciaDescartada, UUID> {

    List<AtividadeProvenienciaDescartada> findByAtividadeId(UUID atividadeId);
}
