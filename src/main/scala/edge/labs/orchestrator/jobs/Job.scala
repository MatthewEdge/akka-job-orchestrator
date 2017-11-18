package edge.labs.orchestrator.jobs

import edge.labs.orchestrator.dag.Graphable

/* @author medge */

/**
 * Model for a REST Job
 */
case class Job(
  runDate: String = "", // Should be provided when the Job Definition JSON file is read in
  id: String,
  description: String,
  dependencies: Set[String],

  continueOnFailure: Boolean = false,

  baseUrl: String = "", // Should be provided by Pipeline container
  endpoint: String,
  method: String,
  queryParams: Option[Map[String, String]],
  body: Option[String] = None,

  // Polling params are optional but if present trigger the Job to be executed by a Polling REST Actor
  pollEndpoint: Option[String],
  pollMethod: Option[String],
  pollQueryParams: Option[Map[String, String]],
  pollFor: Option[Map[String, String]],
  pollFailure: Option[Map[String, String]]
) extends Graphable[Job, String]


