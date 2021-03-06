/*
 Copyright (c) 2011, 2012, 2013, 2014 The Regents of the University of
 California (Regents). All Rights Reserved.  Redistribution and use in
 source and binary forms, with or without modification, are permitted
 provided that the following conditions are met:

    * Redistributions of source code must retain the above
      copyright notice, this list of conditions and the following
      two paragraphs of disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      two paragraphs of disclaimer in the documentation and/or other materials
      provided with the distribution.
    * Neither the name of the Regents nor the names of its contributors
      may be used to endorse or promote products derived from this
      software without specific prior written permission.

 IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 REGENTS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT
 LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 A PARTICULAR PURPOSE. THE SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF
 ANY, PROVIDED HEREUNDER IS PROVIDED "AS IS". REGENTS HAS NO OBLIGATION
 TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 MODIFICATIONS.
*/

package Chisel
import scala.collection.mutable.ArrayBuffer

// Disable println warnings. It's what we do.
// scalastyle:off regex
/** This Singleton implements a log4j compatible interface.
  It is used through out the Chisel package to report errors and warnings
  detected at runtime.
  */
object ChiselError {
  var hasErrors: Boolean = false;
  private val ChiselErrors = new ArrayBuffer[ChiselError];

  def contains(chiselError: ChiselError): Boolean = {
    ChiselErrors.contains(chiselError)
  }

  def isEmpty: Boolean = {
    ChiselErrors.isEmpty
  }

  def getErrorList: List[ChiselError] = ChiselErrors.toList

  def clear() {
    ChiselErrors.clear()
    hasErrors = false
  }

  /** emit an error message */
  def error(chiselError: ChiselError) {
    hasErrors = true
    ChiselErrors += chiselError
  }

  def error(mf: => String, line: StackTraceElement) {
    val chiselError = new ChiselError(() => mf, line)
    error(chiselError)
  }

  def error(m: String) {
    val stack = Thread.currentThread().getStackTrace
    error(m, findFirstUserLine(stack) getOrElse stack(0))
  }

  /** Emit an informational message
    (useful to track long running passes) */
  def info(m: String): Unit =
    println(tag("info", Console.MAGENTA) + " [%2.3f] ".format(Driver.elapsedTime/1e3) + m)

  def warning(m: => String, errline: StackTraceElement) {
    if (Driver.wError) {
      error(m, errline)
    } else {
      ChiselErrors += new ChiselError(() => m, errline, 1)
    }
  }

  /** emit a warning message */
  def warning(m: => String) {
    val stack = Thread.currentThread().getStackTrace
    warning(m, findFirstUserLine(stack) getOrElse stack(0))
  }

  def findFirstUserLine(stack: Array[StackTraceElement]): Option[StackTraceElement] = {
    findFirstUserInd(stack) map { stack(_) }
  }

  def findFirstUserInd(stack: Array[StackTraceElement]): Option[Int] = {
    var seenChiselMain = false
    def isUserCode(ste: StackTraceElement): Boolean = {
      val className = ste.getClassName()
      try {
        val cls = Class.forName(className)
        if( cls.getSuperclass() == classOf[Module] ) {
          true
        } else {
        /* XXX Do it the old way until we figure if it is safe
               to remove from Node.scala
           var line: StackTraceElement = findFirstUserLine(Thread.currentThread().getStackTrace)
         */
          val dotPos = className.lastIndexOf('.')
          if( dotPos > 0 ) {
            if (className == "Chisel.chiselMain$") {
              seenChiselMain = true
            }
            (className.subSequence(0, dotPos) != "Chisel") && !className.contains("scala") &&
            !className.contains("java") && !className.contains("$$") && !className.startsWith("sun.")
          } else if (seenChiselMain) {
            // If we're above ChiselMain, we must be in "user" code.
            true
          } else {
            false
          }
        }
      } catch {
        case e: java.lang.ClassNotFoundException => false
      }
    }
    val idx = stack.indexWhere(isUserCode)
    if(idx < 0) {
      // println("COULDN'T FIND LINE NUMBER (" + stack(1) + ")")
      None
    } else {
      // There may be an anonymous method in this class which we skipped
      //  since it has "$$" in its name. If we find such a method lower
      //  down on the stack, return its index.
      val userStackElement = stack(idx)
      val userClassName = userStackElement.getClassName()
      val userFileName = userStackElement.getFileName()
      val lowestIdx = stack.indexWhere(ste => ste.getClassName().startsWith(userClassName) && ste.getFileName() == userFileName)
      Some(lowestIdx)
    }
  }

  // Print stack frames up to and including the "user" stack frame.
  def printChiselStackTrace() {
    val stack = Thread.currentThread().getStackTrace
    val idx = ChiselError.findFirstUserInd(stack)
    idx match {
      case None => {}
      case Some(x) => for (i <- 0 to x) println(stack(i))
    }
  }

  /** Prints error messages generated by Chisel at runtime. */
  def report() {
    if (!ChiselErrors.isEmpty) {
      for(err <- ChiselErrors)  err.print;
    }
  }

  /** Throws an exception if there has been any error recorded
    before this point. */
  def checkpoint() {
    if(hasErrors) {
      throw new IllegalStateException(
        Console.UNDERLINED + "CODE HAS " +
        Console.UNDERLINED + Console.BOLD + ChiselErrors.filter(_.isError).length + Console.RESET +
        Console.UNDERLINED + " " +
        Console.UNDERLINED + Console.RED + "ERRORS" + Console.RESET +
        Console.UNDERLINED + " and " +
        Console.UNDERLINED + Console.BOLD + ChiselErrors.filter(_.isWarning).length + Console.RESET +
        Console.UNDERLINED + " " +
        Console.UNDERLINED + Console.YELLOW + "WARNINGS" + Console.RESET)
    }
  }

  def tag(name: String, color: String): String =
    s"[${color}${name}${Console.RESET}]"
}

class ChiselError(val errmsgFun: () => String, val errline: StackTraceElement,
val errlevel: Int = 0) {

  val level = errlevel
  val line = errline
  val msgFun = errmsgFun

  def isError: Boolean = (level == 0)
  def isWarning : Boolean = (level == 1)

  def print() {
    /* Following conventions for error formatting */
    val levelstr =
      if (isError) {
        ChiselError.tag("error", Console.RED)
      } else {
        ChiselError.tag("warn", Console.YELLOW)
      }
    if( line != null ) {
      println(levelstr + " " + line.getFileName + ":" +
        line.getLineNumber + ": " + msgFun() +
        " in class " + line.getClassName)
    } else {
      println(levelstr + ": " + msgFun())
    }
  }
}
