package com.logicovercode.wdocker.api

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.{CreateNetworkResponse, PullImageResultCallback}
import com.github.dockerjava.api.model.Network.Ipam
import com.github.dockerjava.api.model.Network.Ipam.Config
import com.github.dockerjava.api.model.{Container, Network}
import com.logicovercode.wdocker.{ContainerDefinition, DockerFactory, DockerKit, DockerNetwork}

import java.util.concurrent.TimeUnit
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

object DockerSystem {

  def listNetworks(implicit dockerClient: DockerClient): Try[Seq[Network]] = Try {
    dockerClient.listNetworksCmd().exec().asScala.toSeq
  }

  def listRunningContainers(implicit dockerClient: DockerClient): Try[Seq[Container]] = Try {
    dockerClient
      .listContainersCmd()
      .withShowSize(true)
      .withShowAll(true)
      .exec()
      .asScala
      .toSeq
  }

  def isNetworkExists(networkName: String)(implicit dockerClient: DockerClient): Try[Boolean] = {
    for {
      networks <- listNetworks(dockerClient)
      names = networks.map(_.getName)
      exists = names.contains(networkName)
    } yield exists
  }

  def findNetworkByName(networkName: String)(implicit dockerClient: DockerClient): Try[Network] = {
    for {
      networks <- listNetworks(dockerClient)
      existingNetwork <- Try(networks.find(n => n.getName.equalsIgnoreCase(networkName)).get)
    } yield existingNetwork
  }

  sealed trait NetworkStatus {
    def exists: Boolean
  }
  case class NewNetworkCreated(networkId: String) extends NetworkStatus {
    override def exists: Boolean = true
  }
  case class NetworkAlreadyExists(networkId: String) extends NetworkStatus {
    override def exists: Boolean = true
  }
  case class NetworkCreationFailed(network: DockerNetwork, errorMsg: String) extends NetworkStatus {
    override def exists: Boolean = false
  }

  def tryNetworkConnectivity(dockerNetwork: DockerNetwork)(implicit dockerClient: DockerClient): NetworkStatus = {

    val tryNetworkStatus: Try[NetworkStatus] = findNetworkByName(dockerNetwork.name).map(net => NetworkAlreadyExists(net.getId)) orElse
      createNetwork(dockerNetwork).map(networkResponse => NewNetworkCreated(networkResponse.getId))

    tryNetworkStatus match {
      case Success(networkStatus) => networkStatus
      case Failure(ex)            => NetworkCreationFailed(dockerNetwork, ex.getMessage)
    }
  }

  def deleteNetworkIfExists(networkName: String)(implicit dockerClient: DockerClient): Try[(Boolean, String)] = {

    for {
      status <- isNetworkExists(networkName)
      _ @(id, status) <- status match {
        case false => Try("network don't exists", false)
        case true  => deleteNetwork(networkName)
      }
    } yield (status, id)
  }

  private def deleteNetwork(networkName: String)(implicit dockerClient: DockerClient): Try[(String, Boolean)] = Try {

    val removeNetworkCommand = dockerClient.removeNetworkCmd(networkName)

    removeNetworkCommand.exec();
    (s"$networkName removed", true)
  }

  private def createNetwork(dockerNetwork: DockerNetwork)(implicit dockerClient: DockerClient): Try[CreateNetworkResponse] = Try {

    val networkName = dockerNetwork.name
    val subnet = dockerNetwork.subnet
    //networkName : String, subnet : Option[String]

    val baseNetworkCommand = dockerClient
      .createNetworkCmd()
      .withName(networkName)
      .withDriver("bridge")

    val networkCommand = subnet match {
      case Some(sn) =>
        baseNetworkCommand.withIpam(
          new Ipam().withConfig(
            new Config()
              .withSubnet(sn)
          )
        )
      case None => baseNetworkCommand
    }

    val networkResponse = networkCommand.exec();
    println(s"Network ${dockerNetwork.name} created with id ${networkResponse.getId}\n")
    networkResponse
  }

  def pullDockerImage(mayBeHubUser: Option[String], imageName: String, imageTag: String, pullTimeoutInSeconds: Long)(implicit
      dockerClient: DockerClient
  ): Try[Unit] = Try {

    val img = mayBeHubUser.map(_ + "/").getOrElse("") + imageName

    println(s"pulling image $img:$imageTag")

    dockerClient
      .pullImageCmd(img)
      .withTag(imageTag)
      .exec(new PullImageResultCallback())
      .awaitCompletion(pullTimeoutInSeconds, TimeUnit.SECONDS);
  }

  def remoteTags(mayBeHubUser: Option[String], imageName: String): Try[Seq[String]] = Try {
    val img = mayBeHubUser.map(_ + "/").getOrElse("") + imageName
    val url = s"https://registry.hub.docker.com/v1/repositories/$img/tags"
    val json = scala.io.Source.fromURL(url).mkString
    val multilineArray = json
      .replace("{", "\n{")
      .replace("]", "\n]")
      .split("\n")
      .toSeq

    val nameTokens = for {
      line <- multilineArray
      tokens = line.split(",")
      if (tokens.size >= 2)
    } yield tokens(1)

    for {
      nameToken <- nameTokens
      tokenWithoutKey = nameToken.replace("name", "")
      tokenValue = tokenWithoutKey
        .replace("\"", "")
        .replace(":", "")
        .replace("}", "")
    } yield tokenValue.trim
  }

  def pullAndStartContainerDefinition(
                             containerDefinition: ContainerDefinition,
                             imagePullTimeout: FiniteDuration,
                             startTimeout: FiniteDuration
                           )(implicit dockerClient: DockerClient, dockerFactory: DockerFactory): Try[Unit] = Try {

    DockerSystem.pullDockerImage(
      containerDefinition.mayBeHubUser,
      containerDefinition.image,
      containerDefinition.tag,
      imagePullTimeout.toSeconds
    )(dockerClient)

    case class ContainerKit(containerDefinition: ContainerDefinition, imagePullTimeout : FiniteDuration,
                            containerStartTimeout : FiniteDuration, _dockerFactory: DockerFactory) extends DockerKit{


      override val StartContainersTimeout = containerStartTimeout
      override val PullImagesTimeout = imagePullTimeout
      override implicit def dockerFactory: DockerFactory = _dockerFactory
      override def containerDefinitions: List[ContainerDefinition] = List(containerDefinition)
    }

    val containerKit = ContainerKit(containerDefinition, imagePullTimeout, startTimeout, dockerFactory)

    containerKit.startAllOrFail()
  }

  def startContainer(containerId: String)(implicit dockerClient: DockerClient): Try[Unit] = Try {
    dockerClient.startContainerCmd(containerId).exec()
  }
}
