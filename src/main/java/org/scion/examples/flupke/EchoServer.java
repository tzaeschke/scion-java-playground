/*
 * Copyright © 2022, 2023, 2024 Peter Doornbosch
 *
 * This file is part of Kwik, an implementation of the QUIC protocol in Java.
 *
 * Kwik is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Kwik is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.scion.examples.flupke;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicStream;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.SysOutLogger;
import net.luminis.quic.server.ApplicationProtocolConnection;
import net.luminis.quic.server.ApplicationProtocolConnectionFactory;
import net.luminis.quic.server.ServerConnectionConfig;
import net.luminis.quic.server.ServerConnector;
import org.scion.Constants;
import org.scion.socket.DatagramSocket;

/**
 * A sample server that runs a very simple echo protocol on top of QUIC.
 * The echo protocol is a request-response protocol, where the client sends one request on a new stream and the server
 * responds by echoing the data from the request in a response on the same stream. After sending the response, the
 * stream is closed.
 *
 * The server's main method requires three arguments:
 * - certificate file (can be self-signed)
 * - key file with the private key of the certificate
 * - port number
 */
public class EchoServer {

    private static void usageAndExit() {
        System.err.println("Usage: cert file, cert key file, port number");
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        args = new String[]{"cert.crt", "cert.key", "4443"};
        if (args.length != 3 || ! Arrays.stream(args).limit(2).allMatch(a -> new File(a).exists())) {
            usageAndExit();
        }

        int port = -1;
        try {
            port = Integer.valueOf(args[2]);
        }
        catch (NumberFormatException noNumber) {
            usageAndExit();
        }

        Logger log = new SysOutLogger();
        log.timeFormat(Logger.TimeFormat.Long);
        log.logWarning(true);
        log.logInfo(true);

        ServerConnectionConfig serverConnectionConfig = ServerConnectionConfig.builder()
                .maxOpenPeerInitiatedBidirectionalStreams(12)  // Mandatory setting to maximize concurrent streams on a connection.
                .build();

        ServerConnector serverConnector = ServerConnector.builder()
                .withPort(port)
                .withCertificate(new FileInputStream(args[0]), new FileInputStream(args[1]))
                .withConfiguration(serverConnectionConfig)
                .withLogger(log)
                .withSocket(new DatagramSocket(port))
                .build();

        registerProtocolHandler(serverConnector, log);

        serverConnector.start();

        log.info("Started echo server on port " + port);
    }

    private static void registerProtocolHandler(ServerConnector serverConnector, Logger log) {
           serverConnector.registerApplicationProtocol("echo", new EchoProtocolConnectionFactory(log));
    }

    /**
     * The factory that creates the (echo) application protocol connection.
     */
    static class EchoProtocolConnectionFactory implements ApplicationProtocolConnectionFactory {
        private final Logger log;

        public EchoProtocolConnectionFactory(Logger log) {
            this.log = log;
        }

        @Override
        public ApplicationProtocolConnection createConnection(String protocol, QuicConnection quicConnection) {
            return new EchoProtocolConnection(quicConnection, log);
        }

        @Override
        public int maxConcurrentPeerInitiatedUnidirectionalStreams() {
            return 0;  // Because unidirectional streams are not used
        }

        @Override
        public int maxConcurrentPeerInitiatedBidirectionalStreams() {
            return Integer.MAX_VALUE;   // Because from protocol perspective, there is no limit
        }
    }

    /**
     * The echo protocol connection.
     */
    static class EchoProtocolConnection implements ApplicationProtocolConnection {

        private Logger log;

        public EchoProtocolConnection(QuicConnection quicConnection, Logger log) {
            this.log = log;
        }

        @Override
        public void acceptPeerInitiatedStream(QuicStream quicStream) {
            // Need to handle incoming stream on separate thread; using a thread pool is recommended.
            new Thread(() -> handleEchoRequest(quicStream)).start();
        }

        private void handleEchoRequest(QuicStream quicStream) {
            try {
                // Note that this implementation is not safe to use in the wild, as attackers can crash the server by sending arbitrary large requests.
                byte[] bytesRead = quicStream.getInputStream().readAllBytes();
                System.out.println("Read echo request with " + bytesRead.length + " bytes of data.");
                quicStream.getOutputStream().write(bytesRead);
                quicStream.getOutputStream().close();
            } catch (IOException e) {
                log.error("Reading quic stream failed", e);
            }
        }
    }
}
