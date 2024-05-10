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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;

import net.luminis.quic.QuicClientConnection;
import net.luminis.quic.QuicStream;
import net.luminis.quic.core.QuicClientConnectionImpl;
import net.luminis.quic.log.Logger;
import net.luminis.quic.log.SysOutLogger;
import net.luminis.quic.run.KwikCli;
import net.luminis.quic.run.KwikVersion;
import org.scion.jpan.Scion;
import org.scion.jpan.ScionException;

public class KwikClientHttp2 {

  public static void main2(String[] args) throws Exception {
        KwikCli cli = new KwikCli();
        cli.main(new String[] {"-i", "https://ethz.ch:443"});
}

    public static void main(String[] args) throws IOException {
        System.out.println("KWIK: " + KwikVersion.getVersion());

        // For non-default configuration, use the builder
        Logger stdoutLogger = new SysOutLogger();
        stdoutLogger.useRelativeTime(true);
        stdoutLogger.logPackets(true);

        // https://github.com/quicwg/base-drafts/wiki/ALPN-IDs-used-with-QUIC
        // String applicationProtocolId = "h3";
        // https://github.com/netsec-ethz/scion-apps/blob/master/pkg/quicutil/single.go#L30-L48
        String applicationProtocolId = "qs"; // QUIC over SCION
        // QuicClientConnection connection = QuicClientConnection.newBuilder()
        QuicClientConnection connection = ScionConnectionBuilder.newBuilder()
                //.uri(URI.create("https://www.google.com:443"))
                .uri(URI.create("https://ethz.ch:443"))
                //.uri(URI.create("https://netsys.ovgu.de:443"))
                .logger(stdoutLogger)
                .applicationProtocol(applicationProtocolId)
                .noServerCertificateCheck()

//                .uri(URI.create("https://129.132.230.98:443"))
//                // https://github.com/netsec-ethz/scion-apps/blob/master/pkg/quicutil/single.go
//                // -> APLN "qs"
//                .applicationProtocol("hq")
//                .socketFactory(address -> new org.scion.jpan.socket.DatagramSocket())
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        connection.connect();
//byte[] re = new byte[] {1, 48, 0, 0, 80, 18, 119, 119, 119, 46, 103, 111, 111, 103, 108, 101, 46, 99, 111, 109, 58, 52, 52, 51, -47, -41, -63, 95, 80, 20, 70, 108, 117, 112, 107, 101, 32, 104, 116, 116, 112, 51, 32, 108, 105, 98, 114, 97, 114, 121};
//String s = new String(re);
//System.out.println("Data: " + s);
        //    GET /topology HTTP/1.1
        //    User-Agent: Java/11.0.22
        //    Host: 127.0.1.1:45678
        //    Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2
        //    Connection: keep-alive
        String NL = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("GET / HTTP/1.1").append(NL);
        sb.append("User-Agent: Java/11.0.22").append(NL);
        //sb.append("Host: 127.0.1.1:45678").append(NL);
        sb.append("Accept: text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2").append(NL);
        sb.append("Connection: keep-alive").append(NL);

        QuicStream quicStream = connection.createStream(true);
        OutputStream output = quicStream.getOutputStream();
        output.write(sb.toString().getBytes());
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
            super.socketFactory(
                    ignored ->
                            new org.scion.jpan.socket.DatagramSocket(30041).setRemoteDispatcher(true));
            super.addressResolver(hostName -> {
                        System.out.println("STUB: ProxySelector.select()");
                        try {
                            InetAddress address = Scion.defaultService().getScionAddress(hostName).getInetAddress();
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
