This document is intended to capture use cases, and also document how
to execute them for users.


# Roles and Use Cases

## Security Device

### 1.1 As a security device, I would like ask if an IP or Domain is malicious

    curl http://ciahost/cia/ip/192.168.1.1/verdict

or

    curl http://ciahost/cia/domain/badhost.com/verdict

### 1.2 As a security device, I would like to provide context for why an IP is malicious

    curl http://ciahost/cia/ip/192.168.1.1/judgements

### 1.3 As a security device, I would like to pull down a set of verdicts as a feed
 - all new verdicts for a given observable type in this hour, or day?
 - all verdicts currently active for a given observable type?
 - limit to N items
 - needs to be paginated

### 1.4 As a security device, I would like to record a sighting of an indicator

curl -XPOST -d'{:observable {...}...}' http://ciahost/cia/indicator/ID/sighting

## Incident Responder

### 2.1 As an incident responder, I would like to know what malware is asociated with an IP

For each jugement object returned by:

    curl http://ciahost/cia/ip/192.168.1.1/indicators

Extract the IDs from the `indicated__TTP.ttp__id' fields and

    curl http://ciahost/cia/ttp/ID

### 2.2 As an incident responder, I would like to know what campaigns are asociated with an IP

For each jugement object returned by:

    curl http://ciahost/cia/ip/192.168.1.1/indicators

Extract the IDs from the `related_campaigns.campaign__id' fields and

    curl http://ciahost/cia/campaigns/ID

### 2.3 As an incident responder, I would like to know what suggest COAs are asociated with an IP

For each jugement object returned by:

    curl http://ciahost/cia/ip/192.168.1.1/indicators

Extract the IDs from the `suggested_COA.COA__id' fields and

    curl http://ciahost/cia/coa/ID

## Security Operator

### 3.1 As a security operator, I would like to import a threat feed

For each entry in the feed, based on the source of the feed, and it's
content, choose a "origin" value such as "Bob's Threat Intel" and a
reason such as "Known RAT IP"

    curl -XPOST -d'{"disposition": 2, "observable": {...}' http://ciahost/cia/judgements

### 3.2 As a security operator, I would like to import an indicator

    curl -XPOST -d'{"title":   }' http://ciahost/cia/indicators

Extract the indicator ID from the created Indicator, and then import
your observables with that indicator id.  Set the origin and reason as
you would when creating a Judgement without an indicator.

    curl -XPOST -d'{"disposition": 2, "indicator": ID, "observable": {...}' http://ciahost/cia/judgements

### 3.3 As a security operator, I would like to whitelist my internal IPs

### 3.4 As a security operator, I would like to record that an indicator was wrong

## Intelligence Producer

### 4.1 As an intel producer I would like to be able to publish my actionable threat content ###

Determine appropriate priority
Determine appropriate source identifier
Determine what URLs I want to make available as source and reason URIs

Post a catalog of indicators and TTPs

Post judgements as we make them, and link them to the indicators
  - determine confidence and severity
  - restrict only to very high confidence

### 4.2 As an intel producer I would like to be able to publish SNORT indicators

### 4.3 As an intel producer I would like to be able to publish OpenIOC indicators

### 4.4 As an intel producer I would like to be able to publish simple observable matching indicators

### 4.5 As an intel producer I would like record a new Campaign

### 4.6 As an intel producer I would like record a new Actor

### 4.7 As an intel producer I would like record a new type of Malware

### 4.8 as an integrator, I would like to export data as STIX 1.2 XML

### 4.9 as an integrator, I would like to import data as STIX 1.2 XML


## Integrator

An integrator is a software developer or system deployer who wants to
run an instance of the CTIA within their own infrastructure, or
embedded in an device or service.  We assume familiarity with Java,
and common practices in that community.  While CTIA is implemented in
Clojure, the intent is that all extension points have well defined
Interfaces that Java, or other JVM languages, can implement.

### 5.1 As an integrator, I would like a stream of events for all data operations

### 5.2 As an integrator, I would like to be able to provide my own storage layer

### 5.3 as an integrator, I would like a supported Python Client

### 5.4 as an integrator, I would like to authenticate against my existing system

### 5.5 as an integrator, I would like to replicate data from one instance to another

### 5.6 as an integrator, I would like to define my own event hooks

### 5.7 as an integrator, I would like to define my own business logic hooks

### 5.8 as an integrator, I would like to define my own event consumers




