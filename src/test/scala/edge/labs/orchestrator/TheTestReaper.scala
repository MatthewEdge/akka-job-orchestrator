package edge.labs.orchestrator

import akka.actor.ActorRef
import akka.testkit.{TestKitBase, TestProbe}

/* @author medge */

/**
 * Reaper which can be used to watch Test Actor instances for termination
 */
trait TheTestReaper { this: TestKitBase =>

  // TestProbe specifically for watching for Actor termination
  val reaper = TestProbe()

  /**
   * Have the Reaper TestProbe watch the given ActorRef
   *
   * @param target ActorRef to watch
   */
  def reaperWatch(target: ActorRef): Unit = {
    reaper.watch(target)
  }

  /**
   * Alias for reaper.expectTerminated(target)
   *
   * @param target ActorRef
   */
  def expectReaped(target: ActorRef): Unit = {
    reaper.expectTerminated(target)
  }

}
