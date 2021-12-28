package com.logicovercode.wdocker

trait DockerFactory {

  def createExecutor(): DockerCommandExecutor
}
