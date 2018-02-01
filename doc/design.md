# Design

```
Good design is making something intelligible and memorable. Great design is making something memorable and meaningful.
-- Dieter Rams
```

### Design Themes

* Simplicity of design and interaction
* Focus on the Data
* Model distinct from Implementation
* Only the essential services

## Deployment Models

The CTIA is intended for several different deployment models, with
radically varying scaling, performance and storage requirements.

### Global/Enterprise Cloud Service

A single global/enterprise scale repository of verdicts and Threat
Intel data.

  * A place for all cisco products to get verdicts on shared Observables
  * A place for all cisco products to ask what Cisco collectively knows
  about an Observable.
  * A place for all cisco products and teams to record their judgements
  and indicators.
  * A place for all cisco products to record Sightings
  * A place for all cisco teams to record their published Threat Intel
  data.
  * A place for all cisco products to get Threat Intel.

All data in CTIA is visible to all users, by design.  So this will only
contain shared data, as defined in the Cisco Visibility Model.
Practically, that means Judgements related to IPs, Domains, and
Checksums.  For unshared data, the Pre-Customer Private Cloud model
would be used.

#### Requirements

* Plug-in authentication and authorization
* Very-Scalable storage model
* Highly scalable, low latency verdict/judgement/indicator service
* HTTP cache support
* Capability restrictions
* Ability to serve feeds of data

### Per-Customer Private Cloud Service

As the Global Cloud Service, but for data specific to a single
customer organization -- as defined by their own data sharing
boundaries.  A business may have multiple instances if national data
laws require it.  Observables that are not sharable according to the
Cisco Visbility Model can be references here, as could Sighitngs, and
Incidents and other data that organizations are not willing to share.

They can configure their devices to query their instance specifically.
And effectively see an overlay of their data on the global data set
for Judgements and Verdicts.  This allows the most performance
critical queries to be made once.  The remainder of the API would
require explicit lookups to the global instance.

#### Requiremnts

* Plug-in authentiation and authorization
* Small to medium storage size
* Low overhead per instance
* Multi-tenant, with complete isolation

### On-Premises Service

As Per-Customer Private Cloud Service, but in a VM or appliance that
runs on the customers premises.  A stripped down version of this
deployment model is the intended target for the Open Source
distribution of the CTIA code base.

#### Requirements

* Plug-in authentiation and authorization
* Small to medium storage size
* VM or appliance deployment
* Ability to pull feeds
* Pass-thru queries
* Target external stores
