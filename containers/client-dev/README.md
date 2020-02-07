# CTIA Client Dev Instance via Docker-Compose

This is a containerized and integrated deployment of the Cisco Threat Intelligence API, for use in client development. It contains everything needed to stand up a CTIA instance, and develop client code that writes into it.

This client-dev container integrates:

- CTIA service on port 3000
- ElasticSearch service on port 9200
- Redis service on port 6379

And nothing else!

## Building and Starting the Containers

### Linux Host Configuration
If your docker host OS is Linux,
Elasticsearch 5x bootstrap checks the maximum map count configuration,
tune it accordingly with `sudo sysctl -w vm.max_map_count=262144`


To run the latest containers from Docker HUB simply invoke docker-compose with this configuration:

``` bash
~/path-to/ctia/containers/client-dev$ docker-compose up
```

To build and run the containers with docker-compose, install docker and docker compose. Then, `cd` to your the `ctia` directory (at the top of this repo) and run the following commands to build the CTIA jar, the CTIA docker container, and then to start the services via `docker-compose`:


```
~/path-to/ctia$ lein clean

~/path-to/ctia$ lein uberjar
Compiling 1 source files to ~/path-to/ctia/target/classes
Compiling ctia.main
2019-01-18 19:25:25,858 INFO (main) [org.eclipse.jetty.util.log] - Logging initialized @32445ms
Compiling ctia.main
Created ~/path-to/ctia/target/ctia-1.1.0.jar
Created ~/path-to/ctia/target/ctia.jar

~/path-to/ctia$ cd containers/client-dev/

~/path-to/ctia/containers/client-dev$ docker-compose build
redis-client-dev uses an image, skipping
elasticsearch-client-dev uses an image, skipping
Building ctia
Step 1/6 : FROM clojure:alpine
...
Successfully tagged ctia:latest

~/path-to/ctia/containers/client-dev$ docker-compose up
```

## URI Endpoints

- http://localhost:3000 - CTIA
- http://localhost:9200 - ES
