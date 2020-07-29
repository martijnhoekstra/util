package com.twitter.conversions

import com.twitter.util.StorageUnit
import org.scalatest.funsuite.AnyFunSuite
import com.twitter.conversions.StorageUnitOps._

class StorageUnitOpsTest extends AnyFunSuite {

  test("converts") {
    assert(StorageUnit.fromBytes(1) == 1.byte)
    assert(StorageUnit.fromBytes(2) == 2.bytes)

    assert(StorageUnit.fromKilobytes(1) == 1.kilobyte)
    assert(StorageUnit.fromKilobytes(3) == 3.kilobytes)

    assert(StorageUnit.fromMegabytes(1) == 1.megabyte)
    assert(StorageUnit.fromMegabytes(4) == 4.megabytes)

    assert(StorageUnit.fromGigabytes(1) == 1.gigabyte)
    assert(StorageUnit.fromGigabytes(5) == 5.gigabytes)

    assert(StorageUnit.fromTerabytes(1) == 1.terabyte)
    assert(StorageUnit.fromTerabytes(6) == 6.terabytes)

    assert(StorageUnit.fromPetabytes(1) == 1.petabyte)
    assert(StorageUnit.fromPetabytes(7) == 7.petabytes)
  }

}
