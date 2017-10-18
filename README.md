[![Build Status](https://travis-ci.org/threatgrid/ctia.svg?branch=master)](https://travis-ci.org/threatgrid/ctia)
[![Stories in Ready](https://badge.waffle.io/threatgrid/ctia.png?label=ready&title=Ready)](https://waffle.io/threatgrid/ctia)
# Cisco Threat Intel API

A Pragmatic, Operationalized Threat Intel Service and Data Model

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

In addition to the RESTful HTTP API, it also has an Event Bus.

The data model is defined in the [CTIM](/threatgrid/ctim) project,
although it's quite easy to see the API and the models it handles
using the built-in [Swagger UI](http://localhost:3000/index.html) once
you have it running.

CTIA is implemented in [Clojure](http://clojure.org), and if you need
a crash course in running clojure projects, check out
[Clojure for the Brave and True](http://www.braveclojure.com/getting-started/).

## Usage

### Data Stores and External Dependencies

CTIA uses Leiningen as its "build" tool, you can install it by
following the instructions here: http://leiningen.org/#install

By default, CTIA uses Elasticsearch 5.x as its data store.  Assuming you
have it running on 127.0.0.1:9200 you can simply start
CTIA.

You can jump to the [Developer](#Developer) section to see instructions
on how to run elasticsearch and other optional supporting tools using
Docker.  CTIA can use redis to store some of its objects (Verdicts)
and also can send streams of events to Redis and Elasticsearch.

#### Purging ES Stores

Using an uberjar you can purge all the ES Stores with this command:

`java -cp ctia.jar:resources:. clojure.main -m ctia.task.purge-es-stores`

Using lein use this one:

`lein run -m ctia.task.purge-es-stores`

### Run the application locally

Running from a cloned repository:

`lein run`

If you like, you can cider-connect to the NREPL listener that it
starts by default.  Be sure NOT to run the service this way in
production, unless you ensure access to the NREPL port is restricted.

### Packaging and running as standalone jar

This is the proper way to run this in production.

```
lein do clean, uberjar
java -Xmx4g -Djava.awt.headless=true -XX:MaxPermSize=256m -Dlog.console.threshold=INFO -jar target/ctia.jar
```

Obviously, one may tweak the java arguments as needed.

## Development

The easiest way to get running is to use `docker`.

On Mac OS X, you should use
[Docker for Mac](https://docs.docker.com/docker-for-mac/) which
includes all the dependencies you need to run Docker containers.

**With Kafka and Zookeeper now part of the dev cluster, you will need
  to increase the memory you allocate to Docker.  You can do this thru
  your Docker preferences.  This has been tested with an 8mb
  allocation.**

We  provide a  default `containers/dev/docker-compose.yml`  which will
bring up the  dependencies you need in containers.  We  also provide a
`containers/test/docker-compose.yml`    which     is    the    cluster
configuration that our Travis CI tests again.  If you add services, be
sure  to update  both so  your unit  tests work.

You can then bring up a development environment:

```
cd containers/dev
docker-compose -f docker-compose.yml build
docker-compose -f docker-compose.yml up
```

Using docker for mac, this will bind the following ports on your
development machine to the services running in the containers:

* Redis - 6379
* elasticsearch - 9200 and 9300
* kibana - 5601
* zookeeper - 2181
* kafka - 9092

If you run into issues with one of your contains being in a weird
state, you can delete the image.  First shut down the docker-compose
cluster, and then run `docker images ls` to list the images, and then
you can delete the image for the container that is giving you trouble.
The usual culprits are elasticsearch and zookeeper.

It can be very useful to use _Kitematic_ to monitor and interact with
your containers.


If you ever need to reset your entire dev environment, perform the
following steps

* `docker images ps` - to get a list of images and their image IDs
* `docker rmi IMGID` - to delete the `zookeeper`, `elasticsearch`, `kafka`, `kibana` and `redis` images
* `cd containers/dev; docker-compose -f docker-compose.yml up --force-recreate`

Now, in another terminal, you can run CTIA.


#### Deprecated Docker Toolbox support

If you can't use docker directly and are forced to use Docker Toolbox,
you will then need to tell your CTIA where to find its dependencies.
The services will be listening on your docker-machine's IP, which you
can get with the command, `docker-machine ip`, and then you define
your own `resources/ctia.properties` file with the following values:

```
ctia.store.es.default.host=192.168.99.100
ctia.store.es.default.port=9200
ctia.hook.es.host=192.168.99.100
ctia.hook.es.port=9200
ctia.hook.redis.host=192.168.99.100
ctia.hook.redismq.host=192.168.99.100
```

Or you could initialize your properties with:

```
lein init-properties
```

### Testing and CI

All PRs must pass `lein test` with no fails.  All new code should have
tests accompanying it.

### Data Access Control

Document Access control is defined at the document level, rules are defined using TLP (Traffic Light Protocol) by default:

#### Green/White TLP

| Identity  | Read     | Write    |
|-----------|----------|----------|
| Owner     | &#10004; | &#10004; |
| Group/Org | &#10004; | &#10004; |
| Others    | &#10004; |          |

#### Amber TLP

| Identity  | Read     | Write    |
|-----------|----------|----------|
| Owner     | &#10004; | &#10004; |
| Group/Org | &#10004; | &#10004; |
| Others    |          |          |

#### Red TLP

| Identity  | Read     | Write    |
|-----------|----------|----------|
| Owner     | &#10004; | &#10004; |
| Group/Org |          |          |
| Others    |          |          |


#### Custom Access Rules

it is possible to grant additional access to any user/group using either `authorized_users`
or `authorized_groups` document fields, when an identity is marked in one of these fields, 
it gets full R/W access to the documents.

Examples:

The following actor Entity is marked as `Red`, thus allowing only its owner RW access,
since "foo" and "bar" are marked as `authorized_users` the owners of those identites also have RW access.

```json
  {"id": "actor-5023697b-3857-4652-9b53-ccda297f9c3e",
   "type": "actor",
   "schema_version": "0.4.2",
   "actor_type": "Hacker",
   "confidence": "High",
   "source": "a source",
   "tlp": "red",
   "valid_time": {},
   "authorized_users": ["foo" "bar"]}
```

The following actor Entity is marked as `Amber`, thus allowing only its owner or group RW access,
since "foogroup" and "bargroup" are marked as `authorized_groups` identities in these groups also get full RW access.

```json
  {"id": "actor-5023697b-3857-4652-9b53-ccda297f9c3e",
   "type": "actor",
   "schema_version": "0.4.2",
   "actor_type": "Hacker",
   "confidence": "High",
   "source": "a source",
   "tlp": "red",
   "valid_time": {},
   "authorized_groups": ["foogroup" "bargroup"]}
```

### Store Migrations
 
 There is a dedicated task to migrate data from prior versions of CTIA.
 this task will run through all configured stores, transform and copy data to new Elasticsearch indices.
 
 - This task doesn't alter the existing indices, you should delete them after a successful migration.

 - As the migration task copies indexes, make sure you have enough disk space before launching it.
 
 - After the migration task completes, you will need to edit your properties, changing each store index to the new one and restart CTIA.
 
 - In case of failure, you can relaunch the task at will, it should fully recreate the new indices.
 
 - make sure the resulting indices from your prefix configuration don't match existing ones as they will be deleted.
 
 Launch the task with:
 
`java -cp ctia.jar:resources:. clojure.main -m ctia.task.migrate-es-stores <prefix> <migrations> <batch-size> <confirm?>`

or from source with leiningen:

`lein run -m ctia.task.migrate-es-stores <prefix> <migrations> <batch-size> <confirm?>`

#### Task arguments

| argument   | description                                                                                     | example       |
|------------|-------------------------------------------------------------------------------------------------|---------------|
| prefix     | a prefix string for the newly create indexes, it will be wrapped with `v<prefix>_`              | 0.4.16        |
| migrations | a migrations task list to run                                                                   | 0.4.16,0.4.17 |
| batch-size | how many documents to fetch and and convert at once                                             | 1000          |
| confirm?   | setting this to false will not write anything to the ES data store and simulate transforms only | true          |


#### Available migrations

| migration task | target CTIA versions          | sample command                                                                                   |
|----------------|-------------------------------|--------------------------------------------------------------------------------------------------|
|         0.4.16 | All versions before 1.0.0-rc1 | `java -cp ctia.jar:resources:. clojure.main -m ctia.task.migrate-es-stores 0.4.16 0.4.16 200 true` |
|                |                               |                                                                                                  |


## License

Copyright © 2015-2016 Cisco Systems

Eclipse Public License v1.0

## Data Model

The data model of CTIA is closely based on
[STIX](http://stixproject.github.io/data-model/), with a few
simplifications.  See [Cisco Threat Intel Model](https://github.com/threatgrid/ctim/tree/master/doc/) for details.

