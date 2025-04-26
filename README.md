# Keres - Performance Testing as Code

<p align="center">
    <img src="https://img.shields.io/badge/license-MIT-blue.svg"/>
    <img src="https://img.shields.io/badge/java-21%2B-orange"/>
<p>

<div align="center">
  <img src="src/main/resources/report-viewer/res/logo.png" alt="Keres logo" height="180"/>
</div>

## The Agent of Controlled Chaos
> "Load testing without Keres is like combat-testing armor with a toothpick."
> _- The Performance Warrior's Handbook_

**Keres** is a code-powered load generation and performance testing tool. It provides a set of tools for designing, composing and executing performance tests.
It can be used as a standalone entity, or it can be used as a **node**, connected to **Nyx** hub application, thus allowing users to generate scalable amounts of load on demand.

## Getting started
### Installation
First step is adding the core dependency in your project **.pom** file:
```xml
<dependency>
    <groupId>io.github.vizanarkonin</groupId>
    <artifactId>keres</artifactId>
    <version>1.1.2</version>
</dependency>
```

### Configuration class definition
The **Erebus** data protocol allows users to view and change runtime parameters and configurations - user definitions, scenarios, some project specific parameters and so on - on the fly, using **Nyx** hub UI controls.
In order to make it possible, we need to make sure the hub is aware these parameters exist in the first place.
Right now it is done by implementing a static config class that extends **KeresStaticConfigProvider** type. This type provides required API for user to register and handle said configurations. Note, however, that for the time being this config provider is flat - there are no config types nesting supported yet.
Here's an example of **Config** class implementation:
```java
package core;

import io.github.vizanarkonin.keres.core.config.KeresStaticConfigProvider;
import io.github.vizanarkonin.keres.core.grpc.ParamType;

public class Config extends KeresStaticConfigProvider {
    // Config labels
    private static final String SCENARIO_NAME           = "ScenarioName";
    private static final String SCENARIO                = "Scenario";
    private static final String USER_DEFINITION_NAME    = "UserDefName";
    private static final String BASE_URL                = "BaseURL";

    static {
        // Scenario name, scenario and user definition configs are mandatory
        registerConfig(
            SCENARIO_NAME, 
            ParamType.SCENARIO_NAME,
            "scenarios.Scenario");
        
        registerConfig(
            SCENARIO, 
            ParamType.SCENARIO, 
            "");

        registerConfig(
            USER_DEFINITION_NAME, 
            ParamType.USER_DEFINITION_NAME, 
            "userDefinitions.DefaultUser");

        // Further configs are optional and project-specific.
        registerConfig(
            BASE_URL, 
            ParamType.STRING, 
            "http://127.0.0.1");
    }

    // No getters needed for scenario/user def parameters - these default parameters have default getters in base class
    public static String getBaseURL()   { return getString(BASE_URL); }
}
```

### Creating project building blocks
Project structure is roughly split into 3 layers:
- Foundation layer - here we have base building blocks like **requests**, **actions** and respective **clients**. They define the individual actions a virtual user is going to perform.
- User definition layer - here we define the user behavior - what to do before/after it starts, and what to do during the execution itself.
- Scenario layer - here we define the flow of the test itself - how much users to create an any given point, how long to keep it running, etc.

#### Foundation layer
We will not focus on creating a custom protocol client implementations (this deserves a seperate section), switching focus to creating flow building blocks instead - **requests** and **actions**
This example will use existing HTTP client implementation.
##### HTTP Client - requests
Request definitions with all required parameters, checks and actions on completion can be defined using **KeresHttpRequest** class.
Instance of **KeresHttpRequest** is later used by client to build an HTTP request and perform beforementioned tasks on it - some clean-up, checks and assertions, saving response values, etc.
In it's current state, it works like this:
- Client builds and executes **request**, using all provided information (method, body, headers, etc)
- Client executes **post-request tasks** (if any provided) - it uses a lambda function, which provides user with **Response** instance.
- Client executes **checks** (if any provided) - it uses a lambda function, which provides user with **Response** and **SoftAssertions** instances.
- Client executes **save** tasks (if any provided) - it uses lambda function, which provides user with **Response** object and expects user to return a string. This string is then put into a client's session storage for further use.

