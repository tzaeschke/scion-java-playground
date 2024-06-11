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

import com.google.common.util.concurrent.AtomicDouble;
import com.zaxxer.ping.IcmpPinger;
import com.zaxxer.ping.PingResponseHandler;
import com.zaxxer.ping.PingTarget;
import org.jetbrains.annotations.NotNull;
import org.scion.jpan.*;
import org.scion.jpan.internal.PathRawParser;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This program takes a list of ISD/AS addresses and tries to measure latency to all of them. It
 * will also attempt an ICMP ping for comparison.<br>
 * The list is derived from <a
 * href="https://docs.anapaya.net/en/latest/resources/isd-as-assignments/">here</a> and is locally
 * stored in ISD-AS-Assignments.csv, see {@link ParseAssignments}.java.
 *
 * <p>There are several options for executing measurements (see "Policy"):<br>
 * - SCMP traceroute vs SCMP echo<br>
 * - Fastest vs shortest
 *
 * <p>Shortest: Report results on the path with the fewest hops. The number of hops can be evaluated
 * locally, so this is very fast.
 *
 * <p>Fastest: Report the path with the lowest latency. This takes much longer because it will try
 * all available paths before it can report on the best path.
 */
public class Echo {
  private static final boolean PRINT = true;
  private final int localPort;

  private int nAsTried = 0;
  private int nAsSuccess = 0;
  private int nAsError = 0;
  private int nAsTimeout = 0;
  private int nAsNoPathFound = 0;

  private int nPathTried = 0;
  private int nPathSuccess = 0;
  private int nPathTimeout = 0;

  private int nIcmpTried = 0;
  private int nIcmpSuccess = 0;
  private int nIcmpError = 0;
  private int nIcmpTimeout = 0;

  private static final Set<Long> listedAs = new HashSet<>();
  private static final Set<Long> seenAs = new HashSet<>();

  private enum Policy {
    /** Fastest path using SCMP traceroute */
    FASTEST_TR,
    /** Shortest path using SCMP traceroute */
    SHORTEST_TR,
    /** Fastest path using SCMP echo */
    FASTEST_ECHO,
    /** Fastest path using SCMP echo */
    SHORTEST_ECHO
  }

  private static final Policy POLICY = Policy.SHORTEST_TR;
  private static final boolean SHOW_PATH = true;

  public Echo(int localPort) {
    this.localPort = localPort;
  }

  public static void main(String[] args) throws IOException {
    // Local port must be 30041 for networks that expect a dispatcher
    Echo demo = new Echo(30041);
    for (ParseAssignments.HostEntry e : ParseAssignments.getList()) {
      //      if (!e.getName().startsWith("\"ETH")) {
      //        continue;
      //      }
      System.out.print(ScionUtil.toStringIA(e.getIsdAs()) + " " + e.getName() + "   ");
      demo.runDemo(e.getIsdAs());
      listedAs.add(e.getIsdAs());
    }

    // Try to identify ASes that occur in any paths but that are not on the public list.
    int nSeenButNotListed = 0;
    for (Long isdAs : seenAs) {
      if (!listedAs.contains(isdAs)) {
        nSeenButNotListed++;
      }
    }

    println("");
    println("AS Stats:");
    println(" all        = " + demo.nAsTried);
    println(" success    = " + demo.nAsSuccess);
    println(" no path    = " + demo.nAsNoPathFound);
    println(" timeout    = " + demo.nAsTimeout);
    println(" error      = " + demo.nAsError);
    println(" not listed = " + nSeenButNotListed);
    println("Path Stats:");
    println(" all        = " + demo.nPathTried);
    println(" success    = " + demo.nPathSuccess);
    println(" timeout    = " + demo.nPathTimeout);
    println("ICMP Stats:");
    println(" all        = " + demo.nIcmpTried);
    println(" success    = " + demo.nIcmpSuccess);
    println(" timeout    = " + demo.nIcmpTimeout);
    println(" error      = " + demo.nIcmpError);
  }

