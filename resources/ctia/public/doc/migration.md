There is a dedicated task to migrate data from prior versions of CTIA. 
This task will run through all configured stores, transform and copy data to new Elasticsearch indices. It is possible to migrate data between clusters.
A migration state will be stored in a configured state to enable restart.

# :warning: Prepare Migration :warning:
 - As the migration task copies indexes, make sure you have enough disk space before launching it.
 - Make sure the resulting indices from your prefix configuration don't match existing ones as they will be deleted.
 - Prepare the migration properties (ex: `ctia_migration.properties`):
   - Keep current CTIA ES properties to read source indices: host, port, protocol and current store indices.
   - Define your migration properties and configure your target store properties (see migration properties below)
   - Modify `aliased`, `rollover`, and `shards` options according to the need of future indices. The migrated indices will be built with these options. Note that only `max_docs` rollover condition will be considered during migration.
   - Configure desired number of shards.
 - Prepare the future CTIA properties (ex: `ctia_vX.X.X.properties`) that will be used to restart CTIA on migrated indices. In particular you will have to set `aliased`, `rollover`, and `indexname` options according to targeted indices state.
 - If possible, stop any processes that push data into CTIA.


## migration properties


### base properties

| argument                    | description                                      | example      | default value      |
|-----------------------------|--------------------------------------------------|--------------|--------------------|
| ctia.migration.migration-id | id of the migration state to create or restart   | migration-1  | required     |
| ctia.migration.prefix       | prefix of the newly created indices              | 1.1.0        | required     |
| ctia.migration.migrations   | a comma separated list of migration ids to apply | 0.4.28,1.0.0 | required     |
| ctia.migration.batch-size   | number of migrated documents per batch           | 1000         | required     |
| ctia.migration.buffer-size  | max batches in buffer between source and target  | 10           | required     |
| ctia.migration.stores       | comma separated list of stores to migrate        | tool,malware | required     |

### target store properties

You can configure target stores similarly to source store by prefixing each store Elasticsearch configuration by `ctia.migration.store.es.<store-name`. Similarly to CTIA standard store definition, you can specify default values:
```
ctia.migration.store.es.default.host=es-storage.int.iroh.site
ctia.migration.store.es.default.port=443
ctia.migration.store.es.default.protocol=https
ctia.migration.store.es.default.replicas=2
ctia.migration.store.es.default.shards=10
ctia.migration.store.es.default.refresh_interval=1s
ctia.migration.store.es.default.default_operator=AND
```
Then you can specify Elasticsearch properties for each target store.
```
ctia.migration.store.es.sighting.indexname=v1.2.0_ctia_sighting
ctia.migration.store.es.sighting.rollover.max_docs=100000000
```

When a parameter is not specified for target store neither in its ES configuration nor in the default migration ES configuration, the source parameters are reused.

