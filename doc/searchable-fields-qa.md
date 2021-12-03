# QA instructions for https://github.com/advthreat/iroh/issues/5929 

## Mechanics of the search improvements

The whole kit & caboodle has begun with this [ticket](https://github.com/advthreat/response/issues/573). While trying to improve the search, we've encountered a few things. First, we've found that simply allowing other search modes (beside Lucene) breaks our previous [workflows](https://github.com/advthreat/iroh/issues/5597). Later we discovered that we couldn't search mixing different types of fields, i.e., keywords and text.

Keywords in Elasticsearch usually are used for structured content such as IDs, email addresses, hostnames, status codes, zip codes, or tags. So we do use them quite a lot. We also use text fields. And when searching for data, specifying fields, mixing keywords and text fields sometimes just doesn't work.

Take this example:

    {
        "query_string": {
            "query": "the intrusion event 3\\:19187\\:7 incident",
            "default_operator": "AND",
            "fields": ["title", "source"]
        }
    }

Let's imagine we have an Incident with the title: "incident" and source: "intrusion event 3:19187:7". Even though the search phrase contains "the", the search engine should ignore it - it's a stop word. But the query above doesn't work. And the reason is that title and source are mapped to store different types of values.

So why do we have to specify which fields to include in the search now? Older versions of ES could use [_all](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/mapping-all-field.html), which forces ES to search in every field, treating every field like text. However, that approach requires extra CPU cycles; it is slow and less accurate. More importantly, this option is now deprecated and no longer available in the new versions. And we're currently undergoing migration to Elasticsearch v7.

So we have to specify the fields. But we cannot break existing API functionality. So we don't force clients to instruct API which fields to use - if `search-fields` aren't specified, we quietly "inject" default fields. Each entity has a different set of "default searchable fields".

But, this still doesn't solve the problem of mixed types of fields. 

To solve that, we decided to use "nested fields" feature. So, going back to the example above - `source` is a keyword and thus cannot be used together with text fields in the same query. But we can add a sub-field to it, of type text. Would then the above query work? Well, then you'd have to specify that sub-field in the query, like so: `"fields": ["title", "source.text"]`. But we don't need to burden the user with these pesky details. We can do that translation ourselves. So, what the API does - for every field that is a keyword and has a sub-field of type text, it would automatically change the query, so the search is performed using the text sub-field. You send a request with: `"fields": ["title", "source"]` and internally, it gets translated to: `"fields": ["title", "source.text"]`.

Is that all? Well, not quite.
Actually, in order for it to work, just adding a sub-field is not enough. We can add it now, but all the existing data would not be affected by that change. So, what do we need to do? We have to refresh the mapping for all our existing records.

And that is a very intimidating task. That is why we need to test everything thoroughly before enabling things in production.

We have added a couple of feature flags (in addition to existing config properties) to enable or disable things easily.

The first config flag you need to know is:

`ctia.store.es.default.refresh-mappings`  -- when set to true, it would queue the mapping update at the initialization phase when the server starts.

`ctia.feature-flags` - is a new way of setting arbitrary feature flags. It can take multiple comma-separated values. Right now, we have: `enforce-search-fields` and `translate-searchable-fields`

so they can be set like this, e.g.,

    ctia.feature-flags=enforce-search-fields:true, translate-searchable-fields:false

`enforce-search-fields` - when set to true, and if the user has not selected which fields to include in the search, it would explicitly perform the search using default searchable fields (again, each entity has its own set of those fields).

`translate-searchable-fields` - when set to true, does the automatic translation and sends the query with the correct sub-field

## Regressions to watch for

### Queries that end with double quotes

Some systems today would append an empty string to the end of the query. We shouldn't break that
[advthreat/iroh#5597 Search behavior changed for Ribbon case/incident queries](https://github.com/advthreat/iroh/issues/5597)

### Make sure Lucene syntax still works

We recently added a simple query format for searching things, but we need to confirm that search still works for queries like:

    `description:Incident AND title:Injection`

## Testing Data

Some data has to be generated to test the search. You can use Bulk Import to create a few unrelated entities and Bundle Import if you want to test entities linked to each other e.g, `Asset` and `AssetMapping`.

It is a good strategy to set some property of these test subjects to something unique to eliminate ambiguous results. You can set `source` of every entity to be something like: `dec3-2021`

We suggest exploring various ways to query data and also changing the shape of data itself. Saturate the test cases to flush out any bugs.

<details>
<summary>
Here's an example with a single Actor and three Incident entities
</summary>
```json
{
    "actors": [
        {
            "confidence": "High",
            "timestamp": "2016-02-11T00:40:48Z",
            "tlp": "green",
            "language": "language",
            "actor_type": "Hacker",
            "intended_effect": "Fraud",
            "source_uri": "http://example.com/actors/legion-of-doom",
            "external_references": [],
            "title": "LOD Group",
            "short_description": "founded by the hacker Lex Luthor",
            "external_ids": [],
            "source": "dec3-2021",
            "type": "actor",
            "planning_and_operational_support": "MUHS 9th Hour",
            "revision": 1,
            "schema_version": "1.0",
            "sophistication": "Innovator",
            "identity": {
                "related_identities": [],
                "description": "LOD identity"
            },
            "valid_time": {
                "end_time": "2016-07-11T00:40:48Z",
                "start_time": "2016-02-11T00:40:48Z"
            },
            "motivation": "Ego",
            "description": "Legion Of Doom"
        }
    ],
    "incidents": [
        {
            "description": "description of first incident",
            "assignees": [],
            "schema_version": "1.1.3",
            "revision": 1,
            "type": "incident",
            "source": "dec3-2021",
            "external_ids": [],
            "short_description": "first incident",
            "title": "Lorem Ipsum Test Incident",
            "incident_time": {
                "closed": "2016-02-11T00:40:48Z",
                "discovered": "2016-02-11T00:40:48Z",
                "opened": "2016-02-11T00:40:48Z",
                "rejected": "2016-02-11T00:40:48Z",
                "remediated": "2016-02-11T00:40:48Z",
                "reported": "2016-02-11T00:40:48Z"
            },
            "external_references": [],
            "discovery_method": "Log Review",
            "source_uri": "http://example.com/incident-one",
            "intended_effect": "Extortion",
            "categories": [
                "Denial of Service",
                "Improper Usage"
            ],
            "status": "Open",
            "language": "language",
            "tlp": "green",
            "timestamp": "2016-02-11T00:40:48Z",
            "confidence": "High"
        },
        {
            "description": "second incident",
            "assignees": [],
            "schema_version": "1.1.3",
            "revision": 1,
            "type": "incident",
            "source": "dec3-2021",
            "external_ids": [],
            "short_description": "incident numero dos",
            "title": "Lorem Ipsum Test Incident dos",
            "incident_time": {
                "closed": "2016-02-11T00:40:48Z",
                "discovered": "2016-02-11T00:40:48Z",
                "opened": "2016-02-11T00:40:48Z",
                "rejected": "2016-02-11T00:40:48Z",
                "remediated": "2016-02-11T00:40:48Z",
                "reported": "2016-02-11T00:40:48Z"
            },
            "external_references": [],
            "discovery_method": "Log Review",
            "source_uri": "http://example.com/incident-two",
            "intended_effect": "Extortion",
            "categories": [
                "Denial of Service",
                "Improper Usage"
            ],
            "status": "Open",
            "language": "language",
            "tlp": "green",
            "timestamp": "2016-02-11T00:40:48Z",
            "confidence": "High"
        },
        {
            "description": "third incident",
            "assignees": [],
            "schema_version": "1.1.3",
            "revision": 1,
            "type": "incident",
            "source": "dec3-2021",
            "external_ids": [],
            "short_description": "incident numero tres",
            "title": "incident numero three",
            "incident_time": {
                "closed": "2016-02-11T00:40:48Z",
                "discovered": "2016-02-11T00:40:48Z",
                "opened": "2016-02-11T00:40:48Z",
                "rejected": "2016-02-11T00:40:48Z",
                "remediated": "2016-02-11T00:40:48Z",
                "reported": "2016-02-11T00:40:48Z"
            },
            "external_references": [],
            "discovery_method": "Log Review",
            "source_uri": "http://example.com/incident-three",
            "intended_effect": "Extortion",
            "categories": [
                "Denial of Service",
                "Improper Usage"
            ],
            "status": "Open",
            "language": "language",
            "tlp": "green",
            "timestamp": "2016-02-11T00:40:48Z",
            "confidence": "High"
        }
    ]
}
```
</details>

You can POST that using Bulk Import route

## TODO: Testing

### Basics

#### Query for non-existing values
#### Query including non-existing fields
#### Search for Incidents that have specific values in specified fields
#### Looking for the same value in different fields in multiple records
