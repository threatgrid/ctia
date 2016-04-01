[![Stories in Ready](https://badge.waffle.io/threatgrid/ctia.png?label=ready&title=Ready)](https://waffle.io/threatgrid/ctia)
# Cisco Threat Intel API


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

# Design

Support multiple CTIAs references each other, so the IDs needs to be
URIs realy, or UUIDs with a namespace that can be turned into a
hostname easily.

## Personas

### Security Device

### Security Operator

### Incident Responder

### Threat Analyst

## Use Cases

### As a security device, I would like ask if an IP or Domain is malicious

    curl http://ctiahost/ctia/ip/192.168.1.1/verdict

or

    curl http://ctiahost/ctia/domain/badhost.com/verdict

### As a security device, I would like to provide context for why an IP is malicious

    curl http://ctiahost/ctia/ip/192.168.1.1/judgements

### As an incident responder, I would like to know what malware is asociated with an IP

For each jugement object returned by:

    curl http://ctiahost/ctia/ip/192.168.1.1/judgements

Extract the IDs from the `indicators` field

Call curl http://ctiahost/ctia/indicators/ID

### As a security operator, I would like to import a threat feed

For each entry in the feed, based on the source of the feed, and it's
content, choose a "origin" value such as "Bob's Threat Intel" and a
reason such as "Known RAT IP"

    curl -XPOST -d'{"disposition": 2, "observable": {...}' http://ctiahost/ctia/judgements

### As a security operator, I would like to import an indicator

    curl -XPOST -d'{"title":   }' http://ctiahost/ctia/indicators

Extract the indicator ID from the created Indicator, and then import
your observables with that indicator id.  Set the origin and reason as
you would when creating a Judgement without an indicator.

    curl -XPOST -d'{"disposition": 2, "indicator": ID, "observable": {...}' http://ctiahost/ctia/judgements

### As a security operator, I would like to whitelist my internal IPs

### As a security operator, I would like to record that an indicator was wrong

### As a security device, I would like to pull down a set of verdicts as a feed
 - all new verdicts for a given observable type in this hour, or day?
 - all verdicts currently active for a given observable type?
 - limit to N items
 - needs to be paginated
