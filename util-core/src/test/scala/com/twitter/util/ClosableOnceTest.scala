package com.twitter.util

import com.twitter.conversions.DurationOps._
import org.scalatest.funsuite.AnyFunSuite

class ClosableOnceTest extends AnyFunSuite {

  private def ready[T <: Awaitable[_]](awaitable: T): T =
    Await.ready(awaitable, 10.seconds)

  test("wrap") {
    var closedCalls = 0
    val underlying = new Closable {
      def close(deadline: Time): Future[Unit] = {
        closedCalls += 1
        Future.Done
      }
    }

    val closableOnce = ClosableOnce.of(underlying)
    assert(closableOnce.isClosed == false)
    closableOnce.close()
    assert(closableOnce.isClosed == true)
    closableOnce.close()
    assert(closedCalls == 1)
  }

  test("if closeOnce throws an exception, the closeable is closed with that exception") {
    val ex = new Exception("boom")
    var closedCalls = 0
    val closableOnce = new ClosableOnce {
      protected def closeOnce(deadline: Time): Future[Unit] = {
        closedCalls += 1
        throw ex
      }
    }

    assert(closableOnce.isClosed == false)

    assert(ready(closableOnce.close()).poll.get.throwable == ex)
    assert(closedCalls == 1)
    assert(closableOnce.isClosed == true)

    assert(ready(closableOnce.close()).poll.get.throwable == ex)
    assert(closedCalls == 1)
  }
}
