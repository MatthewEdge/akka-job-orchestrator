package edge.labs.orchestrator.pipelines

import edge.labs.orchestrator.{Command, Event}

/* @author medge */

abstract class PipelineCommand extends Command

case class StartPipeline() extends PipelineCommand


/**
 * Note: These classes have an overloaded constructor that derives runDate from the given Pipeline model for convenience.
 *       It is preferred to use this variant when CREATING these events to reduce the need for runDate to be all over the code.
 *       runDate must still be mentioned in case expansion (i.e case PipelineStarted(runDate, pipeline) )
 */
abstract class PipelineEvent extends Event

/* Pipeline Started Event */
case class PipelineStarted(runDate: String, pipeline: Pipeline) extends PipelineEvent {
  def this(pipeline: Pipeline) = this(pipeline.runDate, pipeline)
}

object PipelineStarted {
  def apply(pipeline: Pipeline) = new PipelineStarted(pipeline)
}

/* Pipeline Completed Event */
case class PipelineCompleted(runDate: String, pipeline: Pipeline) extends PipelineEvent {
  def this(pipeline: Pipeline) = this(pipeline.runDate, pipeline)
}

object PipelineCompleted {
  def apply(pipeline: Pipeline) = new PipelineCompleted(pipeline)
}

/* Pipeline Failed Event */
case class PipelineFailed(runDate: String, pipeline: Pipeline, reason: String, cause: Throwable = null) extends PipelineEvent {
  def this(pipeline: Pipeline, reason: String, cause: Throwable) = this(pipeline.runDate, pipeline, reason, cause)
}

object PipelineFailed {
  def apply(pipeline: Pipeline, reason: String) = new PipelineFailed(pipeline, reason, null)
  def apply(pipeline: Pipeline, reason: String, cause: Throwable) = new PipelineFailed(pipeline, reason, cause)
}