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
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import net.luminis.http3.Http3Client;
import net.luminis.http3.Http3ClientBuilder;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.SysOutLogger;
import org.scion.Constants;

public class Sample {

    public static void main(String[] args) throws IOException, InterruptedException {
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

        // OTHER TODO
        // - FIX SPURIOUS ERRORS!
        // - Use same DAEMON property as SCION proto / Disable for testing!
        // = Proper tests for /etc/scion/host reader & add /etc/hosts reader
        // - More (concurrent) API tests
        // - Facility to flush DNS cache?


        // Flupke Http3Client.newBuilder().localAddress() is broken (does not compile).


        System.setProperty(Constants.PROPERTY_BOOTSTRAP_NAPTR_NAME, "ethz.ch");
        args = new String[]{"https://ethz.ch"};
        //args = new String[]{"https://129.132.230.98"};
        //args = new String[]{"https://google.ch"};
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
        // HttpClient defaultClient = Http3Client.newHttpClient();

        // For non-default configuration, use the builder
        Logger stdoutLogger = new SysOutLogger();
        stdoutLogger.useRelativeTime(true);
        stdoutLogger.logPackets(true);

        HttpClient client = ((Http3ClientBuilder) Http3Client.newBuilder())
                .logger(stdoutLogger)
                .connectTimeout(Duration.ofSeconds(3))
                //.socketFactory(ignored -> new java.net.DatagramSocket())
                .socketFactory(ignored -> new org.scion.socket.DatagramSocket(30041))
       //         .localAddress(129.132.19.216")
                //.localAddress(InetAddress.getByName("129.132.19.216"))
                .build();

        try {
            long start = System.currentTimeMillis();
            HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            long end = System.currentTimeMillis();
            reportResult(httpResponse, end - start);

            Thread.sleep(10000);
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
            String outputFile = "http3-response.txt";
            Files.write(Paths.get(outputFile), httpResponse.body().getBytes());
            System.out.println("Response body written to file: " + outputFile);
        }
        else {
            System.out.println(httpResponse.body());
        }
    }
}
