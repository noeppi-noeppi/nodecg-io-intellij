package io.github.noeppi_noeppi.nodecg_io_intellij

import java.net.InetSocketAddress

import com.sun.net.httpserver.HttpServer

object IntellijServer {

  val DEFAULT_PORT = 19524

  private var server: HttpServer = _

  def start(): Unit = {
    val port = System.getProperties.getOrDefault("nodecg.io.port", DEFAULT_PORT).toString.toInt
    server = HttpServer.create()
    server.bind(new InetSocketAddress(port), 0)
    server.createContext("/").setHandler(IntellijHandler)
    server.start()
  }

  def stop(): Unit = {
    if (server != null) {
      server.stop(0)
      server = null
    }
  }
}
