package br.com.menthoros.backend.services.helper;

import br.com.menthoros.backend.enums.ErroCategoriaPull;
import br.com.menthoros.backend.enums.ResultadoPull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PullAcumulador: o resultado do pull a partir do que aconteceu")
class PullAcumuladorTest {

    @Test
    @DisplayName("sem interrupção nem ignoradas → COMPLETO, com as inserções")
    void completo() {
        var acc = new PullAcumulador();
        acc.inserida();
        acc.inserida();

        assertThat(acc.resultado()).isEqualTo(new PullResultado(ResultadoPull.COMPLETO, null, 2, 0));
    }

    @Test
    @DisplayName("janela vazia sem erro → COMPLETO com zero")
    void janelaVazia() {
        assertThat(new PullAcumulador().resultado())
                .isEqualTo(new PullResultado(ResultadoPull.COMPLETO, null, 0, 0));
    }

    @Test
    @DisplayName("interrompido depois de inserir → PARCIAL com o que foi commitado")
    void parcialPorInsercao() {
        var acc = new PullAcumulador();
        acc.inserida();
        acc.inserida();
        acc.interrompido(ErroCategoriaPull.INESPERADO);

        assertThat(acc.resultado())
                .isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.INESPERADO, 2, 0));
    }

    @Test
    @DisplayName("interrompido depois de avançar sem inserir (fatia varrida sem corrida) → PARCIAL")
    void parcialPorAvanco() {
        var acc = new PullAcumulador();
        acc.avancou();
        acc.interrompido(ErroCategoriaPull.RATE_LIMIT);

        assertThat(acc.resultado().resultado()).isEqualTo(ResultadoPull.PARCIAL);
    }

    @Test
    @DisplayName("interrompido sem progresso → FALHA")
    void falha() {
        var acc = new PullAcumulador();
        acc.interrompido(ErroCategoriaPull.RATE_LIMIT);

        assertThat(acc.resultado())
                .isEqualTo(new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.RATE_LIMIT, 0, 0));
    }

    @Test
    @DisplayName("ignorada sem interrupção → PARCIAL com a categoria da ignorada")
    void ignoradaDaParcial() {
        var acc = new PullAcumulador();
        acc.inserida();
        acc.ignorada(ErroCategoriaPull.DADOS_INVALIDOS);

        assertThat(acc.resultado())
                .isEqualTo(new PullResultado(ResultadoPull.PARCIAL, ErroCategoriaPull.DADOS_INVALIDOS, 1, 1));
    }

    @Test
    @DisplayName("a categoria da interrupção prevalece sobre a da ignorada")
    void interrupcaoPrevalece() {
        var acc = new PullAcumulador();
        acc.ignorada(ErroCategoriaPull.DADOS_INVALIDOS);
        acc.interrompido(ErroCategoriaPull.CREDENCIAL);

        assertThat(acc.resultado())
                .isEqualTo(new PullResultado(ResultadoPull.FALHA, ErroCategoriaPull.CREDENCIAL, 0, 1));
    }

    @Test
    @DisplayName("QA — janela não esgotada (teto por ciclo) é PARCIAL, não COMPLETO: sobrou trabalho")
    void backlogPendenteEParcial() {
        var acc = new PullAcumulador();
        acc.inserida();
        acc.backlogPendente();

        assertThat(acc.resultado()).isEqualTo(new PullResultado(ResultadoPull.PARCIAL, null, 1, 0));
    }

    @Test
    @DisplayName("QA — falha de infraestrutura (banco) é transitória, nunca inesperada")
    void falhaDeBancoETransitoria() {
        assertThat(PullAcumulador.falhaDeInfraestrutura(new org.springframework.dao.CannotAcquireLockException("deadlock")))
                .isTrue();
        assertThat(PullAcumulador.falhaDeInfraestrutura(
                new IllegalStateException("envolve", new org.springframework.dao.QueryTimeoutException("timeout"))))
                .isTrue();
        assertThat(PullAcumulador.falhaDeInfraestrutura(
                new org.springframework.transaction.CannotCreateTransactionException("pool esgotado")))
                .isTrue();
        assertThat(PullAcumulador.falhaDeInfraestrutura(
                new org.springframework.jdbc.CannotGetJdbcConnectionException("conexão recusada")))
                .isTrue();
        assertThat(PullAcumulador.falhaDeInfraestrutura(new IllegalStateException("bug no mapper"))).isFalse();
    }

    @Test
    @DisplayName("QA — erro de banco DETERMINÍSTICO (constraint, uso da API) não é transitório: tem de contar tentativa")
    void erroDeterministicoDeBancoNaoETransitorio() {
        // se fosse transitório, a atividade "veneno" nunca chegaria a 3 tentativas e travaria a fatia para sempre
        assertThat(PullAcumulador.falhaDeInfraestrutura(
                new org.springframework.dao.DataIntegrityViolationException("value too long for type varchar(100)")))
                .isFalse();
        assertThat(PullAcumulador.falhaDeInfraestrutura(
                new org.springframework.dao.InvalidDataAccessApiUsageException("uso errado")))
                .isFalse();
        assertThat(PullAcumulador.falhaDeInfraestrutura(
                new org.springframework.transaction.TransactionSystemException("commit falhou por validação")))
                .isFalse();
    }

    @Test
    @DisplayName("cadeia de causa cíclica não trava a classificação")
    void causaCiclicaNaoTrava() {
        var a = new IllegalStateException("a");
        var b = new IllegalArgumentException("b", a);
        a.initCause(b);

        assertThat(PullAcumulador.falhaDeInfraestrutura(a)).isFalse();
    }
}
