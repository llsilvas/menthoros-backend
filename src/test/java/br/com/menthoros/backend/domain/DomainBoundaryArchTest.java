package br.com.menthoros.backend.domain;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Fiscaliza a fronteira de {@code br.com.menthoros.backend.domain.planner..} e
 * {@code br.com.menthoros.backend.domain.compliance..}: nucleo deterministico puro do
 * PlannerEngine, sem dependencia de persistencia, web ou IA. Ver deterministic-planner-engine,
 * design.md Decisao 3.
 *
 * <p>Escopo deliberadamente restrito aos dois pacotes que esta change introduz — nao a
 * {@code domain..} inteiro, pois {@code domain.audit.AuditableEntity} (pre-existente, fora
 * de escopo) e um {@code @MappedSuperclass} JPA legitimo, nao nucleo deterministico.
 */
@AnalyzeClasses(packages = "br.com.menthoros.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainBoundaryArchTest {

    private static final String PLANNER_DOMAIN = "br.com.menthoros.backend.domain.planner..";
    private static final String COMPLIANCE_DOMAIN = "br.com.menthoros.backend.domain.compliance..";

    @ArchTest
    static final ArchRule plannerDomainDoesNotDependOnEntities =
            noClasses().that().resideInAPackage(PLANNER_DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage("br.com.menthoros.backend.entity..")
                    .because("domain/planner e nucleo puro; entidades JPA sao mapeadas para records na camada de service");

    @ArchTest
    static final ArchRule complianceDomainDoesNotDependOnEntities =
            noClasses().that().resideInAPackage(COMPLIANCE_DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage("br.com.menthoros.backend.entity..")
                    .because("domain/compliance e nucleo puro; entidades JPA sao mapeadas para records na camada de service");

    @ArchTest
    static final ArchRule plannerDomainDoesNotDependOnRepositories =
            noClasses().that().resideInAPackage(PLANNER_DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage("br.com.menthoros.backend.repository..")
                    .because("domain/planner nao acessa persistencia diretamente");

    @ArchTest
    static final ArchRule complianceDomainDoesNotDependOnRepositories =
            noClasses().that().resideInAPackage(COMPLIANCE_DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage("br.com.menthoros.backend.repository..")
                    .because("domain/compliance nao acessa persistencia diretamente");

    @ArchTest
    static final ArchRule plannerDomainDoesNotDependOnSpringWeb =
            noClasses().that().resideInAPackage(PLANNER_DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.web..")
                    .because("domain/planner e independente de transporte HTTP");

    @ArchTest
    static final ArchRule complianceDomainDoesNotDependOnSpringWeb =
            noClasses().that().resideInAPackage(COMPLIANCE_DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.web..")
                    .because("domain/compliance e independente de transporte HTTP");

    @ArchTest
    static final ArchRule plannerDomainDoesNotDependOnSpringAi =
            noClasses().that().resideInAPackage(PLANNER_DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.ai..")
                    .because("domain/planner nao chama LLM");

    @ArchTest
    static final ArchRule complianceDomainDoesNotDependOnSpringAi =
            noClasses().that().resideInAPackage(COMPLIANCE_DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.ai..")
                    .because("domain/compliance nao chama LLM");

    @ArchTest
    static final ArchRule plannerDomainDoesNotDependOnJpaAnnotations =
            noClasses().that().resideInAPackage(PLANNER_DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                    .because("tipos de dominio sao records, nao entidades JPA");

    @ArchTest
    static final ArchRule complianceDomainDoesNotDependOnJpaAnnotations =
            noClasses().that().resideInAPackage(COMPLIANCE_DOMAIN)
                    .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                    .because("tipos de dominio sao records, nao entidades JPA");
}
