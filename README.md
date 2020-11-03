[![Build Status](https://travis-ci.org/threatgrid/ctia.svg?branch=master)](https://travis-ci.org/threatgrid/ctia)
# Cisco Threat Intel API

A Pragmatic, Operationalized Threat Intel Service and Data Model

For full documentation see [doc/](doc/)

We also think the [Use Cases](doc/use_cases.md) document is a good
starting point.

Interactive, Swagger docs for the API are available once
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

In addition to the RESTful HTTP API, it also has a GraphQL API and many event handlers.

The data model is defined in the [CTIM](https://github.com/threatgrid/ctim) project,
although it's quite easy to see the API and the models it handles
using the built-in [Swagger UI](http://localhost:3000/index.html) once
you have it running.

CTIA is implemented in [Clojure](http://clojure.org)

## Usage

### Data Stores and External Dependencies

CTIA uses Leiningen as its "build" tool, you can install it by
following the instructions here: http://leiningen.org/#install

By default, CTIA uses Elasticsearch 5.x as its data store.  Assuming you
have it running on 127.0.0.1:9200 you can simply start
CTIA.

You can jump to the [Development](#Development) section to see instructions
on how to run elasticsearch and other optional supporting tools using
Docker.  CTIA may use Kafka, Redis and ES to push events.

#### Purging ES Stores

Using an uberjar build you can purge all the ES Stores with this command:

`java -cp ctia.jar:resources:. clojure.main -m ctia.task.purge-es-stores`

Using lein use this one:

`lein run -m ctia.task.purge-es-stores`

### Run the application locally

Running from a cloned repository:

`lein run -m ctia.main`

### Packaging and running as standalone jar

This is the proper way to run this in production.

```
lein do clean, uberjar
java -Xmx4g -Djava.awt.headless=true -Dlog.console.threshold=INFO -jar target/ctia.jar
```

You may tweak the java arguments as per your requirements.

## Development

The easiest way to get running is to use `docker`.

On Mac OS X, you should use
[Docker for Mac](https://docs.docker.com/docker-for-mac/) which
includes all the dependencies you need to run Docker containers.

**With Kafka and Zookeeper now part of the dev cluster, you will need
  to increase the memory you allocate to Docker.  You can do this thru
  your Docker preferences.  This has been tested with a 4GB
  allocation.**

We  provide a  default `containers/dev/docker-compose.yml`  which will
bring up the  dependencies you need in containers. 

You can bring up a development environment:

```
docker-compose -f containers/dev/docker-compose.yml up
```

Using docker for mac, this will bind the following ports on your
development machine to the services running in the containers:

* Redis - 6379
* elasticsearch - 9200 and 9300
* kibana - 5601
* zookeeper - 2181
* kafka - 9092
* riemann - 5555-5557
* riemann-dash - 4567

If you ever need to reset your entire dev environment,
just kill the docker-compose process and run:

```
docker-compose -f containers/dev/docker-compose.yml down
docker-compose -f containers/dev/docker-compose.yml up --force-recreate --remove-orphans
```

In particular, this resets ElasticSearch indices, which cannot
be created more than once.

### Local workflow

To start CTIA locally, use `./scripts/run`.

For a REPL workflow, run `lein repl`. Use `(start)` to start CTIA,
`(stop)` to stop it, and `(go)` to restart it.

### Testing and CI

All PRs must pass `lein test` with no fails for PRs to be accepted.
Any new code functionality/change should have tests accompanying it.

### Rebalancing the tests

Continuous integration test runs are parallelized using
prior timing information in `dev-resources/ctia_test_timings.edn`.

If these timings become out of date, CI runs may take longer than necessary
and the tests should be rebalanced.

There are 2 ways to do this, described in the below subsections:
1. locally (slow)
2. by downloading a GitHub Actions artifact for a recent build (fast).

#### Rebalance tests locally (slow)

1. Run `./build/run-tests.sh` to get the latest test timings.
2. Run `./scripts/summarize-tests.clj` to collect timings
3. Run `cp target/test-results/all-test-timings.edn dev-resources/ctia_test_timings.edn`
   to update the latest timings.
4. Commit this change and push.

#### Rebalance tests via GitHub Actions (fast)

You will need a completed GitHub Actions Pull Request build to base it on.

1. Visit the GitHub Actions Pull Request build you would like to use to rebalance
   - eg., https://github.com/threatgrid/ctia/actions/runs/342562872
2. Go to the `all-pr-checks` job (should be the last job) and download the `all-test-timings` artifact
3. Unzip the downloaded file and locate the inflated `all-test-timings.edn` file.
4. Copy the inflated `all-test-timings.edn` file to `dev-resources/ctia_test_timings.edn`
5. Commit this change and push.

Note: Unfortunately, even public artifacts require an API key to download
them via the API, so this is not straightforward to automate
([see more](https://github.com/actions/upload-artifact/issues/51)).

### Data Access Control

Document Access control is defined at the document level, rules are defined using TLP combined with the max-record-visibility property (Traffic Light Protocol) by default:


#### Everyone Max Record visibility

##### Green/White TLP

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

#### Group Max Record visibility

##### Green/White TLP

| Identity  | Read     | Write    |
|-----------|----------|----------|
| Owner     | &#10004; | &#10004; |
| Group/Org | &#10004; | &#10004; |
| Others    |          |          |

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

Please note that the `authorized_groups` property may work only if max record visibility is set to `everyone`

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

## Bundle import

The `/bundle` API endpoint allows users with the correct permissions to POST a CTIM [bundle object](https://github.com/threatgrid/ctim/blob/master/src/ctim/schemas/bundle.cljc).

The ability to post bundles is controlled by the `import-bundle` capability.

When a bundle is submitted:

1. All entities that have already been imported with the external ID whose prefix has been configured with the `ctia.store.external-key-prefixes` property are searched.
2. If they are identified by transient IDs, a mapping table between transient and stored IDs is built.
3. Only new entities are created in the same way as the `/bulk` API endpoint with transient IDs resolutions. Existing entities are not modified.

If more than one entity is referenced by the same external ID, an error is reported.

Response of the bundle API endpoint:

```clojure
{:results [{:id "http://example.com/ctia/entity-type/entity-type-991d8dfb-b54e-4435-ac58-2297b4d886c1"
            :tempid "transient:1f48f48c-4130-47f1-92dc-a6df8ab110b6"
            :action "create"
            :external_id "indicator-abuse-ch-077d653844d95d8cd8e4e51cb1f9215feae50426"
            :error "An error occurs"}]
```

|Field          |Description|
|---------------|-----------|
|`:id`          |The real ID|
|`:original_id` |Provided ID if different from real ID (ex: transient ID) |
|`:result`      |`error`, `created` or `exists` |
|`:external_id` |External ID used to identify the entity|
|`:error`       |Error message|


### Feeds

CTIA allows generating Feeds as public urls meant to easily exchange data 
through views skipping authentication. You may use this functionality to generate blocklists
easily consumable by simple systems.

The first kind of `Feed` is the `Indicator` one, you may create it posting a `Feed` document
specifying an `indicator_id` and an `output` type.

CTIA will then return the realized `Feed` document including two new fields: `feed_view_url` and `feed_view_url_csv`

- both of those urls will be publicly available without authentication so they must be shared carefully.
- both urls return the Judgements associated with the provided `indicator_id` through their relationships
- depending on the selected `output` it will either extract and return the observables only or the full Judgements
- the CSV view output either the full `Judgement` as CSV or the Observable values only depending on the `output`. 

### Elasticsearch Store management

see [CTIA Elasticsearch Stores: managing big Indices](doc/es_stores.md)

see [Migration procedure](doc/migration.md)

see [CTIA Elasticsearch CRUD details](doc/es_stores.pdf)


### Store Checks
 
 There is a dedicated task to check all stores of a configured CTIA instance.
 this task will run through all configured stores and validate each document in bulk.
  
 Launch the task with:
 
`java -cp ctia.jar:resources:. clojure.main -m ctia.task.check-es-stores <batch-size>`

or from source with leiningen:

`lein run -m ctia.task.check-es-stores <batch-size>`

#### Task arguments

| argument   | description                            | example |
|------------|----------------------------------------|---------|
| batch-size | how many documents to validate at once |    1000 |


#### API

##### List Pagination

HTTP routes providing a list use a default limit of 100 records.
An API client can change this parameter up to 10 0000 records.

when a limit is applied to the response, pagination headers are returned:

| header     | description                                                                    | example                                        |
|------------|--------------------------------------------------------------------------------|------------------------------------------------|
| X-TOTAL    | total number of hits in the data store                                         | 5000                                           |
| X-OFFSET   | the current pagination offset                                                  | 200                                            |
| X-NEXT     | ready made parameters to fetch the next results page                           | limit=100&offset=100&search_after=foo          |
| X-PREVIOUS | ready made parameters to fetch the previous results page                       | limit=100&offset=0                             |
| X-SORT     | the sort parameter for use with `search_after`, the id of the last result page | ["actor-77b01a42-6d2b-4081-8fd0-c887bf54140c"] |
|            |                                                                                |                                                |


To easily scroll through all results of a list, just iterate, appending `X-Next` to your base query URL.
if no `X-Next` header is present, you have reached the last page.

##### Offset Pagination

To be used for simple matters, when the result window is inferior to 10 000 (`offset` + `limit`)
use a combination of `offset` and `limit` parameters to paginate results.


#### Stateless Cursor Pagination

To be used when the result window is superior to `10 000`, allows to easily loop across all pages of a query response.
use `limit` and `offset` along with `search_after` filled with the value from the `X-Sort` response header to get the next page.

#### Rate limit

Requests may be rate limited by enabling the middleware using the `ctia.http.rate-limit.enabled` property.

It rate limits how many HTTP requests a CTIA group can make in an hour. The group is identified with the property :identity of the current Ring request.

Before the rate limit is reached, the header `X-Ratelimit-Group-Limit` is returned in the response:

```
HTTP/1.1 200 OK
Content-Type: application/json;charset=utf-8
Date: Wed, 31 Oct 2018 14:05:30 GMT
Server: Jetty(9.4.z-SNAPSHOT)
Strict-Transport-Security: max-age=31536000; includeSubdomains
Vary: Accept-Encoding, User-Agent
X-Ctim-Version: 1.0.6
X-Ctia-Config: b9b3477528d9616ed85221f2827bf1da443e8f00
X-Ctia-Version: 70323eb3b72da558e7f056e418533402f65d335a
X-Ratelimit-Group-Limit: 8000
```

If the rate limit is exceeded:

- The client receives a response with the 429 HTTP status, a `retry-after` header and the JSON message `{"error": "Too Many Requests"}`. The retry-after header indicates the number of seconds to wait before making a new request.

 ```
HTTP/1.1 429 Too Many Requests
Content-Length: 30
Content-Type: application/json
Date: Wed, 31 Oct 2018 14:05:30 GMT
Retry-After: 3557
Server: Jetty(9.4.z-SNAPSHOT)
Strict-Transport-Security: max-age=31536000; includeSubdomains
X-Ctim-Version: 1.0.6
X-Ctia-Config: b9b3477528d9616ed85221f2827bf1da443e8f00
X-Ctia-Version: 70323eb3b72da558e7f056e418533402f65d335a
 ```

- A message is logged with the :info level

## License

Copyright © 2015-2020 Cisco Systems

Eclipse Public License v1.0

## Data Model

The data model of CTIA is closely based on
[STIX](http://stixproject.github.io/data-model/), with a few
simplifications.  See [Cisco Threat Intel Model](https://github.com/threatgrid/ctim/tree/master/doc/) for details.

