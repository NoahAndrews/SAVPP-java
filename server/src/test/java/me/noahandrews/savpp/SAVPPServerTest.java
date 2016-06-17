package me.noahandrews.savpp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

import static me.noahandrews.savpp.SAVPPProto.ConnectionRequest;
import static me.noahandrews.savpp.SAVPPProto.SAVPPMessage;
import static me.noahandrews.savpp.SAVPPServer.State.*;
import static org.junit.Assert.assertEquals;

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

public class SAVPPServerTest {
    private SAVPPServer savppServer;

    private Socket socket;

    private final String MD5_HASH = "5a73e7b6df89f85bb34129fcdfd7da12";
    private final String MD5_HASH_2 = "bedb04bb540934fda8b12ed4aaa2fc34";

    Logger logger = LogManager.getLogger();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        savppServer = new SAVPPServer(MD5_HASH);
        savppServer.startListening();

        while(savppServer.getState() != LISTENING) {}

        logger.debug("Connecting socket to SAVPPServer");
        socket = new Socket("localhost", SAVPPValues.PORT_NUMBER);
    }

    @After
    public void tearDown() throws Exception {
        socket.close();
        savppServer.tearDown();
    }

    @Test(timeout = 1000)
    public void testServerCreation() throws Exception {
        printTestHeader("server creation test");

        savppServer.tearDown(); //TODO: clean this mess up
        while(savppServer.getState() != DESTROYED) {}
        savppServer = new SAVPPServer(MD5_HASH);
        CountDownLatch latch = new CountDownLatch(1);

        savppServer.setEventHandler(new SAVPPServer.EventHandler() {
            @Override
            public void serverStarted() {
                latch.countDown();
            }
        });
        savppServer.startListening();

        latch.await();
        assertEquals(LISTENING, savppServer.getState());
    }

    @Test(timeout = 2000)
    public void incorrectHash() throws Exception {
        printTestHeader("incorrect hash test");

        CountDownLatch latch = new CountDownLatch(1);
        final String[] hash = new String[1];
        savppServer.setEventHandler(new SAVPPServer.EventHandler() {
            @Override
            public void incorrectMD5HashReceived(String receivedHash) {
                logger.debug("Received incorrect hash event");
                hash[0] = receivedHash;
                latch.countDown();
            }
        });
        submitConnectionRequest(MD5_HASH_2);
        latch.await();
        assertEquals(MD5_HASH_2, hash[0]);
        assertEquals(LISTENING, savppServer.getState());
    }

    @Test(timeout = 1000)
    public void testConnection() throws Exception {
        printTestHeader("connection test");

        CountDownLatch latch = new CountDownLatch(1);

        savppServer.setEventHandler(new SAVPPServer.EventHandler() {
            @Override
            public void connectionEstablished() {
                latch.countDown();
            }
        });

        submitConnectionRequest(MD5_HASH);

        latch.await();
        assertEquals(CONNECTED, savppServer.getState());
    }

    @Test(timeout = 1000)
    public void invalidHashRaisesException() throws Exception {
        printTestHeader("invalid hash test");
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid MD5 hash");
        savppServer = new SAVPPServer("1234567890abcdef");
    }

    @Test(timeout = 3000)
    public void invalidData() throws Exception {
        printTestHeader("invalid data test");

        System.out.println("Sending junk data");

        socket.getOutputStream().write("This is not a SAVPP Message".getBytes());

        InputStream inputStream = socket.getInputStream();

        SAVPPMessage message = SAVPPMessage.parseDelimitedFrom(inputStream);
        assertEquals(SAVPPProto.Error.ErrorType.INVALID_DATA, message.getError().getType());
    }

    private void printTestHeader(String descriptor) {
        System.out.println("\n\nRunning " + descriptor + "\n==========================================================");
    }

    private void submitConnectionRequest(String md5Hash) throws IOException {
        logger.traceEntry();
        SAVPPMessage connectionRequest = SAVPPMessage.newBuilder()
                .setType(SAVPPMessage.MessageType.CONNECTION_REQUEST)
                .setConnectionRequest(ConnectionRequest.newBuilder().setMd5(md5Hash))
                .build();

        connectionRequest.writeDelimitedTo(socket.getOutputStream());
        logger.traceExit();
    }

    //TODO: Test what happens when something other than a SAVPPMessage is sent

    //TODO: When the first SAVPPMessage is something other than a ConnectionRequest, expect an error packet
    //TODO: Sending a ConnectionRequest at this point should result in a successful connection.

    //TODO: Test what happens when a process is already bound to the SAVPP port

    //TODO: If tearDown has been called, calling any other method should result in an exception.

    //TODO: Write tests to go from manual state verification to automated state verification

    //TODO: If a valid but incorrect MD5 hash is received, expect an event to alert us as well as an error packet to be sent to the guest
    //TODO: Sending a valid MD5 has at this point should result in a successful connection.

    //TODO: Once a connection is initiated, it must be established in 5 seconds or the connection is shut down.

    //TODO: When a connection is fully established, the host should send the guest the current timestamp.

    //TODO: Test adding multiple event handlers

    //TODO: There should be an optional human-readable identifier that can be specified in ConnectionRequest

    //TODO: If a second connection attempt is made while a connection is active, we should get back an error packet.

    //TODO: Verify proper destruction
}
