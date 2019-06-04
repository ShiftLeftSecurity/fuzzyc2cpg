package io.shiftleft.fuzzyc2cpg;

import java.util.concurrent.atomic.AtomicLong;

public class IdPool {

  private static AtomicLong currentId = new AtomicLong(1);

  public static long getNextId() {
    return currentId.incrementAndGet();
  }

}
