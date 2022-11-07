# QA instructions for [advthreat/iroh#5929 reactivate default fields + search-token rewrite](https://github.com/advthreat/iroh/issues/5929)

*Due to the extended nature of this document, it was decided to keep it in a file instead of the PR description header*

## Mechanics of the search improvements

The whole kit & caboodle has begun with this [ticket](https://github.com/advthreat/response/issues/573). While trying to improve the search, we've encountered a few things. First, we've found that simply allowing other search modes (beside Lucene) breaks our previous [workflows](https://github.com/advthreat/iroh/issues/5597). Later we discovered that we couldn't search mixing different types of fields, i.e., keywords and text.

Keywords usually are used for structured content such as IDs, email addresses, hostnames, status codes, zip codes, tags, etc. So we use them quite a lot. We also use text fields. And when searching for data, specifying fields, mixing keywords and text fields sometimes just doesn't work.

Take this Elasticsearch query example:

    {
        "query_string": {
            "query": "the intrusion event 3\\:19187\\:7 incident",
            "default_operator": "AND",
            "fields": ["title", "source"]
        }
    }

Let's imagine we have an Incident with the title: "incident" and source: "intrusion event 3:19187:7". Even though the search phrase contains "the", the search engine should ignore it - it's a stop word. But the query above doesn't work. And the reason is that `title` and `source` are mapped to store different types of values.

So why do we have to specify which fields to include in the search now? We never had to do that before. Older versions of ES could use [_all](https://www.elastic.co/guide/en/elasticsearch/reference/6.8/mapping-all-field.html) option, which forces ES to search in every field, treating every field like text. However, that approach requires extra CPU cycles; it is slow and less accurate. Most importantly, this option is deprecated and no longer available in the new versions. And we're currently undergoing migration to Elasticsearch v7.

So we have to specify the fields. But we cannot break existing API functionality. Just like before, we don't force clients to instruct API which fields to use - if no `search-fields` specified, we quietly "inject" default fields. Each entity has a different set of "default searchable fields". And the search would be performed **only** on those fields. That is the set of allowed fields for searching.

But, that still doesn't solve the problem of mixed types of fields. 

We're solving that using the "nested fields" feature. Going back to the example above, `source` is a keyword, and `title` is a type of text, and thus they cannot be used together in the same query. But we can add a sub-field to `source`, of text type. Would then that query work? Well, then you'd have to specify that sub-field in the query, like so: `"fields": ["title", "source.text"]`. But we don't need to burden the user with these pesky details. We can do that translation ourselves. So, what the API does - for every field that is a keyword and has a sub-field of type text, it would automatically change the query, so the search is performed using the text sub-field. You send a request with: `"fields": ["title", "source"]` - internally it gets translated to: `"fields": ["title", "source.text"]`.

Is that all? Well, not quite.
Actually, in order for that to work, just adding a sub-field is not enough. We can add it now, but that change would not affect all the existing data. So, what do we do? We have to refresh the mapping for all our existing records. Basically, we need to tell ES: "Hey, we've changed the way how the records get stored; Could you now re-index existing records, please?"

And that is an intimidating task - it's too much data to process. That is why we need to test everything thoroughly before enabling all these things in production.

We have added a couple of feature flags (in addition to existing config properties) to turn things on and off quickly if something goes awry.

The first config flag you need to know is:

`ctia.store.es.default.refresh-mappings` - when set to true, it would queue the mapping update at the initialization phase when the server starts.

`ctia.feature-flags` - is a new way of setting arbitrary feature flags. It can take multiple comma-separated values. Right now, we have: `enforce-search-fields` and `translate-searchable-fields`

so they can be set like this, e.g.,

    ctia.feature-flags=enforce-search-fields:true, translate-searchable-fields:false

`enforce-search-fields` - when set to true, and if the user has not indicated which fields to include in the search, CTIA would explicitly perform the search using default searchable fields (again, each entity has its own set of those fields).

`translate-searchable-fields` - when set to "true", does the automatic translation and sends the query with the correct sub-field, i.e., "source" gets sent as "source.text"

## Regressions to watch for

### Queries that end with double quotes

Apparently, some systems today could append an empty string to the end of the query. We shouldn't break that:
[advthreat/iroh#5597 Search behavior changed for Ribbon case/incident queries](https://github.com/advthreat/iroh/issues/5597)

### Make sure Lucene syntax still works

We recently added a simple query format for searching things, but we need to confirm that search still works for queries like: `description:Incident AND title:Injection`

## Sample data for testing

It helps to have some data to test the search. You can use `Bulk Import` to create a few unrelated entities; or `Bundle Import` if you want to test entities linked to each other, e.g., `Asset` and `AssetMapping`.

It is a good strategy to set some property of these test subjects to something unique to eliminate ambiguous results. You can set `source` of every entity to be something like: `dec3_2021`

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
            "source": "dec3_2021",
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
            "source": "dec3_2021",
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
            "source": "dec3_2021",
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
            "source": "dec3_2021",
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

You can POST it using Bulk Import route to create these entities.

## Testing

We suggest exploring various ways to query, frequently changing the shape of data, and trying different queries. Saturate test cases to flush out any bugs. What's listed below is an outline with a few examples of what to test. These examples assume you're using the test data above.

### Query for non-existing values

Send a search query with a bogus text

GET http://private.intel.int.iroh.site/ctia/actor/search?source=dec3_2021&query=lorem_ipsum_suspendisse_potenti

**Expected**: empty list/no results

### Query including non-existing fields

Even though the following query should work:

GET http://private.intel.int.iroh.site/ctia/actor/search?source=dec3_2021&query=LOD

This next one is expected to fail:

GET http://private.intel.int.iroh.site/ctia/actor/search?source=dec3_2021&query=LOD&search_fields=some_strange_field

**Expected:** Error like this or similar:

```json
{
  "errors": {
    "search_fields": [
      "(not (#{\"id\" \"short_description\" \"title\" \"source\" \"actor_type\" \"description\"} \"some_strange_field\"))"
    ]
  }
}
```

### Search for Incidents that have specific values in specified fields

The following query yields nothing (even though there is an Incident with the description containing "first", it doesn't include "Ipsum"):

GET http://private.intel.int.iroh.site/ctia/incident/search?source=dec3_2021&query=first%20Ipsum&search_fields=description

However, if we add the title field (without changing the query), it should find the matching record:

GET http://private.intel.int.iroh.site/ctia/incident/search?source=dec3_2021&query=first%20Ipsum&search_fields=description&search_fields=title

**Expected:** A single Incident

### Looking for the same value in different fields in multiple records

Both next queries return two incidents because two incidents have "numero" in their short_description fields:

GET http://private.intel.int.iroh.site/ctia/incident/search?source=dec3_2021&query=numero&search_fields=short_description
GET http://private.intel.int.iroh.site/ctia/incident/search?source=dec3_2021&query=numero

**Expected:** Two Incidents

Now, if we want to match a record with "numero" in multiple fields, we still can use Lucene syntax, like so:

This query `short_description:numero AND title:numero` should return only a single incident

GET http://private.intel.int.iroh.site/ctia/incident/search?source=dec3_2021&query=short_description%3Anumero%20AND%20title%3Anumero

**Expected:** Single Incident

But changing the query slightly: `short_description:numero OR title:numero` once again, gets both incidents:

GET http://private.intel.int.iroh.site/ctia/incident/search?source=dec3_2021&query=short_description%3Anumero%20OR%20title%3Anumero

**Expected:** Two incidents

### Testing mixed types of fields

Now, here's an interesting bit coming up for which I think all that explanation of the search mechanics felt necessary.
Let's try a query where:

- Two different types of fields involved, 
- And the query contains a stop word

GET http://private.intel.int.iroh.site/ctia/incident/search?source=dec3_2021&query=the%20Lorem&search_fields=title&search_fields=source

Do you see? The query has "the", and yields no results. Remove "the", and it would return two Incidents. But the point is - it has to work with "the". "The" is a stopword, and Elasticsearch should ignore it. And in fact - it does. But because now we're mixing two different types of fields - keyword and text, nothing comes up.

Now, let's set this property: `ctia.feature-flags=translate-searchable-fields:true` and restart the server

After that, sending the same query again should work.

**Expected:** Two incidents

You should also try other stopwords: "a", "is", "are", etc.

### Testing mapping update

This one is a bit tricky. It requires you to find some data (and that's important) that has existed before this version of CTIA. Any data that's created in this version of CTIA, including the data above, would work *(unless it's deliberately imported via a previous version of CTIA)*. But you need to find data that existed before and query it similarly as shown in the previous section.

You need to find an entity and send a mixed fields request. First, send a wide-range query ("query=*"); From the result, pick an element and using the information in the "title", send a query, and don't forget to include "source" field and a stopword. *'source' is a good field to use here because it is for sure mapped as keyword*
Most likely, that attempt would yield nothing. Then you should set:

    ctia.store.es.default.refresh-mappings=true
    
Restart the server, and try again. And this time, your query should work.

### Testing of enforcing search fields

If you try this query:

GET http://private.intel.int.iroh.site/ctia/incident/search?source=dec3_2021&simple_query=second%7Cextortion

It should return all three Incidents because the query says: "get everything that contains 'second' or 'extortion'" *('%7C' part of the query is encoded "or" operator - "|")*. And you can see from the results, `intended_effect` field of every Incident indeed is set to "extortion".

Now, set the feature flags like this:

    ctia.feature-flags=translate-searchable-fields:true,enforce-search-fields:true
    
Restart the server, and try sending the same query again. It should return only a single Incident.
Why is that? The reason is that `enforce-search-fields:true`, when there are no `search_fields` specified, "injects" default fields, and default searchable fields for Incident entity do not include "intended_effect" field *TODO: add a link to ctia.entity.incident/searchable-fields*, and thus it would be ignored.

**Expected:** Only the second Incident

### Testing queries that end with quotes

That previously mentioned issue: [Search behavior changed](https://github.com/advthreat/iroh/issues/5597), where we accidentally broke the search and had to revert the changes. Let's make sure we're not reintroducing it again:

GET http://localhost:3000/ctia/incident/search?source=dec3_2021&query=the%20%22dos%22+%22%22&search_fields=title

**Expected:** Single incident with the title : "Lorem Ipsum Test Incident dos"
