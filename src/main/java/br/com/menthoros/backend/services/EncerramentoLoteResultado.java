package br.com.menthoros.backend.services;

import java.util.List;

/**
 * Resultado do encerramento em lote da semana de uma assessoria (ou da sua projeção/preview).
 *
 * @param atletasProcessados atletas com semana corrente que foram encerrados (ou projetados)
 * @param atletasSemPlano    atletas sem plano na semana corrente (ignorados, não é falha)
 * @param planosConcluidos   quantos planos ficaram CONCLUIDO
 * @param treinosPerdidosTotal soma dos treinos marcados como PERDIDO
 * @param resultados         resumo por atleta encerrado
 * @param falhas             atletas cujo encerramento falhou (lote continua)
 */
public record EncerramentoLoteResultado(
        int atletasProcessados,
        int atletasSemPlano,
        int planosConcluidos,
        int treinosPerdidosTotal,
        List<EncerramentoSemanaResultado> resultados,
        List<FalhaAtleta> falhas
) {
}
