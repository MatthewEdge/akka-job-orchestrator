package edge.labs.orchestrator.rest

import scala.concurrent.duration._
import scalaj.http.{Http, HttpRequest}

/**
 * Simple REST client for performing simple GET/POST using the ScalaJ Http Library
 *
 * Created by medge on 6/6/16.
 */
object ScalajRestClient extends RestClient {

  /**
   * Perform a HTTP GET and return a Http Status Code/Status message
   *
   * @param url String
   * @param queryParams optional Map[String, String] query parameters
   * @return (httpStatusCode: Int, responseBody: String)
   */
  def get(url: String, queryParams: Option[Map[String, String]] = None): (Int, String) = {

    val httpRequest = withParamsIfPresent(url, queryParams)
    val response = httpRequest.asString

    (response.code, response.body)
  }

  /**
   * Perform a HTTP POST and return a Http Status Code/Status message
   *
   * @param url String
   * @param body String POST body
   * @param queryParams Map[String, String] optional query parameters
   * @return (httpStatusCode: Int, responseBody: String)
   */
  def post(url: String, queryParams: Option[Map[String, String]] = None, body: Option[String]): (Int, String) = {

    // If the body param is present, add it to the HttpRequest postData
    val httpRequest =
      body.foldLeft(withParamsIfPresent(url, queryParams)) { case (acc, postBody) => acc.postData(postBody) }

    val response = httpRequest.asString

    (response.code, response.body)
  }

  /**
   * Constructs a HttpRequest object, optionally adding queryParams if they are present
   *
   * @param url String
   * @param queryParams optional Map[String, String]
   * @return HttpRequest
   */
  private def withParamsIfPresent(url: String, queryParams: Option[Map[String, String]]): HttpRequest = {
    queryParams.map(_.foldLeft(baseClient(url)) {
      case (request, (key, value)) => request.param(key, value)
    }).getOrElse(Http(url))
  }

  /**
   * Creates a base HTTP Client with the appropriate Timeout values set
   *
   * @param url String URL to query
   * @return Http
   */
  private def baseClient(url: String): HttpRequest = {
    Http(url)
      .timeout(
        connTimeoutMs = 10.seconds.toMillis.toInt,
        readTimeoutMs = 30.minutes.toMillis.toInt
      )
  }

}
