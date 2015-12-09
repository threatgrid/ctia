# Cisco Intel API

THIS IS NOT OPERATIONAL

This is currently a API sketch, with a data model (schema) partially
defined.  You can run it but most of the API calls will not function.

Documentation for the API is available once you have it running, at:

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

`lein ring server`

If you like, you can cider-connect to the NREPL listener that it
starts by default.  Be sure NOT to run the service this way in
production, unless you ensure access to the NREPL port is restricted.

### Packaging and running as standalone jar

This is the proper way to run this in production.

```
lein do clean, ring uberjar
java -jar target/server.jar
```

### Packaging as war

`lein ring uberwar`

## License

Copyright ©  2015 Cisco


## Implementation Notes

The intent is for this service to be embedded in other JVM
applications.

We would like to generate client libaries from the swagger , and
provide officially supported versions of them for users.  To do this
we need to ensure we use the most appropriate types, and swagger
metadata in our API definition (see handler.clj).

## Data Model

## Entities

Simple, atomic observables that have a consistent identity that is
stable enough to be attributed an intent or nature.

* Checksum (sha256, sha1, md5)
* IP addr (ipv4 or ipv6)
* Domain (or hostname)
* URL

## Judgement

A judgement about the intent of an Entity, made by a data source, an Origin.sr

### Disposition

 * 1 - Clean
 * 2 - Malicious
 * 3 - Suspicious
 * 4 - Common

### Confidence

 * Integer 0-100 `confidence`

### Severity

 * Integer 0-100 `severity`

### Priority

Should this judgement override an exiting Verdict?  The higher, the
more priority.  If not provided, defaults to the Origin's priority.

 * Optional Integer 0-100 `priority`

### Origin
 
An URI linking back to the supporting data for the judgement, or a
resource describing the judgement in more detail in the Producing
system.

 * Optional String `origin`
 * Optional URI `origin_uri`

## Verdict

The current collective notion of the intent or nature of the Entity,
based on Judgements.  In the current Cisco Sandbox API, the
"disposition" call is very similiar to a Verdict.

### Disposition
 * Clean
 * Malicious
 * Suspicious
 * Common

## Relations

A relationship between two Entities, such as:

* Downloaded_By
* Uploaded_To
* Resolved_To
* Contains

## Tags

A free form text attached to an Entity, Relationship, or Disposition.
A tag can be elaborated within the system, adding descriptive text.

## Feedback

A positive or negative feedback on a Disposition

## Origin

The author of threat intel, either human or machine.

* String `name`
* Int `priority`

## Consumer

A device that asks questions of the system, consuming the threat
intel.
