package edge.labs.orchestrator.util.json

import akka.http.scaladsl.model.StatusCodes
import edge.labs.orchestrator.{BaseTest, TestModels}

/* @author medge */

/**
 * Unit Tests for the JsonSupport Trait
 */
class JsonSupportTest extends BaseTest with TestModels {

  object Test extends JsonSupport

  it should "serialize the given JSON to the correct Map" in {

    // Only Strings
    val json = "{\"foo\": \"bar\"}"
    val parsedA = Test.jsonToMap[String, String](json)

    parsedA should contain only ("foo" -> "bar")

    // Integers
    val jsonWithInts = "{\"foo\": 5}"
    val parsedB = Test.jsonToMap[String, Int](jsonWithInts)

    parsedB should contain only ("foo" -> 5)
  }

  it should "serialize a Model to the correct JSON" in {
    Test.toJson(testJobA) shouldEqual "{\"runDate\":\"18000101\",\"id\":\"A\",\"description\":\"Test Job A\",\"dependencies\":[],\"continueOnFailure\":false,\"baseUrl\":\"http://localhost\",\"endpoint\":\"/A\",\"method\":\"GET\"}"
  }

  // Effectively a "did you create HttpResponse correctly" test
  it should "create an Akka HTTP HttpResponse object from the given Model" in {

    // Default status should be a HTTP 200
    Test.jsonResponse(testJobA).status shouldBe StatusCodes.OK

    // Overridden HTTP 404
    Test.jsonResponse(testJobB, StatusCodes.NotFound).status shouldBe StatusCodes.NotFound

  }

}
