# Data Structures

##### Table of Contents

- [Actor](#actor)
- [Campaign](#campaign)
- [Course of Action](#coa)
- [Exploit Target](#exploit_target)
- [Incident](#incident)
- [Indicator](#indicator)
- [Tools, Techniques, & Procedures](#ttp)
- [Shared Structures](#shared_structures)
- [Relationship Structures](#relationship_structures)
- [Vocabularies](#vocabularies)

<a name="actor"/>
## Actor

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
timestamp | [Time](#time) | &#10003;
source | [Source](#source) |
identity | [Identity](#identity) |
type | [ThreatActorType](#threat_actor_type) | &#10003;
motivation | [Motivation](#motivation) |
sophistication | [Sophistication](#sophistication) |
intended_effect | [IntendedEffect](#intended_effect) |
planning_and_operational_support | string |
observed_TTPs | [RelatedTTPs](#related_ttps) |
associated_campaigns | [AssociatedCampaigns](#associated_campaigns) |
associated_actors | [AssociatedActors](#associated_actors) |
confidence | [HighMedLow](#high_med_low) |
expires | [Time](#time) | &#10003;

### References

STIX [ThreatActorType](http://stixproject.github.io/data-model/1.2/ta/ThreatActorType/)

<a name="campaign"/>
## Campaign

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
timestamp | [Time](#time) | &#10003;
version | string |
names | string |
intended_effect | [IntendedEffect](#intended_effect) |
status | [CampaignStatus](#campaign_status) |
related_TTPs | [RelatedTTPs](#related_ttps) |
related_incidents | [RelatedIncidents](#related_incidents) |
attribution | [AttributedActors](#attributed_actors) |
associated_campaigns | [AssociatedCampaigns](#associated_campaigns) |
confidence | [HighMedLow](#high_med_low) |
activity | [Activity](#activity) |
source | [Source](#source) |
type | string | &#10003;
expires | [Time](#time) | &#10003;
indicators | [RelatedIndicators](#related_indicators) | &#10003;

### References

STIX [CampaignType](http://stixproject.github.io/data-model/1.2/campaign/CampaignType/)

<a name="coa"/>
## Course of Action (COA)

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
timestamp | [Time](#time) |
stage | [COAStage](#coa_stage) |
type | [COAType](#coa_type) |
objective | (string, ...) |
impact | string |
cost | [HighMedLow](#high_med_low) |
efficacy | [HighMedLow](#high_med_low) |
source | [Source](#source) |
related_COAs | [RelatedCOAs](#related_coas) |

### References

STIX [CourseOfActionType](http://stixproject.github.io/data-model/1.2/coa/CourseOfActionType/)

<a name="exploit_target"/>
## Exploit Target

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
timestamp | string | &#10003;
version | string |
vulnerability | [Vulnerability](#vulnerability) |
weakness | [Weakness](#weakness) |
configuration | [Configuration](#configuration) |
potential_COAs | [PotentialCOAs](#potential_coas) |
source | [Source](#source) |
related_exploit_targets | [RelatedExploitTargets](#related_exploit_targets) |

<a name="configuration"/>
### Configuration

Key | Value | Mandatory?
--- | --- | ---
description | (string, ...) | &#10003;
short_description | (string, ...) |
cce_id | string |

<a name="weakness"/>
### Weakness

Key | Value | Mandatory?
--- | --- | ---
description | (string, ...) | &#10003;
cwe_id | string |

<a name="vulnerability"/>
### Vulnerability

Key | Value | Mandatory?
--- | --- | ---
is_known | boolean |
is_public_acknowledged | boolean |
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) |
cve_id | string |
osvdb_id | integer |
source | string |
discovered_datetime | [Time](#time) |
published_datetime | [Time](#time) |
affected_software | (string, ...) |
references | (string, ...) |

### References

STIX [ExploitTargetType](http://stixproject.github.io/data-model/1.2/et/ExploitTargetType/)

<a name="incident"/>
## Incident

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
timestamp | [Time](#time)  | &#10003;
confidence | [HighMedLow](#high_med_low) | &#10003;
status | [Status](#status) |
version | string |
incident_time | [IncidentTime](#incident_time) |
categories | ([IncidentCategory](#incident_category), ...) |
reporter | [Source](#source) |
responder | [Source](#source) |
coordinator | [Source](#source) |
victim | string |
affected_assets | ([AffectedAsset](#affected_asset), ...) |
impact_assessment | [ImpactAssessment](#impact_assessment) |
sources | [Source](#source) |
security_compromise | string |
discovery_method | [DiscoveryMethod](#discovery_method) |
coa_requested | ([COARequested](#coa_requested), ...) |
coa_taken | ([COARequested](#coa_requested), ...) |
contact | [Source](#source) |
history | ([History](#history), ...) |
related_indicators | [RelatedIndicators](#related_indicators) |
related_observables | [RelatedObservables](#related_observables) |
leveraged_TTPs | [LeveragedTTPs](#leveraged_ttps) |
attributed_actors | [AttributedActors](#attributed_actors) |
related_incidents | [RelatedIncidents](#related_incidents) |
intended_effect | [IntendedEffect](#intended_effect) |

<a name="history"/>
### History

Key | Value | Mandatory?
--- | --- | ---
action_entry | [COARequested](#coa_requested) |
journal_entry | string |

<a name="incident_time"/>
### IncidentTime

Key | Value | Mandatory?
--- | --- | ---
first_malicious_action | [Time](#time) |
intial_compromoise | [Time](#time) |
first_data_exfiltration | [Time](#time) |
incident_discovery | [Time](#time) |
incident_opened | [Time](#time) |
containment_achieved | [Time](#time) |
restoration_achieved | [Time](#time) |
incident_reported | [Time](#time) |
incident_closed | [Time](#time) |

<a name="impact_assessment"/>
### ImpactAssessment

Key | Value | Mandatory?
--- | --- | ---
direct_impact_summary | [DirectImpactSummary](#direct_impact_summary) |
indirect_impact_summary | [IndirectImpactSummary](#indirect_impact_summary) |
total_loss_estimation | [TotalLossEstimation](#total_loss_estimation) |
impact_qualification | [ImpactQualification](#impact_qualification) |
effects | ([Effect](#effect), ...) |

<a name="total_loss_estimation"/>
### TotalLossEstimation

Key | Value | Mandatory?
--- | --- | ---
initial_reported_total_loss_estimation | [LossEstimation](#loss_estimation) |
actual_total_loss_estimation | [LossEstimation](#loss_estimation) |

<a name="loss_estimation"/>
### LossEstimation

Key | Value | Mandatory?
--- | --- | ---
amount | number |
iso_currency_code | number |

<a name="indirect_impact_summary"/>
### IndirectImpactSummary

Key | Value | Mandatory?
--- | --- | ---
loss_of_competitive_advantage | [SecurityCompromise](#security_compromise) |
brand_and_market_damage | [SecurityCompromise](#security_compromise) |
increased_operating_costs | [SecurityCompromise](#security_compromise) |
local_and_regulatory_costs | [SecurityCompromise](#security_compromise) |

<a name="direct_impact_summary"/>
### DirectImpactSummary

Key | Value | Mandatory?
--- | --- | ---
asset_losses | [ImpactRating](#impact_rating) |
business_mission_distruption | [ImpactRating](#impact_rating) |
response_and_recovery_costs | [ImpactRating](#impact_rating) |

<a name="affected_asset"/>
### AffectedAsset

Key | Value | Mandatory?
--- | --- | ---
type | string |
description | (string, ...) |
ownership_class | [OwnershipClass](#ownership_class) |
management_class | [ManagementClass](#management_class) |
location_class | [LocationClass](#location_class) |
property_affected | [PropertyAffected](#property_affected) |
identifying_observables | [RelatedObservables](#related_observables) |

<a name="property_affected"/>
### PropertyAffected

Key | Value | Mandatory?
--- | --- | ---
property | [LossProperty](#loss_property) |
description_of_effect | string |
type_of_availability_loss | string |
duration_of_availability_loss | [LossDuration](#loss_duration) |
non_public_data_compromised | [NonPublicDataCompromised](#non_public_data_compromised) |

<a name="non_public_data_compromised"/>
### NontPublicDataCompromised

Key | Value | Mandatory?
--- | --- | ---
security_compromise | [SecurityCompromise](#security_compromise) | &#10003;
data_encrypted | boolean |

### References

STIX [IncidentType](http://stixproject.github.io/data-model/1.2/incident/IncidentType/)

<a name="indicator"/>
## Indicator

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
alternate_ids | (string, ...) |
version | number |
negate | boolean |
type | ([IndicatorType](#indicator_type), ...) |
valid_time_position | [ValidTime](#valid_time) |
observable | [ObservableReference](#observable_reference) |
composite_indicator_expression | [CompositeIndicatorExpression](#composite_indicator_expression) |
indicated_TTP | [RelatedTTP](#related_ttp) |
likely_impact | string |
suggested_COAs | [SuggestedCOAs](#suggested_coas) |
confidence | [HighMedLow](#high_med_low) |
sightings | [Sightings](#sightings) |
related_indicators | [RelatedIndicators](#related_indicators) |
related_campaigns | [RelatedCampaigns](#related_campaigns) |
related_COAs | [RelatedCOAs](#related_coas) |
expires | [Time](#time) |
producer | string | &#10003;
specifications | (&#91;[JudgementSpecification](#judgement_specification) &#124; [ThreatBrainSpecification](#threat_brain_specification) &#124; [SnortSpecification](#snort_specification) &#124; [SIOCSpecification](#sioc_specification) &#124; [OpenIOCSpecification](#open_ioc_specification)&#93;, ...) |

<a name="sightings"/>
### Sightings

Key | Value | Mandatory?
--- | --- | ---
Sightings_count | Integer |
sightings | ([Sighting](#sighting), ...) | &#10003;

<a name="sighting"/>
### Sighting

Key | Value | Mandatory?
--- | --- | ---
timestamp | [Time](#time) |
source | [Source](#source) |
reference | [URI](#uri) |
confidence | [HighMedLow](#high_med_low) |
description | (string, ...) |
related_observables | [RelatedObservables](#related_observables) |

<a name="open_ioc_specification"/>
### OpenIOCSpecification

Key | Value | Mandatory?
--- | --- | ---
type | "OpenIOC" | &#10003;
open_IOC | string | &#10003;

<a name="sioc_specification"/>
### SIOCSpecification

Key | Value | Mandatory?
--- | --- | ---
type | "SIOC" | &#10003;
SIOC | string | &#10003;

<a name="snort_specification"/>
### SnortSpecification

Key | Value | Mandatory?
--- | --- | ---
type | "Snort" | &#10003;
snort_sig | string | &#10003;

<a name="threat_brain_specification"/>
### ThreatBrainSpecification

Key | Value | Mandatory?
--- | --- | ---
type | "ThreatBrain" | &#10003;
query | string | &#10003;
variables | (string, ...) | &#10003;

<a name="judgement_specification"/>
### JudgementSpecification

Key | Value | Mandatory?
--- | --- | ---
type | "Judgement" | &#10003;
judgements | ([Reference](#reference), ...) | &#10003;
required_judgements | ([Reference](#reference), ...) | &#10003;

<a name="valid_time"/>
### ValidTime

Key | Value | Mandatory?
--- | --- | ---
start_time | [Time](#time) |
end_time | [Time](#time) |

### References

STIX [IndicatorType](http://stixproject.github.io/data-model/1.2/indicator/IndicatorType/)

<a name="ttp"/>
## Tools, Techniques, & Procedures (TTP)

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
timestamp | [Time](#time) | &#10003;
version | string |
intended_effect | [IntendedEffect](#intended_effect) |
behavior | [Behavior](#behavior) |
resources | [Resource](#resource) |
victim_targeting | [VictimTargeting](#victim_targeting) |
exploit_targeting | [RelatedExploitTargets](#related_exploit_targets) |
related_TTPs | [RelatedTTPs](#related_ttps) |
source | [Source](#source) |
type | string | &#10003;
expires | [Time](#time) | &#10003;
indicators | ([IndicatorReference](#indicator_reference), ...) | &#10003;

<a name="victim_targeting"/>
### VictimTargeting

Key | Value | Mandatory?
--- | --- | ---
indentity | [Identity](#identity) |
targeted_systems | ([SystemType](#system_type), ...) |
targeted_information | ([InformationType](#information_type), ...) |

<a name="resource"/>
### Resource

Key | Value | Mandatory?
--- | --- | ---
tools | ([Tool](#tool), ...) |
infrastructure | [Infrastructure](#infrastructure) |
providers | [Identity](#identity) |

<a name="infrastructure"/>
### Infrastructure

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
type | [AttackerInfrastructure](#attacker_infrastructure) | &#10003;

<a name="behavior"/>
### Behavior

Key | Value | Mandatory?
--- | --- | ---
attack_patterns | ([AttackPattern](#attack_pattern), ...) |
malware_type | ([MalwareInstance](#malware_instance), ...) |

<a name="malware_instance"/>
### MalwareInstance

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
type | ([MalwareType](#malware_type), ...) | &#10003;

<a name="attack_pattern"/>
### AttackPattern

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
capec_id | string |

### References

STIX [TTPType](http://stixproject.github.io/data-model/1.2/ttp/TTPType)

<a name="shared_structures"/>
## Shared Structures

<a name="uri"/>
### URI

string

<a name="time"/>
### Time

Time is stored internally as a Joda DateTime object, serialized as a
string the field should follow the rules of the ISO8601 standard.

<a name="time_structure"/>
### TimeStructure

Key | Value | Mandatory?
--- | --- | ---
start_time | [Time](#time) |
end_time | [Time](#time) |
produced_time | [Time](#time) |
received_time | [Time](#time) |

<a name="tool"/>
### Tool

Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
title | string | &#10003;
description | (string, ...) | &#10003;
short_description | (string, ...) | &#10003;
name | string |
type | [AttackToolType](#attack_tool_type) |
references | (string, ...) |
vendor | string |
version | string |
service_pack | string |

<a name="source"/>
### Source
Key | Value | Mandatory?
--- | --- | ---
description | string |
identity | string |
role | string |
contributing_sources | [SourceReference](#source_reference) |
time | [TimeStructure](#time_structure) |
tools | ([Tool](#tool), ..) |

<a name="contributor"/>
### Contributor

Key | Value | Mandatory?
--- | --- | ---
role | string |
name | string |
email | string |
phone | string |
organization | string |
date | [Time](#time) |
contribution_location | string |

<a name="related_identity"/>
### RelatedIdentity

Key | Value | Mandatory?
--- | --- | ---
confidence | [HighMedLow](#high_med_low) |
information_source | [Source](#source) |
relationship | string |
identity | [IdentityReference](#identity_reference) | &#10003;

<a name="identity"/>
### Identity
Key | Value | Mandatory?
--- | --- | ---
id &#124; idref | string | &#10003;
name | string | &#10003;
related_identities | [RelatedIdentity](#related_identity) | &#10003;

<a name="activity"/>
### Activity

Key | Value | Mandatory?
--- | --- | ---
date_time | [Time](#time) | &#10003;
description | string | &#10003;

<a name="relationship_structures"/>
## Relationship Structures

There are three types of relationship structures:

1. References; see [Reference](#reference) below.
2. Related object structures, which contain references along with
   other fields about the relationship.
3. Scoped objects structures, which contain related object structures
   and an optional [Scope](#scope) field.

### References short-cut

All _RelatedFoo_ style structures may be replaced with a list of
references, when adding scope and similar fields is not desired.  For
example, both of the following JSON structures are proper
[RelatedCampaigns](#related_campaigns).

#### RelatedCampaigns full form

```json
{"scope": "inclusive",
 "related_campaigns": [{"campaign": "campaign-sample-1",
                        "confidence": "High"},
                       {"campaign": "campaign-sample-2",
                        "confidence": "Low"}]}
```

#### RelatedCampaigns short-cut form

```json
["campaign-sample-1", "campaign-sample-2"]
```

<a name="reference"/>
<a name="actor_reference"/>
<a name="campaign_reference"/>
<a name="coa_reference"/>
<a name="exploit_target_reference"/>
<a name="identity_reference"/>
<a name="incident_reference"/>
<a name="indicator_reference"/>
<a name="observable_reference"/>
<a name="source_reference"/>
<a name="ttp_reference"/>
### Reference

A string identifier that points to a stored data structure.  Named
references in this document indicates the type of object that is being
pointed to.

<a name="related_indicator"/>
### RelatedIndicator

Key | Value | Mandatory?
--- | --- | ---
confidence | [HighMedLow](#high_med_low) |
source | [Source](#source) |
relationship | string |
indicator | [IndicatorReference](#indicator_reference) | &#10003;

<a name="related_indicators"/>
### RelatedIndicators

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
related_indicators | ([RelatedIndicator](#related_indicator), ...) | &#10003;

<a name="related_actor"/>
### RelatedActor

Key | Value | Mandatory?
--- | --- | ---
confidence | [HighMedLow](#high_med_low) |
source | [Source](#source) |
relationship | string |
actor | [ActorReference](#actor_reference) | &#10003;

<a name="associated_actors"/>
### AssociatedActors

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
associated_actors | ([RelatedActor](#related_actor), ...) | &#10003;

<a name="attributed_actors"/>
### AttributedActors

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
attributed_actors | ([RelatedActor](#related_actor), ...) | &#10003;

<a name="related_campaign"/>
### RelatedCampaign

Key | Value | Mandatory?
--- | --- | ---
confidence | [HighMedLow](#high_med_low) |
source | [Source](#source) |
relationship | string |
campaign | [CampaignReference](#campaign_reference) | &#10003;

<a name="associated_campaigns"/>
### AssociatedCampaigns

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
associated_campaigns | ([RelatedCampaign](#related_campaign), ...) | &#10003;

<a name="related_campaigns"/>
### RelatedCampaigns

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
related_campaigns | ([RelatedCampaign](#related_campaign), ...) | &#10003;

<a name="related_coa"/>
### RelatedCOA

Key | Value | Mandatory?
--- | --- | ---
confidence | [HighMedLow](#high_med_low) |
source | [Source](#source) |
relationship | string |
COA | [COAReference](#coa_reference) | &#10003;

<a name="potential_coas"/>
### PotentialCOAs

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
potential_COAs | ([RelatedCOA](#related_coa), ...) | &#10003;

<a name="related_coas"/>
### RelatedCOAs

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
related_COAs | ([RelatedCOA](#related_coa), ...) | &#10003;

<a name="suggested_coas"/>
### SuggestedCOAs

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
suggested_COAs | ([RelatedCOA](#related_coa), ...) | &#10003;

<a name="coa_requested"/>
### COARequested

Key | Value | Mandatory?
--- | --- | ---
time | [Time](#time) |
contributors | [Contributors](#contributors) |
COA | [COAReference](#coa_reference) | &#10003;

<a name="related_exploit_target"/>
### RelatedExploitTarget

Key | Value | Mandatory?
--- | --- | ---
confidence | [HighMedLow](#high_med_low) |
source | [Source](#source) |
relationship | string |
exploit_target | [ExploitTargetReference](#exploit_target_reference) | &#10003;

<a name="related_exploit_targets"/>
### RelatedExploitTargets

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
exploit_targets | ([RelatedExploitTarget](#related_exploit_target), ...) | &#10003;

<a name="related_incident"/>
### RelatedIncident

Key | Value | Mandatory?
--- | --- | ---
confidence | [HighMedLow](#high_med_low) |
source | [Source](#source) |
relationship | string |
incident | [IncidentReference](#incident_reference) | &#10003;

<a name="related_incidents"/>
### RelatedIncidents

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
incidents | ([RelatedIncident](#related_incident), ...) | &#10003;

<a name="composite_indicator_expression"/>
### CompositeIndicatorExpression

Key | Value | Mandatory?
--- | --- | ---
operator | "and" &#124; "or" &#124; "not" | &#10003;
indicators | ([IndicatorReference](#indicator_reference), ...) | &#10003;

<a name="related_ttp"/>
### RelatedTTP

Key | Value | Mandatory?
--- | --- | ---
confidence | [HighMedLow](#high_med_low) |
source | [Source](#source) |
relationship | string |
TTP | [TTPReference](#ttp_reference) | &#10003;

<a name="related_ttps"/>
### RelatedTTPs

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
related_TTPs | ([RelatedTPP](#related_ttp), ...) | &#10003;

<a name="observed_ttps"/>
### ObservedTTPs

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
observed_TTPs | ([RelatedTPP](#related_ttp), ...) | &#10003;

<a name="levereged_ttps"/>
### LeveregedTTPs

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
levereged_TTPs | ([RelatedTPP](#related_ttp), ...) | &#10003;

<a name="related_observable"/>
### RelatedObservable

Key | Value | Mandatory?
--- | --- | ---
confidence | [HighMedLow](#high_med_low) |
source | [Source](#source) |
relationship | string |
observable | [ObservableReference](#observable_reference) | &#10003;

<a name="related_observables"/>
### RelatedObservables

Key | Value | Mandatory?
--- | --- | ---
scope | [Scope](#scope) |
related_observables | ([RelatedObservable](#related_observable), ...) | &#10003;

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
- mac
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
