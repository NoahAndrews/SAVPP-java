package me.noahandrews.savpp;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
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
    private static final Logger logger = LogManager.getLogger();

    private State state = DORMANT;

    private ExecutorService connectionHandlerExecutor;
    private List<FutureTask<?>> connectionHandlerTasks;
    private FutureTask<?> connectionListenerTask;

    private ServerSocket serverSocket;

    private EventHandler eventHandler;

    private final String md5Hash;

    public SAVPPServer(String md5Hash) {
        logger.traceEntry();
        if(!isHashValid(md5Hash)) {
            throw new IllegalArgumentException("Invalid MD5 hash");
        }
        this.md5Hash = md5Hash;

        connectionHandlerExecutor = Executors.newCachedThreadPool();
        connectionHandlerTasks = Collections.synchronizedList(new ArrayList<>(1));
        logger.traceExit();
    }

    public synchronized void setEventHandler(EventHandler handler) {
        this.eventHandler = handler;
    }

    private synchronized EventHandler getEventHandler() {
        return eventHandler;
    }

    public synchronized void startListening() throws IOException {
        logger.traceEntry();
        ExecutorService connectionListenerExecutor = Executors.newSingleThreadExecutor();
        if(getState() != DORMANT) {
            String message;
            if(getState() == LISTENING){
                message = "Only one guest is currently allowed.";
            } else {
                message = "startListening() can only be called from the dormant state.";
            }
            RuntimeException exception = new RuntimeException(message);
            exception.printStackTrace();
            throw exception;
        }
        logger.debug("Starting connection listener.");
        connectionListenerTask = (FutureTask)connectionListenerExecutor.submit(new ConnectionListener());
        logger.traceExit();
    }

    protected synchronized ServerSocket createServerSocket() throws IOException {
        return new ServerSocket(SAVPPValues.PORT_NUMBER);
    }

    public synchronized State getState() {
        return state;
    }

    private synchronized void setState(State newState) {
        State formerState = getState();
        if(formerState == DESTROYED) {
            throw new IllegalStateException("State cannot be changed after destruction.");
        }
        logger.debug("Changing state from " + formerState + " to " + newState);
        this.state = newState;
    }

    private synchronized ServerSocket getServerSocket() {
        return serverSocket;
    }

    private synchronized void setServerSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public void tearDown() throws ExecutionException, InterruptedException, IOException {
        setState(DESTROYING);
        //TODO: Tear down all sockets cleanly
        int initialNumberOfRunningHandlers = ((ThreadPoolExecutor)connectionHandlerExecutor).getActiveCount();
        boolean wasConnectionListenerInitiallyRunning;
        if(connectionListenerTask == null || connectionListenerTask.isDone()) {
            wasConnectionListenerInitiallyRunning = false;
        } else {
            wasConnectionListenerInitiallyRunning = true;
        }

        for(FutureTask task: connectionHandlerTasks) {
            task.cancel(true);
        }

        if(serverSocket != null) {
            serverSocket.close();
        }

        int newNumberOfRunningHandlers = ((ThreadPoolExecutor)connectionHandlerExecutor).getActiveCount();
        boolean isConnectionListenerRunning;

        while(!connectionListenerTask.isDone()) {}

        if(connectionListenerTask == null || connectionListenerTask.isDone()) {
            isConnectionListenerRunning = false;
        } else {
            isConnectionListenerRunning = true;
        }

        if(wasConnectionListenerInitiallyRunning && !isConnectionListenerRunning) {
            logger.debug("Connection listener killed.");
        } else if(!wasConnectionListenerInitiallyRunning) {
            logger.debug("Connection listener was not running upon tearDown() call.");
        }
        else {
            logger.debug("Connection listener was and still is running.");
        }

        logger.debug("Went from " + initialNumberOfRunningHandlers + " to " + newNumberOfRunningHandlers + " running connection handlers.");

        setState(DESTROYED);
    }

    public enum State {
        DORMANT,
        LISTENING,
        WAITING_FOR_HASH,
        CONNECTED,
        DESTROYING,
        DESTROYED
    }

    private class ConnectionListener implements Runnable {
        @Override
        public void run() {
            logger.traceEntry("ConnectionListener.run()");
            ServerSocket serverSocket;
            try {
                serverSocket = createServerSocket();
                setServerSocket(serverSocket);
                setState(LISTENING);
                if (getEventHandler() != null) {
                    getEventHandler().serverStarted();
                }
                while (!serverSocket.isClosed()) {
                    Socket socket = null;
                    try {
                        socket = serverSocket.accept();
                    } catch (SocketException e) {
                        if (getState() != DESTROYING) {
                            throw new RuntimeException(e.getMessage(), e);
                        }
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    logger.debug("Starting connection handler");
                    connectionHandlerTasks.add((FutureTask) connectionHandlerExecutor.submit(new ConnectionHandler(socket)));
                }
                logger.debug("Connection listener shutting down.");
                logger.traceExit();
            } catch (IOException e) {
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
            logger.traceEntry();

            if (getState() == CONNECTED) {
                //TODO: Reject the connection, send back an error packet, and close the socket.
                sendErrorMessage(SAVPPProto.Error.ErrorType.NOT_ACCEPTING_CONNECTIONS);
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }

            setState(WAITING_FOR_HASH);

            SAVPPMessage message;
            try {
                do {
                    message = SAVPPMessage.parseDelimitedFrom(socket.getInputStream());

                    if (message.getType() != SAVPPMessage.MessageType.CONNECTION_REQUEST) {
                    } else if(getState() != WAITING_FOR_HASH) {
                       sendErrorMessage(SAVPPProto.Error.ErrorType.ALREADY_CONNECTED);
                    } else {
                        logger.debug("Connection request received");
                        String receivedHash = message.getConnectionRequest().getMd5();
                        if (receivedHash.equals(md5Hash)) {
                            setState(CONNECTED);
                            getEventHandler().connectionEstablished();
                        } else {
                            logger.debug("Incorrect hash received");
                            setState(LISTENING);
                            getEventHandler().incorrectMD5HashReceived(receivedHash);
                            socket.close();
                            return;
                        }
                    }
                } while (!socket.isClosed());
            } catch (InvalidProtocolBufferException e) {
                sendErrorMessage(SAVPPProto.Error.ErrorType.INVALID_DATA);
            } catch (IOException e) {
                e.printStackTrace();
                //TODO: handle this somehow
            }
            logger.debug("Connection handler shutting down.");
            logger.traceExit();
        }

        private void sendErrorMessage(SAVPPProto.Error.ErrorType errorType) {
            try {
                SAVPPMessage errorMessage = SAVPPMessage.newBuilder()
                        .setType(SAVPPMessage.MessageType.ERROR)
                        .setError(SAVPPProto.Error.newBuilder().setType(errorType))
                        .build();
                logger.debug("Sending error message of type " + errorType);
                errorMessage.writeDelimitedTo(socket.getOutputStream());
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static abstract class EventHandler {
        public void connectionEstablished() {
        }

        public void incorrectMD5HashReceived(String receivedHash) {
        }

        public void serverStarted() {
        }
    }

    //TODO: ConnectionHandler should be its own class. Furthermore, it should consist of little more than a looping switch,
    //TODO: that defers to other classes to handle the different types of messages.
}
