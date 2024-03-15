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

import net.luminis.http3.Http3Client;
import net.luminis.http3.Http3ClientBuilder;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.SysOutLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

/** Demo with flupke HTTP/3 client. https://github.com/ptrd/flupke */
public class FlupkeClientHttp3 {

  public static void main2(String[] args) throws IOException, InterruptedException {
    URI uri = URI.create("https://sample.com:443");
    HttpRequest request = HttpRequest.newBuilder(uri).GET().build(); // TODO ?

    HttpClient.Builder clientBuilder = new Http3ClientBuilder();
    HttpClient client = clientBuilder.build();
    HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
    System.out.println("Received: " + httpResponse.body());
  }


  public static void main(String[] args) throws IOException, InterruptedException {

    args = new String[]{"https://ethz.ch"};
    // args = new String[]{"https://www.google.com"};

    if (args.length != 1) {
      System.err.println("Missing argument: server URL");
      System.exit(1);
    }

    URI serverUrl = URI.create(args[0]);

    HttpRequest request = HttpRequest.newBuilder()
            .uri(serverUrl)
            .header("User-Agent", "Flupke http3 library")
            .timeout(Duration.ofSeconds(10))
            .build();

    // Easiest way to create a client with default configuration
    HttpClient defaultClient = Http3Client.newHttpClient();

    // For non-default configuration, use the builder
    Logger stdoutLogger = new SysOutLogger();
    stdoutLogger.useRelativeTime(true);
    stdoutLogger.logPackets(true);

    HttpClient client = ((Http3ClientBuilder) Http3Client.newBuilder())
            .logger(stdoutLogger)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    try {
      long start = System.currentTimeMillis();
      HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
      long end = System.currentTimeMillis();
      reportResult(httpResponse, end - start);
    }
    catch (IOException e) {
      System.err.println("Request failed: " + e.getMessage());
    }
    catch (InterruptedException e) {
      System.err.println("Request interrupted: " + e.getMessage());
    }
  }

  private static void reportResult(HttpResponse<String> httpResponse, long duration) throws IOException {
    System.out.println("Request completed in " + duration + " ms");
    System.out.println("Got HTTP response " + httpResponse);
    System.out.println("-   HTTP headers: ");
    httpResponse.headers().map().forEach((k, v) -> System.out.println("--  " + k + "\t" + v));
    long downloadSpeed = httpResponse.body().length() / duration;
    System.out.println("-   HTTP body (" + httpResponse.body().length() + " bytes, " + downloadSpeed + " KB/s):");
    if (httpResponse.body().length() > 10 * 1024) {
      String outputFile = "http3-response.html";
      Files.write(Paths.get(outputFile), httpResponse.body().getBytes());
      System.out.println("Response body written to file: " + outputFile);
    }
    else {
      System.out.println(httpResponse.body());
    }
  }
}
