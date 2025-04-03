# Keres - scenario builder
Scenario builder is a module, designed to simplify load scenarios creation.
Given a wide variety of possible load profiles, quantities and lengths, using class-based scenarios might lead to cluttering the namespace with excessive amount of said classes.
Configuring it via regular config flags also incorporates confusion, since some parameters might not be applicable in some cases.

To go around that, we're incorporating a scriptable scenario builder.
It uses macros-like pseudo-language to provide instructions to TaskExecutor, while still keeping it understandable and human readable.

## Basic rules
### Syntax
Scenario builder macro-language follows C-like syntax for clarity:
- Function names start with the capital letter and use CamelCase
```java
CreateUsers(amount:5);
```
- Each command ends with "**;**" separator. Everything beyond that is considered a next command
- If function has a parameter(s) - each parameter is named, following with "**:**" and a value after it. Parameter names start with lower-case symbol and use camelCase
```java
Delay(seconds:5);
```
- If function implies nesting (some actions done) - it should have a curly-braced section. Contents of the curly-braced section follow the same standard rules. In case of these methods, curly braces end will be considered a separator, so semicolon should not be placed
```java
Repeat(times:3) {
    CreateUsers(amount:5);
    Delay(seconds:5);
}
```
- Code comments can be placed in scenario using **## ##** blocks. These will be trimmed and ommited by translator
```java
## Comment block ##
```
- Translator converts everything to lower-case before processing, so commands are not case sensetive.
- Translator also trims all spaces, tabs and newlines, so code formatting will not affect functionality.

### Example scenario:
```java
## Warm-up cycle ##
Repeat(times:3) {
    CreateUsers(amount:5);
    Delay(seconds:5);
}
WaitForAllClientsToFinish();

## Main cycle - uses closed profile ##
Repeat(times:5) {
    CreateLoopedUsers(amount:20);
    Delay(minutes:1);
}
Delay(minutes:20);
RemoveLoopedUsersOverTime(amount:100,minutes:5);
```

## Commands list
### Users handling
#### Regular users
Regular users follow simple 1-task logic - they execute task once, then cease.
##### CreateUsers
Creates given number of regular users instantly.

Expected parameters:
- **amount**(*numeric*) - number of users to create

Example:
```java
CreateUsers(amount:20);
```

##### CreateUsersOverTime
Creates given number of users over specified period of time. 

Expected parameters:
- **amount**(*numeric*) - number of users to create
- Any of the following. Can have multiple parameters specified at once:
    - **seconds**(*numeric*)
    - **minutes**(*numeric*)
    - **hours**(*numeric*)
    - All values above are summed up and used as final time period

Example:
```java
CreateUsersOverTime(amount:500,hours:1,minutes:30,seconds:30);
```

#### Cycled users
Cycled users run for given amount of cycles (tasks) before finishing. They can be removed from the pool prematurely, if need be.
##### CreateCycledUsers
Creates a number of cycled users, which will execute given amount of cycles.

Expected parameters:
- **amount**(*number*) - number of users to create
- **cyclesToRun**(*number*) - number of cycles each user should execute

Example:
```java
CreateCycledUsers(amount:20,cyclesToRun:50);
```

##### CreateCycledUsersOverTime
Creates given number of cycled users over specified period of time. 

Expected parameters:
- **amount**(*numeric*) - number of users to create
- **cyclesToRun**(*number*) - number of cycles each user should execute
- Any of the following. Can have multiple parameters specified at once:
    - **seconds**(*numeric*)
    - **minutes**(*numeric*)
    - **hours**(*numeric*)
    - All values above are summed up and used as final time period

Example:
```java
CreateCycledUsersOverTime(amount:500,cyclesToRun:50,hours:1,minutes:30,seconds:30);
```

##### RemoveCycledUsers
Removes a number of cycled users from active users pool. If actual number of users is less than the one specified in the call - all remaining users will be removed.

Expected parameters:
- **amount**(*number*) - number of users to create

Example:
```java
RemoveCycledUsers(amount:20);
```

##### RemoveCycledUsersOverTime
Removes given number of cycled users over specified period of time. 

Expected parameters:
- **amount**(*numeric*) - number of users to create
- Any of the following. Can have multiple parameters specified at once:
    - **seconds**(*numeric*)
    - **minutes**(*numeric*)
    - **hours**(*numeric*)
    - All values above are summed up and used as final time period

Example:
```java
RemoveCycledUsersOverTime(amount:500,hours:1,minutes:30,seconds:30);
```

#### Looped users
Looped users run indefinetely - in infinite loop. Their primary goal is to keep the constant load in closed load profiles. They can only be stopped through stop command or interrupt.
##### CreateLoopedUsers
Creates a number of looped users.

Expected parameters:
- **amount**(*number*) - number of users to create

Example:
```java
CreateLoopedUsers(amount:20);
```

##### CreateLoopedUsersOverTime
Creates given number of looped users over specified period of time. 

Expected parameters:
- **amount**(*numeric*) - number of users to create
- Any of the following. Can have multiple parameters specified at once:
    - **seconds**(*numeric*)
    - **minutes**(*numeric*)
    - **hours**(*numeric*)
    - All values above are summed up and used as final time period

Example:
```java
CreateLoopedUsersOverTime(amount:500,hours:1,minutes:30,seconds:30);
```

##### RemoveLoopedUsers
Removes a number of looped users from active users pool. If actual number of users is less than the one specified in the call - all remaining users will be removed.

Expected parameters:
- **amount**(*number*) - number of users to create

Example:
```java
RemoveLoopedUsers(amount:20);
```

##### RemoveLoopedUsersOverTime
Removes given number of looped users over specified period of time. 

Expected parameters:
- **amount**(*numeric*) - number of users to create
- Any of the following. Can have multiple parameters specified at once:
    - **seconds**(*numeric*)
    - **minutes**(*numeric*)
    - **hours**(*numeric*)
    - All values above are summed up and used as final time period

Example:
```java
RemoveLoopedUsersOverTime(amount:500,hours:1,minutes:30,seconds:30);
```

### Flow handling
There are several commands to control the flow of the scenario itself
##### Delay
Halts the execution for given amount of time.

Expected parameters:
- Any of the following. Can have multiple parameters specified at once:
    - **seconds**(*numeric*)
    - **minutes**(*numeric*)
    - **hours**(*numeric*)
    - All values above are summed up and used as final time period

Example:
```java
Delay(hours:1,minutes:30,seconds:30);
```

##### Repeat
Creates a loop, that repeats given procedures given amount of times.
Commands for execution should be placed within curly braces. 
**NOTE:** **repeat** command cannot be nested in any other curly-braced functions

Expected parameters:
- **times**(*numeric*) - amount of iterations to perform

Example:
```java
Repeat(times:5) {
    CreateLoopedUsers(amount:20);
    Delay(minutes:1);
}
```

##### WaitForAllClientsToFinish
Blocks the flow and waits for all currently active clients to finish execution.
**NOTE:** The wait is uncoditional, so it will wait for ANY type of client. Make sure to not create a deadlock with this waiter while there are looped clients running.

Expected parameters: **None**

Example:
```java
WaitForAllClientsToFinish();
```