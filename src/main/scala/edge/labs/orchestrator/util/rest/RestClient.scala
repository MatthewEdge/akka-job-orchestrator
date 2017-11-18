package edge.labs.orchestrator.util.rest

import java.net.SocketTimeoutException

/* @author medge */

/**
 * Denotes the interface for a Simple REST Client
 */
trait RestClient {

  /**
   * Delegate to the appropriate REST method based on the given method arg
   *
   * @param runDate String
   * @param baseUrl String
   * @param method String
   * @param maybeQueryParams Option[ Map[String, String] ]
   * @return (Int, String) - httpStatusCode, responseBody
   */
  def submitRequest(
    runDate: String,
    baseUrl: String,
    method: String,
    maybeQueryParams: Option[Map[String, String]] = None,
    body: Option[String] = None)
  : (Int, String) = {

    // Construct URL with baseUrl/endpoint/runDate in that order
    val url = s"$baseUrl/$runDate"
    val httpMethod = method.toUpperCase()

    // Submit the REST request
    try {
      httpMethod match {
        case RestMethod.GET =>
          get(url, maybeQueryParams)

        case RestMethod.POST =>
          post(url, maybeQueryParams, body)
      }
    } catch {
      case timeout: SocketTimeoutException =>
        timeout.printStackTrace()
        (500, s"REST request timed out for $httpMethod $url")
    }
  }

  /**
   * Perform a HTTP GET and return a Http Status Code/Status message
   *
   * @param url String
   * @param queryParams optional Map[String, String] query parameters
   * @return (httpStatusCode: Int, responseBody: String)
   */
  def get(url: String, queryParams: Option[Map[String, String]] = None): (Int, String)

  /**
   * Perform a HTTP POST and return a Http Status Code/Status message
   *
   * @param url String
   * @param queryParams Map[String, String] optional query parameters
   * @param body Option[String] POST body. Usually this should be set
   * @return (httpStatusCode: Int, responseBody: String)
   */
  def post(url: String, queryParams: Option[Map[String, String]] = None,  body: Option[String]): (Int, String)

}

object RestMethod {
  val GET = "GET"
  val POST = "POST"
}
