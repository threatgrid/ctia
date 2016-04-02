"Slowly I turned...step by step...inch by inch...,"

# Debutant Release

Core functionality, suitable for content consumers and producers to
target it's API and get their data in and out.  Cover the basic
use-cases, and provide an operational example of the data model in
flight.

  * All endpoints operational with the in-memory, disk persistence, and Elasticsearch stores
  * Schema defined for all major entity types
  * Event Model defined for all operations

## Use Cases

Completion requires not only a full example, but also documentation of
all user facing surfaces.

### Security Device

  * [ ] 1.1 As a security device, I would like ask if an IP or Domain is malicious
  * [ ] 1.2 As a security device, I would like to provide context for why an IP is malicious
  * [ ] 1.4 As a security device, I would like to record a sighting of an indicator

### Incident Response

  * [ ] 2.1 As an incident responder, I would like to know what malware is asociated with an IP

### Security Operator

  * [ ] 3.1 As a security operator, I would like to import a threat feed
  * [ ] 3.2 As a security operator, I would like to import an indicator
  * [ ] 3.3 As a security operator, I would like to whitelist my internal IPs
  * [ ] 3.4 3.4 As a security operator, I would like to record that an indicator was wrong

### Intelligence Producer

  * [ ] 4.4 As an intel producer I would like to be able to publish
    simple observable matching indicators
  * [ ] 4.5 As an intel producer I would like record a new Campaign
  * [ ] 4.6 As an intel producer I would like record a new Actor
  * [ ] 4.7 As an intel producer I would like record a new type of Malware


# Better Together Release

Ready to operate within an Threat Intel architecture, as hub, or
consumer, and support basic lifecycle functions for "supported"
deployments

## Use Cases

### Integrator

  * [ ] 5.1 As an integrator, I would like a stream of events for all data operations
  * [ ] 5.2 As an integrator, I would like to be able to provide my own storage layer
  * [ ] 5.3 as an integrator, I would like a supported Python Client
  * [ ] 5.4 as an integrator, I would like to authenticate against my existing system
  * [ ] 5.5 as an integrator, I would like to replicate data from one instance to another
  * [ ] 5.6 as an integrator, I would like to define my own data store
  * [ ] 5.9 as an integrator, I would like to define my own event hooks
  * [ ] 5.7 as an integrator, I would like to define my own business logic hooks
  * [ ] 5.8 as an integrator, I would like to define my own event consumers

# A few more releases....

# Release 1.0

The following "interfaces" must be stable enough for external products
and parties to target with guaranteed
[semantic versioning](http://semver.org/)

  * Swagger API
  * Event Schema
  * Extension Interface

## Use Cases

### Intelligence Producer

  * [ ] 4.8 as an intel producer, I would like to export data as STIX 1.2 XML
  * [ ] 4.9 as an intel producer, I would like to import data as STIX 1.2 XML


# The future

## OpenC2 support
## TWIGS/STIX 2.0
## Base UI
