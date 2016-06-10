package me.noahandrews.savpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static me.noahandrews.savpp.SAVPPProto.*;

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

public class SAVPPGuest {
    private final String hostname;
    private OutputStream outputStream;
    private InputStream inputStream;

    public SAVPPGuest(String hostname) {
        this.hostname = hostname;
    }

    public void connect(String md5Hash) throws IOException {
        if(!isHashValid(md5Hash)) {
            throw new IllegalArgumentException("Invalid MD5 hash");
        }

        SAVPPMessage message = SAVPPMessage.newBuilder()
                .setType(SAVPPMessage.Type.CONNECTION_REQUEST)
                .setConnectionRequest(ConnectionRequest.newBuilder().setMd5(md5Hash))
                .build();

        Socket socket = createSocket();
        outputStream = socket.getOutputStream();
        inputStream = socket.getInputStream();

        message.writeDelimitedTo(outputStream);
    }

    private boolean isHashValid(String md5Hash) {
        return md5Hash.length() == 32 && md5Hash.matches("[0-9a-fA-F]+");
    }

    protected Socket createSocket() throws IOException {
        return new Socket(hostname, SAVPPValues.PORT_NUMBER);
    }
}
