package io.github.vizanarkonin.keres.fixtures;

import io.github.vizanarkonin.keres.core.config.KeresStaticConfigProvider;
import io.github.vizanarkonin.keres.core.grpc.ParamType;

public class SmokeConfigProvider extends KeresStaticConfigProvider {
    static String scenarioName = "io.github.vizanarkonin.keres.fixtures.SmokeScenario";
    static String userDefName = "io.github.vizanarkonin.keres.fixtures.SmokeUser";

    static {
        registerConfig("USER_DEF_NAME", ParamType.USER_DEFINITION_NAME, userDefName);
        registerConfig("SCENARIO_NAME", ParamType.SCENARIO_NAME, scenarioName);
    }

    public static void setUserDef(String name) {
        userDefName = name;
        registerConfig("USER_DEF_NAME", ParamType.USER_DEFINITION_NAME, name);
    }

    public static void setScenario(String name) {
        scenarioName = name;
        registerConfig("SCENARIO_NAME", ParamType.SCENARIO_NAME, name);
    }

    public static String getUserDefinitionName() {
        return userDefName;
    }

    public static String getScenarioName() {
        return scenarioName;
    }
}
