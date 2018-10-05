package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.outputmodules.ProtoOutputModule;
import java.util.Arrays;

public class Main {

  public static void main(String[] args) {
    String[] fileAndDirNames = readInputDirFromArguments(args);
    Fuzzyc2Cpg fuzzyc2Cpg = new Fuzzyc2Cpg(new ProtoOutputModule());
    fuzzyc2Cpg.runAndOutput(fileAndDirNames);
  }

  private static String[] readInputDirFromArguments(String[] args) {
    if (args.length == 0) {
      showUsageAndExit();
    }
    return Arrays.copyOfRange(args, 0, args.length);
  }

  private static void showUsageAndExit() {
    System.out.println("fuzzyc2cpg <dir_1> ... <dir_n>");
    System.exit(1);
  }

}
