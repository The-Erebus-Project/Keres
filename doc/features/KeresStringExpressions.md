# Keres - String expressions
In real world, a huge number of requests are dynamic - their payload and parameters may vary and depend on the current state of the system/session.
To make sure a user can "inject" parametrized values - Keres supports string expressions mechanic.
In short - it allows the client to parse the address/payload/headers/etc, locate specific expressions and replace them with real values - provided they have said values stored in client's session storage.

## How it works
### Step 1 - storing value
Each client, inherited from **KeresClientBase** type, have it's own **SessionData** storage - pretty much a key-value storage for session-specific parameters.
There are several ways to put a value in it:
- Use **KeresClientBase**'s **setSessionValue(key, value)** method - this will put given value into the storage manually.
- Feed a set of values using [Feeders](./Feeders.md)

### Step 2 - injecting value
Now we need to inject that value into our target string. To do that - we use the string expression.
In essence - it's a parameter name, surrounded by double curly braces:
```
{{someParameterName}}
```
**KeresClientBase** type provides **processStringExpression(target)** method, which accepts a raw string with string expression in it. Once accepted - it will search for all parameters in double-curly braces and replace them with their respective values from client's session storage. If no values are found in session storage - rxpressions are left as it is, hence simplifying further debugging.

**KeresHttpClient** implementation and it's builders use this method by default for following values:
- URL
- Headers
- String body
- Multipart string body values
- URLEncodedForm body values

For cases when user needs to submit a string body without processing string expression (for instance - in case of an enormously large string value) - you can use **rawStringBody** method. It does the same as **stringBody** - apart from processing string expressions.