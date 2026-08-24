package br.com.menthoros.backend.services;

import br.com.menthoros.backend.services.helper.IntervalsIcuActivityPersister;
import br.com.menthoros.backend.services.helper.TreinoDedupHelper;
import br.com.menthoros.backend.services.helper.TssCalculatorService;
import br.com.menthoros.backend.services.impl.IngestaoTreinoRealizadoServiceImpl;
import br.com.menthoros.backend.services.impl.TsbServiceImpl;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.time.LocalDate;
import java.util.UUID;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * CA11 (`ingestao-treino-realizado`, tasks 8.1/8.3) — fecha o seam: {@code TsbService.
 * atualizarTsbDia}/{@code recalcularDesde}, {@code TssCalculatorService.calcularTss} e
 * {@code TreinoDedupHelper.saveIdempotent} só podem ser chamados de dentro do seam único de
 * ingestão, com duas exceções documentadas em {@code design.md}:
 *
 * <ul>
 *   <li>{@code TsbServiceImpl} — self-calls legítimos (a própria implementação, incluindo o
 *       backfill {@code recalcularHistoricoCompleto} e o fallback D3 de {@code calcularTss});</li>
 *   <li>{@code IntervalsIcuActivityPersister} — conflito real de contrato com o seam
 *       (ordem evento/reconciliação, pre-mortem #10), não migra por decisão registrada em
 *       "Achado de implementação (Bloco 1, Seção 5)".</li>
 * </ul>
 *
 * <p>Um novo chamador externo a essas três classes é exatamente o regresso que este teste existe
 * para pegar — a orquestração (dedup, tssCalculado, evento, carga) voltaria a divergir entre
 * caminhos, o defeito original que a change corrige.
 */
@AnalyzeClasses(packages = "br.com.menthoros.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class IngestaoTreinoRealizadoSeamArchTest {

    private static final DescribedPredicate<JavaClass> CHAMADORES_PERMITIDOS =
            equivalentTo(IngestaoTreinoRealizadoServiceImpl.class)
                    .or(equivalentTo(TsbServiceImpl.class))
                    .or(equivalentTo(IntervalsIcuActivityPersister.class))
                    .as("fora do seam de ingestão (com as duas exceções documentadas em design.md)");

    @ArchTest
    static final ArchRule apenasOSeamChamaAtualizarTsbDia =
            noClasses().that(DescribedPredicate.not(CHAMADORES_PERMITIDOS))
                    .should().callMethod(TsbService.class, "atualizarTsbDia", UUID.class, LocalDate.class)
                    .because("TsbService.atualizarTsbDia só pode ser chamado do seam de ingestão (D9/CA11) — "
                            + "ver design.md para as exceções documentadas (IntervalsIcuActivityPersister)");

    @ArchTest
    static final ArchRule apenasOSeamChamaRecalcularDesde =
            noClasses().that(DescribedPredicate.not(CHAMADORES_PERMITIDOS))
                    .should().callMethod(TsbService.class, "recalcularDesde", UUID.class, LocalDate.class)
                    .because("TsbService.recalcularDesde só pode ser chamado do seam de ingestão (D9/CA11)");

    @ArchTest
    static final ArchRule apenasOSeamChamaCalcularTss =
            noClasses().that(DescribedPredicate.not(CHAMADORES_PERMITIDOS))
                    .should().callMethod(TssCalculatorService.class, "calcularTss",
                            br.com.menthoros.backend.entity.TreinoRealizado.class)
                    .because("TssCalculatorService.calcularTss só pode ser chamado do seam de ingestão (D9/CA11) — "
                            + "ver design.md para as exceções documentadas (IntervalsIcuActivityPersister)");

    /**
     * {@code TreinoDedupHelper} não tem {@code TsbServiceImpl} como exceção — só o seam e o
     * caller documentado o chamam; a classe não pode ficar package-private (task 8.3) porque
     * {@code services}/{@code services.impl}/{@code services.helper} são pacotes distintos e
     * vários tipos cruzam essa fronteira — esta regra é o substituto arquitetural do "sem public".
     */
    private static final DescribedPredicate<JavaClass> CHAMADORES_PERMITIDOS_DEDUP =
            equivalentTo(IngestaoTreinoRealizadoServiceImpl.class)
                    .or(equivalentTo(IntervalsIcuActivityPersister.class))
                    .as("fora do seam de ingestão (com a exceção documentada em design.md)");

    @ArchTest
    static final ArchRule apenasOSeamChamaSaveIdempotent =
            noClasses().that(DescribedPredicate.not(CHAMADORES_PERMITIDOS_DEDUP))
                    .should().callMethod(TreinoDedupHelper.class, "saveIdempotent",
                            br.com.menthoros.backend.entity.TreinoRealizado.class, String.class, UUID.class)
                    .because("TreinoDedupHelper.saveIdempotent só pode ser chamado do seam de ingestão (D9/CA11) — "
                            + "ver design.md para a exceção documentada (IntervalsIcuActivityPersister)");
}
