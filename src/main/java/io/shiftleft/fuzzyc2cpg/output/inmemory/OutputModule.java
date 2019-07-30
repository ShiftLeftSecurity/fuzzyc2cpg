package io.shiftleft.fuzzyc2cpg.output.inmemory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.shiftleft.codepropertygraph.Cpg;
import io.shiftleft.codepropertygraph.cpgloading.OverflowDbConfig;
import io.shiftleft.codepropertygraph.cpgloading.ProtoCpgLoader;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;

public class OutputModule implements CpgOutputModule {

  private LinkedList<CpgStruct.Builder> cpgBuilders;
  private Cpg cpg;

  protected OutputModule() {
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

    List<CpgStruct> list = new LinkedList<>();
    list.add(mergedBuilder.build());
    cpg = ProtoCpgLoader.loadFromListOfProtos(list, OverflowDbConfig.withDefaults().disabled());

  }

}
