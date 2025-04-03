package io.github.vizanarkonin.keres.core.config;

import java.util.HashMap;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.vizanarkonin.keres.core.grpc.ParamType;
import io.github.vizanarkonin.keres.core.grpc.CurrentNodeParameters;
import io.github.vizanarkonin.keres.core.grpc.NodeParameter;

/**
 * Base class for initializing and feeding the static project config to the Keres.
 * TODO: Right now it is working as a 1-layer solution - there's no nesting in configs. We might want to add wrapper type in case we'll need some
 */
public abstract class KeresStaticConfigProvider {
    protected static final Logger log                               = LogManager.getLogger("StaticConfigProvider");
    private final static HashMap<String, NodeParameter> valuesMap   = new HashMap<>();

    /**
     * Static getter for user definition class name.
     * NOTE: Since having at least 1 user definition is required to start a run in the first place - 
     * not registering a USER_DEFINITION_NAME value will result in an exception during the run start attempt.
     * @return - User definition class name
     */
    public static String getUserDefinitionName() {
        return valuesMap
            .values()
            .stream()
                .filter(param -> param.getType() == ParamType.USER_DEFINITION_NAME)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User definition name parameter was not found. Make sure you identify it as ArgType.USER_DEFINITION_NAME with String type value"))
                .getStr();
    }

    /**
     * Static getter for user definition class name.
     * NOTE: Even though this can be overriden by specifying a custom scenario value (ArgType.SCENARIO)
     * NOTE: Since having at least 1 user definition is required to start a run in the first place - 
     * not registering a USER_DEFINITION_NAME value will result in an exception during the run start attempt.
     * @return - User definition class name
     */
    public static String getScenarioName() {
        return valuesMap
            .values()
            .stream()
                .filter(param -> param.getType() == ParamType.SCENARIO_NAME)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Scenario name parameter was not found. Make sure you identify it as ArgType.SCENARIO_NAME with String type value"))
                .getStr();
    }

    /**
     * Static getter for custom scenario string.
     * NOTE: This parameter is optional - if it's not set or is empty - a ParamType.SCENARIO_NAME will be used for the run instead.
     * @return - Custom scenario string
     */
    public static String getScenario() {
        Optional<NodeParameter> argValue = valuesMap
            .values()
            .stream()
                .filter(param -> param.getType() == ParamType.SCENARIO)
                .findFirst();
        
        if (argValue.isPresent())
            return argValue.get().getStr();
        else {
            return "";
        }
    }

    /**
     * Populates CurrentNodeParameters builder instance with currently registered values.
     * @param target - Builder instance.
     */
    public static void populateNodeParameters(CurrentNodeParameters.Builder target) {
        valuesMap.values().forEach(param -> target.addParameters(param));
    }

    /**
     * Node parameters setter - consumes a JSONArray with the data received from the hub and sets them into a config storage
     * @param requestData - JSONArray with data, received from the hub.
     */
    public static void setNodeParameters(JSONArray requestData) {
        for (int index = 0; index < requestData.length(); index++) {
            JSONObject param = requestData.getJSONObject(index);
            String name = param.getString("name"); 

            if (!valuesMap.containsKey(name)) {
                log.error("Config entry with name " + name + " was not found. Ignoring");
                continue;
            }

            NodeParameter nodeParam = valuesMap.get(name);
            NodeParameter.Builder newParamBuilder = NodeParameter.newBuilder()
                .setName(nodeParam.getName())
                .setType(nodeParam.getType());

            switch (nodeParam.getType()) {
                case ParamType.SCENARIO:
                case ParamType.SCENARIO_NAME:
                case ParamType.USER_DEFINITION_NAME:
                case ParamType.STRING: {
                    try {
                        newParamBuilder
                            .setStr(param.getString("value"));
                    } catch (Exception e) {
                        log.error(e);
                        log.error(ExceptionUtils.getStackTrace(e));
                    }
                    break;
                }
                case ParamType.INT: {
                    try {
                        newParamBuilder
                            .setInt(param.getInt("value"));
                    } catch (Exception e) {
                        log.error(e);
                        log.error(ExceptionUtils.getStackTrace(e));
                    }
                    break;
                }
                case ParamType.BOOL: {
                    try {
                        newParamBuilder
                            .setBool(param.getBoolean("value"));
                    } catch (Exception e) {
                        log.error(e);
                        log.error(ExceptionUtils.getStackTrace(e));
                    }
                    break;
                }
                default: {
                    throw new RuntimeException("Unhandled ParamType - " + nodeParam.getType());
                }
            }

            valuesMap.put(name, newParamBuilder.build());
        }
    }

    /**
     * Registers a new config value in the storage.
     * Mandatory step to make that particular parameter available for editing from the hub application.
     * @param name  - Parameter name
     * @param type  - Parameter type
     * @param value - Parameter value
     */
    public static void registerConfig(String name, ParamType type, Object value) {
        NodeParameter.Builder paramBuilder = NodeParameter.newBuilder()
            .setName(name)
            .setType(type);
        
        switch (type) {
            case ParamType.STRING:
            case ParamType.SCENARIO:
            case ParamType.USER_DEFINITION_NAME:
            case ParamType.SCENARIO_NAME: {
                if (value instanceof String) {
                    paramBuilder.setStr((String) value);
                } else {
                    throw new RuntimeException("Value for parameter of type " + type + " is " + value.getClass() + ". It should be String");
                }
                break;
            }
            case ParamType.INT: {
                if (value instanceof Integer || value instanceof Long) {
                    paramBuilder.setInt((long) value);
                } else {
                    throw new RuntimeException("Value for parameter of type " + type + " is " + value.getClass() + ". It should be either Integer or Long");
                }
                break;
            }
            case ParamType.BOOL: {
                if (value instanceof Boolean) {
                    paramBuilder.setBool((boolean) value);
                } else {
                    throw new RuntimeException("Value for parameter of type " + type + " is " + value.getClass() + ". It should be Boolean");
                }
                break;
            }
            default:
                throw new RuntimeException("Unhandled ParamType " + type);
        }

        valuesMap.put(name, paramBuilder.build());
    }

    public static NodeParameter getConfig(String name) {
        return valuesMap.get(name);
    }

    public static String getString(String name) {
        NodeParameter arg = getConfig(name);
        if (arg.getType() == ParamType.STRING || arg.getType() == ParamType.SCENARIO_NAME || arg.getType() == ParamType.USER_DEFINITION_NAME || arg.getType() == ParamType.SCENARIO) {
            return arg.getStr();
        } else {
            throw new RuntimeException("Config parameter " + name + " is of type " + arg.getType() + ", but expected STRING");
        }
    }

    public static long getInt(String name) {
        NodeParameter arg = getConfig(name);
        if (arg.getType() == ParamType.INT) {
            return arg.getInt();
        } else {
            throw new RuntimeException("Config parameter " + name + " is of type " + arg.getType() + ", but expected INT");
        }
    }

    public static boolean getBool(String name) {
        NodeParameter arg = getConfig(name);
        if (arg.getType() == ParamType.BOOL) {
            return arg.getBool();
        } else {
            throw new RuntimeException("Config parameter " + name + " is of type " + arg.getType() + ", but expected BOOL");
        }
    }
}
