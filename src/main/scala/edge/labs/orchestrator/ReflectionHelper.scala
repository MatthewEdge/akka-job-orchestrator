package edge.labs.orchestrator

import scala.reflect.{ClassTag, classTag}

/**
 * Helper methods to assist with Reflection operations
 *
 * @author medge
 */
trait ReflectionHelper {

  def runtimeClassOf[T : ClassTag]: Class[_] = classTag[T].runtimeClass

}
