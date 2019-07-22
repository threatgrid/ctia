# CTIA elasticsearch stores: managing big indices

This document describes how big Elasticsearch indices are managed in CTIA.

For CRUD implementation details, see [slides](doc/es_stores.pdf).

## Configuring Aliased stores
In the configuration, we can define if stores should use aliases or not. This can be defined per store or with a default behavior. Aliased store are highly recommended for large stores like sightings or relationships to ease Elasticsearch scaling.

For instance we could set the `aliased` property to `false` by default and to `true` for large stores only
```properties
ctia.store.es.default.aliased=false
ctia.store.es.sighting.aliased=true
ctia.store.es.relationship.aliased=true
ctia.store.es.judgment.aliased=true
ctia.store.es.event.aliased=true
```

When `aliased` is set to true, it will not be considered unless one of `rollover` conditions are set, `max_docs` or `max_age`. Once again, this is configurable per store.
```properties
ctia.store.es.default.rollover.max_docs=10000000
ctia.store.es.default.rollover.max_age=6m
ctia.store.es.event.rollover.max_docs=10000000
ctia.store.es.event.rollover.max_age=1m
ctia.store.es.event.rollover.max_docs=5000000
ctia.store.es.event.rollover.max_age=1w
```

## Unaliased stores: 1 store = 1 index

- CTIA directly writes on configured index name
```properties
ctia.store.es.event.indexname=ctia_event
ctia.store.es.sighting.indexname=ctia_sighting
ctia.store.es.relationship.indexname=ctia_relationship
...
```
- During the Initialization, for each unaliased ES store, CTIA creates one template with proper index settings and type mapping. The index will be created with first document insertion, and configured from that template.
- CRUD operations and Search requests directly target the index.

Elasticsearch search indices have a fixed number of [shards](https://www.elastic.co/guide/en/elasticsearch/reference/5.6/_basic_concepts.html#getting-started-shards-and-replicas). It's impossible to modifiy this number of shards without reindexing the corresponding index. This limits horizontal scaling when shards become too big. Unless you are sure about the fact that configured stores will not exceed 20Gb per shard (meaning you can plan that), we strongly discourage to use unaliased stores. Some stores that could remain small are for instance `malware` or `indicator`. You can configure the number of shards as follow:
``
ctia.store.es.default.shards=5

## Aliased stores: 1 store / n indices, read/write aliases
We use aliases and _rollover API to ease horizontal scaling on very big stores: 
- multiple indices, with read alias on all indices and write alias on most recent index. read alias is the store name, write alias is the store name suffixed by -write, index names are the store name suffixed by a number.
- During the initialization CTIA generates a template with a read alias and the first index with the write and read aliases.
- ES CRUD operations do not work on multiple indices:
    - GET is replaced by a _search request with an ids query on read alias
    - CREATE are done on the most recent index using write alias
    - UPDATE and DELETE require 2 steps:
        - a _search with an ids query on read alias to retrieve the real index of the targeted document.
        - update / delete request on that retrieved index.
- _rollover api is used to create a new index and to “roll over” write alias on that new index, when some rules (max_docs and / or max_age) are matched.


### Rollover task
CTIA currently supports Elasticsearch 5.x which requires to manually request `_rollover` API. Since indices could be shared by multiple CTIA instances, we designed an external task that must be launched frequently to request `_rollover` with configured conditions. This task requires no parameter, it simply reads from configuration, detects aliased stores and their rollover conditions. 
You can launch that task with:
`java -cp ctia.jar:resources:. clojure.main -m ctia.task.rollover`
or from source:
`lein run -m ctia.task.rollover` 


## From aliased to unaliased and back

Changing the `aliased` mode will only be applied after a migration, it's not possible to do it while running neither by restarting CTIA with modified configuration. In order to do so, you have to modify `aliased` and `rollover` properties and run a migration with current settings. After the migration, properly set the new indexname and restart CTIA.
When the target store is aliased, the migration process will manage requesting of the _rollover API to trigger it at the right time while avoiding an excessive number of call to that API. Note that `max_age` is a meaningless _rollover condition during the migration, since it checks the age of the index which is here newly created.

see [Migration procedure](doc/migration.md)
