package io.shiftleft.fuzzyc2cpg

import better.files._

object SourceFiles {

  /**
    * For a given array of input paths, determine all C/C++
    * source files by inspecting filename extensions.
    * */
  def determine(inputPaths: Set[String], sourceFileExtensions: Set[String]): Set[String] = {
    def hasSourceFileExtension(file: File): Boolean =
      file.extension.exists(sourceFileExtensions.contains)

    val (dirs, files) = inputPaths
      .map(File(_))
      .partition(_.isDirectory)

    val matchingFiles = files.filter(hasSourceFileExtension).map(_.toString)
    val matchingFilesFromDirs = dirs
      .flatMap(_.listRecursively.filter(hasSourceFileExtension))
      .map(File(".").path.relativize(_).toString)

    matchingFiles ++ matchingFilesFromDirs
  }
}