As an example, let's take a look on a typical Auth request - we execute request itself, check if response code is correct (200), then extract and save access and refresh tokens. We put into an **AuthService** type, which is intended to house all auth service-related requests.
```java
package requests;

import core.Config;
import io.github.vizanarkonin.keres.core.clients.http.builders.KeresHttpRequest;

public class AuthService {
    public static KeresHttpRequest authorize(String email, String password) {
        return KeresHttpRequest.post(
            "Auth request",                         // Request name
            Config.getBaseURL() + "/api/v1/auth/")  // URL
                .stringBody("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .header("Content-Type", "application/json; charset=utf-8")
                // Once all parameters are set - we call build() method to return an instance of KeresHttpRequest.
                .build()
                // Here we validate that we've received a valid response
                .check((response, assertion) -> {
                    assertion
                        .assertThat(response.getResponseCode())
                        .isEqualTo(200)
                        .as("Auth response code");
                })
                // String value returned from this lambda will be saved in client's session storage with "accessToken" name
                .saveValue("accessToken", response -> { 
                    return JsonPath.parse(response.getResponseContent()).read("$.accessToken");
                })
                // String value returned from this lambda will be saved in client's session storage with "refreshToken" name
                .saveValue("refreshToken", response -> {
                    return JsonPath.parse(response.getResponseContent()).read("$.refreshToken");
                });
    }
}
```

##### HTTP Client - Actions
**Actions** are a sequence of requests/actions, that are required to achieve certain result.
For example, loading a home page requires a 3-step transaction:
- Initial request to get the page itself
- A number of parallel requests to fetch the resources
- If applicable - a number of AJAX requests to fetch the data

In some cases, we would like to know how long it took from start to finish, and to make sure we won't have to waste time calculating it by hand - we can use **Sequential** and **Parallel** actions.
We can initiate both from the HTTP client:
```java
httpClient
    .action("Authorize and get to home page", client -> {
        client
            .execute(AuthService.authorize("user", "password"))
            .execute(HomePage.getHomePage())
            // Actions can be nested in each other.
            .parallelAction("Load resources", 
                client.execute(HomePage.fetchUsersTableData()),
                client.execute(HomePage.fetchtasksTableData())
            );
    });
```
Or use pre-built action definition, which is later fed to the client:
```java
public static SequentialAction authorizeAndLoadHomePage(String username, String password) {
    return ActionController.sequentialAction(
        "Auth and load home page", client -> {
                client
                    .execute(AuthService.authorize("user", "password"))
                    .execute(HomePage.getHomePage())
                    // Actions can be nested in each other.
                    .parallelAction("Load resources", 
                        client.execute(HomePage.fetchUsersTableData()),
                        client.execute(HomePage.fetchtasksTableData())
                    );
            }
        );
}
```

Note that each action handler has 4 receivers (method overloads):
- Name and Runnable task
- Name and Consumer task, which provides the client instance to the lambda
- Name and an instance of KeresHttpRequest
- Pre-built Action instance

#### User definition layer
Once we've got foundation done, we can start using those bricks to assemble a user definition layer.
User definition layer provides the set of tasks each virtual a user is going to execute during it's lifetime.
For more control, user flow is divided on 7 steps:
- **setUp** method (Optional) - executed once when virtual user is initialized.
- **beforeTask** method (Optional) - pre-step, executed before every user task.
- **@BeforeTask** methods (Optional) - methods, marked with **@BeforeTask**. This annotation accepts 1 optional parameter - **taskNames**.
  - If **taskNames** is defined - this method will only be executed before said task methods.
  - If **taskNames** is not defined - this method will be executed before ANY task method.
