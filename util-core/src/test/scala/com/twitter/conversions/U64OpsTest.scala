package com.twitter.conversions

import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class U64OpsTest extends AnyFunSuite with ScalaCheckDrivenPropertyChecks {
  import com.twitter.conversions.U64Ops._

  test("toU64HextString") {
    forAll { l: Long => assert(l.toU64HexString == "%016x".format(l)) }
  }

  test("toU64Long") {
    forAll { l: Long =>
      val s = "%016x".format(l)
      assert(s.toU64Long == java.lang.Long.parseUnsignedLong(s, 16))
    }
  }
}
