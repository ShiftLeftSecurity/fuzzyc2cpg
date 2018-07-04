package io.shiftleft.fuzzyc2cpg;

public class IdPool {

  static long currentId = 1;

  public static long getNextId() {
    return currentId++;
  }

}
