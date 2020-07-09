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
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final Path protoTempDir;
  private final boolean writeToDisk;

  public OutputModule(boolean writeToDisk,
                      Path protoTempDir) {
    this.writeToDisk = writeToDisk;
    this.protoTempDir = protoTempDir;
  }

  /**
   * This is called for each code property graph. There is one
   * code property graph per method, and one graph for the overall
   * program structure.
   * */

  @Override
  public void persistCpg(CpgStruct.Builder cpg, String identifier) throws IOException {
    CpgStruct buildCpg = cpg.build();
    if (writeToDisk) {
      String outputFilename = getOutputFileName(identifier);
      try (FileOutputStream outStream = new FileOutputStream(outputFilename)) {
        buildCpg.writeTo(outStream);
      }
    }
  }

  /**
   * The complete handling for an already existing file should not be necessary.
   * This was added as a last resort to not get incomplete cpgs.
   * In case we have a hash collision, the resulting cpg part file names will not
   * be identical over different java2cpg runs.
   */
  private String getOutputFileName(String outputIdentifier) {
    String outputFilename = null;
    int postfix = 0;
    boolean fileExists = true;
    int resolveAttemptCounter = 0;

    while (fileExists && resolveAttemptCounter < 10) {
      outputFilename = generateOutputFilename(postfix, outputIdentifier);
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

  private String generateOutputFilename(int postfix, String outputIdentifier) {
    HashFunction hashFunction = Hashing.murmur3_128();

    Hasher hasher = hashFunction.newHasher();
    hasher.putUnencodedChars(outputIdentifier);
    hasher.putInt(postfix);

    String protoSuffix = ".bin";
    return protoTempDir.toString() + File.separator + hasher.hash() + protoSuffix;
  }
}
