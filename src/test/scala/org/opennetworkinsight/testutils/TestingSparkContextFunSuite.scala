/**
  * THIS CODE WAS COPIED DIRECTLY FROM THE OPEN SOURCE PROJECT TAP (Trusted Analytics Platform)
  * which has an Apache V2.0
  */

package org.opennetworkinsight.testutils

import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import org.scalatest.{BeforeAndAfterAll, FunSuite}

trait TestingSparkContextFunSuite extends FunSuite with BeforeAndAfterAll {

  var sparkContext: SparkContext = null
  var sqlContext : SQLContext = null

  override def beforeAll() = {
    sparkContext = TestingSparkContext.sparkContext
    sqlContext = new SQLContext(sparkContext)
  }

  /**
   * Clean up after the test is done
   */
  override def afterAll() = {
    TestingSparkContext.cleanUp()
    sparkContext = null
  }

}
