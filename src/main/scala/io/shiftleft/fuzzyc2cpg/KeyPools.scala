package io.shiftleft.fuzzyc2cpg

import io.shiftleft.passes.{IntervalKeyPool, KeyPool}

object KeyPools {

  /**
    * Divide the keyspace into n intervals and return
    * a list of corresponding key pools.
    * */
  def obtain(n: Long, maxValue: Long = Long.MaxValue): List[KeyPool] = {
    val nIntervals = Math.max(n, 1)
    val intervalLen: Long = maxValue / nIntervals
    List.range(0, nIntervals).map { i =>
      val first = i * intervalLen
      val last = first + intervalLen - 1
      new IntervalKeyPool(first, last)
    }
  }

}
