package pt.fabm

import org.h2.tools.Server

boolean withWeb = false;
String[] args
if (withWeb) {
    args = []
} else {
    args = ['-tcp', '-tcpPort', '9092']
}

Server.main(args)

