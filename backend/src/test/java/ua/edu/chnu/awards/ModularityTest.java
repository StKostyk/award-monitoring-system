package ua.edu.chnu.awards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;
import org.springframework.modulith.docs.Documenter;

import com.tngtech.archunit.core.importer.ImportOption;

class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(AwardMonitoringSystemApplication.class);

    @Test
    void ac21_theApplicationConsistsOfTheAgreedModulesAndVerifies() {
        assertThat(MODULES.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
            .containsExactlyInAnyOrder("audit", "auth", "authz", "award", "common", "config", "delegation",
                "document", "gdpr", "metrics", "notification", "organization", "user");

        MODULES.verify();
    }

    @Test
    void ac22_commonDependsOnNoModuleAndNoModuleDependsOnConfig() {
        assertThat(dependencies("common")).isEmpty();
        assertThat(MODULES.stream().filter(module -> dependencies(module.getIdentifier().toString())
            .contains("config"))).isEmpty();
    }

    @Test
    void ac24_aModuleUsingAnotherModulesRepositoryIsReported() {
        ImportOption everything = location -> true;
        ApplicationModules fixture = ApplicationModules.of("ua.edu.chnu.awards.modulith.fixture", everything);

        Violations violations = fixture.detectViolations();

        assertThat(violations.hasViolations()).isTrue();
        assertThat(violations.getMessage()).contains("BetaService", "AlphaRepository");
        assertThatThrownBy(fixture::verify).isInstanceOf(Violations.class);
    }

    @Test
    void ac26_documenterWritesTheComponentDiagramAndCanvases() {
        new Documenter(MODULES)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases();

        assertThat(Path.of("target", "spring-modulith-docs", "components.puml")).isNotEmptyFile();
        assertThat(Path.of("target", "spring-modulith-docs", "module-notification.adoc")).isNotEmptyFile();
    }

    private static List<String> dependencies(String module) {
        return MODULES.getModuleByName(module).orElseThrow().getDirectDependencies(MODULES).uniqueModules()
            .map(dependency -> dependency.getIdentifier().toString()).toList();
    }
}
