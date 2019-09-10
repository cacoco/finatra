package com.twitter.inject.server

import com.twitter.finagle.http.{Fields, Method, Request, Response, Status}
import com.twitter.server.AdminHttpServer
import com.twitter.util.{Closable, Try}
import java.net.URI

/** Internal utility which represents an http client to the AdminHttpInterface of the [[EmbeddedTwitterServer]] */
private[twitter] abstract class AdminHttpClient private[twitter] (
  twitterServer: com.twitter.server.TwitterServer,
  verbose: Boolean = false
) { self: EmbeddedTwitterServer =>

  /* Public */

  final lazy val httpAdminClient: EmbeddedHttpClient = {
    start()
    val client = new EmbeddedHttpClient("httpAdminClient", httpAdminPort(), disableLogging)
      .withDefaultHeaders(() => defaultRequestHeaders)
      .withStreamResponses(streamResponse)
    closeOnExit {
      if (isStarted) {
        Closable.make { deadline =>
          info(s"Closing embedded http client: ${client.label}", disableLogging)
          client.close(deadline)
        }
      } else Closable.nop
    }
    client
  }

  def httpGetAdmin(
    path: String,
    accept: String = null,
    headers: Map[String, String] = Map(),
    suppress: Boolean = false,
    andExpect: Status = Status.Ok,
    withLocation: String = null,
    withBody: String = null
  ): Response = {

    val request = createApiRequest(path, Method.Get)
    httpAdminClient
      .apply(request, addAcceptHeader(accept, headers), suppress, andExpect, withLocation, withBody)
  }

  def healthResponse(expectedHealthy: Boolean = true): Try[Response] = {
    val expectedBody = if (expectedHealthy) "OK\n" else ""

    Try {
      httpGetAdmin("/health", andExpect = Status.Ok, withBody = expectedBody, suppress = !verbose)
    }
  }

  def adminHttpServerRoutes: Seq[AdminHttpServer.Route] = {
    twitterServer.routes
  }

  /* Protected */

  protected def addAcceptHeader(
    accept: String,
    headers: Map[String, String]
  ): Map[String, String] = {
    if (accept != null)
      headers + (Fields.Accept -> accept.toString)
    else
      headers
  }

  protected def createApiRequest(path: String, method: Method = Method.Get): Request = {
    val pathToUse =
      if (path.startsWith("http"))
        URI.create(path).getPath
      else
        path

    Request(method, pathToUse)
  }
}
