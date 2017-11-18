# Orchestrator Run Walk Through

To visualize a sample execution we will examine three Pipelines: PipelineA, PipelineB, and PipelineC. We use
three purely for showing the concurrency options at the Pipeline level as well as the Job level.

PipelineA.json

    {
      "id": "PipelineA",
      "version": "0.0.1",
      "dependencies": [],

      "baseUrl": "http://localhost:8080/A",

      "jobs": [
        {
          "id": "JobA",
          "description": "Description of Job A",
          "dependencies": [],

          "endpoint": "/jobA",
          "method": "GET",

          "queryParams": {
            "job": "A"
          }
        }
      ]
    }

PipelineB.json

    {
      "id": "PipelineB",
      "version": "0.0.1",
      "dependencies": ["PipelineA"],

      "baseUrl": "http://localhost:8080/B",

      "jobs": [
        {
          "id": "JobA",
          "description": "Description of Job A",
          "dependencies": [],

          "endpoint": "/jobA",
          "method": "GET",

          "queryParams": {
            "job": "A"
          }
        },

        {
          "id": "JobB",
          "description": "Description of Job B",
          "dependencies": [],

          "endpoint": "/jobB",
          "method": "GET",

          "pollEndpoint": "/pollB",
          "pollMethod": "GET",

          "pollFor": {
            "status": "Completed"
          },

          "pollFailure": {
            "status": "Failed"
          }
        }
      ]
    }

PipelineC.json

    {
      "id": "PipelineC",
      "version": "0.0.1",
      "dependencies": ["PipelineA"],

      "baseUrl": "http://localhost:8080/C",

      "jobs": [
        {
          "id": "JobA",
          "description": "Description of Job A",
          "dependencies": [],

          "endpoint": "/jobA",
          "method": "POST",

          "body": "{\"foo\": \"bar\"}"
        },
        {
          "id": "JobB",
          "description": "Description of Job B",
          "dependencies": ["JobA"],

          "endpoint": "/jobB",
          "method": "GET"
        },
        {
          "id": "JobC",
          "description": "Description of Job C",
          "dependencies": ["JobA"],

          "endpoint": "/jobC",
          "method": "GET"
        },
        {
          "id": "JobD",
          "description": "Description of Job D",
          "dependencies": [],

          "endpoint": "/jobD",
          "method": "GET"
        },
        {
          "id": "JobE",
          "description": "Description of Job E",
          "dependencies": ["JobD"],

          "endpoint": "/jobE",
          "method": "GET"
        },
        {
          "id": "JobF",
          "description": "Description of Job F",
          "dependencies": ["JobB", "JobC", "JobE"],

          "endpoint": "/jobF",
          "method": "GET"
        },
        {
          "id": "JobG",
          "description": "Description of Job G",
          "dependencies": ["JobE"],

          "endpoint": "/jobG",
          "method": "GET"
        },
        {
          "id": "JobH",
          "description": "Description of Job H",
          "dependencies": ["JobF", "JobG"],

          "endpoint": "/jobH",
          "method": "GET"
        },
      ]
    }

So our DAG for the Pipelines forms a simple _fan out_ graph:

                  A
                 / \
                B   C

Meaning that once PipelineA completes, both PipelineB _and_ PipelineC can begin execution. The `DAG` is considered complete
when both PipelineB and PipelineC complete successfully

## Job DAGs

PipelineA only has `JobA` so the Job DAG is just a single node

PipelineB is a simple linear graph of `JobA -> JobB`

PipelineC is a more complex Job DAG simply to showcase the possibilities for the DAG definition. Visually, it looks like
this:

                A       D
               / \      |
              /   \     |
             B     C    |
              \   /     E
               \ /     / \
                F <---    G
                 \       /
                  \     /
                   \   /
                     H

## Application Startup

Most of the application startup code resides in the `Main.scala` file. Think of this file as your
main run configuration. When the application is starting up there are a couple pre-init steps to take:

1. Instantiate an instance of an EventRepository to use. This is the repository the Actors in the next
step will use
2. Spin up an instance of the `EventReader` and `EventWriter` Actors
    - These Actors are responsible for fetching/writing (respectively) Events related to a run
    - This functionality is mainly used for the Status API
3. Spin up an instance of the `Reaper`
    - Don't fear the Reaper. She's just responsible for cleaning up resources after a run

The REST API must also be bound during startup to create the interface used to interact with the Orchestrator.
By default this API is bound to `0.0.0.0` on port `8080`

## Starting

We start a run with default parameters using the following REST call:

    GET /orchestrator/v1/run

This does a number of things (in Main.scala):

