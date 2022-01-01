package com.logicovercode.wdocker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientImpl}
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.google.common.base.Strings.isNullOrEmpty
import com.google.common.net.HostAndPort
import OsFunctions.currentOsOption

import java.util.{Locale, Optional}

object DockerHostAndClientReResolver {

  private val UNIX_SCHEME = "unix"
  private val DEFAULT_UNIX_ENDPOINT = "unix:///var/run/docker.sock"
  private val DEFAULT_ADDRESS = "localhost"
  private val DEFAULT_PORT = 2375

  def hostAndClient(): (String, DockerClient) = {
    val recomputedHost = recomputeHost()
    System.out.println("recomputed host : " + recomputedHost)

    val defaultConfigBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder
    val config = {
      recomputedHost.isPresent match {
        case true => defaultConfigBuilder.withDockerHost(recomputedHost.get).build
        case false => defaultConfigBuilder.build
      }
    }

    val httpClient = new ApacheDockerHttpClient.Builder()
      .dockerHost(config.getDockerHost)
      .sslConfig(config.getSSLConfig)
      .maxConnections(100)
      .build

    (config.getDockerHost.getHost, DockerClientImpl.getInstance(config, httpClient))
  }

  def recomputeHost(): Optional[String] = {

    val endPoint =  sys.env.get("DOCKER_HOST") orElse findOsEndpoint( currentOsOption )

    endPoint match {
      case Some(ep) if( ep.startsWith(UNIX_SCHEME + "://") ) => Optional.ofNullable(ep)
      case Some(ep) => Optional.of(recomputeHost(ep))
      case None => Optional.ofNullable(null)
    }
  }

  private def findOsEndpoint(osOption : Option[String]): Option[String] = {
    osOption.map { os =>
      val osLowerCase = os.toLowerCase(Locale.ENGLISH)
      val isOSUnixBased = osLowerCase.equalsIgnoreCase("linux") || osLowerCase.contains("mac")
      val endPoint = if (isOSUnixBased) DEFAULT_UNIX_ENDPOINT else DEFAULT_ADDRESS + ":" + DEFAULT_PORT
      endPoint
    }
  }

  private def recomputeHost(dockerHost: String): String = {
    val stripped = dockerHost.replaceAll(".*://", "")
    val hostAndPort = HostAndPort.fromString(stripped)
    val hostText = hostAndPort.getHostText

    val port = hostAndPort.getPortOrDefault(DEFAULT_PORT)
    val address = if (isNullOrEmpty(hostText)) DEFAULT_ADDRESS else hostText
    "tcp" + "://" + address + ":" + port
  }
}