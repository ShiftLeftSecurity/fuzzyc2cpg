package io.shiftleft.fuzzyc2cpg.outputmodules;

import io.shiftleft.cpgloading.tinkergraph.ProtoToCpg;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;
import io.shiftleft.cpgloading.ProtoCpgLoader;
import io.shiftleft.queryprimitives.steps.starters.Cpg;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Output module that writes CPG into in memory graph database
 * */
public class InMemoryGraphOutputModule implements OutputModule {

  private final List<CpgStruct.Builder> cpgBuilders = new ArrayList<>();

  @Override
  public void output(CpgStruct.Builder cpgBuilder, String outputFilename) {
    cpgBuilders.add(cpgBuilder);
  }

  public Cpg getCpg() {
    CpgStruct.Builder mergedBuilder = CpgStruct.newBuilder();
    Cpg cpg;

    cpgBuilders.forEach(builder -> {
      mergedBuilder.mergeFrom(builder.build());
    });

    ProtoToCpg protoToCpg = new ProtoToCpg();
    ProtoCpgLoader protoCpgLoader = new ProtoCpgLoader(protoToCpg);

    byte[] bytes = mergedBuilder.build().toByteArray();
    InputStream inputStream = new ByteArrayInputStream(bytes);
    try {
      cpg = protoCpgLoader.loadFromInputStream(inputStream);
    } catch (IOException e) {
      System.err.println("Error loading CPG from byte array input stream");
      cpg = null;
    }

    return cpg;
  }

}
