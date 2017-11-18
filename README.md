# Job Orchestrator

Job Orchestration tool built in Scala with Akka as the concurrency provider. Currently
only supports single-call REST Jobs and polling REST Jobs.

Jobs are organized into groups of related work called `Pipelines`. Job execution
is represented as a DAG to support ordering, dependency representation, and concurrency
hints.

Pipelines can also be organized into a DAG which is executed with the same semantics
as a DAG of Jobs. This two-layer concurrency allows for more fine-grained control
over execution but also means the overall DAG can get quite complex.

## TODO

Web Interface for DAG design
DAG cycle detection
Typesafe DAG

## REST API

Orchestrator currently comes with a REST API entry point with the following endpoints:

    # Execute Orchestrator with the JSON Job definitions found in the default directory (application.conf)
    # Generates a runDate based on the current date
    GET /orchestrator/v1/run

    # Execute the given Pipeline definition with a generated runDate based on the current date
    POST /orchestrator/v1/run
        Accepts: application/json
        Body: Pipeline JSON

    # Execute Orchestrator with the JSON Job definitions found in the default directory (application.conf)
    # Uses the runDate provided
    GET /orchestrator/v1/run/{runDate}

    # Execute the given Pipeline definition with the given runDate
    POST /orchestrator/v1/run/{runDate}
            Accepts: application/json
            Body: Pipeline JSON

By default this REST API binds to port `8080`. This can be changed in the application.conf file with the
property `app.port`.

## Building the Orchestrator Artifacts

Orchestrator has a two-part build step:

1. Maven Shade builds the über JAR with all Orchestrator code
2. Maven Assembly builds a `tar.gz` archive with the Orchestrator JAR AND the JSON Job files found in the /jobs
directory

Both are linked to the `mvn package` lifecycle
