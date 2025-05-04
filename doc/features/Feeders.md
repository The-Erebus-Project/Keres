# Keres - Parameter value feeders
Sometimes we may need to provide a number of parameters to the client's session context. Adding them one by one may lead to bloated code and lack of dynamic.
To get around it - Keres provides the **Feeder** mechanic.
In essence - feeder allows user to create a data provider (file, DB connection, response, static data, etc), which will be able to inject sets of data directly into the client's session storage.

## Feeder base - how it works
Keres provides a base class, called **KeresFeeder**, which allows users to create their own feeder implementations.
Data is stored in 2 collections:
- Headers list - contains the list of parameter names to be injected
- Values list - contains a list of data arrays. Each array represents a single row of data, with elements indexes corresponding to the header index.

Let's take this dataset as an example:
| name | surname | lastLogin  | isBlocked |
| ---- | ------- | ---------  | --------- |
| John | Doe     | 10.04.2011 | 0         |
| Jane | Doe     | 03.11.2012 | 1         |

Here, the header row (name, surname, lastLogin and isBlocked) will constitute the headers, while 2 rows of values will constitute 2 arrays of data in the values list.
Upon injection in the client, one row of the data will be taken from the set, and each value will be put into client's data storage with respective name - according to header's value.

Feeders have 2 operational modes:
- Circular - feeder will be giving datasets in numerical order, and upon reaching the end of the values list - it will start from the beginning.
- Random - feeder will pick a random data set on each hit.

## Existing implementations

### CSV File feeder
CSV File feeder is implemented in **KeresCsvFileFeeder** type.
Upon initialization, it accepts a string with .csv file location. It then opens it up and loads it into the memory.
First row of the file is considered a header row, while all following rows are considered values. So previously mentioned data set would look like this:
```csv
name,surname,lastLogin,isBlocked
John,Doe,10.04.2011,0
Jane,Doe,03.11.2012,1
```

### File contents feeder
File contents feeder is implemented in **KeresFileFeeder** type.
It is used to read the contents from provided files, and then feed them using one single parameter - which is defined upon feeder creation:
```java
private KeresFileFeeder(String paramName, File... files) {
    headers.add(paramName);
    
    for (File file : files) {
        loadFile(file);
    }
}

private KeresFileFeeder(String paramName, String... filesLocations) {
    headers.add(paramName);
    
    for (String fileLocation : filesLocations) {
        File file = new File(fileLocation);
        loadFile(file);
    }
}
```
User can provide a folder address - in this case, feeder will recursively traverse it's contents and load up all the file contents from it.

One of the uses for this feeder - serve as data provider for cases when we need to use multiple different payloads for one request.