package com.logicovercode.wdocker

import com.github.dockerjava.core.DefaultDockerClientConfig

trait DockerKitDockerJava extends DockerKit {

  override implicit val dockerFactory: DockerFactory = new DockerJavaExecutorFactory(
    new Docker(DefaultDockerClientConfig.createDefaultConfigBuilder().build()))
}