- **task itself** - any method, marked with **@KeresTask** annotation. This annotation accepts 2 optional parameters:
  - **weight** - defines the "weight" of the task, i.e. how often it is going to get executed compared to other tasks in the class. Default value is 1.
    - If user definition task only contains 1 method with **@KeresTask** annotation - **weight** parameter is not used.
    - If user definition task contains multiple methods with this annotation - it will use weight-based random to pick a task to execute.
  - **delayAfterTaskInMs** - defines the delay after the task execution. Default value is 0.
- **@AfterTask** methods (Optional) - same as **@BeforeTask**, but executed AFTER the task. Follows the same rules as former annotation.
- **afterTask** method (Optional) - executed once task is completed. 
- **tearDown** method (Optional) - executed when user reached the end of it's lifespan. Can be used for clean-up and tear-down procedures.

To create a user definition, we need to create a new class, that will extend **KeresUserDefinition** abstract class. This class should also be marked with **@KeresUserDefinitionMetaData** annotation, in order to be indexed by the controller and being selectable from hub UI.
Each virtual client will have it's own instance of user definition class, so there's no need to worry about keeping stored attributes/variables (unless you intend to share them between runners, that is).
Here's an example of the implementation:
```java
package userDefinitions;

import io.github.vizanarkonin.keres.core.clients.http.KeresHttpClient;
import io.github.vizanarkonin.keres.core.interfaces.AfterTask;
import io.github.vizanarkonin.keres.core.interfaces.BeforeTask;
import io.github.vizanarkonin.keres.core.interfaces.KeresTask;
import io.github.vizanarkonin.keres.core.interfaces.KeresUserDefinition;
import io.github.vizanarkonin.keres.core.interfaces.KeresUserDefinitionMetaData;

import requests.AuthService;
import requests.DataService;
import requests.HomePage;

@KeresUserDefinitionMetaData(
    userDefId = "DU-1",
    description = "Default user behavior implementation" 
)
public class DefaultUser extends KeresUserDefinition {
    private KeresHttpClient httpClient = new KeresHttpClient();

    @Override
    public void setUp() {
        AuthService.authorize("username", "password");
    }

    // We only execute this method before openProfilePage task.
    @BeforeTask(taskNames = "openProfilePage")
    public void refreshDataStream() {
        httpClient.execute(DataService.refreshData());
    }

    // We want this task to execute 3 times as much as openProfilePage
    @KeresTask(weight = 6, delayAfterTaskInMs = 200)
    public void openIndexPage() {
        httpClient
            .action("Open home page", client -> {
                client
                    .execute(HomePage.getHomePage())
                    .parallelAction("Load resources", 
                        client.execute(HomePage.fetchUsersTableData()),
                        client.execute(HomePage.fetchtasksTableData())
                    );
            });
    }

    @KeresTask(weight = 2, delayAfterTaskInMs = 100)
    public void openProfilePage() {
        httpClient.execute(UserService.openProfilePage());
    }

    // We only execute this method after openProfilePage task.
    @AfterTask(taskNames = "openProfilePage")
    public void resetUserVisit() {
        httpClient.execute(DataService.resetVisit());
    }

    @Override
    public void tearDown() {
        httpClient.execute(AuthService.logOut());
    }
}
```

##### Scenario layer
Finally, we can create the scenario itself.
This will consume user definition we've created earlier and set the tone of the execution - amount of users created at any given moment, pauses between stages, etc.
In order to create a scenario you need to:
- Create a new type, that extends **KeresScenario** class.
- Mark that type with **@KeresScenarioMetaData** annotation - this serves as scenario descriptor, containing scenario ID and brief description, and also allows controller to index it and make it available for selection from Hub UI.