1. Generate a `runDate` from the current date in the `yyyyMMdd` format
2. Fetch all JSON Pipeline definitions from the default JSON folder (specified in `application.conf`) and derive them into
a `DAG[Pipeline]`
3. Create an instance of the `Supervisor` Actor who will be responsible for running the derived `DAG[Pipeline]` from
the previous step with the generated `runDate` from Step 1

Note that there are also a couple lambda functions initialized in Main.scala which tells the Supervisor and
PipelineSupervisor instances _how_ they create child Actors. A more in-depth discussion for why this
is done this way can be found in the [Akka Primer doc](../primer/akka.md).

## Starting PipelineA

When `Supervisor` starts (with a Start() message) it will begin executing the `DAG[Pipeline]` given to it by:

1. Fetching the `startNodes` from the DAG (the first Set of Pipeline's which can be executed). In our scenario this is only
`PipelineA`
2. For each start node, create an instance of a `PipelineSupervisor` with the Pipeline to execute and send it a `StartPipeline`
message to kick off its execution.

The `Supervisor` then effectively goes idle while execution of each starting `PipelineSupervisor` starts. It will
stay in this "idle" state until it begins receiving messages from the PipelineSupervisors it creates.

## Execution of PipelineA

It's pretty much the same story for the startup of a `PipelineSupervisor` as it is for the `Supervisor`:

1. Fetch the `startNodes` from the `DAG[Job]` derived from the Pipeline's Jobs. For PipelineA this is only `JobA`
2. For each start node, create an instance of a `RestApiActor` with the Job to execute and send it a `StartJob`
message to kick off its execution.

Before going idle the `PipelineSupervisor` sends a `PipelineStarted` message to the parent `Supervisor` to
notify that execution has started.

Supervisor will then publish this event to the Akka internal Event Stream to be handled by....well whoever listens
for those messages (EventWriter currently) and mark the referenced Pipeline as a `submitted` Pipeline.

This information is used to determine whether a Pipeline should be submitted later on when concurrently
querying dependencies.

## Execution of JobA

From the JSON definition of `JobA` in `PipelineA` we know that this action is a simple HTTP GET against the `/jobA` endpoint.
Executing this REST request is handled by an instance of the `RestApiActor` Actor.

All details about submission and response checking are contained in this Actor. One very important note to make is the
actual endpoint being hit is:

    // baseUrl/endpoint/runDate
    http://localhost:8080/A/jobA/{runDate}

Note the {runDate} placeholder. The current runDate assigned to this run is automatically appended to the URL. This
means that for this REST API it must accept runDate as a Path Parameter at the end of the endpoint.

Three possible Event messages can be sent back to the parent `PipelineSupervisor` by the RestApiActor:

1. `JobStarted` when execution begins (allows for the chance of any startup failures to hit). This is _always_ sent unless
Actor creation fails
2. `JobCompleted` if the REST call completes successfully
3. `JobFailed` if the REST call fails for some reason. This can be triggered by a number of reasons: HTTP 500, an invalid
response body, a failure in the RestClient, and so on. The reason will usually be logged and, in the case of a thrown exception,
captured as the `cause` property of the JobFailed event

# JobA Completes

If JobA completes successfully then the `PipelineSupervisor` that spun up that Job will receive a `JobCompleted` Event message
from the `RestApiActor` that was running JobA. Or, if JobA failed, it will receive a `JobFailed` event.

Because JobA is the only Job in the `DAG[Job]` it has to execute the PipelineSupervisor will trigger success/failure by
sending a `PipelineCompleted`/`PipelineFailed` Event message, respectively, to the parent `Supervisor` and will
then shut down (clean up resources).

# PipelineA Completes

When `Supervisor` receives a message from the `PipelineSupervisor` for PipelineA it has two possible actions:

1. If PipelineA failed (`PipelineFailed` message received) and the `continueOnFailure` flag of PipelineA is false, the entire
run is failed and a `Failed` Event is published to the Event Stream.
2. Otherwise, if PipelineA succeeded, the next Set of Pipelines to execute will be queried from the DAG. In this scenario, this
would be `PipelineB` AND `PipelineC`

An instance of `PipelineSupervisor` will be created: one for PipelineB and one for PipelineC, just like before with PipelineA.
The exact same process will kick off for starting each Pipeline, executing their Jobs based on the order defined in the
`DAG[Job]` for each Pipeline, and the same success/failure conditions will be evaluated at both the Job level as well
as the Pipeline level.

## All Pipelines Complete

Assuming that both PipelineB and PipelineC complete successfully a `PipelineCompleted` event will be sent to the
`Supervisor` from each `PipelineSupervisor` instance. When both messages are received the `Supervisor` will
trigger a successful run by publishing a `Completed` event to the Event Stream and then shut down.

After shutdown the Reaper will see all Actors for the run have shut down and will log completion to the console.
