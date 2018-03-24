package edge.labs.orchestrator

import scala.reflect.{ClassTag, classTag}

/**
 * Helper methods to assist with Reflection operations
 *
 * @author medge
 */
trait ReflectionHelper {

  def runtimeClassOf[T : ClassTag]: Class[_] = classTag[T].runtimeClass

  /**
   * Return the Runtime Class Name of the given type T
   *
   * @tparam T Type parameter
   * @return String Runtime Class Name
   */
  def classNameOf[T: ClassTag]: String = runtimeClassOf[T].getName

}