Here's an example of a scenario implementation
```java
package scenarios;

import java.time.Duration;

import io.github.vizanarkonin.Keres.core.interfaces.KeresScenario;
import io.github.vizanarkonin.Keres.core.interfaces.KeresScenarioMetaData;

@KeresScenarioMetaData(
    description = "ES-1", 
    scenarioId = "Example load scenario"
)
public class Scenario extends KeresScenario {

    @Override
    public void execute() {
        // Scenario executor instance comes courtesy of KeresScenario base class - there's no need to initialize it manually.
        scenarioExecutor
            // Create 5 Looped users instantly
            .createLoopedUsersAtOnce(5)
            // Wait for 5 seconds, let the users do their job.
            .waitFor(Duration.ofSeconds(5))
            // Create 5 looped users over 5 seconds - 1 user per second
            .createLoopedUsersOverPeriodOfTime(5, Duration.ofSeconds(5))
            // Wait for 5 seconds, let the users do their job.
            .waitFor(Duration.ofSeconds(5))
            // Removes 10 looped users over 10 seconds - 1 user per second.
            .removeLoopedUsersOverTime(10, Duration.ofSeconds(10))
            // In case tearDown takes time - we wait until all users have finished executing before concluding the test.
            .waitForAllRunnersToFinish();
    }
}
```

Note that there are 3 types of users that can be created by scenario executor:
- **Regular** user - a one-shot type of user. Initializes, runs single task and then finishes.
- **Cycled** user - Initializes, runs specified amount of tasks and then finishes.
- **Looped** user - Initializes, runs indefinetly, stops and finishes when commanded by scenario executor (or an emergency stop is called).

### Execution
**Keres** provides 2 execution options - standalone and node. 
Standalone option implies that the process we run is completely independent and works on it's own. It is useful for debugging and low-load runs.
Node option turns it into a remote-controlled load generator - it's configuration can be configured from Nyx hub page, and multiple nodes can be combined to generate load during a test run.

#### Executing created scenarios
Once everything is done - we can proceed to running the scenario.
Right now there are no dedicated plugins for maven/gradle/ant, so initialization is up to end user.
For this example we will use standard main-class approach:
```java
public static void main(String[] args) {
    //This option is useful for debugging. During a live run we would like to disable it to reduce performance impact from logging.
    KeresController.setPrintFailedRequests(false);
    // Here we set the results output folder name. The path here is relative to current work folder.
    KeresController.setResultsFolderRoot("results");
    // Here we inject the config class we created earlier.
    KeresController.injectConfigProvider(Config.class);

    // Finally, we run the scenario itself.
    KeresController.runScenario();
}
```

#### Starting project as Node
**Keres**-powered project can be connected to **Nyx** hub application, allowing users to scale the load horizontally.
In order to connect to a hub, you need to meet these conditions:
- A project must exist in the hub application
- A node must be registered inside the project
- A connection mode is known - could be plaintext, default (transport security) or tls

With all this - we can start the project as a node.
Right now there are no dedicated plugins for maven/gradle/ant, so initialization is up to end user.
For this example we will use standard main-class approach:
```java
public static void main(String[] args) {
    KeresController.setPrintFailedRequests(false);
    KeresController.setResultsFolderRoot("results");
    KeresController.injectConfigProvider(Config.class);

    KeresController.initNode(
        "localhost",                        // Host
        9090,                               // gRPC port. Default value - 9090
        1,                                  // Project ID
        "NODE-4",                           // Node ID
        KeresGrpcConnectionMode.PLAINTEXT   // Connection mode
    );
}
```
Once connected - the entry in nodes table will change it's status to "Idle". This means the node is connected and ready to work.
##### Strict vs Non-strict modes
Starting from version **1.1.0** of **Nyx**, a project now has a **Strict** flag available, which can be set/changed in project settings.
Essentially - this flag regulates the nodes admission policy:
- If the project is **Strict** - only nodes with registered ID's are able to connect to it. If a non-registered or empty node ID is provided - that node is not admissed.
- If the project is not set as Strict:
  - If node have provided a non-empty node ID - it is processed by "strict" rules (i.e. registration is validated).
  - If node ID was not specified (empty string) - Nyx creates a temporary node entity (it doesn't get persisted to DB and only exists as long as node is connected) and returns the generated node ID back to the client. Once the node is disconnected - that entity is discarded.

This addition allows user to create "dynamic" projects, where the amount of nodes is not pre-defined and can vary depending on the tasks or deployment types.