  private void runDemo(long destinationIA) throws IOException {
    nAsTried++;
    ScionService service = Scion.defaultService();
    // Dummy address. The traceroute will contact the control service IP instead.
    InetSocketAddress destinationAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {1, 2, 3, 4}), 12345);
    int nPaths;
    Scmp.TimedMessage msg;
    try {
      List<RequestPath> paths = service.getPaths(destinationIA, destinationAddress);
      if (paths.isEmpty()) {
        String src = ScionUtil.toStringIA(service.getLocalIsdAs());
        String dst = ScionUtil.toStringIA(destinationIA);
        println("No path found from " + src + " to " + dst);
        nAsNoPathFound++;
        return;
      }
      nPaths = paths.size();
      msg = findPaths(paths);
    } catch (ScionRuntimeException e) {
      println("ERROR: " + e.getMessage());
      nAsError++;
      return;
    }

    if (msg == null) {
      return;
    }

    // ICMP ping
    String icmpMs = pingICMP(msg.getPath().getRemoteAddress());

    // output
    String millis = String.format("%.4f", msg.getNanoSeconds() / (double) 1_000_000);
    int nHops = PathRawParser.create(msg.getPath().getRawPath()).getHopCount();
    String addr = msg.getPath().getRemoteAddress().getHostAddress();
    String out = "  " + addr + "  nPaths=" + nPaths + "  nHops=" + nHops;
    println(out + "  time=" + millis + "ms" + "  ICMP=" + icmpMs);
    if (msg.isTimedOut()) {
      nAsTimeout++;
    } else {
      nAsSuccess++;
    }
  }

  private Scmp.TimedMessage findPaths(List<RequestPath> paths) {
    switch (POLICY) {
      case FASTEST_TR:
        return findFastestTR(paths);
      case SHORTEST_TR:
        return findShortestTR(paths);
      case SHORTEST_ECHO:
        return findShortestEcho(paths);
      default:
        throw new UnsupportedOperationException();
    }
  }

  private Scmp.EchoMessage findShortestEcho(List<RequestPath> paths) {
    RequestPath path = PathPolicy.MIN_HOPS.filter(paths);
    ByteBuffer bb = ByteBuffer.allocate(0);
    int id = 0;
    try (ScmpChannel scmpChannel = Scmp.createChannel(localPort)) {
      nPathTried++;
      Scmp.EchoMessage msg = scmpChannel.sendEchoRequest(path, id, bb);
      if (msg == null) {
        println(" -> local AS, no timing available");
        nPathSuccess++;
        return null;
      }

      if (msg.isTimedOut()) {
        nPathTimeout++;
        return msg;
      }

      nPathSuccess++;

      if (SHOW_PATH) {
        print("  " + ScionUtil.toStringPath(path));
      }
      return msg;
    } catch (IOException e) {
      println("ERROR: " + e.getMessage());
      nAsError++;
      return null;
    }
  }

  private Scmp.TracerouteMessage findShortestTR(List<RequestPath> paths) {
    RequestPath path = PathPolicy.MIN_HOPS.filter(paths);
    try (ScmpChannel scmpChannel = Scmp.createChannel(localPort)) {
      nPathTried++;
      List<Scmp.TracerouteMessage> results = scmpChannel.sendTracerouteRequest(path);
      if (results.isEmpty()) {
        println(" -> local AS, no timing available");
        nPathSuccess++;
        return null;
      }

      for (Scmp.TracerouteMessage msg : results) {
        seenAs.add(msg.getIsdAs());
      }

      Scmp.TracerouteMessage msg = results.get(results.size() - 1);
      if (msg.isTimedOut()) {
        nPathTimeout++;
        return msg;
      }

      nPathSuccess++;

      if (SHOW_PATH) {
        print("  " + ScionUtil.toStringPath(path));
      }
      return msg;
    } catch (IOException e) {
      println("ERROR: " + e.getMessage());
      nAsError++;
      return null;
    }
  }

  private Scmp.TracerouteMessage findFastestTR(List<RequestPath> paths) {
    Scmp.TracerouteMessage best = null;
    RequestPath bestPath = null;
    try (ScmpChannel scmpChannel = Scmp.createChannel(localPort)) {
      for (RequestPath path : paths) {
        nPathTried++;
        List<Scmp.TracerouteMessage> results = scmpChannel.sendTracerouteRequest(path);
        if (results.isEmpty()) {
          println(" -> local AS, no timing available");
          nPathSuccess++;
          return null;
        }

        for (Scmp.TracerouteMessage msg : results) {
          seenAs.add(msg.getIsdAs());
        }

        Scmp.TracerouteMessage msg = results.get(results.size() - 1);
        if (msg.isTimedOut()) {
          nPathTimeout++;
          return msg;
        }

        nPathSuccess++;

        if (best == null || msg.getNanoSeconds() < best.getNanoSeconds()) {
          best = msg;
          bestPath = path;
        }
      }
      if (SHOW_PATH) {
        print("  " + ScionUtil.toStringPath(bestPath));
      }
      return best;
    } catch (IOException e) {
      println("ERROR: " + e.getMessage());
      nAsError++;
      return null;
    }
  }

  private String pingICMP(InetAddress address) {
    AtomicDouble seconds = new AtomicDouble(-2);
    PingResponseHandler handler =
        new PingResponseHandler() {
          @Override
          public void onResponse(@NotNull PingTarget pingTarget, double v, int i, int i1) {
            seconds.set(v);
          }

          @Override
          public void onTimeout(@NotNull PingTarget pingTarget) {
            seconds.set(-1);
          }
        };
    IcmpPinger pinger = new IcmpPinger(handler);
    PingTarget target = new PingTarget(address);
    Thread t = new Thread(pinger::runSelector);
    t.start();
    nIcmpTried++;

    pinger.ping(target);
    while (pinger.isPendingWork()) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    pinger.stopSelector();
    if (seconds.get() >= 0) {
      nIcmpSuccess++;
      double rounded = Math.round(seconds.get() * 10000.) / 10000.;
      return rounded * 1000 + "ms"; // milliseconds
    }
    if (seconds.get() == -1) {
      nIcmpTimeout++;
      return "TIMEOUT";
    }
    nIcmpError++;
    return "ERROR";
  }

  private void printPath(RequestPath path) {
    String nl = System.lineSeparator();
    //    sb.append("Actual local address:").append(nl);
    //    sb.append("
    // ").append(channel.getLocalAddress().getAddress().getHostAddress()).append(nl);
    String sb =
        "Using path:"
            + nl
            + "  Hops: "
            + ScionUtil.toStringPath(path)
            + " MTU: "
            + path.getMtu()
            + " NextHop: "
            + path.getInterface().getAddress(); // .append(nl);
    println(sb);
  }

  private static void print(String msg) {
    if (PRINT) {
      System.out.print(msg);
    }
  }

  private static void println(String msg) {
    if (PRINT) {
      System.out.println(msg);
    }
  }
}
