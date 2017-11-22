package edge.labs.orchestrator.jobs

import edge.labs.orchestrator.{Command, Event}

/* @author medge */

/* Command messages relating to Jobs */
abstract class JobCommand extends Command

case class StartJob(job: Job) extends JobCommand

/*
 * Event messages relating to Jobs
 *
 * Note: These classes have an overloaded constructor that derives runDate from the given Job model for convenience.
 *       It is preferred to use this variant when CREATING these events to reduce the need for runDate to be all over the code.
 *       runDate must still be mentioned in case expansion (i.e case JobStarted(runDate, job) )
 */
abstract class JobEvent extends Event

/* Job Started Event */
case class JobStarted(runDate: String, job: Job) extends JobEvent {
  def this(job: Job) = this(job.runDate, job)
}

object JobStarted {
  def apply(job: Job) = new JobStarted(job)
}

/* Job Completed Event */
case class JobCompleted(runDate: String, job: Job) extends JobEvent {
  def this(job: Job) = this(job.runDate, job)
}

object JobCompleted {
  def apply(job: Job) = new JobCompleted(job)
}

/* Job Failed Event */
case class JobFailed(runDate: String, job: Job, reason: String, cause: Throwable) extends JobEvent {
  def this(job: Job, reason: String, cause: Throwable) = this(job.runDate, job, reason, cause)
}

object JobFailed {
  def apply(job: Job, reason: String) = new JobFailed(job, reason, null)
}
