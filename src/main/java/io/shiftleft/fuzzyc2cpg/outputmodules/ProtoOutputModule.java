package io.shiftleft.fuzzyc2cpg.outputmodules;

import io.shiftleft.proto.cpg.Cpg.CpgStruct;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Builder;
import java.io.FileOutputStream;
import java.io.IOException;

public class ProtoOutputModule implements OutputModule {

  public void output(Builder cpgBuilder, String outputFilename) {
    CpgStruct cpgStruct = cpgBuilder.build();
    try (FileOutputStream outStream = new FileOutputStream(outputFilename)) {
      cpgStruct.writeTo(outStream);
    } catch (IOException e) {
      System.err.println("Error writing output file: " + outputFilename);
    }
  }

}
