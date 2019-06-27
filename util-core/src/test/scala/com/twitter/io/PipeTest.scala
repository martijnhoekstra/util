package com.twitter.io

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Await, Future, MockTimer, Return, Time}
import org.scalatest.{FunSuite, Matchers}

class PipeTest extends FunSuite with Matchers {

  private def await[A](f: Future[A]): A = Await.result(f, 5.seconds)

  private def arr(i: Int, j: Int) = Array.range(i, j).map(_.toByte)
  private def buf(i: Int, j: Int) = Buf.ByteArray.Owned(arr(i, j))

  private def assertRead(r: Reader[Buf], i: Int, j: Int): Unit = {
    val n = j - i
    val f = r.read()
    assertRead(f, i, j)
  }

  private def assertRead(f: Future[Option[Buf]], i: Int, j: Int): Unit = {
    assert(f.isDefined)
    val b = await(f)
    assert(toSeq(b) == Seq.range(i, j))
  }

  private def toSeq(b: Option[Buf]): Seq[Byte] = b match {
    case None => fail("Expected full buffer")
    case Some(buf) =>
      val a = new Array[Byte](buf.length)
      buf.write(a, 0)
      a.toSeq
  }

  private def assertWrite(w: Writer[Buf], i: Int, j: Int): Unit = {
    val buf = Buf.ByteArray.Owned(Array.range(i, j).map(_.toByte))
    val f = w.write(buf)
    assert(f.isDefined)
    assert(await(f.liftToTry) == Return.Unit)
  }

  private def assertWriteEmpty(w: Writer[Buf]): Unit = {
    val f = w.write(Buf.Empty)
    assert(f.isDefined)
    assert(await(f.liftToTry) == Return.Unit)
  }

  private def assertReadEofAndClosed(rw: Pipe[Buf]): Unit = {
    assertReadNone(rw)
    assert(rw.close().isDone)
  }

  private def assertReadNone(r: Reader[Buf]): Unit =
    assert(await(r.read()).isEmpty)

  private val failedEx = new RuntimeException("ʕ •ᴥ•ʔ")

  private def assertFailedEx(f: Future[_]): Unit = {
    val thrown = intercept[RuntimeException] {
      await(f)
    }
    assert(thrown == failedEx)
  }

  test("Pipe") {
    val rw = new Pipe[Buf]
    val wf = rw.write(buf(0, 6))
    assert(!wf.isDefined)
    assertRead(rw, 0, 6)
    assert(wf.isDefined)
    assert(await(wf.liftToTry) == Return(()))
  }

  test("Reader.readAll") {
    val rw = new Pipe[Buf]
    val all = Reader.readAll(rw)
    assert(!all.isDefined)
    assertWrite(rw, 0, 3)
    assertWrite(rw, 3, 6)
    assert(!all.isDefined)
    assertWriteEmpty(rw)
    assert(!all.isDefined)
    await(rw.close())
    assert(all.isDefined)
    val buf = await(all)
    assert(toSeq(Some(buf)) == Seq.range(0, 6))
  }

  test("write before read") {
    val rw = new Pipe[Buf]
    val wf = rw.write(buf(0, 6))
    assert(!wf.isDefined)
    val rf = rw.read()
    assert(rf.isDefined)
    assert(toSeq(await(rf)) == Seq.range(0, 6))
  }

  test("fail while reading") {
    val rw = new Pipe[Buf]
    var closed = false
    rw.onClose.ensure { closed = true }
    val rf = rw.read()
    assert(!rf.isDefined)
    assert(!closed)
    val exc = new Exception
    rw.fail(exc)
    assert(closed)
    assert(rf.isDefined)
    val exc1 = intercept[Exception] { await(rf) }
    assert(exc eq exc1)
  }

  test("fail before reading") {
    val rw = new Pipe[Buf]
    rw.fail(new Exception)
    val rf = rw.read()
    assert(rf.isDefined)
    intercept[Exception] { await(rf) }
  }

  test("discard") {
    val rw = new Pipe[Buf]
    var closed = false
    rw.onClose.ensure { closed = true }
    rw.discard()
    val rf = rw.read()
    assert(rf.isDefined)
    assert(closed)
    intercept[ReaderDiscardedException] { await(rf) }
  }

