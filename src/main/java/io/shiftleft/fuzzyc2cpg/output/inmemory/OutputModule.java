package io.shiftleft.fuzzyc2cpg.output.inmemory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import io.shiftleft.cpgloading.ProtoCpgLoader;
import io.shiftleft.cpgloading.tinkergraph.ProtoToCpg;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;
import io.shiftleft.queryprimitives.steps.starters.Cpg;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutputModule implements CpgOutputModule {
  private static Logger logger = LoggerFactory.getLogger(OutputModule.class);

  private Map<Long, Vertex> nodeIdToVertex;
  private LinkedList<CpgStruct.Builder> cpgBuilders;
  private Cpg cpg;

  protected OutputModule() {
    this.nodeIdToVertex = new HashMap<>();
    this.cpgBuilders = new LinkedList<>();
  }

  public Cpg getInternalGraph() {
    return cpg;
  }

  @Override
  public void setOutputIdentifier(String identifier) {

  }

  @Override
  public void persistCpg(CpgStruct.Builder cpg) {

    synchronized (cpgBuilders) {
      cpgBuilders.add(cpg);
    }
  }

  public void persist() {
    constructTinkerGraphFromCpg();
  }

  private void constructTinkerGraphFromCpg() {
    CpgStruct.Builder mergedBuilder = CpgStruct.newBuilder();

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
  }

}
