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
import org.scion.jpan.internal.IPHelper;
import org.scion.jpan.internal.STUN;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StunAll {

    public static void main(String[] args) throws IOException {
        Path file = toResourcePath("stun-servers3.txt");

        BufferedReader br = new BufferedReader(new FileReader(file.toFile()));
        String st;
        int nTotal = 0;
        int nUnknownHost = 0;
        final ByteUtil.MutInt nNull = new ByteUtil.MutInt(0);
        final ByteUtil.MutInt nTimeout = new ByteUtil.MutInt(0);
        final ByteUtil.MutInt nSuccess = new ByteUtil.MutInt(0);
        while ((st = br.readLine()) != null) {
            nTotal++;
            System.out.print("Trying: " + st + " ... ");
            InetSocketAddress addr;
            if (st.startsWith("[") || Character.isDigit(st.charAt(0))) {
                addr = IPHelper.toInetSocketAddress(st);
            } else {
                try {
                    int pos = st.indexOf(":");
                    if (pos > 0) {
                        InetAddress inet = InetAddress.getByName(st.substring(0, pos));
                        int port = Integer.parseInt(st.substring(pos + 1));
                        addr = new InetSocketAddress(inet, port);
                    } else {
                        InetAddress inet = InetAddress.getByName(st);
                        addr = new InetSocketAddress(inet, 3478);
                    }
                } catch (UnknownHostException e) {
                    nUnknownHost++;
                    System.out.println("Unknown host");
                    continue;
                }
            }
            System.out.print(addr + " ... ");
            if (test(addr, nTimeout, nNull, nSuccess)) {
                // yay!
            }
        }
        System.out.println("Summary");
        System.out.println("   total:        " + nTotal);
        System.out.println("   unknown host: " + nUnknownHost);
        System.out.println("   timeout:      " + nTimeout.get());
        System.out.println("   null:         " + nNull.get());
        System.out.println("   success:      " + nSuccess.get());
    }

    private static boolean test(
            InetSocketAddress server,
            ByteUtil.MutInt nTimeout,
            ByteUtil.MutInt nNull,
            ByteUtil.MutInt nSuccess)
            throws IOException {
        ByteBuffer out = ByteBuffer.allocate(1000);
        STUN.TransactionID id = STUN.writeRequest(out);
        out.flip();

        try (DatagramSocket channel = new DatagramSocket()) {
            channel.setSoTimeout(1000);

            DatagramPacket pOut = new DatagramPacket(out.array(), out.remaining(), server);
            long time0 = System.nanoTime();
            channel.send(pOut);

            ByteBuffer in = ByteBuffer.allocate(1000);
            DatagramPacket pIn = new DatagramPacket(new byte[1000], 1000);
            try {
                channel.receive(pIn);
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout");
                nTimeout.v++;
                return false;
            }
            long time1 = System.nanoTime();
            //      InetSocketAddress server2 = (InetSocketAddress) pIn.getSocketAddress();
            //      System.out.println("Received from: " + server2);
            for (int i = 0; i < pIn.getLength(); i++) {
                in.put(pIn.getData()[i]);
            }
            in.flip();

            boolean isSTUN = STUN.isStunPacket(in, id);
            if (isSTUN) {
                InetSocketAddress external = STUN.parseResponse(in, id);
                if (external == null) {
                    nNull.v++;
                    System.out.println("NULL");
                    return false;
                }
                long timeMs = (time1 - time0) / 1_000_000;
                System.out.print("Address: " + external);
                System.out.println("    time= " + timeMs + "ms");
                nSuccess.v++;
            }
        }
        return true;
    }

    public static Path toResourcePath(Path file) {
        if (file == null) {
            return null;
        }
        try {
            ClassLoader classLoader = StunAll.class.getClassLoader();
            URL resource = classLoader.getResource(file.toString());
            if (resource != null) {
                return Paths.get(resource.toURI());
            }
            throw new IllegalArgumentException("Resource not found: " + file);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path toResourcePath(String fileName) {
        if (fileName == null) {
            return null;
        }
        return toResourcePath(Paths.get(fileName));
    }
}