  test("close") {
    val rw = new Pipe[Buf]
    var closed = false
    rw.onClose.ensure { closed = true }
    val wf = rw.write(buf(0, 6)) before rw.close()
    assert(!wf.isDefined)
    assert(!closed)
    assert(await(rw.read()).contains(buf(0, 6)))
    assert(!wf.isDefined)
    assertReadEofAndClosed(rw)
    assert(closed)
  }

  test("write then reads then close") { testWriteReadClose }
  def testWriteReadClose = {
    val rw = new Pipe[Buf]
    val wf = rw.write(buf(0, 6))

    assert(!wf.isDone)
    assertRead(rw, 0, 6)
    assert(wf.isDone)
    assert(!rw.close().isDone)
    assertReadEofAndClosed(rw)
  }

  test("read then write then close") { readWriteClose }
  def readWriteClose = {
    val rw = new Pipe[Buf]

    val rf = rw.read()
    assert(!rf.isDefined)

    val wf = rw.write(buf(0, 6))
    assert(wf.isDone)
    assertRead(rf, 0, 6)

    assert(!rw.close().isDone)
    assertReadEofAndClosed(rw)
  }

  test("write after fail") {
    val rw = new Pipe[Buf]
    rw.fail(failedEx)

    assertFailedEx(rw.write(buf(0, 6)))
    val cf = rw.close()
    assert(!cf.isDone)

    assertFailedEx(rw.read())
    assertFailedEx(cf)
  }

  test("write after close") {
    val rw = new Pipe[Buf]
    val cf = rw.close()
    assert(!cf.isDone)
    assertReadEofAndClosed(rw)
    assert(cf.isDone)

    intercept[IllegalStateException] {
      await(rw.write(buf(0, 1)))
    }
  }

  test("write while write pending") {
    val rw = new Pipe[Buf]
    var closed = false
    rw.onClose.ensure { closed = true }
    val wf = rw.write(buf(0, 1))
    assert(!wf.isDone)

    intercept[IllegalStateException] {
      await(rw.write(buf(0, 1)))
    }

    // the extraneous write should not mess with the 1st one.
    assertRead(rw, 0, 1)
    assert(!closed)
  }

  test("read after fail") {
    val rw = new Pipe[Buf]
    rw.fail(failedEx)
    assertFailedEx(rw.read())
  }

  def readAfterCloseNoPendingReads = {
    val rw = new Pipe[Buf]
    assert(!rw.close().isDone)
    assertReadEofAndClosed(rw)
  }
  test("read after close with no pending reads") {
    readAfterCloseNoPendingReads
  }

  def readAfterClosePendingData = {
    val rw = new Pipe[Buf]

    val wf = rw.write(buf(0, 1))
    assert(!wf.isDone)

    // close before the write is satisfied wipes the pending write
    assert(!rw.close().isDone)
    intercept[IllegalStateException] {
      await(wf)
    }
    assertReadNone(rw)
    intercept[IllegalStateException] {
      await(rw.onClose)
    }
  }
  test("read after close with pending data") { readAfterClosePendingData }

  test("read while reading") {
    val rw = new Pipe[Buf]
    var closed = false
    rw.onClose.ensure { closed = true }
    val rf = rw.read()
    intercept[IllegalStateException] {
      await(rw.read())
    }
    assert(!rf.isDefined)
    assert(!closed)
  }

  test("discard with pending read") {
    val rw = new Pipe[Buf]

    val rf = rw.read()
    rw.discard()

    intercept[ReaderDiscardedException] {
      await(rf)
    }
  }

  test("discard with pending write") {
    val rw = new Pipe[Buf]

    val wf = rw.write(buf(0, 1))
    rw.discard()

    intercept[ReaderDiscardedException] {
      await(wf)
    }
  }

  test("close not satisfied until writes are read") {
    val rw = new Pipe[Buf]
    val cf = rw.write(buf(0, 6)).before(rw.close())
    assert(!cf.isDone)

    assertRead(rw, 0, 6)
    assert(!cf.isDone)
    assertReadEofAndClosed(rw)
  }

