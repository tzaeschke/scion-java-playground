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

import java.io.*;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;

import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicStream;
import net.luminis.quic.core.QuicClientConnectionImpl;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.SysOutLogger;
import net.luminis.quic.run.KwikCli;
import net.luminis.quic.run.KwikVersion;
import org.scion.jpan.Scion;
import org.scion.jpan.socket.DatagramSocket;
import org.scion.jpan.ScionException;

import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class KwikClientHttps {

  public static void main2(String[] args) throws Exception {
    KwikCli cli = new KwikCli();
    KwikCli.main(new String[] {"-i", "https://ethz.ch:443"});
  }

  public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
    System.out.println("KWIK: " + KwikVersion.getVersion());

    // For non-default configuration, use the builder
    Logger stdoutLogger = new SysOutLogger();
    stdoutLogger.useRelativeTime(true);
    stdoutLogger.logPackets(true);

    System.out.println(
        Arrays.toString(SSLContext.getDefault().getSupportedSSLParameters().getProtocols()));
    //        HttpsURLConnection c;
    //        c = HttpsURLConnection.getDefaultSSLSocketFactory().createSocket()

    // URI uri = URI.create("https://www.google.com:443");
    URI uri = URI.create("https://ethz.ch:443");
    // URI uri = URI.create("https://netsys.ovgu.de:443");

    // https://github.com/quicwg/base-drafts/wiki/ALPN-IDs-used-with-QUIC
    // String applicationProtocolId = "h3";
    // https://github.com/netsec-ethz/scion-apps/blob/master/pkg/quicutil/single.go#L30-L48
    String applicationProtocolId = "qs"; // QUIC over SCION
    // QuicClientConnection connection = QuicClientConnection.newBuilder()
    QuicClientConnection connection =
        ScionConnectionBuilder.newBuilder()
            .uri(uri)
            .logger(stdoutLogger)
            .applicationProtocol(applicationProtocolId)
            .noServerCertificateCheck()

            //                .uri(URI.create("https://129.132.230.98:443"))
            //                //
            // https://github.com/netsec-ethz/scion-apps/blob/master/pkg/quicutil/single.go
            //                // -> APLN "qs"
            //                .applicationProtocol("hq")
            //                .socketFactory(address -> new org.scion.jpan.socket.DatagramSocket())
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    connection.connect();

    String host = "";
    int port = 443;
    QuicSocket quicSocket = new QuicSocket(uri, connection);
    SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    try (SSLSocket socket = (SSLSocket) factory.createSocket(quicSocket, host, port, true)) {

      socket.setEnabledCipherSuites(new String[] {"TLS_AES_128_GCM_SHA256"});
      socket.setEnabledProtocols(new String[] {"TLSv1.3"});

      String message = "Hello World Message";
      System.out.println("sending message: " + message);
      OutputStream os = new BufferedOutputStream(socket.getOutputStream());
      os.write(message.getBytes());
      os.flush();

      InputStream is = new BufferedInputStream(socket.getInputStream());
      byte[] data = new byte[2048];
      int len = is.read(data);
      System.out.printf("client received %d bytes: %s%n", len, new String(data, 0, len));
    }

    //    GET /topology HTTP/1.1
    //    User-Agent: Java/11.0.22
    //    Host: 127.0.1.1:45678
    //    Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2
    //    Connection: keep-alive
    String NL = System.lineSeparator();
      String sb = "GET / HTTP/1.1" + NL +
              "User-Agent: Java/11.0.22" + NL +
              // sb.append("Host: 127.0.1.1:45678").append(NL);
              "Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2" + NL +
              "Connection: keep-alive" + NL;

    QuicStream quicStream = connection.createStream(true);
    OutputStream output = quicStream.getOutputStream();
    output.write(sb.getBytes());
    output.close();
    InputStream input = quicStream.getInputStream();
    byte[] received = new byte[1000];
    int len = input.read(received);
  }

  private static class ScionConnectionBuilder extends QuicClientConnectionImpl.ExtendedBuilder {

    ScionConnectionBuilder() {
      super();
    }

    public static QuicClientConnection.Builder newBuilder() {
      return new ScionConnectionBuilder();
    }

    @Override
    public QuicClientConnectionImpl build() throws SocketException, UnknownHostException {
      super.socketFactory(ignored -> new DatagramSocket(30041).setRemoteDispatcher(true));
      super.addressResolver(
          hostName -> {
            System.out.println("STUB: ProxySelector.select()");
            try {
              InetAddress address =
                  Scion.defaultService().getScionAddress(hostName).getInetAddress();
              System.out.println("STUB: ProxySelector.select() - 2 " + address);
              return address;
            } catch (ScionException e) {
              // SCION resolution does not work, try norma resolution
            }
            System.out.println(
                "STUB: ProxySelector.select() - 3 " + InetAddress.getByName(hostName));
            return InetAddress.getByName(hostName);
          });
      return super.build();
    }
  }
}
