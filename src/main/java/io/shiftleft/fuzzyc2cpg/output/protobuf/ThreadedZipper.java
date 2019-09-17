package io.shiftleft.fuzzyc2cpg.output.protobuf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ThreadedZipper extends Thread {
  private Logger logger = LoggerFactory.getLogger(getClass());
  private Path protoDir;
  private String outputFile;
  private static long TIMEOUT = Long.MAX_VALUE;

  ThreadedZipper(Path protoDir, String outputFile) {
    this.protoDir = protoDir;
    this.outputFile = outputFile;
  }

  private void doCopy(ZipEntry entry) {
    try {
      logger.debug("copying from " + entry.from + " to " + entry.to);
      Files.copy(entry.from, entry.to, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      logger.error("Failed to copy files in the zipper", exception);
      throw new RuntimeException(exception);
    }
  }

  @Override
  public void run() {
    ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    try {
      // create zip file system
      Map<String, String> env = new HashMap<>();
      env.put("create", "true");

      // handle the -n parameter
      Path path = Paths.get(this.outputFile);
      URI zipUri;
      try {
        zipUri = URI.create("jar:" + path.toUri().toString());
      } catch (Exception exception) {
        pool.shutdownNow();
        logger.error(
            "Failed to create URI using path " + path.toAbsolutePath().toString(), exception);
        throw new RuntimeException(exception);
      }
      if (Files.exists(path)) {
        Files.delete(path);
      }
      logger.debug("Writing file to: " + path);
      try (FileSystem zipFileSystem = FileSystems.newFileSystem(zipUri, env)) {

        // if the input is a file
        File inputFile = protoDir.toFile();
        if (inputFile.isFile()) {
          // TODO: should crash, we expect a directory
          logger.debug("Zipping " + inputFile.getName() + " file");
          doCopy(new ZipEntry(inputFile, zipFileSystem));
        } else {
          // loop over sorted files
          File[] files = inputFile.listFiles();
          if (files == null) {
            logger.error("Couldn't list files in " + inputFile);
            return;
          }
          Arrays.sort(files);

          List<ZipEntry> entries = Arrays.stream(files).flatMap(f -> {
            if (f.isFile()) {
              return Stream.of(new ZipEntry(f, zipFileSystem));
            } else {
              return Stream.empty();
            }
          }).collect(Collectors.toList());

          // create the few special entries in sorted order, while the thread pool will create
          // the rest randomly
          Iterator<ZipEntry> iterator = entries.iterator();
          while (iterator.hasNext()) {
            ZipEntry entry = iterator.next();

            if (!entry.getTo().getFileName().toString().startsWith("$")) {
              break;
            }

            doCopy(entry);

            iterator.remove();
          }
          // force create ZIP entries in sorted order before copying in thread pool
          entries.forEach(entry ->
              pool.submit(new Runnable() {
                public void run() {
                  doCopy(entry);
                }
              })
          );

          // NOTE: Abandon hope all ye who enter here.
          // Before you even start asking yourself if that's really the right order...
          // Yes, that's the right way to shutdown and await for the pool
          // of executors to finish. Otherwise, you might still be having some race-conditions.
          // That's what the book says. Move on.
          pool.shutdown();
          if (!pool.awaitTermination(TIMEOUT, TimeUnit.SECONDS)) {
            logger.error("Failed to finish tasks in a timely manner. Cleaning up...");
            pool.shutdownNow();
            if (!pool.awaitTermination(TIMEOUT, TimeUnit.SECONDS)) {
              throw new RuntimeException("Executor pool didn't terminate on time");
            }
          }
        }
      }
    } catch (IOException | InterruptedException exception) {
      pool.shutdownNow();
      logger.error("Failed to create the zip file", exception);
      throw new RuntimeException(exception);
    }
  }

  private class ZipEntry {
    private Path from;
    private Path to;

    public ZipEntry(File file, FileSystem fs) {
      this.from = Paths.get(file.getAbsolutePath());
      this.to = fs.getPath(from.getFileName().toString());
    }

    public Path getFrom() {
      return from;
    }

    public Path getTo() {
      return to;
    }
  }
}
