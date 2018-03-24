package edge.labs.orchestrator

import java.io.File

import com.typesafe.config._

import scala.reflect.ClassTag

/**
 * Abstraction over Typesafe's Config object which reads in application-specific properties into typed variables.
 * Keeps settings keys in one place for easier maintenance
 *
 * @author medge
 */
object Settings {

  // Attempt to load environment-based config. Default to application.conf if not present
  lazy val config: Config = sys.props.get("app.environment")
    .map(_.toLowerCase())
    .map(ConfigFactory.load)
    .getOrElse(ConfigFactory.load())

  def apply(): Settings = new Settings(config)

}

/**
 * Attempts to extract relevant App-specific configurations to type-safe variables
 * for consumption by client classes
 *
 * @param config Config
 */
class Settings(config: Config) extends RichConfig(config) {

  // REST API Settings
  val port: Int = getRequired[Int]("app.port")

  // Key name for the Blocking IO dispatcher used to run Futures on a separate Thread Pool than the Akka System
  val blockingIoDispatcher: String = "app.dispatchers.blocking-io-dispatcher"

  // Key for the EventReader/EventWriter Dispatcher
  val eventActorDispatcher: String = "app.dispatchers.event-actor-dispatcher"

  // Days to keep events in the EventsRepository
  val daysToKeep: Int = getRequired[Int]("app.events.daysToKeep")

  // File path to the Pipeline JSON definition files
  val pipelineJsonBaseUrl: String = new File(getRequired[String]("app.pipelines.basePath")).getAbsolutePath
  val pipelineDefaultFolder: String = getRequired[String]("app.pipelines.defaultFolder")

}

/**
 * Enhancements to Typesafe's Config object
 *
 * @param config Config
 */
sealed class RichConfig(config: Config) extends ReflectionHelper {

  /**
   * Get the value at the given key, attempt to cast to the given type, and then wrap in an Option
   *
   * @param key String
   * @tparam T Type to attempt to cast to
   * @return Option[T] - None if the key is not found
   */
  def get[T : ClassTag](key: String): Option[T] = {
    try {
      val result = config.getAnyRef(key).asInstanceOf[T]

      Some(result)
    } catch {
      // Not Found
      case failed: ConfigException.Missing => None
    }
  }

  /**
   * Get the value at the given key and attempt to cast it to the given type.
   *
   * Note: this is a more strict version of get[T] because it throws a SettingsException if the key is not found
   *
   * @param key Config path to required property
   * @tparam T Type to attempt to cast value to
   * @return T
   * @throws SettingsException if the key could not be found or casting to the given type failed
   */
  @throws[SettingsException]
  def getRequired[T : ClassTag](key: String): T = get[T](key) match {
    case Some(obj) => obj
    case None => throw new SettingsException(s"Key $key not found in any configuration file!")
  }

}

/**
 * Exception for Settings-related failures
 *
 * @param message String
 * @param cause Throwable
 */
class SettingsException(message: String, cause: Throwable = null) extends Exception(message, cause)
