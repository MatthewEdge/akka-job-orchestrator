package edge.labs.orchestrator.jobs.rest

/* @author medge */

/**
 * Creates and returns a mock RestClient implementation
 */
object MockRestClient {

  /**
   * Creates a new instance of RestClient with the get() and post() methods replaced by the given
   * stub functions
   *
   *
   * @return RestClient
   */
  def apply(statusCode: Int, respBody: String = "Test Body") = new RestClient {
    override def get(url: String, queryParams: Option[Map[String, String]]): (Int, String) = {
      (statusCode, respBody)
    }

    override def post(url: String, queryParams: Option[Map[String, String]], body: Option[String] = None): (Int, String) = {
      (statusCode, respBody)
    }
  }

}
