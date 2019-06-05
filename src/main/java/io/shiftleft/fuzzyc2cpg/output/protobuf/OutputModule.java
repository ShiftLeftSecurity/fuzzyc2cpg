package io.shiftleft.fuzzyc2cpg.output.protobuf;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import io.shiftleft.fuzzyc2cpg.output.CpgOutputModule;
import io.shiftleft.proto.cpg.Cpg.CpgStruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;

public class OutputModule implements CpgOutputModule {
  private Logger logger = LoggerFactory.getLogger(getClass());
  private static String ProtoSuffix = ".bin";

  private CpgStruct protoCpg;
  private final Path protoTempDir;

  private boolean writeToDisk;
  private boolean keepInternalGraph;

  private String outputIdentifier;
  private String outputSubDir = "";


  public OutputModule(boolean keepInternalGraph,
                      boolean writeToDisk,
                      Path protoTempDir) {
    this.keepInternalGraph = keepInternalGraph;
    this.writeToDisk = writeToDisk;
    this.protoTempDir = protoTempDir;
  }

  CpgStruct getProtoCpg() {
    return protoCpg;
  }


  @Override
  public void setOutputIdentifier(String identifier) {
    outputIdentifier = identifier;
  }

  @Override
  public void setOutputSubDir(String outputSubDir) {
    this.outputSubDir = outputSubDir;
  }

  /**
   * This is called for each code property graph.
   * */

  @Override
  public void persistCpg(CpgStruct.Builder cpg) throws IOException {
    CpgStruct buildCpg = cpg.build();
    if (writeToDisk && buildCpg.getSerializedSize() > 0) {
      String outputFilename = getOutputFileName();
      try (FileOutputStream outStream = new FileOutputStream(outputFilename)) {
        buildCpg.writeTo(outStream);
      }
    }
    if (keepInternalGraph) {
      this.protoCpg = buildCpg;
    }
  }

  /**
   * The complete handling for an already existing file should not be necessary.
   * This was added as a last resort to not get incomplete cpgs.
   * In case we have a hash collision, the resulting cpg part file names will not
   * be identical over different java2cpg runs.
   */
  private String getOutputFileName() {
    String outputFilename = null;
    int postfix = 0;
    boolean fileExists = true;
    int resolveAttemptCounter = 0;

    while (fileExists && resolveAttemptCounter < 10) {
      outputFilename = generateOutputFilename(postfix);
      if (Files.exists(Paths.get(outputFilename))) {
        postfix = ThreadLocalRandom.current().nextInt(0, 100000);

        logger.warn("Hash collision identifier={}, postfix={}." +
            " Retry with random postfix.", outputIdentifier, postfix);

        resolveAttemptCounter++;
      } else {
        fileExists = false;
      }
    }

    if (fileExists) {
      logger.error("Unable to resolve hash collision. Cpg will be incomplete");
    }

    return outputFilename;
  }

  // You might be thinking that it would be much wiser to choose
  // filenames that actually tell us which file the CPG was generated from.
  // If one wanted to do that, one would first need to use an encoding in
  // order to ensure that path separators are not used in zip file entries.
  // Even then though, we have no control over the length of names, and so
  // they need to be truncated such that they can be used on different
  // platforms. Long story short, hashing here means that we lose the ability
  // to encode any useful information in the filename, however, we end up
  // with a more resilient file format that works well on multiple platforms.

  private String generateOutputFilename(int postfix) {
    HashFunction hashFunction = Hashing.murmur3_128();

    Hasher hasher = hashFunction.newHasher();
    hasher.putUnencodedChars(outputIdentifier);
    hasher.putInt(postfix);

    String dir = protoTempDir.toString() + File.separator;
    if (!outputSubDir.equals("")) {
      dir += outputSubDir + File.separator;
      new File(dir).mkdirs();
    }
    return dir + hasher.hash() + ProtoSuffix;
  }
}
