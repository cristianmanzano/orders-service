package com.cmanzano.orders.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Las reglas de la arquitectura hexagonal se verifican en cada build: si alguien importa
 * Spring en el dominio o un adaptador desde la aplicación, el pipeline falla.
 */
@AnalyzeClasses(packages = "com.cmanzano.orders", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule layersRespectTheDependencyRule = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule domainIsPureJava = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "reactor..", "jakarta..", "software.amazon..", "org.apache.kafka..",
                    "io.github.resilience4j..", "com.fasterxml..")
            .because("the domain model must be framework agnostic");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnFrameworks = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "software.amazon..", "org.apache.kafka..",
                    "io.github.resilience4j..")
            .because("use cases only depend on ports (Reactor is accepted as the async contract)");

    @ArchTest
    static final ArchRule adaptersDoNotDependOnEachOther = slices()
            .matching("com.cmanzano.orders.infrastructure.adapter.(*).(*)..")
            .should().notDependOnEachOther();
}
