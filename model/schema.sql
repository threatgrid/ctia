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
