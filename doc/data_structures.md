# Data Structures

[Data Model Graph](model.png)
[Data Model Graph as `.dot` ](model.dot)

The data model of CTIA is closely based on
[STIX](http://stixproject.github.io/data-model/) with a few
simplifications:

  * The base Types cannot be documented inside of each other.  It's
  like always having to use an _idref_.  This is because we intend to
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
  JSON and simpler, since we are dealing with specific cases in CTIA.
  We will use default vocabularies whenever they are available.

  * We assume specific string representations for descriptions and
  such, instead of the more complex structured data which allows the
  specification of multiple formats.  This is to enforce a more secure
  representation fromat suitable for embedding in web applications.

##### Table of Contents

- [Actor](#actor)
- [Campaign](#campaign)
- [Course of Action](#coa)
- [Exploit Target](#exploit_target)
- [Feedback](#feedback)
- [Incident](#incident)
- [Indicator](#indicator)
- [Judgement](#judgement)
- [Tools, Techniques, & Procedures](#ttp)
- [Verdict](#verdict)
- [Shared Structures](#shared_structures)
- [Relationship Structures](#relationship_structures)
- [Vocabularies](#vocabularies)

<a name="actor"/>
## Actor

| Key                                        | Value                                          | Mandatory? |
| --- | ----- | ---------- |
| id                                         | [ID](#id)                                      | &#10003;   |
| title                                      | string                                         | &#10003;   |
| description                                | (string, ...)                                  | &#10003;   |
| short_description                          | (string, ...)                                  | &#10003;   |
| timestamp                                  | [Time](#time)                                  | &#10003;   |
| tlp                                        | [TLP](#tlp)                                    |            |
| source                                     | [Source](#source)                              |            |
| identity                                   | [Identity](#identity)                          |            |
| actor\_type                                | [ThreatActorType](#threat_actor_type)          | &#10003;   |
| motivation                                 | [Motivation](#motivation)                      |            |
| sophistication                             | [Sophistication](#sophistication)              |            |
| Intended\_effect                           | [IntendedEffect](#intended_effect)             |            |
| planning\_and\_operational\_support        | string                                         |            |
| observed_TTPs                              | ([RelatedTTP](#related_ttp), ...)              |            |
| associated_campaigns                       | ([RelatedCampaign](#associated_campaign), ...) |            |
| associated_actors                          | ([RelatedActor](#related_actor), ...)          |            |
| confidence                                 | [HighMedLow](#high_med_low)                    |            |
| expires                                    | [Time](#time)                                  | &#10003;   |

Stored instances will also receive the following fields, but MAY not be shared:

| Key     | Value         | Description                               |
| --- | --- | --- |
| type    | "actor"       | Model type                                |
| created | [Time](#time) | Timestamp when object was created in CTIA |
| owner   | string        | String identifying the creating user      |

### References

STIX [ThreatActorType](http://stixproject.github.io/data-model/1.2/ta/ThreatActorType/)

<a name="campaign"/>
## Campaign

| Key                           | Value                                         | Mandatory? |
| --- | --- | --- |
| id                            | [ID](#id)                                     | &#10003;   |
| title                         | string                                        | &#10003;   |
| description                   | (string, ...)                                 | &#10003;   |
| short_description             | (string, ...)                                 | &#10003;   |
| timestamp                     | [Time](#time)                                 | &#10003;   |
| tlp                           | [TLP](#tlp)                                   |            |
| version                       | string                                        |            |
| names                         | string                                        |            |
| intended_effect               | [IntendedEffect](#intended_effect)            |            |
| status                        | [CampaignStatus](#campaign_status)            |            |
| related_TTPs                  | ([RelatedTTP](#related_ttp), ...)             |            |
| related_incidents             | ([RelatedIncident](#related_incident), ...)   |            |
| attribution                   | ([RelatedActor](#related_actor), ...)         |            |
| associated_campaigns          | ([RelatedCampaign](#realted_campaign), ...)   |            |
| confidence                    | [HighMedLow](#high_med_low)                   |            |
| activity                      | [Activity](#activity)                         |            |
| source                        | [Source](#source)                             |            |
| campaign_type                 | string                                        | &#10003;   |
| expires                       | [Time](#time)                                 | &#10003;   |
| indicators                    | ([RelatedIndicator](#related_indicator), ...) | &#10003;   |

Stored instances will also receive the following fields, but MAY not be shared:

| Key     | Value         | Description                               |
| --- | --- | --- |
| type    | "campaign"    | Model type                                |
| created | [Time](#time) | Timestamp when object was created in CTIA |
| owner   | string        | String identifying the creating user      |

### References

STIX [CampaignType](http://stixproject.github.io/data-model/1.2/campaign/CampaignType/)

<a name="coa"/>
## Course of Action (COA)

| Key                    | Value                             | Mandatory? |
| --- | --- | --- |
| id                     | [ID](#id)                         | &#10003;   |
| title                  | string                            | &#10003;   |
| description            | (string, ...)                     | &#10003;   |
| short\_description     | (string, ...)                     | &#10003;   |
| timestamp              | [Time](#time)                     |            |
| tlp                    | [TLP](#tlp)                       |            |
| stage                  | [COAStage](#coa_stage)            |            |
| coa_type               | [COAType](#coa_type)              |            |
| objective              | (string, ...)                     |            |
| impact                 | string                            |            |
| cost                   | [HighMedLow](#high_med_low)       |            |
| efficacy               | [HighMedLow](#high_med_low)       |            |
| source                 | [Source](#source)                 |            |
| related_COAs           | ([RelatedCOA](#related_coa), ...) |            |

Stored instances will also receive the following fields, but MAY not be shared:

| Key     | Value         | Description                               |
| --- | --- | --- |
| type    | "campaign"    | Model type                                |
| created | [Time](#time) | Timestamp when object was created in CTIA |
| owner   | string        | String identifying the creating user      |

### References

STIX [CourseOfActionType](http://stixproject.github.io/data-model/1.2/coa/CourseOfActionType/)

<a name="exploit_target"/>
## Exploit Target

| Key                      | Value                                                  | Mandatory? |
| --- | --- | --- |
| id                       | [ID](#id)                                              | &#10003;   |
| title                    | string                                                 | &#10003;   |
| description              | (string, ...)                                          | &#10003;   |
| short_description        | (string, ...)                                          | &#10003;   |
| timestamp                | string                                                 | &#10003;   |
| version                  | string                                                 |            |
| tlp                      | [TLP](#tlp)                                            |            |
| vulnerability            | [Vulnerability](#vulnerability)                        |            |
| weakness                 | [Weakness](#weakness)                                  |            |
| configuration            | [Configuration](#configuration)                        |            |
| potential\_COAs          | ([RelatedCOAs](#related_coa), ...)                     |            |
| source                   | [Source](#source)                                      |            |
| related\_exploit_targets | ([RelatedExploitTarget](#related_exploit_target), ...) |            |

Stored instances will also receive the following fields, but MAY not be shared:

| Key     | Value         | Description                               |
| --- | --- | --- |
| type    | "COA"         | Model type                                |
| created | [Time](#time) | Timestamp when object was created in CTIA |
| owner   | string        | String identifying the creating user      |

<a name="configuration"/>
### Configuration

| Key                | Value         | Mandatory? |
| --- | --- | --- |
| description        | (string, ...) | &#10003;   |
| short\_description | (string, ...) |            |
| cce_id             | string        |            |

<a name="weakness"/>
### Weakness

| Key         | Value         | Mandatory? |
| --- | --- | --- |
| description | (string, ...) | &#10003;   |
| cwe_id      | string        |            |

<a name="vulnerability"/>
### Vulnerability

| Key                      | Value         | Mandatory? |
| --- | --- | --- |
| is\_known                | boolean       |            |
| is\_public\_acknowledged | boolean       |            |
| title                    | string        | &#10003;   |
| description              | (string, ...) | &#10003;   |
| short\_description       | (string, ...) |            |
| cve\_id                  | string        |            |
| osvdb\_id                | integer       |            |
| source                   | string        |            |
| discovered\_datetime     | [Time](#time) |            |
| published\_datetime      | [Time](#time) |            |
| affected_software        | (string, ...) |            |
| references               | (string, ...) |            |

### References

STIX [ExploitTargetType](http://stixproject.github.io/data-model/1.2/et/ExploitTargetType/)

<a name="feedback"/>
## Feedback

A positive, neutral or negative feedback on a Judgement

| Key       | Value                                      | Mandatory? |
| --- | --- | --- |
| id        | [ID](#id)                                  | &#10003;   |
| timestamp | [Time](#time)                              | &#10003;   |
| judgement | [JudgementReference](#judgement_reference) | &#10003;   |
| source    | string                                     |            |
| tlp       | [TLP](#tlp)                                |            |
| feedback  | -1 &#124; 0 &#124; 1                       | &#10003;   |
| reason    | string                                     | &#10003;   |

Stored instances will also receive the following fields, but MAY not be shared:

| Key     | Value         | Description                               |
| --- | --- | --- |
| type    | "feedback"    | Model type                                |
| created | [Time](#time) | Timestamp when object was created in CTIA |
| owner   | string        | String identifying the creating user      |

<a name="incident"/>
## Incident

| Key                  | Value                                          | Mandatory? |
| --- | --- | --- |
| id                   | [ID](#id)                                      | &#10003;   |
| title                | string                                         | &#10003;   |
| description          | (string, ...)                                  | &#10003;   |
| short\_description   | (string, ...)                                  | &#10003;   |
| timestamp            | [Time](#time)                                  | &#10003;   |
| confidence           | [HighMedLow](#high\_med_low)                   | &#10003;   |
| tlp                  | [TLP](#tlp)                                    |            |
| status               | [Status](#status)                              |            |
| version              | string                                         |            |
| incident\_time       | [IncidentTime](#incident\_time)                |            |
| categories           | ([IncidentCategory](#incident\_category), ...) |            |
| reporter             | [Source](#source)                              |            |
| responder            | [Source](#source)                              |            |
| coordinator          | [Source](#source)                              |            |
| victim               | string                                         |            |
| affected\_assets     | ([AffectedAsset](#affected\_asset), ...)       |            |
| impact\_assessment   | [ImpactAssessment](#impact\_assessment)        |            |
| sources              | [Source](#source)                              |            |
| security\_compromise | string                                         |            |
| discovery\_method    | [DiscoveryMethod](#discovery\_method)          |            |
| COA\_requested       | ([COARequested](#coa\_requested), ...)         |            |
| COA_taken            | ([COARequested](#coa\_requested), ...)         |            |
| contact              | [Source](#source)                              |            |
| history              | ([History](#history), ...)                     |            |
| related\_indicators  | ([RelatedIndicator](#related\_indicator), ...) |            |
| related\_observables | ([Observable](#observable), ...)               |            |
| leveraged\_TTPs      | [LeveragedTTPs](#leveraged\_ttps)              |            |
| attributed\_actors   | ([RelatedActor](#related\_actors), ...)        |            |
| related\_incidents   | ([RelatedIncident](#related\_incident), ...)   |            |
| intended\_effect     | [IntendedEffect](#intended_effect)             |            |

Stored instances will also receive the following fields, but MAY not be shared:

| Key     | Value         | Description                               |
| --- | --- | --- |
| type    | "incident"    | Model type                                |
| created | [Time](#time) | Timestamp when object was created in CTIA |
| owner   | string        | String identifying the creating user      |

<a name="history"/>
### History

| Key            | Value                          | Mandatory? |
| --- | --- | --- |
| action\_entry  | [COARequested](#coa_requested) |            |
| journal\_entry | string                         |            |

<a name="incident_time"/>
### IncidentTime

| Key                        | Value         | Mandatory? |
| --- | --- | --- |
| first\_malicious\_action   | [Time](#time) |            |
| intial\_compromoise        | [Time](#time) |            |
| first\_data\_exfiltration  | [Time](#time) |            |
| incident\_discovery        | [Time](#time) |            |
| incident\_opened           | [Time](#time) |            |
| containment\_achieved      | [Time](#time) |            |
| restoration\_achieved      | [Time](#time) |            |
| incident\_reported         | [Time](#time) |            |
| incident_closed            | [Time](#time) |            |

<a name="impact_assessment"/>
### ImpactAssessment

| Key                       | Value                                               | Mandatory? |
| --- | --- | --- |
| direct\_impact\_summary   | [DirectImpactSummary](#direct\_impact\_summary)     |            |
| indirect\_impact\_summary | [IndirectImpactSummary](#indirect\_impact\_summary) |            |
| total\_loss\_estimation   | [TotalLossEstimation](#total_loss_estimation)       |            |
| impact\_qualification     | [ImpactQualification](#impact_qualification)        |            |
| effects                   | ([Effect](#effect), ...)                            |            |

<a name="total_loss_estimation"/>
### TotalLossEstimation

| Key                                        | Value                               | Mandatory? |
| --- | --- | --- |
| initial\_reported\_total\_loss\_estimation | [LossEstimation](#loss\_estimation) |            |
| actual\_total\_loss\_estimation            | [LossEstimation](#loss\_estimation) |            |

<a name="loss_estimation"/>
### LossEstimation

| Key                 | Value  | Mandatory? |
| --- | --- | --- |
| amount              | number |            |
| iso\_currency\_code | number |            |

<a name="indirect_impact_summary"/>
### IndirectImpactSummary

| Key                              | Value                                       | Mandatory? |
| --- | --- | --- |
| loss\_of\_competitive\_advantage | [SecurityCompromise](#security\_compromise) |            |
| brand\_and\_market\_damage       | [SecurityCompromise](#security\_compromise) |            |
| increased\_operating\_costs      | [SecurityCompromise](#security\_compromise) |            |
| local\_and\_regulatory\_costs    | [SecurityCompromise](#security\_compromise) |            |

<a name="direct_impact_summary"/>
### DirectImpactSummary

| Key                            | Value                           | Mandatory? |
| --- | --- | --- |
| asset\_losses                  | [ImpactRating](#impact\_rating) |            |
| business\_mission\_distruption | [ImpactRating](#impact\_rating) |            |
| response\_and\_recovery\_costs | [ImpactRating](#impact\_rating) |            |

<a name="affected_asset"/>
### AffectedAsset

| Key                      | Value                                   | Mandatory? |
| --- | --- | --- |
| type                     | string                                  |            |
| description              | (string, ...)                           |            |
| ownership\_class         | [OwnershipClass](#ownership\_class)     |            |
| management\_class        | [ManagementClass](#management\_class)   |            |
| location\_class          | [LocationClass](#location\_class)       |            |
| property\_affected       | [PropertyAffected](#property\_affected) |            |
| identifying\_observables | ([Observable](#observable), ...)        |            |

<a name="property_affected"/>
### PropertyAffected

| Key                              | Value                                                       | Mandatory? |
| --- | --- | --- |
| property                         | [LossProperty](#loss\_property)                             |            |
| description\_of\_effect          | string                                                      |            |
| type\_of\_availability\_loss     | string                                                      |            |
| duration\_of\_availability\_loss | [LossDuration](#loss\_duration)                             |            |
| non\_public\_data\_compromised   | [NonPublicDataCompromised](#non\_public\_data\_compromised) |            |

<a name="non_public_data_compromised"/>
### NontPublicDataCompromised

| Key                  | Value                                       | Mandatory? |
| --- | --- | --- |
| security\_compromise | [SecurityCompromise](#security\_compromise) | &#10003;   |
| data\_encrypted      | boolean                                     |            |

### References

STIX [IncidentType](http://stixproject.github.io/data-model/1.2/incident/IncidentType/)

<a name="indicator"/>
## Indicator

An indicator is a test, or a collection of judgements that define
criteria for identifying the activity, or presence of malware, or
other unwanted software.

We follow the
[STiX IndicatorType](http://stixproject.github.io/data-model/1.2/indicator/IndicatorType/)
closely, with the exception of not including observables within the
indicator, and preferring a _specification_ object encoded in JSON as
opposed to an opaque _implemntation_ block.

Additional, you will want to either define judgements against
Observables that are linked to this indicator, with the ID in the
_indicators_ field of those Judgements, or you can provide a
_specification_ value.

JSON Example:

```json
{
  "description": ["Indicator example"],
  "producer": "test",
  "observable": {
    "value": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "type": "sha256"},
  "type": ["File Hash Watchlist"],
  "title": "string",
  "expires": "2016-02-22T20:49:30.766Z",
  "confidence": "High",
  "valid_time_position": {
    "start_time": "2016-02-10T20:49:30.767Z",
    "end_time": "2016-02-15T20:49:30.767Z"}
}
```

| Key | Value | Mandatory? | Description |
| --- | --- | --- | --- |
| id | [ID](#id) | &#10003; | |
| title | string | &#10003; | A short and hopefully descriptive and unique title |
| description | (string, ...) | &#10003; | A longer, in-depth description of the indicator |
| short\_description | (string, ...) | &#10003; | A short sentence or two describing the indicator |
| alternate\_ids | (string, ...) | | |
| tlp | [TLP](#tlp) | |
| version | number | | |
| negate | boolean | | |
| indicator_type | ([IndicatorType](#indicator_type), ...) | | The indicator type, such as URL Watchlist, or Malware Artifact, or Malware Behavior |
| valid\_time\_position | [ValidTime](#valid\_time) | | |
| observable | [Observable](#observable) | | |
| composite\_indicator\_expression | [CompositeIndicatorExpression](#composite\_indicator\_expression) | | |
| indicated\_TTP | [RelatedTTP](#related\_ttp) | | A list of the IDs of TTPs objects related to this indicator |
| likely\_impact | string | | The impact of malware, High, Medium, Low or None |
| suggested\_COAs | ([RelatedCOAs](#related\_coas), ...) | | |
| confidence | [HighMedLow](#high_med_low) | | |
| sightings | ([Sighting](#sighting), ...) | | |
| related\_indicators | ([RelatedIndicator](#related\_indicator), ...) | | One or more indicator related to this one. | |
| related\_campaigns | ([RelatedCampaign](#related\_campaign), ...) | | One or more campaigns related to this indicator. | |
| related\_COAs | ([RelatedCOA](#related\_coa), ...) | | One or more COAs related to this indicator. | |
| kill\_chain\_phases | (string, ...) | | One or more kill chain phases, like "Delivery" | |
| test_mechanisms | (string, ...) | | One or more products or tools that can use the data in this indicator to perform a test for it's presence on a host or network | |
| expires | [Time](#time) | | When the indicator is no longer valid | |
| producer | string | &#10003; | An identifier of the system or person that produced this indicator | |
| specifications | ([Specification](#specification), ...) | | |

Stored instances will also receive the following fields, but MAY not be shared:

| Key     | Value         | Description                               |
| --- | --- | --- |
| type    | "indicator"   | Model type                                |
| created | [Time](#time) | Timestamp when object was created in CTIA |
| owner   | string        | String identifying the creating user      |

<a name="specification"/>
### Specification

One of the following structures:

- [JudgementSpecification](#judgement_specification)
- [ThreatBrainSpecification](#threat_brain_specification)
- [SnortSpecification](#snort_specification)
- [SIOCSpecification](#sioc_specification)
- [OpenIOCSpecification](#open_ioc_specification)

<a name="sighting"/>
### Sighting

| Key                 | Value                            | Mandatory? |
| --- | --- | --- |
| timestamp           | [Time](#time)                    |            |
| source              | [Source](#source)                |            |
| tlp                 | [TLP](#tlp)                      |            |
| reference           | [URI](#uri)                      |            |
| confidence          | [HighMedLow](#high_med_low)      |            |
| description         | (string, ...)                    |            |
| related_observables | ([Observable](#observable), ...) |            |

<a name="open_ioc_specification"/>
### OpenIOCSpecification

| Key      | Value     | Mandatory? |
| --- | --- | --- |
| type     | "OpenIOC" | &#10003;   |
| open_IOC | string    | &#10003;   |

<a name="sioc_specification"/>
### SIOCSpecification

| Key  | Value  | Mandatory? |
| --- | --- | --- |
| type | "SIOC" | &#10003;   |
| SIOC | string | &#10003;   |

<a name="snort_specification"/>
### SnortSpecification

| Key       | Value   | Mandatory? |
| --- | --- | --- |
| type      | "Snort" | &#10003;   |
| snort_sig | string  | &#10003;   |

<a name="threat_brain_specification"/>
### ThreatBrainSpecification

| Key       | Value         | Mandatory? |
| --- | --- | --- |
| type      | "ThreatBrain" | &#10003;   |
| query     | string        | &#10003;   |
| variables | (string, ...) | &#10003;   |

<a name="judgement_specification"/>
### JudgementSpecification

| Key                  | Value                                              | Mandatory? |
| --- | --- | --- |
| type                 | "Judgement"                                        | &#10003;   |
| judgements           | ([JudgementReference](#judgement\_reference), ...) | &#10003;   |
| required\_judgements | ([JudgementReference](#judgement_reference), ...)  | &#10003;   |

<a name="valid_time"/>
### ValidTime

| Key         | Value         | Mandatory? |
| --- | --- | --- |
| start_time  | [Time](#time) |            |
| end\_time   | [Time](#time) |            |

### References

STIX [IndicatorType](http://stixproject.github.io/data-model/1.2/indicator/IndicatorType/)

<a name="judgement"/>
## Judgement

A statement about the intent of an Observable.  Since a core goal of
the CTIA is to provide a simple verdict service, these judgements are
the basis for the returned verdicts.  These are also the primary means
by which users of the CTIA go from observables on their system, to the
indicators and threat intelligence data in CTIA.

| Key               | Value                                         | Mandatory? | default       | description                                                        |
| --- | --- | --- | --- | --- |
| id                | [ID](#id)                                     | &#10003;   |               |                                                                    |
| observable        | [Observable](#observable)                     | &#10003;   |               |                                                                    |
| disposition       | [DispositionNumber](#disposition\_number)     | &#10003;   |               |                                                                    |
| source            | [Source](#source)                             | &#10003;   |               |                                                                    |
| priority          | integer (0-100)                               | &#10003;   | user specific |                                                                    |
| confidence        | [HighMedLow](#high_med_low)                   | &#10003;   | 100           |                                                                    |
| severity          | integer                                       | &#10003;   | 100           |                                                                    |
| timestamp         | [Time](#time)                                 | &#10003;   | POST time     |                                                                    |
| tlp               | [TLP](#tlp)                                   |            |               |                                                                    |
| reason            | string                                        |            |               | short description of why the judgement was made                    |
| disposition\_name | [DispositionName](#disposition\_name)         |            |               |                                                                    |
| expires           | [Time](#time)                                 |            | Jan 1 2535    |                                                                    |
| source\_uri       | [URI](#uri)                                   |            |               | link where a user can see what the source thinks of the observable |
| reason\_uri       | [URI](#uri)                                   |            |               | link where a user can get information supporting the reason        |
| indicators        | ([RelatedIndicator](#related_indicator), ...) |            |               |                                                                    |

Stored instances will also receive the following fields, but MAY not be shared:

| Key     | Value         | Description                               |
| --- | --- | --- |
| type    | "judgement"   | Model type                                |
| created | [Time](#time) | Timestamp when object was created in CTIA |
| owner   | string        | String identifying the creating user      |

### Judgement Example

Here is a quick example using curl:

```shell
curl -X POST --header "Content-Type: application/json" \
--header "Accept: application/json" \
-d '{"observable":{"type":"ip", "value":"127.0.0.1"},
     "disposition":2, "source":"internet chat forum"}' "http://localhost:3000/ctia/judgements"
```

<a name="ttp"/>
## Tools, Techniques, & Procedures (TTP)

| Key                    | Value                                                  | Mandatory? |
| --- | --- | --- |
| id                     | [ID](#id)                                              | &#10003;   |
| title                  | string                                                 | &#10003;   |
| description            | (string, ...)                                          | &#10003;   |
| short_description      | (string, ...)                                          | &#10003;   |
| timestamp              | [Time](#time)                                          | &#10003;   |
| tlp                                        | [TLP](#tlp)                                    |            |
| version                | string                                                 |            |
| intended_effect        | [IntendedEffect](#intended_effect)                     |            |
| behavior               | [Behavior](#behavior)                                  |            |
| resources              | [Resource](#resource)                                  |            |
| victim\_targeting      | [VictimTargeting](#victim_targeting)                   |            |
| exploit\_targeting     | ([RelatedExploitTarget](#related_exploit_target), ...) |            |
| related\_TTPs          | ([RelatedTTP](#related\_ttp), ...)                     |            |
| source                 | [Source](#source)                                      |            |
| ttp_type               | string                                                 | &#10003;   |
| expires                | [Time](#time)                                          | &#10003;   |
| indicators             | ([IndicatorReference](#indicator_reference), ...)      | &#10003;   |

Stored instances will also receive the following fields, but MAY not be shared:

| Key     | Value         | Description                               |
| --- | --- | --- |
| type    | "ttp"         | Model type                                |
| created | [Time](#time) | Timestamp when object was created in CTIA |
| owner   | string        | String identifying the creating user      |

<a name="victim_targeting"/>
### VictimTargeting

| Key                   | Value                                        | Mandatory? |
| --- | --- | --- |
| indentity             | [Identity](#identity)                        |            |
| targeted\_systems     | ([SystemType](#system\_type), ...)           |            |
| targeted\_information | ([InformationType](#information\_type), ...) |            |
| targeted_observables  | ([Observable](#observable), ..)              |            |

<a name="resource"/>
### Resource

| Key            | Value                             | Mandatory? |
| --- | --- | --- |
| tools          | ([Tool](#tool), ...)              |            |
| infrastructure | [Infrastructure](#infrastructure) |            |
| providers      | [Identity](#identity)             |            |

<a name="infrastructure"/>
### Infrastructure

| Key         | Value                                              | Mandatory? |
| --- | --- | --- |
| description | string                                             | &#10003;   |
| type        | [AttackerInfrastructure](#attacker_infrastructure) | &#10003;   |

<a name="behavior"/>
### Behavior

| Key              | Value                                        | Mandatory? |
| --- | --- | --- |
| attack\_patterns | ([AttackPattern](#attack\_pattern), ...)     |            |
| malware\_type    | ([MalwareInstance](#malware\_instance), ...) |            |

<a name="malware_instance"/>
### MalwareInstance

| Key         | Value                               | Mandatory? |
| --- | --- | --- |
| description | string                              | &#10003;   |
| type        | ([MalwareType](#malware_type), ...) | &#10003;   |

<a name="attack_pattern"/>
### AttackPattern

The capec_id field below refers to an entry in the Common Attack
Pattern Enumeration and Classification
[CAPEC](https://capec.mitre.org).

| Key         | Value  | Mandatory? |
| --- | --- | --- |
| description | string | &#10003;   |
| capec_id    | string |            |

### References

STIX [TTPType](http://stixproject.github.io/data-model/1.2/ttp/TTPType)

<a name="verdict"/>
## Verdict

The Verdict is chosen from all of the Judgements on that Observable which
have not yet expired.  The highest priority Judgement becomes the
active verdict.  If there is more than one Judgement with that
priority, than Clean disposition has priority over all others, then
Malicious disposition, and so on down to Unknown.

| Key               | Value                                       | Mandatory? |
| --- | --- | --- |
| disposition       | [DispositionNumber](#disposition\_number)   | &#10003;   |
| judgement         | [JudgementReference](#judgement\_reference) |            |
| disposition\_name | [DispositionName](#disposition_name)        |            |

The disposition_name field is optional, but is intended to be show to
a user.  Applications must therefore remember the mapping of numbers
to human words.

Stored instances will also receive the following fields, but MAY not be shared:

| Key     | Value         | Description                               |
| --- | --- | --- |
| type    | "verdict"     | Model type                                |
| created | [Time](#time) | Timestamp when object was created in CTIA |
| owner   | string        | String identifying the creating user      |

### Example Verdict

```json
    { "judgement": "judgmeent-de305d54-75b4-431b-adb2-eb6b9e546014",
      "disposition": 1,
      "disposition_name": "Clean" }
```

<a name="shared_structures"/>
## Shared Structures

<a name="id"/>
### ID

string

The ID field is related to the [Referene](#reference) field.  IDs are
strings of the form: type-<128bitUUID>, for example
`judgment-de305d54-75b4-431b-adb2-eb6b9e546014` for a
[Judgement](#judgement).

The CTIA _ID_ field compares to the STIX _id_ field.  The optional STIX
_idref_ field is not used.

<a name="uri"/>
### URI

string

<a name="tlp"/>
### TLP

Document marking as a string using [Traffic Light Protocol](https://www.us-cert.gov/tlp) convention

<a name="time"/>
### Time

Time is stored internally as a java.util.Date object, serialized as a
string the field should follow the rules of the ISO8601 standard.

<a name="time_structure"/>
### TimeStructure

| Key            | Value         | Mandatory? |
| --- | --- | --- |
| start\_time    | [Time](#time) |            |
| end\_time      | [Time](#time) |            |
| produced\_time | [Time](#time) |            |
| received\_time | [Time](#time) |            |

<a name="tool"/>
### Tool

| Key          | Value                               | Mandatory? |
| --- | --- | --- |
| description  | string                              | &#10003;   |
| type         | [AttackToolType](#attack_tool_type) |            |
| references   | (string, ...)                       |            |
| vendor       | string                              |            |
| version      | string                              |            |
| service_pack | string                              |            |

<a name="source"/>
### Source
| Key                   | Value                                 | Mandatory? |
| --- | --- | --- |
| description           | string                                | &#10003;   |
| identity              | string                                |            |
| role                  | string                                |            |
| contributing\_sources | [SourceReference](#source\_reference) |            |
| time                  | [TimeStructure](#time_structure)      |            |
| tools                 | ([Tool](#tool), ..)                   |            |

<a name="contributor"/>
### Contributor

| Key                    | Value         | Mandatory? |
| --- | --- | --- |
| role                   | string        |            |
| name                   | string        |            |
| email                  | string        |            |
| phone                  | string        |            |
| organization           | string        |            |
| date                   | [Time](#time) |            |
| contribution\_location | string        |            |

<a name="related_identity"/>
### RelatedIdentity

| Key                 | Value                                     | Mandatory? |
| --- | --- | --- |
| confidence          | [HighMedLow](#high\_med\_low)             |            |
| information\_source | [Source](#source)                         |            |
| relationship        | string                                    |            |
| identity            | [IdentityReference](#identity\_reference) | &#10003;   |

<a name="identity"/>
### Identity
| Key                 | Value                                 | Mandatory? |
| --- | --- | --- |
| description         | string                                | &#10003;   |
| related\_identities | [RelatedIdentity](#related\_identity) | &#10003;   |

<a name="activity"/>
### Activity

| Key         | Value         | Mandatory? |
| --- | --- | --- |
| date_time   | [Time](#time) | &#10003;   |
| description | string        | &#10003;   |

<a name="observable"/>
### Observable

An observable is a simple, atomic value that denotes an entity which
as an identity that is stable enough to be attributed an intent or
nature.  These do not exist as objects within the CTIA storage model,
you never create an observable.

| Key   | Value                              | Mandatory? |
| --- | --- | --- |
| value | string                             | &#10003;   |
| type  | [ObservableType](#observable_type) | &#10003;   |

#### Observable Examples

|  Type | Representation | Example (JSON) |
| ------- | --------------- | ------- |
| ip | The IP address of a host in normal form | {"type": "ip", "value": "192.168.1.1"} |
| ipv6 | IPv6 address of a host, the format is x\:x\:x\:x\:x\:x\:x\:x, where the 'x's are the hexadecimal values of the eight 16-bit pieces of the address.  Letters must be lowercase | {"type": "ipv6", "value": "fedc:ba98:7654:3210:fedc:ba98:7654:3210"} |
| device | Hex device address, letters must be lowercase. | {"type": "mac", "00:0a:27:02:00:53:24:c4"} |
| user | A unique identifier string, such as SSO login | {"type": "user", "value": "salesguy"} |
| domain | a hostname or domain name, like "foo.com" | {"type": "domain", "value": "badsite.com"} |
| sha256 | A hex representation of the SHA256 of a file, letters lowercased. | {"type": "sha256", "value": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"} |
| md5 | A hex repreentation of the MD5 of a file, letters lowercased. | {"type": "md5", "value": "d41d8cd98f00b204e9800998ecf8427e"} |
| sha1 | a hex representation of the SHA1 of a file, letters lowercased. | {"type": "sha1", "value": "da39a3ee5e6b4b0d3255bfef95601890afd80709"} |
| url | A string containing a URL | {"type": "url", "value": "https://panacea.threatgrid.com"} |

<a name="dispositions"/>
### Dispositions

| Integer | Value      |
| --- | --- |
|       1 | Clean      |
|       2 | Malicious  |
|       3 | Suspicious |
|       4 | Common     |
|       5 | Unknown    |

<a name="disposition_number"/>
### DispositionNumber

An integer from the [Dispositions](#dispositions) table above.

<a name="disposition_name"/>
### DispositionName

A value from the [Dispositions](#dispositions) table above.

<a name="relationship_structures"/>
## Relationship Structures

There are two types of relationship structures:

1. References; see [Reference](#reference) below.
2. Related object structures, which contain references along with
   meta data about the relationship.

<a name="reference"/>
<a name="actor_reference"/>
<a name="campaign_reference"/>
<a name="coa_reference"/>
<a name="exploit_target_reference"/>
<a name="feedback_reference"/>
<a name="identity_reference"/>
<a name="incident_reference"/>
<a name="indicator_reference"/>
<a name="judgement_reference"/>
<a name="source_reference"/>
<a name="ttp_reference"/>
<a name="verdict_reference"/>
### Reference

A string identifier that points to a stored data structure.  Named
references in this document (in the form of _FooReference_) indicate
the type of object that is being pointed to.

A reference may be an [ID](#id) when the object is available on the
same CTIA instance.  When the reference points to a remote CTIA
instance, the reference must be full URL, such as
`https://ctia.someplace.else.org/judgement/de305d54-75b4-431b-adb2-eb6b9e546014`
and that object should be retrievable at that URI.

<a name="related_indicator"/>
### RelatedIndicator

| Key          | Value                                      | Mandatory? |
| --- | --- | --- |
| type         | "indicator"                                | &#10003;   |
| confidence   | [HighMedLow](#high_med_low)                |            |
| source       | [Source](#source)                          |            |
| relationship | string                                     |            |
| indicator    | [IndicatorReference](#indicator_reference) | &#10003;   |

<a name="related_actor"/>
### RelatedActor

| Key          | Value                              | Mandatory? |
| --- | --- | --- |
| type         | "actor"                            | &#10003;   |
| confidence   | [HighMedLow](#high\_med\_low)      |            |
| source       | [Source](#source)                  |            |
| relationship | string                             |            |
| actor        | [ActorReference](#actor_reference) | &#10003;   |

<a name="related_campaign"/>
### RelatedCampaign

| Key          | Value                                    | Mandatory? |
| --- | --- | --- |
| type         | "campaign"                               | &#10003;   |
| confidence   | [HighMedLow](#high_med_low)              |            |
| source       | [Source](#source)                        |            |
| relationship | string                                   |            |
| campaign     | [CampaignReference](#campaign_reference) | &#10003;   |

<a name="related_coa"/>
### RelatedCOA

| Key          | Value                          | Mandatory? |
| --- | --- | --- |
| type         | "COA"                          | &#10003;   |
| confidence   | [HighMedLow](#high\_med\_low)  |            |
| source       | [Source](#source)              |            |
| relationship | string                         |            |
| COA          | [COAReference](#coa_reference) | &#10003;   |

<a name="coa_requested"/>
### COARequested

| Key          | Value                          | Mandatory? |
| --- | --- | --- |
| time         | [Time](#time)                  |            |
| contributors | [Contributors](#contributors)  |            |
| COA          | [COAReference](#coa_reference) | &#10003;   |

<a name="related_exploit_target"/>
### RelatedExploitTarget

| Key             | Value                                                 | Mandatory? |
| --- | --- | --- |
| type            | "exploit-target"                                      | &#10003;   |
| confidence      | [HighMedLow](#high_med_low)                           |            |
| source          | [Source](#source)                                     |            |
| relationship    | string                                                |            |
| exploit\_target | [ExploitTargetReference](#exploit\_target\_reference) | &#10003;   |

<a name="related_incident"/>
### RelatedIncident

| Key          | Value                                    | Mandatory? |
| --- | --- | --- |
| type         | "incident"                               | &#10003;   |
| confidence   | [HighMedLow](#high_med_low)              |            |
| source       | [Source](#source)                        |            |
| relationship | string                                   |            |
| incident     | [IncidentReference](#incident_reference) | &#10003;   |

<a name="composite_indicator_expression"/>
### CompositeIndicatorExpression

| Key        | Value                                             | Mandatory? |
| --- | --- | --- |
| operator   | "and" &#124; "or" &#124; "not"                    | &#10003;   |
| indicators | ([IndicatorReference](#indicator_reference), ...) | &#10003;   |

<a name="related_ttp"/>
### RelatedTTP

| Key          | Value                          | Mandatory? |
| --- | --- | --- |
| type         | "ttp"                          | &#10003;   |
| confidence   | [HighMedLow](#high_med_low)    |            |
| source       | [Source](#source)              |            |
| relationship | string                         |            |
| TTP          | [TTPReference](#ttp_reference) | &#10003;   |

<a name="vocabularies"/>
## Vocabularies

Each vocabulary represents an enumerated type, meaning you must pick
one of the possible values for a given field.

<a name="attacker_infrastructure"/>
### AttackerInfrastructure

- Anonymization
- Anonymization - Proxy
- Anonymization - TOR Network
- Anonymization - VPN
- Communications
- Communications - Blogs
- Communications - Forums
- Communications - Internet Relay Chat
- Communications - Micro-Blogs
- Communications - Mobile Communications
- Communications - Social Networks
- Communications - User-Generated Content Websites
- Domain Registration
- Domain Registration - Dynamic DNS Services
- Domain Registration - Legitimate Domain Registration Services
- Domain Registration - Malicious Domain Registrars
- Domain Registration - Top-Level Domain Registrars
- Hosting
- Hosting - Bulletproof / Rogue Hosting
- Hosting - Cloud Hosting
- Hosting - Compromised Server
- Hosting - Fast Flux Botnet Hosting
- Hosting - Legitimate Hosting
- Electronic Payment Methods

<a name="attack_tool_type"/>
### AttackToolType

- Malware
- Penetration Testing
- Port Scanner
- Traffic Scanner
- Vulnerability Scanner
- Application Scanner
- Password Cracking

<a name="campaign_status"/>
### CampaignStatus

- Ongoing
- Historic
- Future

<a name="coa_stage"/>
### COAStage
- Remedy
- Response

<a name="coa_type"/>
### COAType

- Perimeter Blocking
- Internal Blocking
- Redirection
- Redirection (Honey Pot)
- Hardening
- Patching
- Eradication
- Rebuilding
- Training
- Monitoring
- Physical Access Restrictions
- Logical Access Restrictions
- Public Disclosure
- Diplomatic Actions
- Policy Actions
- Other

<a name="discovery_method"/>
### DiscoveryMethod

- Agent Disclosure
- External - Fraud Detection
- Monitoring Service
- Law Enforcement
- Customer
- Unrelated Party
- Audit
- Antivirus
- Incident Response
- Financial Audit
- Internal - Fraud Detection
- HIPS
- IT Audit
- Log Review
- NIDS
- Security Alarm
- User
- Unknown

<a name="effect"/>
### Effect

- Brand or Image Degradation
- Loss of Competitive Advantage
- Loss of Competitive Advantage - Economic
- Loss of Competitive Advantage - Military
- Loss of Competitive Advantage - Political
- Data Breach or Compromise
- Degradation of Service
- Destruction
- Disruption of Service / Operations
- Financial Loss
- Loss of Confidential / Proprietary Information or Intellectual Property
- Regulatory, Compliance or Legal Impact
- Unintended Access
- User Data Loss

<a name="high_med_low"/>
### HighMedLow

- Low
- Medium
- High
- None
- Unknown

<a name="impact_qualification"/>
### ImpactQualification

- Insignificant
- Distracting
- Painful
- Damaging
- Catastrophic
- Unknown

<a name="impact_rating"/>
### ImpactRating

- None
- Minor
- Moderate
- Major
- Unknown

<a name="incident_category"/>
### IncidentCategory

- Exercise/Network Defense Testing"
- Unauthorized Access
- Denial of Service
- Malicious Code
- Improper Usage
- Scans/Probes/Attempted Access
- Investigation

<a name="indicator_type"/>
### IndicatorType

- Malicious E-mail
- IP Watchlist
- File Hash Watchlist
- Domain Watchlist
- URL Watchlist
- Malware Artifacts
- C2
- Anonymization
- Exfiltration
- Host Characteristics
- Compromised PKI Certificate
- Login Name
- IMEI Watchlist
- IMSI Watchlist

<a name="information_type"/>
### InformationType

- Information Assets
- Information Assets - Corporate Employee Information
- Information Assets - Customer PII
- Information Assets - Email Lists / Archives
- Information Assets - Financial Data
- Information Assets - Intellectual Property
- Information Assets - Mobile Phone Contacts
- Information Assets - User Credentials
- Authentication Cookies

<a name="intended_effect"/>
### IntendedEffect

- Advantage
- Advantage - Economic
- Advantage - Military
- Advantage - Political
- Theft
- Theft - Intellectual Property
- Theft - Credential Theft
- Theft - Identity Theft
- Theft - Theft of Proprietary Information
- Account Takeover
- Brand Damage
- Competitive Advantage
- Degradation of Service
- Denial and Deception
- Destruction
- Disruption
- Embarrassment
- Exposure
- Extortion
- Fraud
- Harassment
- ICS Control
- Traffic Diversion
- Unauthorized Access

<a name="location_class"/>
### LocationClass

- Internally-Located
- Externally-Located
- Co-Located
- Mobile
- Unknown

<a name="loss_duration"/>
### LossDuration

- Permanent
- Weeks
- Days
- Hours
- Minutes
- Seconds
- Unknown

<a name="loss_property"/>
### LossProperty

- Confidentiality
- Integrity
- Availability
- Accountability
- Non-Repudiation

<a name="malware_type"/>
### MalwareType

- Automated Transfer Scripts
- Adware
- Dialer
- Bot
- Bot - Credential Theft
- Bot - DDoS
- Bot - Loader
- Bot - Spam
- DoS/ DDoS
- DoS / DDoS - Participatory
- DoS / DDoS - Script
- DoS / DDoS - Stress Test Tools
- Exploit Kit
- POS / ATM Malware
- Ransomware
- Remote Access Trojan
- Rogue Antivirus
- Rootkit

<a name="management_class"/>
### ManagementClass

- Internally-Managed
- Externally-Management
- CO-Managment
- Unkown

<a name="motivation"/>
### Motivation

- Ideological
- Ideological - Anti-Corruption
- Ideological - Anti-Establishment
- Ideological - Environmental
- Ideological - Ethnic / Nationalist
- Ideological - Information Freedom
- Ideological - Religious
- Ideological - Security Awareness
- Ideological - Human Rights
- Ego
- Financial or Economic
- Military
- Opportunistic
- Political

<a name="observable_type"/>
### ObservableType

- ip
- ipv6
- device
- user
- domain
- sha256
- md5
- sha1
- url

<a name="ownership_class"/>
### OwnershipClass

- Internally-Owned
- Employee-Owned
- Partner-Owned
- Customer-Owned
- Unknown

<a name="scope"/>
### Scope

- inclusive
- exclusive

<a name="security_compromise"/>
### SecurityCompromise

- Yes
- Suspected
- No
- Unknown

<a name="sophistication"/>
### Sophistication

- Innovator
- Expert
- Practitioner
- Novice
- Aspirant

<a name="status"/>
### Status

- New
- Open
- Stalled
- Containment Achieved
- Restoration Achieved
- Incident Reported
- Closed
- Rejected
- Deleted

<a name="system_type"/>
### SystemType

- Enterprise Systems
- Enterprise Systems - Application Layer
- Enterprise Systems - Database Layer
- Enterprise Systems - Enterprise Technologies and Support Infrastructure
- Enterprise Systems - Network Systems
- Enterprise Systems - Networking Devices
- Enterprise Systems - Web Layer
- Enterprise Systems - VoIP
- Industrial Control Systems
- Industrial Control Systems - Equipment Under Control
- Industrial Control Systems - Operations Management
- Industrial Control Systems - Safety, Protection and Local Control
- Industrial Control Systems - Supervisory Control
- Mobile Systems
- Mobile Systems - Mobile Operating Systems
- Mobile Systems - Near Field Communications
- Mobile Systems - Mobile Devices
- Third-Party Services
- Third-Party Services - Application Stores
- Third-Party Services - Cloud Services
- Third-Party Services - Security Vendors
- Third-Party Services - Social Media
- Third-Party Services - Software Update
- Users
- Users - Application And Software
- Users - Workstation
- Users - Removable Media

<a name="threat_actor_type"/>
### ThreatActorType

- Cyber Espionage Operations
- Hacker
- Hacker - White hat
- Hacker - Gray hat
- Hacker - Black hat
- Hacktivist
- State Actor / Agency
- eCrime Actor - Credential Theft Botnet Operator
- eCrime Actor - Credential Theft Botnet Service
- eCrime Actor - Malware Developer
- eCrime Actor - Money Laundering Network
- eCrime Actor - Organized Crime Actor
- eCrime Actor - Spam Service
- eCrime Actor - Traffic Service
- eCrime Actor - Underground Call Service
- Insider Threat
- Disgruntled Customer / User
