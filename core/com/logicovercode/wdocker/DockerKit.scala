package com.logicovercode.wdocker

import java.util.concurrent.Executors

import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.implicitConversions

trait DockerKit {
  implicit def dockerFactory: DockerFactory

  private lazy val log = LoggerFactory.getLogger(this.getClass)

  val PullImagesTimeout = 20.minutes
  val StartContainersTimeout = 20.seconds
  val StopContainersTimeout = 10.seconds

  def containerDefinitions: List[ContainerDefinition] = Nil

  // we need ExecutionContext in order to run docker.init() / docker.stop() there
  implicit lazy val dockerExecutionContext: ExecutionContext = {
    // using Math.max to prevent unexpected zero length of docker containers
    ExecutionContext.fromExecutor(
      Executors.newFixedThreadPool(Math.max(1, containerDefinitions.length * 2)))
  }
  implicit lazy val dockerExecutor = dockerFactory.createExecutor()

  lazy val containerManager = new DockerContainerManager(containerDefinitions, dockerExecutor)

  def isContainerReady(container: ContainerDefinition): Future[Boolean] =
    containerManager.isReady(container)

  def getContainerState(container: ContainerDefinition): DockerContainerState = {
    containerManager.getContainerState(container)
  }

  implicit def containerToState(c: ContainerDefinition): DockerContainerState = {
    getContainerState(c)
  }

  def startAllOrFail(): Unit = {
    //this pull timeout is for all images to get downloaded,
    //but we want individual time out for different image
    //so we have a separate timeout logic and commenting this timeout logic

    //Await.result(containerManager.pullImages(), PullImagesTimeout)

    val allRunning: Boolean = try {
      val future: Future[Boolean] =
        containerManager.initReadyAll(StartContainersTimeout).map(_.map(_._2).forall(identity))
      sys.addShutdownHook(
        Await.ready(containerManager.stopRmAll(), StopContainersTimeout)
      )
      Await.result(future, StartContainersTimeout)
    } catch {
      case e: Exception =>
        log.error("Exception during container initialization", e)
        false
    }
    if (!allRunning) {
      Await.ready(containerManager.stopRmAll(), StopContainersTimeout)
      throw new RuntimeException("Cannot run all required containers")
    }
  }

  def stopAllQuietly(): Unit = {
    try {
      Await.ready(containerManager.stopRmAll(), StopContainersTimeout)
    } catch {
      case e: Throwable =>
        log.error(e.getMessage, e)
    }
  }
}
