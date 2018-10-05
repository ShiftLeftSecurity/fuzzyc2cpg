package io.shiftleft.fuzzyc2cpg.outputmodules;

import io.shiftleft.cpgloading.tinkergraph.ProtoToCpg;
import io.shiftleft.proto.cpg.Cpg.CpgStruct.Builder;
import io.shiftleft.cpgloading.ProtoCpgLoader;
import io.shiftleft.queryprimitives.steps.starters.Cpg;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Output module that writes CPG into in memory graph database
 * */
public class InMemoryGraphOutputModule implements OutputModule {

  Cpg cpg;

  @Override
  public void output(Builder cpgBuilder, String outputFilename) {
    ProtoToCpg protoToCpg = new ProtoToCpg();
    ProtoCpgLoader protoCpgLoader = new ProtoCpgLoader(protoToCpg);

    byte[] bytes = cpgBuilder.build().toByteArray();
    InputStream inputStream = new ByteArrayInputStream(bytes);
    try {
      cpg = protoCpgLoader.loadFromInputStream(inputStream);
    } catch (IOException e) {
      System.err.println("Error loading CPG from byte array input stream");
    }
  }

  public Cpg getCpg() {
    return cpg;
  }

}
