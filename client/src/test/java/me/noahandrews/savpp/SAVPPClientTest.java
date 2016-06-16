package me.noahandrews.savpp;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;

import static me.noahandrews.savpp.SAVPPProto.SAVPPMessage;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

public class SAVPPClientTest {
    private SAVPPClient savppClient;

    private PipedInputStream outgoingDataAsInputStream;
    private PipedOutputStream outgoingDataAsOutputStream;

    private final String MD5_STRING = "5a73e7b6df89f85bb34129fcdfd7da12";

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        outgoingDataAsInputStream = new PipedInputStream();
        outgoingDataAsOutputStream = new PipedOutputStream(outgoingDataAsInputStream);

        final Socket mockedSocket = mock(Socket.class);
        when(mockedSocket.getOutputStream()).thenReturn(outgoingDataAsOutputStream);

        savppClient = new SAVPPClient("localhost") {
            @Override
            protected Socket createSocket() {
                return mockedSocket;
            }
        };
    }

    @Test
    public void testConnect() throws Exception {
        savppClient.connect(MD5_STRING);

        SAVPPMessage message = SAVPPMessage.parseDelimitedFrom(outgoingDataAsInputStream);

        assertEquals(SAVPPMessage.MessageType.CONNECTION_REQUEST, message.getType());
        assertEquals(MD5_STRING, message.getConnectionRequest().getMd5());
    }

    @Test
    public void invalidHashRaisesException() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Invalid MD5 hash");
        savppClient.connect("1234567890abcdef");
    }
}
