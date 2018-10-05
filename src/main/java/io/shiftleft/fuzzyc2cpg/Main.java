package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.outputmodules.ProtoOutputModule;

public class Main {

  public static void main(String[] args) {
    String[] fileAndDirNames = {"input"};
    Fuzzyc2Cpg fuzzyc2Cpg = new Fuzzyc2Cpg(new ProtoOutputModule());
    fuzzyc2Cpg.runAndOutput(fileAndDirNames);
  }

}
