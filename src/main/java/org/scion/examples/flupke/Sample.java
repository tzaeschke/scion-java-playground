/*
 * Copyright © 2019, 2020, 2021, 2022, 2023 Peter Doornbosch
 *
 * This file is part of Flupke, a HTTP3 client Java library
 *
 * Flupke is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Flupke is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.scion.examples.flupke;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import net.luminis.http3.Http3Client;
import net.luminis.http3.Http3ClientBuilder;
import net.luminis.quic.DatagramSocketFactory;
import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicSessionTicket;
import net.luminis.quic.core.ClientConnectionConfig;
import net.luminis.quic.core.QuicClientConnectionImpl;
import net.luminis.quic.core.Version;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.SysOutLogger;
import net.luminis.tls.TlsConstants;
import org.scion.jpan.*;

import static net.luminis.http3.core.Http3ClientConnection.DEFAULT_HTTP3_PORT;

public class Sample {

  public static void main(String[] args) throws IOException, InterruptedException {
    // Create self signed certificate:
    // - openssl genrsa -out cert.key 2048
    // - openssl req -new -key cert.key -out cert.csr
    // - openssl x509 -req -days 3650 -in cert.csr -signkey cert.key -out cert.crt

    // SCION notes:
    // - Flupke (KWIK) may resolve the name to an IP. This is problematic, because with
    //   an IP we can't do DNS lookup for remote ISD/AS.
    //   Library MUST NOT perform DNS lookup. Well, Flupke doesn't, but the JDK does.
    //   FIX: DatagramPacket has an address. Instead of using the packet's SocketAddress
    //        we should get the hostname and do our own lookup!
    //
    // - We must provide the port 30041 (at least as long as we have dispatchers)
    // - If we provide an IP that is in the local network, we should recognize this and
    //   return the local ISD/AS. -> BUG
    //   THIS is DANGEROUS! SCION allows two subnets to be connected directly -> there is no
    //   guarantee that the IP is in the same subnet!
    //   Solution: If we have a hostname we must assume different subnets (unless DNS lookup
    //   says otherwise). Only if we don´t have a hostname we can assume the subnet to be local.
    //
    // - Also, KWIK rightfully discard UDP packets that come from a different IP than what
    //   is reported by a DNS A/AAAA lookup. However, SCION uses a different IP (TXT "scion=").
    //   To prevent packets from being dropped, we have to reverse-map the IP address to A/AAAA.
    //   We can do this using an internal map in the socket/channel.

    // OTHER TODO
    // - FIX SPURIOUS ERRORS!
    // - More (concurrent) API tests

    args = new String[] {"https://ethz.ch"};
    // args = new String[]{"https://www.google.ch"};
    if (args.length != 1) {
      System.err.println("Missing argument: server URL");
      System.exit(1);
    }

    URI serverUrl = URI.create(args[0]);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(serverUrl)
            .header("User-Agent", "Flupke http3 library")
            .timeout(Duration.ofSeconds(10))
            .build();

    // For non-default configuration, use the builder
    Logger stdoutLogger = new SysOutLogger();
    stdoutLogger.useRelativeTime(true);
    stdoutLogger.logPackets(true);

//    ProxySelector proxySelector = new ProxySelector() {
//      @Override
//      public List<Proxy> select(URI uri) {
//        System.out.println("STUB: ProxySelector.select()");
//          try {
//            InetAddress host = Scion.defaultService().getScionAddress(uri.getHost()).getInetAddress();
//            int port = uri.getPort();
//            if (port <= 0) {
//              port = DEFAULT_HTTP3_PORT;
//            }
//            // TODO
//            port = 30041;
//            InetSocketAddress address = new InetSocketAddress(host, port);
//            Proxy.Type type = Proxy.Type.HTTP;
//            Proxy proxy = new Proxy(type, address);
//            System.out.println("STUB: ProxySelector.select() - 2");
//            return List.of(proxy);
//          } catch (ScionException e) {
//              throw new RuntimeException(e);
//          }
//      }
//
//      @Override
//      public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
//        System.out.println("STUB: ProxySelector.connectFailed()");
//      }
//    };

    HttpClient client =
        Http3Client.newBuilder()
            .logger(stdoutLogger)
            .connectTimeout(Duration.ofSeconds(3))
            //.proxy(proxySelector)
            // TODO instead of socketFactory, introduce Quic(Client)ConnectionFactory?
            //       .socketFactory(ignored -> new java.net.DatagramSocket())
//            .socketFactory(
//                ignored ->
//                    new org.scion.jpan.socket.DatagramSocket(30041).setRemoteDispatcher(true))
            .connectionBuilderFactory(ScionConnectionBuilder::new)
            .build();

    try {
      long start = System.currentTimeMillis();
      HttpResponse<String> httpResponse =
          client.send(request, HttpResponse.BodyHandlers.ofString());
      long end = System.currentTimeMillis();
      reportResult(httpResponse, end - start);

      Thread.sleep(10000);
    } catch (IOException e) {
      System.err.println("Request failed: " + e.getMessage());
    } catch (InterruptedException e) {
      System.err.println("Request interrupted: " + e.getMessage());
    }
  }

  private static void reportResult(HttpResponse<String> httpResponse, long duration)
      throws IOException {
    System.out.println("Request completed in " + duration + " ms");
    System.out.println("Got HTTP response " + httpResponse);
    System.out.println("-   HTTP headers: ");
    httpResponse.headers().map().forEach((k, v) -> System.out.println("--  " + k + "\t" + v));
    long downloadSpeed = httpResponse.body().length() / duration;
    System.out.println(
        "-   HTTP body (" + httpResponse.body().length() + " bytes, " + downloadSpeed + " KB/s):");
    if (httpResponse.body().length() > 10 * 1024) {
      String outputFile = "http3-response.html";
      Files.write(Paths.get(outputFile), httpResponse.body().getBytes());
      System.out.println("Response body written to file: " + outputFile);
    } else {
      System.out.println(httpResponse.body());
    }
  }

  private static class ScionConnectionBuilder extends QuicClientConnectionImpl.ExtendedBuilder {

    ScionConnectionBuilder() {
      super();
    }

    @Override
    public QuicClientConnectionImpl build() throws SocketException, UnknownHostException {
      super.socketFactory(ignored -> new ScionDatagramSocket(30041).setRemoteDispatcher(true));
        super.addressResolver(hostName -> {
          System.out.println("STUB: ProxySelector.select()");
          try {
            InetAddress address = Scion.defaultService().lookupAndGetPath(hostName, 12345, PathPolicy.DEFAULT).getRemoteAddress();
            System.out.println("STUB: ProxySelector.select() - 2 " + address);
            return address;
          } catch (ScionException e) {
            // SCION resolution does not work, try norma resolution
          }
          System.out.println("STUB: ProxySelector.select() - 3 " + InetAddress.getByName(hostName));
          return InetAddress.getByName(hostName);
        }
        );
        return super.build();
    }
  }
}
