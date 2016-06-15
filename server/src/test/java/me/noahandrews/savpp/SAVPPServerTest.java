package me.noahandrews.savpp;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static me.noahandrews.savpp.SAVPPProto.ConnectionRequest;
import static me.noahandrews.savpp.SAVPPProto.SAVPPMessage;
import static me.noahandrews.savpp.SAVPPServer.State.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

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

    private ServerSocket mockedServerSocket;
    private Socket mockedSocket;


    private PipedInputStream dataToServerAsInputStream;
    private PipedOutputStream dataToServerAsOutputStream;
    private ExecutorService dataToServerSendingExecutor;

    private final String MD5_HASH = "5a73e7b6df89f85bb34129fcdfd7da12";
    private final String MD5_HASH_2 = "bedb04bb540934fda8b12ed4aaa2fc34";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        mockedServerSocket = mock(ServerSocket.class);
        mockedSocket = mock(Socket.class);

        dataToServerAsInputStream = new PipedInputStream();
        dataToServerAsOutputStream = new PipedOutputStream(dataToServerAsInputStream);
        dataToServerSendingExecutor = Executors.newSingleThreadExecutor();

        savppServer = new SAVPPServer(MD5_HASH) {
            @Override
            protected ServerSocket createServerSocket() {
                return mockedServerSocket;
            }
        };

        doReturn(dataToServerAsInputStream).when(mockedSocket).getInputStream();
        doReturn(mockedSocket).when(mockedServerSocket).accept();
    }

    @Test(timeout = 1000)
    public void testServerCreation() throws Exception {
        printTestHeader("server creation test");
        when(mockedServerSocket.accept()).thenCallRealMethod();

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

    /**
     * This test may throw a SocketException with message "Socket is not bound yet". If you see that, ignore it.
     * It doesn't apply to unit testing. Something is weird with Mockito, and it's calling the real ServerSocket.accept()
     * method instead of just the mocked version.
     * @throws Exception
     */
    @Test(timeout = 2000)
    public void incorrectHash() throws Exception {
        printTestHeader("incorrect hash test");
        submitConnectionRequest(MD5_HASH_2);

        CountDownLatch latch1 = new CountDownLatch(1);
        final String[] hash = new String[1];

        savppServer.setEventHandler(new SAVPPServer.EventHandler() {
            @Override
            public void incorrectMD5HashReceived(String receivedHash) {
                hash[0] = receivedHash;
                latch1.countDown();
            }
        });

        savppServer.startListening();

        latch1.await();
        assertEquals(MD5_HASH_2, hash[0]);
        assertEquals(WAITING_FOR_HASH, savppServer.getState());

        System.out.println("Sending another connection request");

        submitConnectionRequest(MD5_HASH);
        CountDownLatch latch2 = new CountDownLatch(1);
        savppServer.setEventHandler(new SAVPPServer.EventHandler() {
            @Override
            public void connectionEstablished() {
                latch2.countDown();
            }
        });

        latch2.await();
        assertEquals(CONNECTED, savppServer.getState());
    }

    @Test(timeout = 1000)
    public void testConnection() throws Exception {
        printTestHeader("connection test");
        submitConnectionRequest(MD5_HASH);

        CountDownLatch latch = new CountDownLatch(1);

        savppServer.setEventHandler(new SAVPPServer.EventHandler() {
            @Override
            public void connectionEstablished() {
                latch.countDown();
            }
        });

        savppServer.startListening();

        latch.await();
        assertEquals(CONNECTED, savppServer.getState());
    }

    @Test
    public void invalidHashRaisesException() throws Exception {
        printTestHeader("invalid hash test");
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid MD5 hash");
        savppServer = new SAVPPServer("1234567890abcdef");
    }

    @After
    public void tearDown() throws Exception {
        savppServer.tearDown();
    }

    private void printTestHeader(String descriptor) {
        System.out.println("\n\nRunning " + descriptor + "\n==========================================================");
    }

    private void submitConnectionRequest(String md5Hash) throws IOException {
        SAVPPMessage connectionRequest = SAVPPMessage.newBuilder()
                .setType(SAVPPMessage.Type.CONNECTION_REQUEST)
                .setConnectionRequest(ConnectionRequest.newBuilder().setMd5(md5Hash))
                .build();

        dataToServerSendingExecutor.execute(() -> {
            try {
                connectionRequest.writeDelimitedTo(dataToServerAsOutputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
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
}
