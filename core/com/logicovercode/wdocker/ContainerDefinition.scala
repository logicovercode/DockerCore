package com.logicovercode.wdocker

import com.github.dockerjava.api.model.Capability
import ContainerIp.{subnetBase, subnetBaseNode}

case class VolumeMapping(host: String, container: String, rw: Boolean = false)

case class ContainerLink(container: ContainerDefinition, alias: String) {
  require(container.name.nonEmpty, "Container must have a name")
}

case class LogLineReceiver(withErr: Boolean, f: String => Unit)

case class DockerPortMapping(hostPort: Option[Int] = None, address: String = "0.0.0.0")

case class DockerNetwork private(name : String, subnet : Option[String] = None){
  val baseIp : Option[ContainerIp] = subnet.map{ sn =>
    ContainerIp( subnetBase(sn) + "." + (subnetBaseNode(sn).toInt) )
  }
}
object DockerNetwork{
  def apply(name : String, subnet : String) : DockerNetwork = {
    new DockerNetwork(name, Option(subnet))
  }
  def apply(name : String) : DockerNetwork = {
    new DockerNetwork(name, None)
  }
}

case class ContainerIp(ip : String){

  def +(nodeId : Int) = ContainerIp( subnetBase(ip) + "." + (subnetBaseNode(ip).toInt + nodeId) )

  def nodeId() : Int = subnetBaseNode(ip).toInt
}

object ContainerIp{

  def subnetBase(subnet : String) : String = {
    subnet.split("/")(0).substring(0, subnet.lastIndexOf("."))
  }

  def subnetBaseNode(subnet : String) : String = {
    subnet.split("/")(0).substring(subnet.lastIndexOf(".") + 1)
  }
}

case class HostConfig(
    tmpfs: Option[Map[String, String]] = None,
    /**
      * the hard limit on memory usage (in bytes)
      */
    memory: Option[Long] = None,
    /**
      * the soft limit on memory usage (in bytes)
      */
    memoryReservation: Option[Long] = None,

    capabilities : Option[Seq[Capability]] = None
)

case class ContainerDefinition(image : String,
                               tag : String,
                               name: Option[String] = None,
                               mayBeHubUser: Option[String] = None,
                               command: Option[Seq[String]] = None,
                               entrypoint: Option[Seq[String]] = None,
                               bindPorts: Map[Int, DockerPortMapping] = Map.empty,
                               tty: Boolean = false,
                               stdinOpen: Boolean = false,
                               links: Seq[ContainerLink] = Seq.empty,
                               unlinkedDependencies: Seq[ContainerDefinition] = Seq.empty,
                               env: Seq[String] = Seq.empty,
                               networkMode: Option[DockerNetwork] = None,
                               readyChecker: DockerReadyChecker = DockerReadyChecker.Always,
                               volumeMappings: Seq[VolumeMapping] = Seq.empty,
                               logLineReceiver: Option[LogLineReceiver] = None,
                               user: Option[String] = None,
                               hostname: Option[String] = None,
                               hostConfig: Option[HostConfig] = None,
                               ip : Option[ContainerIp] = None,
                               extraHosts : Seq[String] = Seq.empty) {

  def dockerImageUri() : String = {
    val imageWithTag = mayBeHubUser.map(_ + "/").getOrElse("") + image + ":" + tag
    imageWithTag
  }

  def withCommand(cmd: String*) = copy(command = Some(cmd))

  def withEntrypoint(entrypoint: String*) = copy(entrypoint = Some(entrypoint))

  def withPorts(ps: (Int, Int)*) =
    copy(bindPorts = ps.map { case (internalPort, hostPort) => internalPort -> DockerPortMapping(Option(hostPort)) }.toMap)

//  def withPorts(ps: (Int, Option[Int])*) =
//    copy(bindPorts = ps.map { case (internalPort, hostPort) => internalPort -> DockerPortMapping(hostPort) }.toMap)

  def withPortMapping(ps: (Int, DockerPortMapping)*) = copy(bindPorts = ps.toMap)
  def withLinks(links: ContainerLink*) = copy(links = links.toSeq)

  def withUnlinkedDependencies(unlinkedDependencies: ContainerDefinition*) =
    copy(unlinkedDependencies = unlinkedDependencies.toSeq)

  def dependencies: Seq[ContainerDefinition] = links.map(_.container) ++ unlinkedDependencies

  def withReadyChecker(checker: DockerReadyChecker) = copy(readyChecker = checker)

  def withEnv(env: String*) = copy(env = env)

  def withNetworkMode(networkMode: DockerNetwork) = copy(networkMode = Some(networkMode))

  def withVolumes(volumeMappings: Seq[VolumeMapping]) = copy(volumeMappings = volumeMappings)

  def withLogLineReceiver(logLineReceiver: LogLineReceiver) =
    copy(logLineReceiver = Some(logLineReceiver))

  def withUser(user: String) = copy(user = Some(user))

  def withHostname(hostname: String) = copy(hostname = Some(hostname))

  def withHostConfig(hostConfig: HostConfig) = copy(hostConfig = Some(hostConfig))

  def withExtraHost(_extraHosts: String*) = copy(extraHosts = _extraHosts)

  def withIp(_ip: ContainerIp) = copy(ip = Some(_ip))
}
