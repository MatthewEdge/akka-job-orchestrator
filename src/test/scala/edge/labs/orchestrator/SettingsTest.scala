package edge.labs.orchestrator

import com.typesafe.config.ConfigFactory

/* @author medge */

/**
 * Unit Tests for the Settings abstraction class
 */
class SettingsTest extends BaseTest {

  val testConfig = ConfigFactory.parseString(
    """
      |test {
      |  key = foo
      |}
    """.stripMargin
  )

  def testRichConfig() = new RichConfig(testConfig)

  "RichConfig.get" should "return Some(value) for a present key" in {
    testRichConfig().get[String]("test.key") shouldEqual Some("foo")
  }

  "RichConfig.get" should "return None for a missing key" in {
    testRichConfig().get[String]("MISSING") shouldBe None
  }

  "RichConfig.getRequired" should "return the expected for a present key" in {
    testRichConfig().getRequired[String]("test.key") shouldEqual "foo"
  }

  "RichConfig.getRequired" should "throw an exception for a missing required key" in {
    intercept[SettingsException] {
      testRichConfig().getRequired[String]("MISSING")
    }
  }

}
