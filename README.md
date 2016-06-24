[![Build Status](https://travis-ci.org/threatgrid/ctia.svg?branch=master)](https://travis-ci.org/threatgrid/ctia)
[![Stories in Ready](https://badge.waffle.io/threatgrid/ctia.png?label=ready&title=Ready)](https://waffle.io/threatgrid/ctia)
# Cisco Threat Intel API

A Pragmattic, Operationalized Threat Intel Service and Data Model

For full documentation see [doc/index.md](doc/index.md)

We also think the [Use Cases](doc/use_cases.md) document is a good
starting point.

Interactive, browser based documentation for the API is available once
you have it running, at:

    http://localhost:3000/index.html

## Goals

 * Sharing actionable threat intel
 * Simple, pragmatic data model
 * Ease of integration and exploration
 * Extremely fast Verdict lookups
 * Hypertextual integration with other services

This is not a full STIX/TAXII service.  Its intent is to help
Analysts know what is important, and for detection and prevention
tools to know what to look for.

In addition to the RESTful HTTP API, we also want to support a ZeroMQ
based, highly performant binary protocol for Verdict lookups.

## Usage

### Run the application locally

`lein run`

If you like, you can cider-connect to the NREPL listener that it
starts by default.  Be sure NOT to run the service this way in
production, unless you ensure access to the NREPL port is restricted.

This will start up with non-persistent in-memory storage only.

**Remark**:
if you use java 1.7, you might need to add `-XX:MaxPermSize=256m`
to java options.

### Packaging and running as standalone jar

This is the proper way to run this in production.

```
lein do clean, uberjar
java -jar target/server.jar
```

## Development

We provide a default `docker-compose-dev.yml` which will bring up the
dependencies you need in containers.

The easiest way to get running it to install
[Docker Toolbox](https://www.docker.com/products/docker-toolbox) which
includes all the dependencies you need to run Docker containers.

On Mac OS X, start a terminal using "Docker QuickStart Terminal" application.

You can then bring up a development environemnt:
```
cd containers/dev
docker-compose -f docker-compose.yml build
docker-compose -f docker-compose.yml up
```

You will then need to tell your CTIA where to find it's dependencies.
The services will be listening on your docker-machine's IP, which you
can get with the command, `docker-machine ip`, and then you define
your own `resources/ctia.properties` file with the following values:

```
ctia.store.es.default.host=192.168.99.100
ctia.store.es.default.port=9200
ctia.hook.es.host=192.168.99.100
ctia.hook.es.port=9200
ctia.hook.redis.uri=http://192.168.99.100:6379
```

Or you could initialize your properties with:

```
lein init-properties
```

It can be very useful to use _Kitematic_ to monitor and interact with
your containers.  You can also use _VirtualBox_ to modify the
resources available to the VM that is running all of your containers.

If you ever need to reset your entire dev environemnt, you can run
`docker-compose` to rebuild all the containers from scratch:

```
cd containers/dev
docker-compose -f docker-compose.yml up --force-recreate
```


## License

Copyright © 2015-2016 Cisco Systems

Eclipse Public License v1.0


## Implementation Notes

The intent is for this service to be embedded in other JVM
applications.  It currently provides no authentication, or access
control.

We would like to generate client libaries from the swagger, and
provide officially supported versions of them for users.  To do this
we need to ensure we use the most appropriate types, and swagger
metadata in our API definition (see handler.clj).

## Data Model

The data model of CTIA is closely based on
[STIX](http://stixproject.github.io/data-model/), with a few
simplifications.  See [data structures](doc/data_structures.md) for details.
