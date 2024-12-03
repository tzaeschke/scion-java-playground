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

package org.scion.examples.stun;

import org.scion.jpan.internal.ByteUtil;
import org.scion.jpan.internal.STUN;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class StunOne {

  public static void main(String[] args) throws IOException {
    ByteBuffer out = ByteBuffer.allocate(1000);
    STUN.TransactionID id = STUN.writeRequest(out);
    out.flip();

    if (!STUN.isStunPacket(out, id)) {
      throw new IllegalStateException();
    }
    STUN.parseResponse(out, id);
    out.flip();

    // InetAddress addr = InetAddress.getByName("stun.solnet.ch");
    // InetAddress addr = InetAddress.getByName("stun.ipfire.org");
    // InetAddress addr = InetAddress.getByName("relay.webwormhole.io");
    // InetAddress addr = InetAddress.getByName("stun.zoiper.com"); //  XOR
    // InetAddress addr = InetAddress.getByName("stun.12connect.com"); // Vovida 0.98
    // InetAddress addr = InetAddress.getByName("stun.1und1.de");
    // stun.commpeak.com // unknown SOFTWARE
    // stun.counterpath.com // vovida.org 0.98-CPC
    String addrStr;
    // addrStr = "stun.linphone.org";    // DIfferent IP: 226.50.80.11:64249, oRTP 0.99
    // stun.solcon.nl          // DIfferent IP: 226.50.80.11:33989
    // stun.usfamily.net        // Different IP: 154.251.231.174:43532

    addrStr = "stun.ekiga.net"; // Has 2nd MAPPED address with wrong port.
    // addrStr = "stun.ippi.fr";  // ERROR code
    // addrStr = "stun.mywatson.it"; // ERROR code

    addrStr = "stun.dunyatelekom.com"; // Has no MAPPED_ADDRESS

    InetAddress addr = InetAddress.getByName(addrStr);
    InetSocketAddress server = new InetSocketAddress(addr, 3478);
    try (DatagramChannel channel = DatagramChannel.open()) {

      int sent = channel.send(out, server);
      System.out.println("Sent bytes: " + sent);

      System.out.println("Waiting ...");
      ByteBuffer in = ByteBuffer.allocate(1000);
      InetSocketAddress server2 = (InetSocketAddress) channel.receive(in);
      System.out.println("Received from: " + server2);
      in.flip();
      System.out.print("byte[] raw = {");
      for (int i = 0; i < in.remaining(); i++) {
        System.out.print(in.get(i) + ", ");
      }
      System.out.println("};");

      ByteUtil.MutRef<String> error = new ByteUtil.MutRef<>();
      InetSocketAddress external = STUN.parseResponse(in, id::equals, error);
      if (error.get() != null) {
        throw new IllegalStateException(error.get());
      }
      System.out.println("Address: " + external);
    }
  }
}