Below is an example of simple migration properties to migrates `tool` and `sighting` stores into a new indices in the same cluster, preserving existing indices properties and adding a new index prefix. This is equivalent to the `identity` migration that we use to only modify mappings:
```
# Use ES for all Stores
ctia.store.es.default.host=es-storage.int.iroh.site
ctia.store.es.default.port=443
ctia.store.es.default.protocol=https
ctia.store.es.default.replicas=1
ctia.store.es.default.refresh_interval=1s
ctia.store.es.default.default_operator=AND
ctia.store.es.default.shards=5
ctia.store.es.default.refresh=false
ctia.store.es.default.rollover.max_docs=10000000
ctia.store.es.default.aliased=true

ctia.store.actor=es
ctia.store.asset=es
ctia.store.asset-mapping=es
ctia.store.asset-properties=es
ctia.store.attack-pattern=es
ctia.store.event=es
ctia.store.feedback=es
ctia.store.campaign=es
ctia.store.coa=es
ctia.store.data-table=es
ctia.store.identity=es
ctia.store.incident=es
ctia.store.indicator=es
ctia.store.investigation=es
ctia.store.judgement=es
ctia.store.malware=es
ctia.store.relationship=es
ctia.store.casebook=es
ctia.store.sighting=es
ctia.store.identity-assertion=es
ctia.store.tool=es
ctia.store.vulnerability=es
ctia.store.weakness=es
ctia.store.feed=es

ctia.store.es.actor.indexname=v1.1.0_ctia_actor
ctia.store.es.asset.indexname=v1.1.0_ctia_assets
ctia.store.es.asset-mapping.indexname=v1.1.0_ctia_asset_mapping
ctia.store.es.asset-properties.indexname=v1.1.0_ctia_asset_properties
ctia.store.es.attack-pattern.indexname=v1.1.0_ctia_attack_pattern
ctia.store.es.campaign.indexname=v1.1.0_ctia_campaign
ctia.store.es.coa.indexname=v1.1.0_ctia_coa
ctia.store.es.event.indexname=v1.1.0_ctia_event
ctia.store.es.data-table.indexname=v1.1.0_ctia_data-table
ctia.store.es.feedback.indexname=v1.1.0_ctia_feedback
ctia.store.es.identity.indexname=v1.1.0_ctia_identities
ctia.store.es.incident.indexname=v1.1.0_ctia_incident
ctia.store.es.indicator.indexname=v1.1.0_ctia_indicator
ctia.store.es.investigation.indexname=v1.1.0_ctia_investigation
ctia.store.es.judgement.indexname=v1.1.0_ctia_judgement
ctia.store.es.malware.indexname=v1.1.0_ctia_malware
ctia.store.es.relationship.indexname=v1.1.0_ctia_relationship
ctia.store.es.casebook.indexname=v1.1.0_ctia_casebook
ctia.store.es.sighting.indexname=v1.1.0_ctia_sighting
ctia.store.es.identity-assertion.indexname=v1.1.0_ctia_identity-assertion
ctia.store.es.tool.indexname=v1.1.0_ctia_tool
ctia.store.es.vulnerability.indexname=v1.1.0_ctia_vulnerability
ctia.store.es.weakness.indexname=v1.1.0_ctia_weakness
ctia.store.es.feed.indexname=v1.1.0_ctia_feeds

# migration properties
ctia.migration.migration-id=migration-test
ctia.migration.prefix=1.1.0
ctia.migration.migrations=identity
ctia.migration.store-keys=tool,sighting
ctia.migration.batch-size=1000
ctia.migration.buffer-size=3

# migration store parameter for migration states
ctia.migration.store.es.migration.indexname=ctia_migration
```

A more advanced migration can in particular rename indices while ignoring given `prefix`, and in particular change the target cluster. Here is an example of properties that you can add to the one above to migrate data into your local environment (which is handy to make local tests on real data):
```
ctia.migration.store.es.default.host=localhost
ctia.migration.store.es.default.port=9200
ctia.migration.store.es.default.protocol=http
ctia.migration.store.es.default.shards=1

ctia.migration.store.es.tool.indexname=ctia_tool
ctia.migration.store.es.malware.indexname=ctia_sighting
```

# Migration Steps
 - replace `ctia.properties` by the migration property file that you prepared (`ctia_migration.properties`).
 - Launch migration task while your CTIA instance keep running. You can launch parallel migrations for different stores.
 - Stop CTIA server instance.
 - Complete migration using `--restart` parameter to handle writes that occurred during the migration. Keep the same migration properties.
 - After the migration task completes, replace CTIA properties of the server instance with the one you prepared to launch the server with migrated indices (ex: `ctia_vX.X.X.properties`).
 - Launch new version of CTIA. 
 - The migration task doesn't alter the existing indices, you can delete them after a successful migration.

 
 In case of failure, you have 2 solutions depending on the situation:
   - you can relaunch the task at will, it should fully recreate the new indices.
   - you can restart this migration with `--restart` parameter, it will reuse the migration
 
 
# Launch the task with:
 
`java -cp ctia.jar:resources:. clojure.main -m ctia.task.migration.migrate-es-stores <options>`

or from source

`lein run -m ctia.task.migration.migrate-es-stores <options>`

## task arguments

| argument                    | description                                      | example      | default value      |
|-----------------------------|--------------------------------------------------|--------------|--------------------|
| -c, --confirm               | really do the migration?                         |              | false (positional) |
| -r, --restart               | restart ongoing migration?                       |              | false (positional) |
| -h, --help                  | prints usage                                     |              |                    |

# Available migrations

| migration task        | target ctia versions                                           |
|------------------------|-------------------------------------------------------------- |
|                0.4.16 | all versions before 1.0.0-rc1                                  |
|                0.4.28 | all versions before 1.1.0                                      |
|                 1.0.0 | 1.1.0                                                          |
|              identity | used for mapping migration or copying data between clusters    |
| investigation-actions | used for reifying fields of `actions` field  in investigations |
