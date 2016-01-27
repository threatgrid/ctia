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

The data model of CIA is closely based on
[STIX](http://stixproject.github.io/data-model/) with a few
simplifications:

  * The base Types cannot be documented inside of each other.  It's
  like always having to use an `idref`.  This is because we intend to
  build a hypermedia threat intel web combining global and local
  threat intel.

  * It's built on top of a "verdict service" so we simplify
  Observables into their most commonly observed properties.  You no
  longer have to say, "a file, with the sha256 checksum equal to X"
  you would simple say, "a sha256 checksum".  We cross index
  everything on these observables, and distill the indicators down
  into verdicts that allow q quick looking to see if an observable is
  of interest.

  * We flatten some structured data to make it easier to deal with as
  JSON and simpler, since we are dealing with specific cases in CIA.

  * We assume specific string representations for descriptions and
  such, instead of the more complex structured data which allows the
  specification of multiple formats.  This is to enforce a more secure
  representation fromat suitable for embedding in web applications.

## IDs and References

IDs are of the form: type-<128bitUUID>, for example `judgment-de305d54-75b4-431b-adb2-eb6b9e546014`

So, a reference that is just an ID, implies it is available on the
same CIA instance, or server as the data in it was requested.  A
reference can also be a full URL, such as
`https://cia.someplace.else.org/judgement/de305d54-75b4-431b-adb2-eb6b9e546014`
and that object could be retrieved at that URI.

## Observables

Simple, atomic value that denotes an entity which as an identity that
is stable enough to be attributed an intent or nature.  These do not
exist as objects within the CIA storage model, you never create an
observable.

 Type | Representation | Example
-------|---------------|-------
 ip | The IP address of a host in normal form |     {"type": "ip", "value": "192.168.1.1"} 
 ipv6 | IPv6 address of a host, the format is x\:x\:x\:x\:x\:x\:x\:x, where the 'x's are the hexadecimal values of the eight 16-bit pieces of the address.  Letters must be lowercase | {"type": "ipv6", "value": "fedc:ba98:7654:3210:fedc:ba98:7654:3210"} 
 device | Hex device address, letters must be lowercase. | {"type": "mac", "00:0a:27:02:00:53:24:c4"} 
 user | A unique identifier string, such as SSO login | {"type": "user", "value": "salesguy"} 
 domain | a hostname or domain name, like "foo.com" | {"type": "domain", "value": "badsite.com"} 
 sha256 | A hex representation of the SHA256 of a file, letters lowercased. | {"type": "sha256", "value": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" } 
 md5 | A hex repreentation of the MD5 of a file, letters lowercased. | {"type": "md5", "value": "d41d8cd98f00b204e9800998ecf8427e"} 
 sha1 | a hex representation of the SHA1 of a file, letters lowercased. | {"type": "sha1", "value": "da39a3ee5e6b4b0d3255bfef95601890afd80709"} 
 url | A string containing a URL | {"type": "url", "value": "https://panacea.threatgrid.com"} 

The type should also be in lowercase, it will be coerced to lowercase
upon storage.

## Judgement

A statement about the intent of an Observable.  Since a core goal of
the CIA is to provide a simple verdict service, these judgements are
the basis for the returned verdicts.  These are also the primary means
by which users of the CIA go from observables on their system, to the
indicators and threat intelligence data in CIA.

When you create a new Judgement, you must provide:

 * an observable
 * disposition or disposition_name
 * a source ientifier
 
You may include optionally:

 * source_uri -- a link where a user can get see what the source thinkgs of the observable
 * confidence -- default is 100
 * severity -- default is 100
 * timestamp -- default is time object is POSTed to server
 * expires -- default is some Jan 1 2535.
 * priority -- defaults to a user specific value (or can be supplied by some users)
 * reason -- a short description of why this judgement was made
 * reason_uri -- a link where a user can get information supporting this reason


Additional fields will be added to the record when stored, but MAY not be shared:

 * created -- timetamp
 * owner -- string identifying the creating user

Here is a quick example using curl:

```
curl -X POST --header "Content-Type: application/json" \
--header "Accept: application/json" \
-d '{"observable":{"type":"ip", "value":"127.0.0.1"}, 
     "disposition":2, "source":"internet chat forum"}' "http://localhost:3000/cia/judgements"

## Verdict

The Verdict is chosen from all of the Judgements on that Observable which
have not yet expired.  The highest priority Judgement becomes the
active verdict.  If there is more than one Judgement with that
priority, than Clean disposition has priority over all others, then
Malicious disposition, and so on down to Unknown.

    { "judgement": "judgmeent-de305d54-75b4-431b-adb2-eb6b9e546014",
      "disposition": 1,
	  "disposition_name": "Clean" }

The `judgement` field is optional, and is the ID of the judgement that
determined the final verdict.  The `disposition_name` field is also
optional, but is intended to be shown to a user, so applications down
have to always remember the mapping of numbers to human words.


```
curl http://localhost:3000/cia/ips/127.0.0.1/verdict


## Indicator

An indicator is a test, or a collection of judgements that define
criteria for identifying the activity, or presence of malware, or
other unwanted software.

We follow the
[STiX IndicatorType](http://stixproject.github.io/data-model/1.2/indicator/IndicatorType/)
closely, with the exception of not including observables within the
indicator, and preferring a `specification` object encoded in JSON as
opposed to an opaque `implemntation` block.

An indicator should include the following fields:

    * `title` -- A short and hopefully descriptive and unique title
    * `type`  -- Malware Behavior, or File Watchlist
    * `expires` -- timestamp denote when the indicator is no longer valid
    * `indicated_ttps` -- even if pointing to a very simple TTP with just a title
    * `confidence` -- a confidence assertion
	* `producer` -- Who defined this indicator

Additional, you will want to either define judgements against
Observables that are linked to this indicator, with the ID in the
`indicators` field of those Judgements, or you can provide a
`specification` value.

When you create a judgement you can provide the following fields:

Field|Required|Format|Description
------|--------|------|-----------
title|Yes|String|A short title for the indicator
type|Yes|String|The indicator type, such as URL Watchlist, or Malware Artifact, or Malware Behavior
confidence|Yes|String|"Low", "Medium" or "High"
producer|Yes|A string|An identifier of the system or person that produced this indicator.
short\_description|No|String|A short sentence or two describing the indicator
description|No|String, Markdown|A longer, in-depth description of the indicator
expires|No|ISO8601 Timestamp|When the indicator is no longer valid
indicated\_ttps|No|List of IDs|A list of the IDs of TTPs objects related to this indicator
kill\_chain\_phase|No|List of strings|One or more kill chain phases, like "Delivery"
test\_mechanism|No|List of strings|One or more products or tools that can use the data in this indicator to perform a test for it's presence on a host or network
likely\_impact|No|A string|The impact of malware, High, Medium, Low or None
related\_indicators|No|A list of IDs|One or more indicator related to this one.
related\_campaigns|No|A list of IDs|One or more campaigns related to this indicator.
related\_coas|No|A list of IDs|One or more COAs related to this indicator.


Additional fields will be generated upon creation:

Field|Format|Description
------|------|-----------|-------
id|String|A UUID asigned
owner|String|The account or identiy that created the indicator in the system.  May not by the definer, or producer.
created|ISO8601 Timestamp|When the indicator was create.
timestamp|ISO8601 Timestamp|When the indicator was lat modified.

## Feedback

A positive, neutral or negative feedback on a Judgement

# Design

Support multiple CIAs references each other, so the IDs needs to be
URIs realy, or UUIDs with a namespace that can be turned into a
hostname easily.

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

### As a security device, I would like to pull down a set of verdicts as a feed
 - all new verdicts for a given observable type in this hour, or day?
 - all verdicts currently active for a given observable type?
 - limit to N items
 - needs to be paginated
  

