package io.shiftleft.fuzzyc2cpg

import io.shiftleft.proto.cpg.Cpg.CpgStruct

import scala.collection.mutable

class FuzzyC2CpgCache {
  private val functionDeclarations = new mutable.HashMap[String, mutable.ListBuffer[(String, CpgStruct.Builder)]]()

  /**
    * Unless `remove` has been called for `signature`, add (outputIdentifier, cpg)
    * pair to the list declarations stored for `signature`.
    * */
  def add(signature: String, outputIdentifier: String, cpg: CpgStruct.Builder): Unit = {
    functionDeclarations.synchronized {
      if (functionDeclarations.contains(signature)) {
        val declList = functionDeclarations(signature)
        // null is the placeholder that indicates that we've removed
        // a function with this signature before, and hence, we do
        // not need to add it again
        if (declList == null) return
        if (declList.nonEmpty) {
          declList.append((outputIdentifier, cpg))
        }
      } else {
        functionDeclarations.put(signature, mutable.ListBuffer((outputIdentifier, cpg)))
      }
    }
  }

  /**
    * Register placeholder for `signature` to indicate that
    * a function definition exists for this declaration, and
    * therefore, no declaration should be written for functions
    * with this signature.
    * */
  def remove(signature: String): Unit = {
    functionDeclarations.synchronized {
      functionDeclarations.put(signature, null)
    }
  }

  def sortedSignatures: List[String] = {
    functionDeclarations.synchronized {
      functionDeclarations.filter(_._2 != null).keySet.toList.sorted
    }
  }

  def getDeclarations(signature: String): List[(String, CpgStruct.Builder)] = {
    functionDeclarations.synchronized {
      functionDeclarations(signature).toList.filter(_._2 != null)
    }
  }

}
