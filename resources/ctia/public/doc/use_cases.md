This document is intended to capture use cases, and also document how
to execute them for users.


# Roles and Use Cases

## Security Device

### 1.1 As a security device, I would like ask if an IP or Domain is malicious

    curl http://ctiahost/ctia/ip/192.168.1.1/verdict

or

    curl http://ctiahost/ctia/domain/badhost.com/verdict

### 1.2 As a security device, I would like to provide context for why an IP is malicious

    curl http://ctiahost/ctia/ip/192.168.1.1/judgements

### 1.3 As a security device, I would like to pull down a set of verdicts as a feed
 - all new verdicts for a given observable type in this hour, or day?
 - all verdicts currently active for a given observable type?
 - limit to N items
 - needs to be paginated

### 1.4 As a security device, I would like to record a sighting of an indicator

You must first know the indicator ID.  Here is an example looking it up by title.

	curl http://ctiahost/ctia/indicator/title/document-direct-ip-traffic

Post the sighting with the indicator ID.

	curl -XPOST -d'{"observable_relation":{"source":{"type":"sha256","value":"abc"},"relation":"Sent_To","relation":{"type":"ip","value":"10.0.0.1"},...}' http://ctiahost/ctia/indicator/ID/sighting

## Incident Responder

### 2.1 As an incident responder, I would like to know what malware is associated with an IP

For each jugement object returned by:

    curl http://ctiahost/ctia/ip/192.168.1.1/indicators

Extract the IDs from the 'indicated_TTP.ttp_id' fields and

    curl http://ctiahost/ctia/ttp/ID

### 2.2 As an incident responder, I would like to know what campaigns are associated with an IP

For each judgement object returned by:

    curl http://ctiahost/ctia/ip/192.168.1.1/indicators

Extract the IDs from the 'related_campaigns.campaign_id' fields and

    curl http://ctiahost/ctia/campaigns/ID

### 2.3 As an incident responder, I would like to know what suggested COAs are associated with an IP

For each indicator object returned by:

    curl http://ctiahost/ctia/ip/192.168.1.1/indicators

Extract the IDs from the 'suggested_COA.COA__id' fields and

    curl http://ctiahost/ctia/coa/ID

### 2.4 As an incident responder, I would like to record an incident and it's sightings

Determine the ID of the incident's indicator(s).  For example, if you know the title:

	curl http://ctiahost/ctia/indicator/title/document-direct-ip-traffic

Or search for the indicator:

    curl http://ctiahost/ctia/indicator/_search?title=ip # URL TBD

Import the incident, providing the indicator ID.

	curl -XPOST -d'{"valid_time":{...},"confidence":"High","description":"an indicent","suggest_COA":{...},"related_indicators":[{"indicator_id":"indicator-123"}]}' http://ctiahost/ctia/incident

Also import each sighting

	curl -XPOST -d'{"description":"...","timestamp":"...","indicator":{"indicator_id":"indicator-123"}}' http://ctiahost/ctia/sighting

### 2.5 As an incident responder, I would like to prioritize an incident

Determine the phase of the kill chain the incident's indicator(s) belong to.  
The later the phase of the kill chain the more critical the incident is and needs to be addressed on priority

Examples: 
An email-id known to be sending spam emails could be mapped under "Reconaissance"
User visiting a URL known to be serving malicious advertisements could be mapped under "Delivery"
A registery deleted known to done by malware file could be mapped under "Exploit"
A suspicious file visiting an unknown IP address could be mapped under "Command and Control" 
A dropbox link known to be used for uploading exfiltrated files could be "Action on Objectives"

TBA


## Security Operator

### 3.1 As a security operator, I would like to import a threat feed

For each entry in the feed, based on the source of the feed, and it's
content, choose a "origin" value such as "Bob's Threat Intel" and a
reason such as "Known RAT IP"

    curl -XPOST -d'{"disposition": 2, "observable": {...}' http://ctiahost/ctia/judgement

### 3.2 As a security operator, I would like to import an indicator

    curl -XPOST -d'{"title":"..."}' http://ctiahost/ctia/indicator

Extract the indicator ID from the created Indicator, and then import
your observables with that indicator id.  Set the origin and reason as
you would when creating a Judgement without an indicator.

    curl -XPOST -d'{"disposition": 2, "indicator": ID, "observable": {...}}' http://ctiahost/ctia/judgement

### 3.3 As a security operator, I would like to whitelist my internal IPs

Create "clean" judgments for your whitelist IP addresses (disposition number 1)

    curl -XPOST -d'{"observable":{"type":"ip","value":"..."},"disposition_number":1}' http://ctiahost/ctia/judgement

### 3.4 As a security operator, I would like to record that an indicator was wrong

Create feedback for the indicator. Note that IDs are actually URLs, so the ID specifically identifies the item.

    curl -XPOST -d'{"entity_id":"indicator-123","feedback":-1}' http://ctiahost/ctia/feedback

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

    curl -XPOST -d'{"specifications":[{"type":"Snort","snort_sig":"..."}]}' http://ciahost/ctia/indicator

### 4.3 As an intel producer I would like to be able to publish OpenIOC indicators

    curl -XPOST -d'{"specifications":[{"type":"OpenIOC","SOIC":"..."}]}' http://ciahost/ctia/indicator

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

## Threat Intel Analyst (Hunter)

### 6.1 As an Threat Analyst I want to add observables that may not be as malicious.

An analyst may be gathering information around an actor or campaign
and would like to add the observations as a judgement in order to
populate an indicator or incident. This is 'interesting' or
'informational' but not necessarily suspicious. For example: An actor
may use a specific public ip lookup site to check location,
etc... This domain seen in conjunction with host indicators may
indicate compromise. It may also simply be information added to TTPs
or a Campaign.
