package com.logicovercode.wdocker

object OsFunctions {

  def currentOsOption : Option[String] = sys.props.get("os.name").map(_.toLowerCase)

  def isWindowsCategoryOs(os : Option[String] = currentOsOption): Boolean = (for {
    osName <- os
    os_name = osName.toLowerCase
  } yield os_name.contains("win")).getOrElse(false)
}
