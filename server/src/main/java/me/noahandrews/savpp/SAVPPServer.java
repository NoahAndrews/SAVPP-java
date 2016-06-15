package me.noahandrews.savpp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static me.noahandrews.savpp.MD5Checker.isHashValid;
import static me.noahandrews.savpp.SAVPPProto.SAVPPMessage;
import static me.noahandrews.savpp.SAVPPServer.State.*;

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

public class SAVPPServer {
    private State state = DORMANT;

    private ExecutorService connectionHandlerExecutor;
    private List<FutureTask<?>> connectionHandlerTasks;
    private FutureTask<?> connectionListenerTask;

    private EventHandler eventHandler;

    private final String md5Hash;

    public SAVPPServer(String md5Hash) {
        if(!isHashValid(md5Hash)) {
            throw new IllegalArgumentException("Invalid MD5 hash");
        }
        this.md5Hash = md5Hash;

        connectionHandlerExecutor = Executors.newCachedThreadPool();
        connectionHandlerTasks = Collections.synchronizedList(new ArrayList<>(1));
    }

    public void setEventHandler(EventHandler handler) {
        this.eventHandler = handler;
    }

    public synchronized void startListening() throws IOException {
        ExecutorService connectionListenerExecutor = Executors.newSingleThreadExecutor();
        if(getState() != DORMANT) {
            String message;
            if(getState() == LISTENING){
                message = "Only one guest is currently allowed.";
            } else {
                message = "startListening() can only be called from the dormant state.";
            }
            throw new RuntimeException(message);
        }
        System.out.println("Starting connection listener.");
        connectionListenerTask = (FutureTask)connectionListenerExecutor.submit(new ConnectionListener());
    }

    protected synchronized ServerSocket createServerSocket() throws IOException {
        return new ServerSocket(SAVPPValues.PORT_NUMBER);
    }

    public synchronized State getState() {
        return state;
    }

    private synchronized void setState(State newState) {
        State formerState = getState();
        System.out.println("Changing state from " + formerState + " to " + newState);
        if(formerState == DESTROYED) {
            throw new IllegalStateException("State cannot be changed after destruction.");
        }
        this.state = newState;
    }

    public void tearDown() throws ExecutionException, InterruptedException {
        //TODO: Tear down all sockets cleanly
        int initialNumberOfRunningHandlers = ((ThreadPoolExecutor)connectionHandlerExecutor).getActiveCount();
        boolean wasConnectionListenerInitiallyRunning;
        if(connectionListenerTask == null || connectionListenerTask.isDone()) {
            wasConnectionListenerInitiallyRunning = false;
        } else {
            wasConnectionListenerInitiallyRunning = true;
        }

        System.out.println("Tearing down SAVPPServer");
        for(FutureTask task: connectionHandlerTasks) {
            task.cancel(true);
        }
        if(connectionListenerTask != null) {
            connectionListenerTask.cancel(true);
        }

        int newNumberOfRunningHandlers = ((ThreadPoolExecutor)connectionHandlerExecutor).getActiveCount();
        boolean isConnectionListenerRunning;
        if(connectionListenerTask == null || connectionListenerTask.isDone()) {
            isConnectionListenerRunning = false;
        } else {
            isConnectionListenerRunning = true;
        }

        if(wasConnectionListenerInitiallyRunning && !isConnectionListenerRunning) {
            System.out.println("Connection listener killed.");
        } else if(!wasConnectionListenerInitiallyRunning) {
            System.out.println("Connection listener was not running upon tearDown() call.");
        }
        else {
            System.out.println("Connection listener was and still is running.");
        }

        System.out.println("Went from " + initialNumberOfRunningHandlers + " to " + newNumberOfRunningHandlers + " running connection handlers.");

        setState(DESTROYED);
    }

    public enum State {
        DORMANT,
        LISTENING,
        WAITING_FOR_HASH,
        CONNECTED,
        DESTROYED
    }

    private class ConnectionListener implements Runnable {
        @Override
        public void run() {
            try {
                ServerSocket serverSocket = createServerSocket();
                setState(LISTENING);
                eventHandler.serverStarted();
                Socket socket = serverSocket.accept();
                System.out.println("Starting connection handler");
                connectionHandlerTasks.add((FutureTask) connectionHandlerExecutor.submit(new ConnectionHandler(socket)));
                System.out.println("Connection listener shutting down.");
            } catch (Exception e) {
                e.printStackTrace();
                //TODO: fix the state
                //TODO: notify the API consumer that something went wrong
            }
        }
    }

    private class ConnectionHandler implements Runnable {
        Socket socket;

        ConnectionHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            setState(WAITING_FOR_HASH);

            SAVPPMessage message;
            try {
                do {
                    message = SAVPPMessage.parseDelimitedFrom(socket.getInputStream());

                    if(message.getType() != SAVPPMessage.Type.CONNECTION_REQUEST) {
                    } else {
                        String receivedHash = message.getConnectionRequest().getMd5();
                        if(receivedHash.equals(md5Hash)) {
                            setState(CONNECTED);
                            eventHandler.connectionEstablished();
                        } else {
                            eventHandler.incorrectMD5HashReceived(receivedHash);
                        }
                    }
                } while (getState() != CONNECTED);
            } catch (IOException e) {
                e.printStackTrace();
                //TODO: handle this somehow
            }
            System.out.println("Connection handler shutting down.");
        }
    }

    public static abstract class EventHandler {
        public void connectionEstablished() {}

        public void incorrectMD5HashReceived(String receivedHash) {}

        public void serverStarted() {}
    }
}
