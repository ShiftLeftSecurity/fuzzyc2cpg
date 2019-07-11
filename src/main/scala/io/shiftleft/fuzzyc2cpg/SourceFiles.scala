package io.shiftleft.fuzzyc2cpg

import better.files._

object SourceFiles {

  /**
    * For a given array of input paths, determine all C/C++
    * source files by inspecting filename extensions.
    * */
  def determine(inputPaths: List[String]): List[String] = {

    def hasSourceFileExtension(file: File): Boolean = {
      val ext = file.extension
      ext.contains(".c") || ext.contains(".cpp") || ext.contains(".h") || ext.contains(".hpp")
    }

    val (dirs, files) = inputPaths.partition(File(_).isDirectory)

    val matchingFiles = files.filter(f => hasSourceFileExtension(File(f)))
    val matchingFilesFromDirs = dirs
      .flatMap(dir => File(dir).listRecursively.filter(hasSourceFileExtension))
      .map(File(".").path.relativize(_).toString)

    matchingFiles ++ matchingFilesFromDirs
  }

}
