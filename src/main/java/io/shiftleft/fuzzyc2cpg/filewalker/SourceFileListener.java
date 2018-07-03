package io.shiftleft.fuzzyc2cpg.filewalker;

import java.nio.file.Path;

/**
 * Abstract base class for classes observing the SourceFileWalker.
 */

public abstract class SourceFileListener {

  public abstract void initialize();

  public abstract void shutdown();

  public abstract void visitFile(Path filename);

  public abstract void preVisitDirectory(Path dir);

  public abstract void postVisitDirectory(Path dir);

}
