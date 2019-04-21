package edge.labs.orchestrator.json

import java.io.File

import akka.http.scaladsl.model._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.{DefaultFormats, _}

import scala.io.Source

/* @author medge */
trait JsonSupport {

  implicit val formats = DefaultFormats

  /**
   * @param file File
   * @return Boolean
   */
  def isJsonFile(file: File): Boolean = !file.isDirectory && file.getName.endsWith(".json")

  /**
   * Read a JSON file into memory as a flat String
   *
   * @param file File
   * @return String
   */
  def readJsonFile(file: File): String = {
    Source.fromFile(file).getLines().mkString("\n")
  }

  /**
   * Attempt to parse the given json String to the desired model type T
   *
   * @param json String
   * @param mf implicit scala.reflect.Manifest (needed for json4s parse() method)
   * @tparam T Model type desired
   * @return T
   */
  def jsonTo[T](json: String)(implicit mf: scala.reflect.Manifest[T]): T = {
    parse(json).extract[T]
  }

  /**
   * Parse given JSON string to a standard Map[String, Any]
   *
   * @param json String
   * @return Map[String, Any]
   */
  def jsonToMap[K, V](json: String)(implicit mfK: scala.reflect.Manifest[K], mfV: scala.reflect.Manifest[V]): Map[K, V] = {
    jsonTo[Map[K, V]](json)
  }

  /**
   * Write the given Model to a JSON String
   *
   * @param model T model to serialize
   * @tparam T Model Type param
   * @return String JSON
   */
  def toJson[T <: AnyRef](model: T)(implicit mf: scala.reflect.Manifest[T]): String = {
    implicit val formats = Serialization.formats(NoTypeHints)

    Serialization.write[T](model)
  }

  /**
   * Wrapper around toJson() which turns the result into a HttpResponse
   *
   * @param model T model to JSON-ify
   * @param statusCode StatusCode to return. Defaults to HTTP 200 (OK)
   * @tparam T Model Type param
   * @return HttpResponse
   */
  def jsonResponse[T <: AnyRef](model: T, statusCode: StatusCode = StatusCodes.OK)(implicit mf: scala.reflect.Manifest[T]): HttpResponse = {
    HttpResponse(
      statusCode,
      entity = HttpEntity(
        MediaTypes.`application/json`,
        toJson(model)
      )
    )
  }
}
