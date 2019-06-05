package io.shiftleft.fuzzyc2cpg.output.inmemory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

import io.shiftleft.codepropertygraph.Cpg;
import io.shiftleft.codepropertygraph.cpgloading.OnDiskOverflowConfig;
import io.shiftleft.codepropertygraph.cpgloading.ProtoCpgLoader;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;

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
  public void setOutputSubDir(String subdir) {

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

    ProtoCpgLoader protoCpgLoader = new ProtoCpgLoader();

    byte[] bytes = mergedBuilder.build().toByteArray();
    InputStream inputStream = new ByteArrayInputStream(bytes);
    try {
      cpg = protoCpgLoader.loadFromInputStream(inputStream, Optional.empty());
    } catch (IOException e) {
      System.err.println("Error loading CPG from byte array input stream");
      cpg = null;
    }
  }

}
