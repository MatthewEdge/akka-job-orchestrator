package edge.labs.orchestrator.dag

import edge.labs.orchestrator.BaseTest

/**
 * Unit Tests for the DAG data structure class
 * 
 * Created by medge on 6/1/16.
 */
class DAGTest extends BaseTest {

  /*
   * Test DAG. Visually:
   *      1   2
   *     /\  /\
   *    3  4  |
   *       | /
   *       5
   */
  val _dag = new DAG[Int](
    Set(1,2,3,4,5),
    Set((1,3), (1,4), (2,4), (4,5), (2,5))
  )
  
  "startNodes()" should "return the correct Set of values" in {
    _dag.startNodes() should contain theSameElementsAs Set(1,2)
  }

  
  "exitNodes()" should "return the correct Set of values" in {
    _dag.exitNodes() should contain theSameElementsAs Set(3,5)
  }

  
  "predecessors()" should "return the correct Set of values" in {
    _dag.predecessors(3) should contain theSameElementsAs Set(1)
    _dag.predecessors(4) should contain theSameElementsAs Set(1,2)
    _dag.predecessors(5) should contain theSameElementsAs Set(2,4)
  }

  
  "predecessors()" should "return an empty Set of values for a node with no predecessors" in {
    _dag.predecessors(1) shouldBe empty
    _dag.predecessors(2) shouldBe empty
  }

  
  "successors()" should "return the correct Set of values" in {
    _dag.successors(1) should contain theSameElementsAs Set(3,4)
    _dag.successors(2) should contain theSameElementsAs Set(4,5)
  }

  
  "successors()" should "return an empty Set of values for a node with no successors" in {
    _dag.successors(3) shouldBe empty
    _dag.successors(5) shouldBe empty
  }

  "toString()" should "include the nodes" in {
    val dagStr = _dag.toString

    _dag.getNodes.foreach(node => dagStr should include(s"$node") )
  }

}
