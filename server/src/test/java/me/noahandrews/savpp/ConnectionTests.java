package me.noahandrews.savpp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;

import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static me.noahandrews.savpp.SAVPPProto.SAVPPMessage;
import static me.noahandrews.savpp.SAVPPServer.State.CONNECTED;
import static me.noahandrews.savpp.SAVPPServer.State.LISTENING;
import static me.noahandrews.savpp.TestUtils.*;
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

public class ConnectionTests {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public Timeout timeout = new Timeout(1000, TimeUnit.MILLISECONDS);

    @Rule
    public ServerConnector serverConnector = new ServerConnector(MD5_HASH);

    TestUtils testUtils = new TestUtils(serverConnector);

    @Test @SkipServerSetup
    public void testServerCreation() throws Exception {
        printTestHeader("server creation test");

        SAVPPServer savppServer = new SAVPPServer(MD5_HASH);
        CountDownLatch latch = new CountDownLatch(1);

        savppServer.setEventHandler(new SAVPPServer.EventHandler() {
            @Override
            public void serverStarted() {
                latch.countDown();
            } //Verify that the serverStarted method is being called
        });
        savppServer.startListening();

        latch.await();
        assertEquals(LISTENING, savppServer.getState());
        savppServer.tearDown();
    }

    @Test
    public void incorrectHash() throws Exception {
        printTestHeader("incorrect hash test");

        CountDownLatch latch = new CountDownLatch(1);
        final String[] hash = new String[1];
        serverConnector.getServer().setEventHandler(new SAVPPServer.EventHandler() {
            @Override
            public void incorrectMD5HashReceived(String receivedHash) {
                logger.debug("Received incorrect hash event");
                hash[0] = receivedHash;
                latch.countDown();
            }
        });
        testUtils.submitConnectionRequest(MD5_HASH_2);
        latch.await();
        assertEquals(MD5_HASH_2, hash[0]);
        assertEquals(LISTENING, serverConnector.getServer().getState());
        //TODO: Assert that we get back an error packet
    }

    @Test
    public void testConnection() throws Exception {
        printTestHeader("connection test");
        testUtils.connectToServer();
        assertEquals(CONNECTED, serverConnector.getServer().getState());
    }

    @Test @SkipServerSetup
    public void invalidHashRaisesException() throws Exception {
        printTestHeader("invalid hash test");
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid MD5 hash");
        new SAVPPServer("1234567890abcdef");
    }

    @Test
    public void invalidData() throws Exception {
        printTestHeader("invalid data test");

        logger.debug("Sending junk data");

        serverConnector.getSocket().getOutputStream().write("This is not a SAVPP Message".getBytes());

        InputStream inputStream = serverConnector.getSocket().getInputStream();
        SAVPPMessage message = SAVPPMessage.parseDelimitedFrom(inputStream);

        assertEquals(SAVPPProto.Error.ErrorType.INVALID_DATA, message.getError().getType());
    }

    @Test
    public void twoConnectionRequests() throws Exception {
        printTestHeader("double connection request test");
        testUtils.connectToServer();

        logger.debug("Submitting second connection request");
        testUtils.submitConnectionRequest();

        InputStream inputStream = serverConnector.getSocket().getInputStream();
        SAVPPMessage message = SAVPPMessage.parseDelimitedFrom(inputStream);

        assertEquals(SAVPPProto.Error.ErrorType.ALREADY_CONNECTED, message.getError().getType());
    }

    @Test
    public void twoSocketConnections() throws Exception {
        printTestHeader("double connection request test");
        testUtils.connectToServer();

        Socket socket = new Socket("localhost", SAVPPValues.PORT_NUMBER);

        SAVPPMessage message = SAVPPMessage.parseDelimitedFrom(socket.getInputStream());

        assertEquals(SAVPPProto.Error.ErrorType.NOT_ACCEPTING_CONNECTIONS, message.getError().getType());
        assertEquals(-1, socket.getInputStream().read());
    }

    //TODO: Test that when something other than a SAVPPMessage is sent, other messages can be sent successfully afterward

    //TODO: When the first SAVPPMessage is something other than a ConnectionRequest, expect an error packet
    //TODO: Sending a ConnectionRequest at this point should result in a successful connection.

    //TODO: Test what happens when a process is already bound to the SAVPP port

    //TODO: Write tests to go from manual state verification to automated state verification

    //TODO: When a connection is fully established, the host should send the guest the current timestamp in the form of a scrubbing message.

    //TODO: Test adding multiple event handlers

    //TODO: There should be an optional human-readable identifier that can be specified in ConnectionRequest
    //TODO: When the ConnectionRequest is recieved, the API consumer should be notified, informed of the ID, and given the option to accept or deny

    //TODO: Verify proper destruction

    //TODO: When we support multiple clients, each client should have its own event handler. ConnectionEstablished should be moved there.

    //TODO: If a connection request is received from a different socket while in state WAITING_FOR_AUTHORIZATION, deny the second request
    //TODO: If a connection request is received from a different socket while in state WAITING_FOR_HASH, close the first socket and act on the second.
}
