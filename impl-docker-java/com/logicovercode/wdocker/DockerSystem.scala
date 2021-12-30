package com.logicovercode.wdocker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateNetworkResponse
import com.github.dockerjava.api.model.Network.Ipam
import com.github.dockerjava.api.model.Network.Ipam.Config
import com.github.dockerjava.api.model.{Container, Network}

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.Try

object DockerSystem {
//  val dockerClient: DockerClient = {
//    val config = DefaultDockerClientConfig.createDefaultConfigBuilder.build()
//    DockerClientBuilder.getInstance(config).build
//  }

  def listNetworks(implicit dockerClient : DockerClient) : Try[Seq[Network]] = Try{
    dockerClient.listNetworksCmd().exec().asScala.toSeq
  }

  def listRunningContainers(implicit dockerClient : DockerClient) : Try[Seq[Container]] = Try{
    dockerClient
      .listContainersCmd()
      .withShowSize(true)
      .withShowAll(true)
      .exec()
      .asScala.toSeq
  }

  def isNetworkExists(networkName : String)(implicit dockerClient : DockerClient) : Try[Boolean] = {
    for{
      networks <- listNetworks(dockerClient)
      names = networks.map(_.getName)
      exists = names.contains(networkName)
    } yield exists
  }

  def findNetworkByName(networkName : String)(implicit dockerClient : DockerClient) : Try[Network] = {
    for{
      networks <- listNetworks(dockerClient)
      existingNetwork <- Try(networks.find(n => n.getName.equalsIgnoreCase(networkName)).get)
    } yield existingNetwork
  }

  def createNonExistingNetwork(dockerNetwork : DockerNetwork)(implicit dockerClient : DockerClient) : Try[(Boolean, String)] = {

    val networkName = dockerNetwork.name
    val subnet = dockerNetwork.subnet

    for{
      status <- isNetworkExists(networkName)
      _@(id, status) <- status match {
        case true => findNetworkByName(networkName).map(n => (n.getId, false))
        case false => createNetwork(dockerNetwork).map(n => (n.getId, false))
      }
    } yield (status, id)
  }

  def deleteNetworkIfExists(networkName : String)(implicit dockerClient : DockerClient) : Try[(Boolean, String)] = {

    for{
      status <- isNetworkExists(networkName)
      _@(id, status) <- status match {
        case false => Try("network don't exists", false)
        case true => deleteNetwork(networkName)
      }
    } yield (status, id)
  }

  private def deleteNetwork(networkName : String)(implicit dockerClient : DockerClient): Try[(String, Boolean)] = Try{

    val removeNetworkCommand = dockerClient.removeNetworkCmd(networkName)

    removeNetworkCommand.exec();
    (s"$networkName removed", true)
  }

  private def createNetwork(dockerNetwork : DockerNetwork)(implicit dockerClient : DockerClient): Try[CreateNetworkResponse] = Try{

    val networkName = dockerNetwork.name
    val subnet = dockerNetwork.subnet
    //networkName : String, subnet : Option[String]

    val baseNetworkCommand = dockerClient.createNetworkCmd()
      .withName(networkName)
      .withDriver("bridge")

    val networkCommand = subnet match {
      case Some(sn) => baseNetworkCommand.withIpam(
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

  def pullDockerImage(imageName : String)(implicit dockerClient : DockerClient) : Try[Int] = Try{
    import scala.sys.process._
    val dockerPullCommand = s"docker pull -q $imageName"
    println(s"$dockerPullCommand")
    s"$dockerPullCommand" !
  }

  def startContainer(containerId : String)(implicit dockerClient : DockerClient) : Try[Unit] = Try{
    dockerClient.startContainerCmd(containerId).exec()
  }
}
