package io.github.vizanarkonin.keres;

import io.github.vizanarkonin.keres.fixtures.SmokeConfigProvider;
import io.github.vizanarkonin.keres.fixtures.SmokeUser;
import io.github.vizanarkonin.keres.fixtures.SmokeUser2;
import io.github.vizanarkonin.keres.testutil.MockHttpTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SmokeTest {
    private static MockHttpTarget mockServer;
    private static File KERES_RESULTS_FOLDER = new File("KeresResults");
    private static File KERES_RESULTS_NO_BODY_FOLDER = new File("KeresResultsNoBody");

    @BeforeEach
    void initMockServer() throws Exception {
        if (mockServer == null) {
            mockServer = new MockHttpTarget();
            mockServer.configure(
                MockHttpTarget.EndpointConfig.ok("/api/health", "ok"),
                MockHttpTarget.EndpointConfig.noContent("/api/no-body"),
                MockHttpTarget.EndpointConfig.notModified("/api/not-modified"),
                MockHttpTarget.EndpointConfig.slow("/api/slow", 50)
            );
            Thread.sleep(100);
        }
    }

    @Test
    void smokeScenarioRunsEndToEnd() throws Exception {
        mockServer.requestCounter().set(0);

        SmokeUser.setTestHost(mockServer.getHost() + ":" + mockServer.getPort());
        KeresController.setResultsFolderRoot("KeresResults");

        SmokeConfigProvider.setUserDef("io.github.vizanarkonin.keres.fixtures.SmokeUser");
        SmokeConfigProvider.setScenario("io.github.vizanarkonin.keres.fixtures.SmokeScenario");

        KeresController.injectConfigProvider(SmokeConfigProvider.class);
        KeresController.runScenario();
        KeresController.waitForScenarioToFinish();

        assertEquals(5, mockServer.requestCounter().get(), "Mock server should have received exactly 5 requests");

        // Verify results.js was written.
        Path resultsDir = Paths.get("KeresResults");
        assertTrue(Files.exists(resultsDir), "Results directory should exist");

        final List<Path> resultsFile = new ArrayList<>();
        Files.walk(resultsDir)
            .filter(p -> p.toString().endsWith("results.js"))
            .findFirst()
            .ifPresent(resultsFile::add);

        assertFalse(resultsFile.isEmpty(), "results.js should have been generated");
        assertTrue(Files.size(resultsFile.get(0)) > 0, "results.js should not be empty");
    }

    @Test
    void noContentResponseDoesNotNPE() throws Exception {
        mockServer.requestCounter().set(0);

        SmokeUser2.setTestHost(mockServer.getHost() + ":" + mockServer.getPort());
        KeresController.setResultsFolderRoot("KeresResultsNoBody");

        SmokeConfigProvider.setUserDef("io.github.vizanarkonin.keres.fixtures.SmokeUser2");
        SmokeConfigProvider.setScenario("io.github.vizanarkonin.keres.fixtures.SmokeScenario");

        KeresController.injectConfigProvider(SmokeConfigProvider.class);
        KeresController.runScenario();
        KeresController.waitForScenarioToFinish();
        assertEquals(10, mockServer.requestCounter().get(), "Should have received 10 requests");
    }
    

   @Test
   void loopedUsersRemoval() {
        mockServer.requestCounter().set(0);

        SmokeUser.setTestHost(mockServer.getHost() + ":" + mockServer.getPort());
        KeresController.setResultsFolderRoot("KeresResults");

        SmokeConfigProvider.setUserDef("io.github.vizanarkonin.keres.fixtures.SmokeUser");
        SmokeConfigProvider.setScenario("io.github.vizanarkonin.keres.fixtures.LoopedSmokeScenario");

        KeresController.injectConfigProvider(SmokeConfigProvider.class);
        KeresController.runScenario();
        KeresController.waitForScenarioToFinish();

        // Verify results.js was written.
        Path resultsDir = Paths.get("KeresResults");
        assertTrue(Files.exists(resultsDir), "Results directory should exist");
   }

    @AfterEach
    private void removeResultsFolders() {
        List.of(KERES_RESULTS_FOLDER, KERES_RESULTS_NO_BODY_FOLDER).forEach(folder -> deleteFolder(folder));
    }

    private static void deleteFolder(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteFolder(child);
            }
        }
        dir.delete();
    }
}
