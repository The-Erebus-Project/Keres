package io.github.vizanarkonin.keres.fixtures;

import io.github.vizanarkonin.keres.core.clients.http.KeresHttpClient;
import io.github.vizanarkonin.keres.core.clients.http.builders.KeresHttpRequest;
import io.github.vizanarkonin.keres.core.interfaces.KeresTask;
import io.github.vizanarkonin.keres.core.interfaces.KeresUserDefinition;
import io.github.vizanarkonin.keres.core.interfaces.KeresUserDefinitionMetaData;

@KeresUserDefinitionMetaData(
    userDefId = "SmokeUser2",
    description = "Smoke test user definition for no-body endpoints"
)
public class SmokeUser2 extends KeresUserDefinition {
    private static String testHost = "localhost:12345";

    private KeresHttpClient client = new KeresHttpClient();
    private KeresHttpRequest request1 = KeresHttpRequest.get("NoContent", "http://" + testHost + "/api/no-body");
    private KeresHttpRequest request2 = KeresHttpRequest.get("NotModified", "http://" + testHost + "/api/not-modified");

    public static void setTestHost(String host) {
        testHost = host;
    }

    @KeresTask(weight = 1)
    public void hitNoBodyEndpoints() {
        client.execute(request1);
        client.execute(request2);
    }
}
