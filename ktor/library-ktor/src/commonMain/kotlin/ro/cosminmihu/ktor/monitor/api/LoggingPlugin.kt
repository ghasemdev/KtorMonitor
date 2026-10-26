package ro.cosminmihu.ktor.monitor.api

import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.ResponseHandler
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.plugins.sse.ClientSSESession
import io.ktor.client.plugins.sse.SSESession
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.util.AttributeKey
import ro.cosminmihu.ktor.monitor.InternalKtorMonitorApi
import ro.cosminmihu.ktor.monitor.InternalLibraryBridge
import ro.cosminmihu.ktor.monitor.SanitizedHeader
import ro.cosminmihu.ktor.monitor.api.util.ReceiveHook
import ro.cosminmihu.ktor.monitor.api.util.ResponseHook
import ro.cosminmihu.ktor.monitor.api.util.SendHook
import ro.cosminmihu.ktor.monitor.api.util.StateHook
import ro.cosminmihu.ktor.monitor.api.util.TransformHook
import ro.cosminmihu.ktor.monitor.api.util.ignoreMonitorFailure
import ro.cosminmihu.ktor.monitor.api.util.isSseCall
import ro.cosminmihu.ktor.monitor.api.util.logRequest
import ro.cosminmihu.ktor.monitor.api.util.logRequestException
import ro.cosminmihu.ktor.monitor.api.util.logResponse
import ro.cosminmihu.ktor.monitor.api.util.logResponseBody
import ro.cosminmihu.ktor.monitor.api.util.logResponseException
import ro.cosminmihu.ktor.monitor.api.util.monitored
import ro.cosminmihu.ktor.monitor.api.util.monitoredFrames
import ro.cosminmihu.ktor.monitor.attr.KtorMonitorResponseBody
import ro.cosminmihu.ktor.monitor.domain.model.NetworkClient

private val DisableLogging = AttributeKey<Unit>("KtorMonitorDisableLogging")
private val CallIdentifier = AttributeKey<String>("KtorMonitorCallIdentifier")
private const val PluginName = "KtorMonitorLogging"

@OptIn(InternalKtorMonitorApi::class)
internal val LoggingPlugin: ClientPlugin<LoggingConfig> =
  createClientPlugin(PluginName, ::LoggingConfig) {
    // Setup library dependency.
    InternalLibraryBridge.setConfig(
      isActive = pluginConfig.isActive,
      showNotification = pluginConfig.showNotification,
      retentionPeriod = pluginConfig.retentionPeriod,
      maxContentLength = pluginConfig.maxContentLength,
      networkClient = NetworkClient.Ktor,
      iosGroupId = pluginConfig.iosGroupId,
    )

    // Check if plugin is active.
    if (!pluginConfig.isActive) return@createClientPlugin
    // Check if retention period is zero.
    if (!pluginConfig.retentionPeriod.isPositive()) return@createClientPlugin

    // Plugin configuration.
    val filters: List<(HttpRequestBuilder) -> Boolean> = pluginConfig.filters
    val sanitizedHeaders: List<SanitizedHeader> = pluginConfig.sanitizedHeaders

    // Start listen by recent calls.
    InternalLibraryBridge.startListening()

    // Get library dependencies.
    val coroutineScope = InternalLibraryBridge.coroutineScope()

    // Filter out requests that should not be logged.
    fun shouldBeLogged(request: HttpRequestBuilder): Boolean =
      filters.isEmpty() || filters.any { it(request) }

    on(SendHook) { request ->
      // Disable logging for requests that should not be logged.
      if (!shouldBeLogged(request)) {
        request.attributes.put(DisableLogging, Unit)
        return@on
      }

      // Generate id.
      request.attributes.put(CallIdentifier, InternalLibraryBridge.newCallId())

      // Log request.
      val loggedRequest = try {
        logRequest(
          id = request.attributes[CallIdentifier],
          request = request,
          maxContentLength = pluginConfig.maxContentLength,
          coroutineScope = coroutineScope,
          sanitizedHeaders = sanitizedHeaders
        )
      } catch (_: Throwable) {
        null
      }

      // Proceed with request.
      try {
        proceedWith(loggedRequest ?: request.body)
      } catch (cause: Throwable) {
        ignoreMonitorFailure {
          logRequestException(
            id = request.attributes[CallIdentifier],
            cause = cause
          )
        }
        throw cause
      }
    }

    on(ResponseHook) { response ->
      if (response.call.attributes.contains(DisableLogging)) return@on

      // Log response. A monitor failure must never fail the application's call.
      ignoreMonitorFailure {
        logResponse(
          id = response.call.attributes[CallIdentifier],
          response = response,
          sanitizedHeaders = sanitizedHeaders
        )
      }

      try {
        proceed()
      } catch (cause: Throwable) {
        // Log response exception only if not a 4xx/5xx ResponseException
        if (cause !is ResponseException) {
          ignoreMonitorFailure {
            logResponseException(
              id = response.call.attributes[CallIdentifier],
              cause = cause
            )
          }
        }
        throw cause
      }
    }

    on(ReceiveHook) { call, _ ->
      if (call.attributes.contains(DisableLogging)) {
        return@on
      }

      // If ktor-encryptor provided decrypted body override, update DB with the decrypted body
      if (call.attributes.contains(KtorMonitorResponseBody)) {
        ignoreMonitorFailure {
          logResponseBody(
            id = call.attributes[CallIdentifier],
            maxContentLength = pluginConfig.maxContentLength,
            response = call.response,
          )
        }
      }

      try {
        proceed()
      } catch (cause: Throwable) {
        if (cause !is ResponseException) {
          ignoreMonitorFailure {
            logResponseException(
              id = call.attributes[CallIdentifier],
              cause = cause
            )
          }
        }
        throw cause
      }
    }

    // Record SSE events from the parsed session; the raw body is not available for SSE calls.
    on(TransformHook) { call, container ->
      if (call.attributes.contains(DisableLogging) || !call.isSseCall()) return@on

      val id = call.attributes[CallIdentifier]
      val maxContentLength = pluginConfig.maxContentLength
      when (val session = container.response) {
        is ClientSSESession -> proceedWith(
          HttpResponseContainer(
            container.expectedType,
            ClientSSESession(session.call, session.monitored(id, maxContentLength)),
          )
        )

        is SSESession -> proceedWith(
          HttpResponseContainer(
            container.expectedType,
            session.monitored(id, maxContentLength),
          )
        )
      }
    }

    // Record WebSocket frames. Runs after the WebSockets plugin has produced its session,
    // regardless of plugin installation order.
    on(StateHook) { call, container ->
      if (call.attributes.contains(DisableLogging)) return@on

      val session = container.response as? DefaultClientWebSocketSession ?: return@on
      proceedWith(
        HttpResponseContainer(
          container.expectedType,
          session.monitoredFrames(
            id = call.attributes[CallIdentifier],
            maxContentLength = pluginConfig.maxContentLength,
          ),
        )
      )
    }

    val responseObserver: ResponseHandler = observer@{ response ->
      if (response.call.attributes.contains(DisableLogging)) {
        return@observer
      }

      // Log response body safely for standard responses (uses split channel copy).
      ignoreMonitorFailure {
        logResponseBody(
          id = response.call.attributes[CallIdentifier],
          maxContentLength = pluginConfig.maxContentLength,
          response = response,
        )
      }
    }

    ResponseObserver.install(ResponseObserver.prepare { onResponse(responseObserver) }, client)
  }
