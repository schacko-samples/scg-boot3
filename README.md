## Run locally
### Download dependencies and build project
```shell
mvn clean verify -DskipTests=true
```
### Run the gateway and downstream service in the background
```shell
mvn -pl remote-ms/ &
mvn test -pl gateway/
```
### Run manual test (requires Kafka)
```shell
$ curl 'http://localhost:8553/headers' --header 'x-b3-traceid: 1231231231231231' --header 'x-b3-spanid: 1231231231231231'
```
