package pt.fabm

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

Server server = new Server(8085)
server.handler = { String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response ->
    response.writer.println("stay cool ${args}->${System.getProperty('myProp')?.toString()}")

    baseRequest.handled = true
} as AbstractHandler

server.start()
