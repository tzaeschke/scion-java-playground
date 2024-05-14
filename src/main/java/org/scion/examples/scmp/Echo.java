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

package org.scion.examples.scmp;

import org.scion.jpan.*;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;

public class Echo {
  private static final boolean PRINT = true;
  private final int localPort;

  private int nTried = 0;
  private int nSuccess = 0;
  private int nError = 0;
  private int nTimeout = 0;
  private int nNoPathFound = 0;


  public Echo(int localPort) {
    this.localPort = localPort;
  }

  public static void main(String[] args) throws IOException {
    // Local port must be 30041 for networks that expect a dispatcher
    Echo demo = new Echo(30041);
    //        // demo.runDemo(DemoConstants.iaOVGU, serviceIP);
    //        ScionAddress sa = Scion.defaultService().getScionAddress("ethz.ch");
    //        demo.runDemo(sa.getIsdAs(), new InetSocketAddress(sa.getInetAddress(), 30041));
    //        // demo.runDemo(DemoConstants.iaGEANT, serviceIP);

    for (ParseAssignments.HostEntry e : ParseAssignments.getList()) {
      System.out.print(ScionUtil.toStringIA(e.getIsdAs()) + " " + e.getName() + "   ");
      demo.runDemo(e.getIsdAs());
    }

    println("");
    println("Stats:");
    println(" all      = " + demo.nTried);
    println(" success  = " + demo.nSuccess);
    println(" no path  = " + demo.nNoPathFound);
    println(" timeout  = " + demo.nTimeout);
    println(" error    = " + demo.nError);
  }

  private void runDemo(long destinationIA) throws IOException {
    nTried++;
    ScionService service = Scion.defaultService();
    // Dummy address. The traceroute will contact the control service IP instead.
    InetSocketAddress destinationAddress =
        new InetSocketAddress(Inet4Address.getByAddress(new byte[] {1, 2, 3, 4}), 12345);
    RequestPath path;
    try {
      List<RequestPath> paths = service.getPaths(destinationIA, destinationAddress);
      if (paths.isEmpty()) {
        String src = ScionUtil.toStringIA(service.getLocalIsdAs());
        String dst = ScionUtil.toStringIA(destinationIA);
        println("No path found from " + src + " to " + dst);
        nNoPathFound++;
        return;
      }
      path = paths.get(0);
    } catch (ScionRuntimeException e) {
      println("ERROR: " + e.getMessage());
      nError++;
      return;
    }

    try (ScmpChannel scmpChannel = Scmp.createChannel(path, localPort)) {
      // printPath(scmpChannel);
      List<Scmp.TracerouteMessage> results = scmpChannel.sendTracerouteRequest();
      if (!results.isEmpty()) {
        Scmp.TracerouteMessage msg = results.get(results.size() - 1);
        String millis = String.format("%.4f", msg.getNanoSeconds() / (double) 1_000_000);
        println(" " + millis + "ms");
        if (msg.isTimedOut()) {
          nTimeout++;
        } else {
          nSuccess++;
        }
      }
    } catch (IOException e) {
      println("ERROR: " + e.getMessage());
      nError++;
    }
  }

  private void runDemo(long dstIA, InetSocketAddress dstAddress) throws IOException {
    List<RequestPath> paths = Scion.defaultService().getPaths(dstIA, dstAddress);
    if (paths.isEmpty()) {
      System.out.println(" No path found!");
      return;
    }
    RequestPath path = paths.get(0);
    ByteBuffer data = ByteBuffer.allocate(0);

    //        println("Listening on port " + localPort + " ...");
    //        try (ScionDatagramChannel channel = ScionDatagramChannel.open()) {
    //            channel.connect(path);
    //            println("Resolved local address: ");
    //            println("  " + channel.getLocalAddress().getAddress().getHostAddress());
    //        }

    try (ScmpChannel scmpChannel = Scmp.createChannel(path, localPort)) {
      //            printPath(scmpChannel);
      for (int i = 0; i < 1; i++) {
        Scmp.EchoMessage msg = scmpChannel.sendEchoRequest(i, data);
        //                if (i == 0) {
        //                    printHeader(dstIA, dstAddress, data, msg);
        //                }
        String millis = String.format("%.3f", msg.getNanoSeconds() / (double) 1_000_000);
        String echoMsgStr = msg.getSizeReceived() + " bytes from ";
        // TODO get actual address from response
        InetAddress addr = msg.getPath().getRemoteAddress();
        echoMsgStr += ScionUtil.toStringIA(dstIA) + "," + addr.getHostAddress();
        echoMsgStr += ": scmp_seq=" + msg.getSequenceNumber();
        if (msg.isTimedOut()) {
          echoMsgStr += " Timed out after";
        }
        echoMsgStr += " time=" + millis + "ms";
        println(echoMsgStr);
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    } catch (IOException e) {
      System.out.println("  Ping faild to " + dstAddress + " with " + e.getMessage());
    }
  }

  private void printPath(ScmpChannel channel) {
    String nl = System.lineSeparator();
    StringBuilder sb = new StringBuilder();
    //    sb.append("Actual local address:").append(nl);
    //    sb.append("
    // ").append(channel.getLocalAddress().getAddress().getHostAddress()).append(nl);
    RequestPath path = channel.getConnectionPath();
    sb.append("Using path:").append(nl);
    sb.append("  Hops: ").append(ScionUtil.toStringPath(path));
    sb.append(" MTU: ").append(path.getMtu());
    sb.append(" NextHop: ").append(path.getInterface().getAddress()).append(nl);
    println(sb.toString());
  }

  private static void println(String msg) {
    if (PRINT) {
      System.out.println(msg);
    }
  }
}
