package me.noahandrews.savpp;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.net.Socket;

import static me.noahandrews.savpp.SAVPPServer.State.LISTENING;

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

public class ServerConnector implements TestRule {
    SAVPPServer savppServer;
    Socket socket;
    String md5Hash;

    public ServerConnector (String md5Hash) {
        this.md5Hash = md5Hash;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if(description.getAnnotation(SkipServerSetup.class) != null) {
                    base.evaluate();
                    return;
                }

                savppServer = new SAVPPServer(md5Hash);
                savppServer.startListening();

                while(savppServer.getState() != LISTENING) {}

                socket = new Socket("localhost", SAVPPValues.PORT_NUMBER);

                base.evaluate();

                socket.close();
                savppServer.tearDown();
            }
        };
    }

    public SAVPPServer getServer() {
        return savppServer;
    }

    public Socket getSocket() {
        return socket;
    }
}
