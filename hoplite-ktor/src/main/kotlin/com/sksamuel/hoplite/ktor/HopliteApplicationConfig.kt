package com.sksamuel.hoplite.ktor

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ListNode
import com.sksamuel.hoplite.LongNode
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.PrimitiveNode
import com.sksamuel.hoplite.StringNode
import com.sksamuel.hoplite.UndefinedNode
import io.ktor.config.ApplicationConfig
import io.ktor.config.ApplicationConfigValue
import io.ktor.server.engine.ApplicationEngineEnvironment
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.util.KtorExperimentalAPI
import org.slf4j.LoggerFactory
import java.nio.file.Path

@KtorExperimentalAPI
class HopliteApplicationConfig(private val node: Node) : ApplicationConfig {

  override fun config(path: String): ApplicationConfig = HopliteApplicationConfig(node.atKey(path))

  override fun configList(path: String): List<ApplicationConfig> = emptyList()

  override fun property(path: String): ApplicationConfigValue = HopliteApplicationConfigValue(node.atKey(path))

  override fun propertyOrNull(path: String): ApplicationConfigValue? =
    if (node.hasKeyAt(path)) property(path) else null
}

@KtorExperimentalAPI
class HopliteApplicationConfigValue(private val node: Node) : ApplicationConfigValue {

  override fun getString(): String = when (node) {
    is PrimitiveNode -> node.value.toString()
    else -> throw IllegalArgumentException("${node.simpleName} cannot be converted to string")
  }

  override fun getList(): List<String> = when (node) {
    is ListNode -> node.elements.map { element ->
      when (element) {
        is PrimitiveNode -> element.value.toString()
        else -> throw IllegalArgumentException("${element.simpleName} cannot be converted to string")
      }
    }
    is StringNode -> node.value.split(',').toList()
    else -> throw IllegalArgumentException("${node.simpleName} cannot be converted to list")
  }
}

@KtorExperimentalAPI
fun ConfigLoader.loadApplicationEngineEnvironment(first: String, vararg tail: String): ApplicationEngineEnvironment =
  loadApplicationEngineEnvironment(listOf(first) + tail)

@KtorExperimentalAPI
fun ConfigLoader.loadApplicationEngineEnvironment(resources: List<String>): ApplicationEngineEnvironment {
  val node = loadNodeOrThrow(resources)
  return hopliteApplicationEngineEnvironment(node)
}

@KtorExperimentalAPI
fun ConfigLoader.loadApplicationEngineEnvironment(first: Path, vararg tail: Path): ApplicationEngineEnvironment =
  loadApplicationEngineEnvironment(listOf(first) + tail)

@KtorExperimentalAPI
@JvmName("loadApplicationConfigFromPaths")
fun ConfigLoader.loadApplicationEngineEnvironment(paths: List<Path>): ApplicationEngineEnvironment {
  val node = loadNodeOrThrow(paths)
  return hopliteApplicationEngineEnvironment(node)
}

fun hopliteApplicationEngineEnvironment(node: Node): ApplicationEngineEnvironment = applicationEngineEnvironment {

  val hostConfigPath = "ktor.deployment.host"
  val portConfigPath = "ktor.deployment.port"
  val applicationIdPath = "ktor.application.id"

  val applicationId = when (val n = node.atPath(applicationIdPath)) {
    is StringNode -> n.value
    is UndefinedNode -> "Application"
    else -> throw RuntimeException("Invalid value for $applicationIdPath")
  }

  log = LoggerFactory.getLogger(applicationId)
  config = HopliteApplicationConfig(node)

  connector {
    host = when (val n = node.atPath(hostConfigPath)) {
      is StringNode -> n.value
      is UndefinedNode -> "0.0.0.0"
      else -> throw RuntimeException("Invalid value for host: $n")
    }
    port = when (val n = node.atPath(portConfigPath)) {
      is LongNode -> n.value.toInt()
      is StringNode -> n.value.toInt()
      else -> throw RuntimeException("$portConfigPath is not defined or is not a number")
    }
  }
}

