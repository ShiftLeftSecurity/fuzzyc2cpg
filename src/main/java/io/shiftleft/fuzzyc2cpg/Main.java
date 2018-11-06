package io.shiftleft.fuzzyc2cpg;

import io.shiftleft.fuzzyc2cpg.output.protobuf.OutputModuleFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Main {
  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    String[] fileAndDirNames = readInputDirFromArguments(args);
    try {
      Fuzzyc2Cpg fuzzyc2Cpg = new Fuzzyc2Cpg(new OutputModuleFactory("cpg.bin.zip", true, false));
      fuzzyc2Cpg.runAndOutput(fileAndDirNames);
    } catch (Exception exception) {
      logger.error("Failed to generate CPG.", exception);
      System.exit(1);
    }
    System.exit(0);
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
