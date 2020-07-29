package com.twitter.io

import com.twitter.util.Activity
import org.scalatest.funsuite.AnyFunSuite
import scala.util.Random

class CachingActivitySourceTest extends AnyFunSuite {

  test("CachingActivitySource") {
    val cache = new CachingActivitySource[String](new ActivitySource[String] {
      def get(varName: String) = Activity.value(Random.alphanumeric.take(10).mkString)
    })

    val a = cache.get("a")
    assert(a == cache.get("a"))
  }

}
