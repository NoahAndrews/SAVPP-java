package me.noahandrews.savpp;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static me.noahandrews.savpp.SAVPPHost.State.*;
import static me.noahandrews.savpp.SAVPPProto.*;
import static org.junit.Assert.*;
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

public class SAVPPHostTest {
    private SAVPPHost savppHost;

    private ServerSocket mockedServerSocket = mock(ServerSocket.class);
    private Socket mockedSocket = mock(Socket.class);


    private PipedInputStream incomingDataAsInputStream;
    private PipedOutputStream incomingDataAsOutputStream;

    private final String MD5_STRING = "5a73e7b6df89f85bb34129fcdfd7da12";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        incomingDataAsInputStream = new PipedInputStream();
        incomingDataAsOutputStream = new PipedOutputStream(incomingDataAsInputStream);

        savppHost = new SAVPPHost(MD5_STRING) {
            @Override
            protected ServerSocket createServerSocket() {
                return mockedServerSocket;
            }
        };
    }

    @Test(timeout = 1000)
    public void testServerCreation() throws Exception {
        printTestHeader("server creation test");
        when(mockedServerSocket.accept()).thenCallRealMethod();
        savppHost.startListening();

        assertEquals(LISTENING, savppHost.getState());
    }

    @Test(timeout = 1000)
    public void testConnection() throws Exception {
        printTestHeader("connection test");
        SAVPPMessage connectionRequest = SAVPPMessage.newBuilder()
                .setType(SAVPPMessage.Type.CONNECTION_REQUEST)
                .setConnectionRequest(ConnectionRequest.newBuilder().setMd5(MD5_STRING))
                .build();
        connectionRequest.writeDelimitedTo(incomingDataAsOutputStream);
        when(mockedSocket.getInputStream()).thenReturn(incomingDataAsInputStream);
        when(mockedServerSocket.accept()).thenReturn(mockedSocket);

        CountDownLatch latch = new CountDownLatch(1);

        savppHost.setEventHandler(new SAVPPHost.EventHandler() {
            @Override
            public void connectionEstablished() {
                latch.countDown();
            }
        });

        savppHost.startListening();

        latch.await();
        assertEquals(CONNECTED, savppHost.getState());
    }

    @Test
    public void invalidHashRaisesException() throws Exception {
        printTestHeader("invalid hash test");
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid MD5 hash");
        savppHost = new SAVPPHost("1234567890abcdef");
    }

    @After
    public void tearDown() throws Exception {
        savppHost.tearDown();
    }

    private void printTestHeader(String descriptor) {
        System.out.println("\n\nRunning " + descriptor + "\n==========================================================");
    }

    //TODO: Test what happens when something other than a SAVPPMessage is sent

    //TODO: When the first SAVPPMessage is something other than a ConnectionRequest, expect an error packet
    //TODO: Sending a ConnectionRequest at this point should result in a successful connection.

    //TODO: Test what happens when a process is already bound to the SAVPP port

    //TODO: If tearDown has been called, calling any other method should result in an exception.

    //TODO: Write tests to go from manual state verification to automated state verification

    //TODO: If a valid but incorrect MD5 hash is received, expect an event to alert us as well as an error packet to be sent to the guest
    //TODO: Sending a valid MD5 has at this point should result in a successful connection.
}
