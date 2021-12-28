package com.logicovercode.wdocker

class DockerJavaExecutorFactory(docker: Docker) extends DockerFactory {
  override def createExecutor(): DockerCommandExecutor = {
    new DockerJavaExecutor(docker.host, docker.client)
  }
}
