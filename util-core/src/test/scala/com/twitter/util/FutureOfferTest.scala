package com.twitter.concurrent

import org.scalatest.WordSpec
import org.scalatestplus.mockito.MockitoSugar
import com.twitter.util.{Promise, Return}

class FutureOfferTest extends WordSpec with MockitoSugar {
  "Future.toOffer" should {
    "activate when future is satisfied (poll)" in {
      val p = new Promise[Int]
      val o = p.toOffer
      assert(o.prepare().poll == None)
      p() = Return(123)
      assert(o.prepare().poll match {
        case Some(Return(tx)) =>
          tx.ack().poll match {
            case Some(Return(Tx.Commit(Return(123)))) => true
            case _ => false
          }
        case _ => false
      })
    }
  }
}
