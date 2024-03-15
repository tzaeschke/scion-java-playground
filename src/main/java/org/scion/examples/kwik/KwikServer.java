// Copyright 2024 ETH Zurich
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.scion.examples.kwik;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Arrays;

import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicStream;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.SysOutLogger;
import net.luminis.quic.run.KwikVersion;
import net.luminis.quic.server.ApplicationProtocolConnection;
import net.luminis.quic.server.ApplicationProtocolConnectionFactory;
import net.luminis.quic.server.ServerConnectionConfig;
import net.luminis.quic.server.ServerConnector;

public class KwikServer {

  public static void main(String[] args) throws Exception {
    System.out.println("KWIK: " + KwikVersion.getVersion());

    ServerConnectionConfig serverConnectionConfig =
        ServerConnectionConfig.builder()
            .maxOpenPeerInitiatedBidirectionalStreams(
                50) // Mandatory setting to maximize concurrent streams on a connection.
            .build();
    Logger log = new SysOutLogger();

    ServerConnector serverConnector =
        ServerConnector.builder()
            .withPort(443)
                // TODO test with certificates
//            .withCertificate(
//                new FileInputStream("server.cert"), new FileInputStream("servercert.key"))
            .withConfiguration(serverConnectionConfig)
            .withLogger(log)
            .build();

    serverConnector.registerApplicationProtocol(
        "myapplicationprotocol", new MyApplicationProtocolConnectionFactory());

    serverConnector.start();
  }

  private static class MyApplicationProtocolConnectionFactory
      implements ApplicationProtocolConnectionFactory {

    @Override
    public ApplicationProtocolConnection createConnection(String s, QuicConnection quicConnection) {
      return new MyApplicationProtocolConnection();
    }

    @Override
    public int maxConcurrentPeerInitiatedUnidirectionalStreams() {
      return 3; // for HTTP/3
    }

    @Override
    public int maxConcurrentPeerInitiatedBidirectionalStreams() {
      return 0;
    }
  }

  private static class MyApplicationProtocolConnection implements ApplicationProtocolConnection {
    @Override
    public void acceptPeerInitiatedStream(QuicStream stream) {

      byte[] bytes = new byte[2000];
      int len;
      try {
        len = stream.getInputStream().read(bytes);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      System.out.println("Bytes: " + Arrays.toString(bytes));
    }
  }
}
