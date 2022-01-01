package com.logicovercode.wdocker.api

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import com.logicovercode.wdocker.OsFunctions.{currentOsOption, isWindowsCategoryOs}
import com.logicovercode.wdocker.{Docker, DockerCommandExecutor, DockerFactory, DockerHostAndClientReResolver, DockerJavaExecutor}

object DockerContext {

  private def dockerFactoryAndClient(osOption: Option[String]): (DockerFactory, DockerClient) = {

    isWindowsCategoryOs(osOption) match {
      case true =>
        val _ @(host, client) = DockerHostAndClientReResolver.hostAndClient()
        val dockerFactory = new DockerFactory {
          override def createExecutor(): DockerCommandExecutor = new DockerJavaExecutor(host, client)
        }
        (dockerFactory, client)
      case false =>
        val docker = new Docker(DefaultDockerClientConfig.createDefaultConfigBuilder().build(), factory = new NettyDockerCmdExecFactory())

        val dockerFactory = new DockerFactory {
          override def createExecutor(): DockerCommandExecutor = new DockerJavaExecutor(docker.host, docker.client)
        }

        (dockerFactory, docker.client)
    }
  }

  private val dockerFactoryClientTuple = dockerFactoryAndClient(currentOsOption)

  val dockerFactory = dockerFactoryClientTuple._1
  val dockerClient = dockerFactoryClientTuple._2
}
