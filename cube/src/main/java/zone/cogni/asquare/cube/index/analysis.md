
Based on our experience with for example lux-legipro ElasticIndexServiceV2
and IndexMethod I propose this:

#### Manage multiple indexes

Manage multiple indexes can delegate to manage one index.

> GET /api/index
> 
> should return a list of indexes

Requirements
* [config] thread pool

#### Manage one index

> POST /api/index/{name}?clear=<true/false>
>
> default for clear is false
> optionally create, in case of clear of during first time
> and fill index

> DELETE /api/index/{name}
>
> delete an index

Manage one index needs to be able to
* create an index
* delete an index
* fill an index 

Requirements
* [config] elastic
* [config] index name
* [config] elastic settings JSON file

#### Fill index

> POST /api/index/{name}?collection=<query-name>
>
> fill an index


Fill one index needs 

Requirements
* [create] collection of queries to select (graphs and) uris
* [config] paginated query (to run select query)
* [config] rdf store (to run select query and fetch graph)
* [config] model to json (for elastic) 
* [create] sparql to json (for facets for elastic)
(also index name and elastic from "parent")
  
