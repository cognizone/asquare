


Easiest is to load data in fuseki.
Then follow these steps

* set SPARQL Endpoint url to /update (not /query)
* run queries 1, 2, 3 and 4

after this we get a more strict version of the ontology.

next
* set SPARQL Endpoint back to /query
* run queries 5, 6
* validate both have no results

finally you can do also a cleanup of unneeded allValuesFrom

* set SPARQL endpoint back to /update
* run query 7


using 3.21
* original has 5010 triples
* after step 3 : 6506
* after step 4 : 5571
* after step 7 : 4859
