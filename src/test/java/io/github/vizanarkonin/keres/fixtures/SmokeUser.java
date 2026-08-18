package io.github.vizanarkonin.keres.fixtures;

import java.time.Duration;

import io.github.vizanarkonin.keres.core.clients.http.KeresHttpClient;
import io.github.vizanarkonin.keres.core.clients.http.builders.KeresHttpRequest;
import io.github.vizanarkonin.keres.core.interfaces.KeresTask;
import io.github.vizanarkonin.keres.core.interfaces.KeresUserDefinition;
import io.github.vizanarkonin.keres.core.interfaces.KeresUserDefinitionMetaData;

@KeresUserDefinitionMetaData(
    userDefId = "SmokeUser",
    description = "Smoke test user definition"
)
public class SmokeUser extends KeresUserDefinition {
    private static String testHost = "localhost:12345";
    private KeresHttpClient client = new KeresHttpClient();
    private KeresHttpRequest request = KeresHttpRequest.get("SmokeLoadPage", "http://" + testHost + "/api/health");


    public static void setTestHost(String host) {
        testHost = host;
    }

    @KeresTask(weight = 1)
    public void loadPage() {
        client
            .execute(request)
            .waitFor(Duration.ofSeconds(1));
    }
}
