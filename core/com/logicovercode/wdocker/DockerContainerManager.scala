package com.logicovercode.wdocker

import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.concurrent.duration._

class DockerContainerManager(containers: Seq[ContainerDefinition], executor: DockerCommandExecutor)(
    implicit ec: ExecutionContext) {

  private lazy val log = LoggerFactory.getLogger(this.getClass)
  private implicit val dockerExecutor = executor

  private val dockerStatesMap: Map[ContainerDefinition, DockerContainerState] =
    containers.map(c => c -> new DockerContainerState(c)).toMap

  val states = dockerStatesMap.values.toList

  def getContainerState(container: ContainerDefinition): DockerContainerState = {
    dockerStatesMap(container)
  }

  def isReady(container: ContainerDefinition): Future[Boolean] = {
    dockerStatesMap(container).isReady()
  }

  def pullImages(): Future[Seq[String]] = {
    executor.listImages().flatMap { images =>
      val dockerImages = containers.map{ container =>
        import container._
        val imageWithTag = mayBeHubUser.map(_ + "/").getOrElse("") + image + ":" + tag
        imageWithTag
      }
      val imagesToPull: Seq[String] = dockerImages.filterNot { image =>
        val cImage = if (image.contains(":")) image else image + ":latest"
        images(cImage)
      }
      Future.traverse(imagesToPull)(i => executor.pullImage(i)).map(_ => imagesToPull)
    }
  }

  def initReadyAll(
      containerStartTimeout: Duration): Future[Seq[(DockerContainerState, Boolean)]] = {
    import DockerContainerManager._

    @tailrec
    def initGraph(graph: ContainerDependencyGraph,
                  previousInits: Future[Seq[DockerContainerState]] = Future.successful(Seq.empty))
      : Future[Seq[DockerContainerState]] = {
      val initializedContainers = previousInits.flatMap { prev =>
        Future.traverse(graph.containers.map(dockerStatesMap))(_.init()).map(prev ++ _)
      }

      graph.dependants match {
        case None => initializedContainers
        case Some(dependants) =>
          val readyInits: Future[Seq[Future[Boolean]]] =
            initializedContainers.map(_.map(state => state.isReady()))
          val simplifiedReadyInits: Future[Seq[Boolean]] = readyInits.flatMap(Future.sequence(_))
          Await.result(simplifiedReadyInits, containerStartTimeout)
          initGraph(dependants, initializedContainers)
      }
    }

    val containerDependencyGraph = buildDependencyGraph(containers)
    initGraph(containerDependencyGraph).flatMap(Future.traverse(_) { c =>
      c.isReady().map(c -> _).recover {
        case e =>
          log.error(e.getMessage, e)
          c -> false
      }
    })
  }

  def stopRmAll(): Future[Unit] = {
    val future = Future.traverse(states)(_.remove(force = true, removeVolumes = true)).map(_ => ())
    future.onComplete { _ =>
      executor.close()
    }
    future
  }

}

object DockerContainerManager {
  case class ContainerDependencyGraph(containers: Seq[ContainerDefinition],
                                      dependants: Option[ContainerDependencyGraph] = None)

  def buildDependencyGraph(containers: Seq[ContainerDefinition]): ContainerDependencyGraph = {

    @tailrec
    def buildDependencyGraph(graph: ContainerDependencyGraph): ContainerDependencyGraph =
      graph match {
        case ContainerDependencyGraph(allContainers, allDependants) =>
          val containerTuple = allContainers.partition(_.dependencies.isEmpty)
          containerTuple match {
            case (_, Nil) =>
              graph
            case (firstLevelContainersWithoutDependencies, firstLevelContainersWithDependencies) =>
              val allDependenciesOfFirstLevelContainers = allContainers.foldLeft(Seq[ContainerDefinition]()) {
                case (links, container) => (links ++ container.dependencies)
              }
              val (firstLevelContainersWithDependenciesAndLinked, firstLevelContainersWithDependenciesAndNotLinked) = {
                firstLevelContainersWithDependencies.partition{ dependencyOfFirstLevelContainer =>
                  allDependenciesOfFirstLevelContainers.contains(dependencyOfFirstLevelContainer)
                }
              }


              val (containersToBeLeftAtCurrentPosition, containersToBeMovedUpALevel) = {
                val dockerContainers: Seq[ContainerDefinition] = allDependants
                  .map(_.containers)
                  .getOrElse(List.empty)

                dockerContainers
                  .partition(
                    _.dependencies.exists(firstLevelContainersWithDependenciesAndNotLinked.contains)
                  )
              }

              val newGraph = ContainerDependencyGraph(
                containers = firstLevelContainersWithoutDependencies ++ firstLevelContainersWithDependenciesAndLinked,
                dependants = Some(
                  ContainerDependencyGraph(
                    containers = firstLevelContainersWithDependenciesAndNotLinked ++ containersToBeMovedUpALevel,
                    dependants =
                      allDependants.map(_.copy(containers = containersToBeLeftAtCurrentPosition))
                  ))
              )

              buildDependencyGraph(newGraph)
          }
      }

    val graph = buildDependencyGraph( ContainerDependencyGraph(containers) )
    graph
  }
}
