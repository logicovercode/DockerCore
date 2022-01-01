package com.logicovercode.wdocker.api

import com.logicovercode.wdocker.OsFunctions.isWindowsCategoryOs

import scala.language.postfixOps

object DockerProcessFunctions {

  def pid(): Long = {

    import java.lang.management.ManagementFactory
    val bean = ManagementFactory.getRuntimeMXBean

    // Get name representing the running Java virtual machine.
    // It returns something like 6460@AURORA. Where the value
    // before the @ symbol is the PID.
    val jvmName = bean.getName
    println("Name = " + jvmName)

    // Extract the PID by splitting the string returned by the
    // bean.getName() method.
    val pid = jvmName.split("@")(0).toLong
    println("PID  = " + pid)

    pid
  }

  def killDockerManager(
      sbtProcessId: Long,
      osNameOption: Option[String]
  ): Unit = {
    val msg =
      s"attempt to silent process(that is starting docker services) with pid  >$sbtProcessId< on ${osNameOption.get}"

    println(s"$msg")

    isWindowsCategoryOs(osNameOption) match {
      case false => killOnUnixBaseBox(sbtProcessId)
      case true  => killOnWindowsBox(sbtProcessId)
    }
  }

  private def killOnUnixBaseBox(processId: Long): Int = {
    println(s"kill -9 $processId")

    import scala.sys.process._

    s"kill -9 $processId" !
  }

  private def killOnWindowsBox(processId: Long): Int = {
    println(s"TASKKILL /F /PID $processId")

    import scala.sys.process._

    s"TASKKILL /F /PID $processId" !
  }
}