  def closeNotSatisfiedUntillAllReadsDone = {
    val rw = new Pipe[Buf]
    val rf = rw.read()
    val cf = rf.flatMap { _ =>
      rw.close()
    }
    assert(!rf.isDefined)
    assert(!cf.isDone)

    assert(rw.write(buf(0, 3)).isDone)

    assertRead(rf, 0, 3)
    assert(!cf.isDone)
    assertReadEofAndClosed(rw)
  }
  test("close not satisfied until reads are fulfilled")(closeNotSatisfiedUntillAllReadsDone)

  def closeWhileReadPending = {
    val rw = new Pipe[Buf]
    val rf = rw.read()
    assert(!rf.isDefined)

    assert(rw.close().isDone)
    assert(rf.isDefined)
  }
  test("close while read pending")(closeWhileReadPending)

  def closeTwice = {
    val rw = new Pipe[Buf]
    assert(!rw.close().isDone)
    assertReadEofAndClosed(rw)
    assert(rw.close().isDone)
    assertReadEofAndClosed(rw)
  }
  test("close then close")(closeTwice)

  test("close after fail") {
    val rw = new Pipe[Buf]
    rw.fail(failedEx)
    val cf = rw.close()
    assert(!cf.isDone)

    assertFailedEx(rw.read())
    assertFailedEx(cf)
  }

  test("close before fail") {
    val timer = new MockTimer()
    Time.withCurrentTimeFrozen { ctrl =>
      val rw = new Pipe[Buf](timer)
      val cf = rw.close(1.second)
      assert(!cf.isDone)

      ctrl.advance(1.second)
      timer.tick()

      rw.fail(failedEx)

      assertFailedEx(rw.read())
    }
  }

  test("close before fail within deadline") {
    val timer = new MockTimer()
    Time.withCurrentTimeFrozen { _ =>
      val rw = new Pipe[Buf](timer)
      val cf = rw.close(1.second)
      assert(!cf.isDone)

      rw.fail(failedEx)
      assert(!cf.isDone)

      assertFailedEx(rw.read())
      assertFailedEx(cf)
    }
  }

  test("close while write pending") {
    val rw = new Pipe[Buf]
    val wf = rw.write(buf(0, 1))
    assert(!wf.isDone)
    val cf = rw.close()
    assert(!cf.isDone)
    intercept[IllegalStateException] {
      await(wf)
    }
    assertReadNone(rw)
    intercept[IllegalStateException] {
      await(rw.onClose)
    }
  }

  test("close respects deadline") {
    val mockTimer = new MockTimer()
    Time.withCurrentTimeFrozen { timeCtrl =>
      val rw = new Pipe[Buf](mockTimer)
      val wf = rw.write(buf(0, 6))

      rw.close(1.second)

      assert(!wf.isDefined)
      assert(!rw.onClose.isDefined)

      timeCtrl.advance(1.second)
      mockTimer.tick()

      intercept[IllegalStateException] {
        await(wf)
      }

      intercept[IllegalStateException] {
        await(rw.onClose)
      }
      assertReadNone(rw)
    }
  }

  test("read complete data before close deadline") {
    val mockTimer = new MockTimer()
    val rw = new Pipe[Buf](mockTimer)
    Time.withCurrentTimeFrozen { timeCtrl =>
      val wf = rw.write(buf(0, 6)) before rw.close(1.second)

      assert(!wf.isDefined)
      assertRead(rw, 0, 6)
      assertReadNone(rw)
      assert(wf.isDefined)

      timeCtrl.advance(1.second)
      mockTimer.tick()

      assertReadEofAndClosed(rw)
    }
  }

  test("multiple reads read complete data before close deadline") {
    val mockTimer = new MockTimer()
    val buf = Buf.Utf8("foo")
    Time.withCurrentTimeFrozen { timeCtrl =>
      val rw = new Pipe[Buf](mockTimer)
      val writef = rw.write(buf)

      rw.close(1.second)

      assert(!writef.isDefined)
      assert(await(Reader.readAll(rw)) == buf)
      assertReadNone(rw)
      assert(writef.isDefined)

      timeCtrl.advance(1.second)
      mockTimer.tick()

      assertReadEofAndClosed(rw)
    }
  }

}
