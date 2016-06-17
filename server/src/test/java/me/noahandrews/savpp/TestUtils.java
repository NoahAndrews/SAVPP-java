package me.noahandrews.savpp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * MIT License
 * <p>
 * Copyright (c) 2016 Noah Andrews
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
class TestUtils {
    final static String MD5_HASH = "5a73e7b6df89f85bb34129fcdfd7da12";
    final static String MD5_HASH_2 = "bedb04bb540934fda8b12ed4aaa2fc34";

    static Logger logger = LogManager.getLogger();

    private ServerConnector serverConnector;

    public TestUtils(ServerConnector serverConnector) {
        this.serverConnector = serverConnector;
    }

    static void printTestHeader(String descriptor) {
        System.out.println("\n\nRunning " + descriptor + "\n==========================================================");
    }

    void submitConnectionRequest() throws IOException {
        submitConnectionRequest(MD5_HASH);
    }

    void submitConnectionRequest(String md5Hash) throws IOException {
        logger.traceEntry();
        SAVPPProto.SAVPPMessage connectionRequest = SAVPPProto.SAVPPMessage.newBuilder()
                .setType(SAVPPProto.SAVPPMessage.MessageType.CONNECTION_REQUEST)
                .setConnectionRequest(SAVPPProto.ConnectionRequest.newBuilder().setMd5(md5Hash))
                .build();

        connectionRequest.writeDelimitedTo(serverConnector.getSocket().getOutputStream());
        logger.traceExit();
    }

    void connectToServer() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        serverConnector.getServer().setEventHandler(new SAVPPServer.EventHandler() {
            @Override
            public void connectionEstablished() {
                latch.countDown();
            }
        });

        submitConnectionRequest(MD5_HASH);

        latch.await();
    }
}
