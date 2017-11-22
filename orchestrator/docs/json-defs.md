# JSON Definitions and the DAG

## Pipeline

The standard structure of a Pipeline JSON definition takes the following form:

    {
      "id": "UNIQUE_ID",
      "version": "Major.Minor.Revision",
      "dependencies": ["ID", "ID", ...],

      "baseUrl": "BASE_HTTP_URL",

      "jobs": [...]
    }

* `id` is a unique String that identifies the Pipeline during a run. It must be unique across
all Pipelines. For clarity - standard practice says to name the JSON file to match the content of `id`
* `version` marks the version of the definition. This conventionally corresponds to the version in the POM file
* `dependencies` is an array of String `id`s that this Pipeline depends on. These `id`s are CASE SENSITIVE. The
array can also be empty to denote that the Pipeline has _no_ dependencies
* `baseUrl` is the base HTTP URL prefix that will be auto-prepended to all endpoints in the Job definitions if the
Job does not declare its own. This is purely for convenience of not having to type the same base URL in multiple
places
* `jobs` is an array of Job definitions

## Job

The standard structure of a Job JSON definition can take one of the following forms:

    // Standard REST Job
    {
      "id": "UNIQUE_ID",
      "description": "Description of Job",
      "dependencies": ["ID", "ID", ...],

      // Optional. Overrides any value present at the Pipeline level
      "baseUrl": "http://somehost.com",

      "endpoint": "/some/http/endpoint",
      "method": "GET", // POST, PUT, DELETE, ...

      // Optional
      "queryParams": {
        "key1": "value1",
        "key2": "value2",

        ...
      }
    }

* Again, `id` is a unique String that identifies the Job during a run. It must be unique across
all Jobs within the Pipeline.
* `description` provides a short description of the Job being run. Purely for documentation purposes
* `dependencies` is an array of String `id`s that the Job depends on. These `id`s are CASE SENSITIVE. The
array can also be empty to denote that the Job has _no_ dependencies

There is also a _Polling REST Job_ which adds a couple properties to the properties described above:

    // Polling REST Job
    {
      // ... same properties from above, plus...


      "pollEndpoint": "/test/api/v1/status",
      "pollMethod": "GET",

      "pollFor": {
        "status": "Completed"
      },

      "pollFailure": {
        "status": "Failed"
      }
    }

* With this Job the standard `endpoint` will be called first and THEN the `pollEndpoint` will be called
* `pollFor` is a key/value pair that should map to the JSON key/value pair you are
checking. This naturally means that the REST endpoint should be returning JSON. Non-JSON responses
are NOT currently supported
* `pollFailure` is an optional failure condition. If defined, the Polling REST Job will
immediately fail if the REST Response matches this key/value pair.
   - Use this value if your REST Service returns a known **Failure** response so that you
    don't have wait for a Poll Timeout to trigger the Job failure

## DAGs

Acronym for _Directed Acyclic Graph_, the DAG is the execution graph for Pipelines/Jobs. It contains the
logic for deriving which Pipelines/Jobs are next in the graph.

An example DAG could look like the following:

     _       _
    |1|     |2|
     -       -
     |       |
     v       v
     _       _
    |3|     |4|
     -       -
      \     /
         |
         v
         _
        |5|
         -

Nodes `1` and `2` are _start nodes_. i.e they have no dependencies and thus are considered to be the entry points
for the graph. Such nodes are considered to be eligible for **concurrent** execution.

Nodes `3` and `4` have dependencies on the previous nodes `1` and `2` respectively and thus cannot be started
until their respective _predecessor_ completes.

Node `5` is considered an _exit node_. It has no _successors_ to it in the graph and once it's execution is
complete the Graph can be scanned for completion.

The nodes in this DAG can be Pipelines or Jobs. The Orchestrator will automatically derive a DAG for both by evaluating
the dependencies of each JSON item. This is one of the reasons why Pipeline and Job `id`s must be unique.
