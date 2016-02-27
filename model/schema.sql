/* Judgements ID: source-type-timestamp */

CREATE TABLE judgement
(
  id varchar PRIMARY KEY,
  disposition integer NOT NULL,
  source varchar NOT NULL,
  priority int NOT NULL,
  confidence varchar NOT NULL,
  severity int NOT NULL,
  owner varchar NOT NULL,
  created timestamp NOT NULL,
  reason varchar,
  disposition_name varchar,
  valid_time_start_time timestamp,
  valid_time_end_time timestamp,
  source_uri varchar,
  reason_uri varchar,
  observable_type varchar NOT NULL,
  observable_value varchar NOT NULL
);

CREATE TABLE judgement_indicator
(
  confidence varchar,
  source varchar,
  relationship varchar,
  judgement_id varchar NOT NULL,
  indicator_id varchar NOT NULL
);

CREATE TABLE indicator
(
  id varchar PRIMARY KEY,
  version int,
  negate boolean,
  valid_time_start_time timestamp,
  valid_time_end_time timestamp,
  owner varchar,
  created timestamp,
  modified timestamp,
  title varchar NOT NULL,
  description varchar NOT NULL,
  short_description varchar,
  producer varchar NOT NULL,
  likely_impact varchar,
  confidence varchar,
  observable_type varchar,
  observable_value varchar
);

CREATE TABLE indicator_kill_chain_phases
(
  kill_chain_phase varchar NOT NULL,
  indicator_id varchar NOT NULL
);

CREATE TABLE indicator_test_mechanisms
(
  test_mechanism varchar NOT NULL,
  indicator_id varchar NOT NULL
);

CREATE TABLE indicator_judgements
(
  confidence varchar,
  source varchar,
  relationship varchar,
  judgement_id varchar NOT NULL,
  indicator_id varchar NOT NULL
);

CREATE TABLE indicator_alternate_ids
(
  indicator_id varchar,
  alternate_id varchar
);

CREATE TABLE indicator_related_indicators
(
  indicator_id varchar,
  confidence varchar,
  source varchar,
  relationship varchar,
  related_indicator_id varchar
);

CREATE TABLE indicator_composite_indicator_expression
(
  operator varchar NOT NULL,
  indicator_id varchar,
  related_indicator_id varchar
);

CREATE TABLE indicator_indicated_ttp
(
  indicator_id varchar NOT NULL,
  confidence varchar,
  source varchar,
  relationship varchar,
  ttp_id varchar NOT NULL
);

CREATE TABLE indicator_suggested_coas
(
  indicator_id varchar NOT NULL,
  coa_id varchar NOT NULL,
  confidence varchar,
  source varchar,
  relationship varchar
);

CREATE TABLE indicator_related_coas
(
  indicator_id varchar NOT NULL,
  confidence varchar,
  source varchar,
  relationship varchar,
  coa_id varchar NOT NULL
);

CREATE TABLE indicator_related_campaigns
(
  indicator_id varchar NOT NULL,
  confidence varchar,
  source varchar,
  relationship varchar,
  campaign_id varchar NOT NULL
);

CREATE TABLE indicator_types
(
  indicator_id varchar,
  type_id varchar
);

/* Below here is phase 2 */
CREATE TABLE sighting
(
  id varchar PRIMARY KEY,
  timestamp timestamp,
  owner varchar,
  created timestamp,
  modified timestamp,
  reference varchar,
  confidence varchar,
  description varchar
);

CREATE TABLE sighting_observable
(
  sighting_id varchar,
  observable_id varchar
);

CREATE TABLE indicator_sightings
(
  sighting_id varchar,
  sightings_count int
);

CREATE TABLE intended_effect
(
  id varchar PRIMARY KEY,
  intended_effect varchar
);

CREATE TABLE campaign
(
  id varchar PRIMARY KEY,
  created varchar,
  modified varchar,
  owner varchar,
  title varchar,
  description varchar,
  sort_description varchar,
  timestamp timestamp,
  version varchar,
  names varchar,
  status varchar
);


CREATE TABLE campaign_relatedttp
(
  confidence varchar,
  source_id varchar,
  relationship varchar,
  ttp_id varchar,
  campaign_id varchar
);


CREATE TABLE campaign_intended_effect
(
  campaign_id varchar,
  intended_effect_id varchar
);


CREATE TABLE coa
(
  id varchar PRIMARY KEY,
  created timestamp,
  modified timestamp,
  owner varchar,
  title varchar NOT NULL,
  description varchar NOT NULL,
  sort_description varchar,
  alternate_ids varchar,
  timestamp timestamp,
  stage varchar,
  type varchar,
  objective varchar,
  impact varchar,
  cost varchar,
  efficacy varchar,
  source_id varchar
);

CREATE TABLE coa_relatedcoas
(
  confidence varchar,
  coa_id varchar,
  related_coa_id varchar
);

