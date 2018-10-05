package io.shiftleft.fuzzyc2cpg.outputmodules;

import io.shiftleft.proto.cpg.Cpg.CpgStruct.Builder;

public interface OutputModule {

  void output(Builder cpgBuilder, String outputFilename);

}
