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
applications.  It currently provides no authentication, or access
control.

We would like to generate client libaries from the swagger , and
provide officially supported versions of them for users.  To do this
we need to ensure we use the most appropriate types, and swagger
metadata in our API definition (see handler.clj).

## Data Model

## Observables

Simple, atomic value that denotes an entity which as an identity that
is stable enough to be attributed an intent or nature.

* Checksum (sha256, sha1, md5)
* IP addr (ipv4 or ipv6)
* Domain (or hostname)
* URL

## Judgement

A statement about the intent of an Observable.

When you create a new Judgement, you must provide:

 * an observable
 * disposition or disposition_name

You may include optionally:

 * confidence -- default is 100
 * severity -- default is 100
 * timestamp -- default is time object is POSTed to server
 * expires -- default is some Jan 1 2535.
 * priority -- defaults to a user specific value (or can be supplied by some users)
 * origin -- string naming source of data
 * reason
 * reason_uri


Additional fields will be added to the record:

 * created -- timetamp
 * origin -- string identifying the creating user

## Verdict

The Verdict is chosen from all of the Judgements on that Observable which
have not yet expired.  The highest priority Judgement becomes the
active verdict.  If there is more than one Judgement with that
priority, than Clean disposition has priority over all others, then
Malicious disposition, and so on down to Unknown.

## Indicator

A collection of Judgements.

## Feedback

A positive, neutral or negative feedback on a Judgement

# Design

## Requirements

Capabilities: Reading, writing, modifying

For a given API key, a function will call an external service to get a list of capabilities (keywords) which are use to grant permissions to one or more endpoints

Need to decide on a default level

## Personas

### Security Device

### Security Operator

### Incident Responder

### Threat Analyst

## Use Cases

### As a security device, I would like ask if an IP or Domain is malicious

    curl http://ciahost/cia/ip/192.168.1.1/verdict

or

    curl http://ciahost/cia/domain/badhost.com/verdict

### As a security device, I would like to provide context for why an IP is malicious

    curl http://ciahost/cia/ip/192.168.1.1/judgements

### As an incident responder, I would like to know what malware is asociated with an IP

For each jugement object returned by:

    curl http://ciahost/cia/ip/192.168.1.1/judgements

Extract the IDs from the `indicators` field

Call curl http://ciahost/cia/indicators/ID

### As a security operator, I would like to import a threat feed

For each entry in the feed, based on the source of the feed, and it's
content, choose a "origin" value such as "Bob's Threat Intel" and a
reason such as "Known RAT IP"

    curl -XPOST -d'{"disposition": 2, "observable": {...}' http://ciahost/cia/judgements

### As a security operator, I would like to import an indicator

    curl -XPOST -d'{"title":   }' http://ciahost/cia/indicators

Extract the indicator ID from the created Indicator, and then import
your observables with that indicator id.  Set the origin and reason as
you would when creating a Judgement without an indicator.

    curl -XPOST -d'{"disposition": 2, "indicator": ID, "observable": {...}' http://ciahost/cia/judgements

### As a security operator, I would like to whitelist my internal IPs

### As a security operator, I would like to record that an indicator was wrong
