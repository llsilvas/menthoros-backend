package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.ResultadoPull;
import org.jspecify.annotations.Nullable;

/**
 * Acumula o que o pull fez ao longo de TODAS as fases — carga, varredura e finalização (cursor e
 * status). Existe para que uma falha tardia não apague o que já foi commitado: o pull captura a
 * exceção, chama {@link #interrompido} e devolve {@link #resultado()} com as inserções reais, em vez
 * de lançar e deixar o scheduler registrar {@code FALHA}/0 (design D5).
 *
 * <p>Não é thread-safe: um acumulador por ciclo de um atleta.</p>
 */
public final class PullAcumulador {

    private int insercoes;
    private int ignoradas;
    private boolean progresso;
    private @Nullable ErroCategoriaPull interrupcao;
    private @Nullable ErroCategoriaPull categoriaIgnorada;

    /** Um registro novo commitado. */
    public void inserida() {
        insercoes++;
        progresso = true;
    }

    /** Progresso confirmado sem inserção (ex.: fatia do Strava varrida inteira, sem corrida nova). */
    public void avancou() {
        progresso = true;
    }

    /** Atividade pulada sem bloquear o cursor (sem data válida, ou descartada). */
    public void ignorada(ErroCategoriaPull categoria) {
        ignoradas++;
        if (categoriaIgnorada == null) {
            categoriaIgnorada = categoria;
        }
    }

    /** O ciclo parou antes do fim da janela; o resto fica para o próximo. */
    public void interrompido(ErroCategoriaPull categoria) {
        interrupcao = categoria;
    }

    public int insercoes() {
        return insercoes;
    }

    public PullResultado resultado() {
        if (interrupcao != null) {
            return new PullResultado(progresso ? ResultadoPull.PARCIAL : ResultadoPull.FALHA,
                    interrupcao, insercoes, ignoradas);
        }
        if (ignoradas > 0) {
            return new PullResultado(ResultadoPull.PARCIAL, categoriaIgnorada, insercoes, ignoradas);
        }
        return new PullResultado(ResultadoPull.COMPLETO, null, insercoes, 0);
    }
}
