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

package org.scion.examples.flupke;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.luminis.http3.Http3Client;
import net.luminis.http3.Http3ClientBuilder;
import net.luminis.http3.impl.FlupkeVersion;
import net.luminis.http3.server.Http3ApplicationProtocolFactory;
import net.luminis.http3.server.HttpRequestHandler;
import net.luminis.http3.server.HttpServerRequest;
import net.luminis.http3.server.HttpServerResponse;
import net.luminis.quic.QuicConnection;
import net.luminis.quic.QuicStream;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.SysOutLogger;
import net.luminis.quic.run.KwikVersion;
import net.luminis.quic.sample.echo.EchoServer;
import net.luminis.quic.server.*;

/** Demo with flupke HTTP/3 client. https://github.com/ptrd/flupke */
public class FlupkeServerHttp3 {

//  public static void main(String[] args) throws IOException, InterruptedException {
//
//    HttpRequestHandler rh = new HttpRequestHandler() {
//      @Override
//      public void handleRequest(HttpServerRequest request, HttpServerResponse response) throws IOException {
//        System.out.println("Request:");
//        System.out.println("  method: " + request.method());
//        System.out.println("  path:   " + request.path());
//
//        response.getOutputStream().write(42);
//        response.setStatus();
//
//
//
//        if (request.method().equals("GET")) {
//          String path = request.path();
//          if (path.equals("/version")) {
//            response.setStatus(200);
//            String versionLine = "Kwik version: " + KwikVersion.getVersion() + "\n"
//                    + "Flupke version: " + FlupkeVersion.getVersion() + "\n";
//            response.getOutputStream().write(versionLine.getBytes());
//            response.getOutputStream().close();
//            log(request, response);
//            return;
//          }
//
//          if (path.isBlank() || path.equals("/")) {
//            path = "index.html";
//          }
////          File fileInWwwDir = getFileInWwwDir(path);
////          if (fileInWwwDir != null && fileInWwwDir.exists() && fileInWwwDir.isFile() && fileInWwwDir.canRead()) {
////            response.setStatus(200);
////            try (FileInputStream fileIn = new FileInputStream(fileInWwwDir); OutputStream out = response.getOutputStream()) {
////              fileIn.transferTo(out);
////            }
////          }
////          else {
////            Matcher sizeNameMacher = Pattern.compile("/{0,1}(\\d+)([kmKM])").matcher(path);
////            if (sizeNameMacher.matches()) {
////              int size = Integer.parseInt(sizeNameMacher.group(1));
////              String unit = sizeNameMacher.group(2).toLowerCase();
////              long sizeInBytes = size * (unit.equals("k")? 1024: unit.equals("m")? 1024 * 1024: 1);
////              if (sizeInBytes > MAX_DOWNLOAD_SIZE) {
////                response.setStatus(509); // Bandwidth Limit Exceeded
////                return;
////              }
////              transferFileOfSize(sizeInBytes, response.getOutputStream());
////            }
////            else {
//              response.setStatus(404);
////            }
////          }
//        }
//        else {
//          response.setStatus(405);
//        }
//        log(request, response);
//      }
//
//      private void log(HttpServerRequest request, HttpServerResponse response) {
//        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("d/MMM/yyyy:HH:mm:ss Z").withZone(ZoneId.systemDefault()); // TODO
//        // Using standard Apache Access Log format
//        String logLine = request.clientAddress().getHostAddress() + " " +
//                // client identity
//                "- " +
//                // client userid
//                "- " +
//                // time that the request was received
//                "[" + timeFormatter.format(request.time()) + "] " +
//                // request line
//                request.method() + " " + request.path() + " " + "HTTP/3 " +
//                // status code
//                response.status() + " " +
//                // size of the response
//                response.size();
//
//        System.out.println(logLine);
//      }
//    };
//    Http3ApplicationProtocolFactory f = new Http3ApplicationProtocolFactory(rh);
//    ServerConnectionFactory scf = new ServerConnectionFactory();
//    QuicConnection qc = scf.createNewConnection();
//    f.createConnection(null, qc);
//  }




  private static void usageAndExit() {
    System.err.println("Usage: cert file, cert key file, port number");
    System.exit(1);
  }

  public static void main(String[] args) throws Exception {
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
