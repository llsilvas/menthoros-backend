package br.com.menthoros.backend.services;

import br.com.menthoros.backend.entity.Atleta;
import br.com.menthoros.backend.entity.PlanoMetaDados;

import java.util.UUID;

/**
 * Serviço responsável pelo gerenciamento de metadados de planos de treino.
 *
 * <p>Esta interface define operações relacionadas aos metadados que acompanham
 * os planos semanais, incluindo métricas de progressão, volume médio e TSB.
 *
 * @see PlanoMetaDados
 * @see Atleta
 */
public interface PlanoMetadadosService {

    /**
     * Busca ou cria os metadados de plano para um atleta.
     *
     * <p>Este método utiliza cache para otimizar o acesso aos metadados.
     * Se os metadados não existirem, serão criados automaticamente com valores padrão.
     *
     * @param atleta o atleta cujos metadados serão buscados ou criados
     * @return os metadados do plano do atleta
     * @throws IllegalArgumentException se o atleta for nulo
     */
    PlanoMetaDados buscarOuCriarMetadados(Atleta atleta);

    /**
     * Busca os metadados mais recentes de um atleta pelo ID.
     *
     * @param atletaId identificador único do atleta
     * @return os metadados encontrados, ou um novo metadado se não existir
     */
    PlanoMetaDados buscarPorAtletaId(UUID atletaId);
}