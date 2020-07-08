package io.shiftleft.fuzzyc2cpg.output.inmemory;

import io.shiftleft.codepropertygraph.Cpg;
import io.shiftleft.codepropertygraph.cpgloading.ProtoCpgLoader;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule;
import overflowdb.OdbConfig;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;

import java.util.LinkedList;
import java.util.List;

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
  public void setOutputIdentifier(String identifier) { }

  @Override
  public void persistCpg(CpgStruct.Builder cpg) {
    synchronized (cpgBuilders) {
      cpgBuilders.add(cpg);
    }
  }

  public void persist() {
    CpgStruct.Builder mergedBuilder = CpgStruct.newBuilder();

    cpgBuilders.forEach(builder -> {
      mergedBuilder.mergeFrom(builder.build());
    });

    List<CpgStruct> list = new LinkedList<>();
    list.add(mergedBuilder.build());
    cpg = ProtoCpgLoader.loadFromListOfProtos(list, OdbConfig.withoutOverflow());
  }

}
