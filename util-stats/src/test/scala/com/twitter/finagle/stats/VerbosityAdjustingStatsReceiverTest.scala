package com.twitter.finagle.stats

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.OneInstancePerTest

class VerbosityAdjustingStatsReceiverTest extends AnyFunSuite with OneInstancePerTest {

  val inMemory = new InMemoryStatsReceiver()
  val verbose = new VerbosityAdjustingStatsReceiver(inMemory, Verbosity.Debug)

  test("adjusts the verbosity") {
    verbose.counter(Verbosity.Default, "foo")
    verbose.scope("foo").stat("bar")
    verbose.addGauge(Verbosity.Debug, "baz")(0f)

    assert(inMemory.verbosity(Seq("foo")) == Verbosity.Debug)
    assert(inMemory.verbosity(Seq("foo", "bar")) == Verbosity.Debug)
    assert(inMemory.verbosity(Seq("baz")) == Verbosity.Debug)
  }
}
