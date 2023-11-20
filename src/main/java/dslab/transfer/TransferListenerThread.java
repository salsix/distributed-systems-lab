package dslab.transfer;

import dslab.Shutdownable;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferListenerThread extends Thread {
    private final ServerSocket serverSocket;
    private final ExecutorService pool;
    private final int port;
    private final MonitorInfo monitorInfo;
    private final String ip;
    private final ConcurrentHashMap<Integer, Shutdownable> connectionMap = new ConcurrentHashMap<>();

    TransferListenerThread(ServerSocket serverSocket, int poolSize, int port, MonitorInfo monitorInfo) {
        String ip1;
        this.serverSocket = serverSocket;
        pool = Executors.newFixedThreadPool(poolSize);
        this.port = port;
        this.monitorInfo = monitorInfo;
        
        try {
            ip1 = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            ip1 = "Unknown";
        }
        ip = ip1;
    }

    @Override
    public void run() {
        //accept client, make new "thread" for each client
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                System.out.println("TransferListenerThread: Client connected " + client);
                TransferClientConnection clientConnection = new TransferClientConnection(client, port, monitorInfo, ip, connectionMap);
                pool.execute(clientConnection);
                connectionMap.put(clientConnection.hashCode(), clientConnection);
            } catch (SocketException e) {
                //socket closed
                System.out.println("TransferListenerThread Socket exception: " + e.toString());
            } catch (IOException e) {
                System.out.println("TransferListenerThread IO exception: " + e.toString());
                //e.printStackTrace();
                break;
            }
        }

        System.out.println("TransferListenerThread shutdown finished");
    }

    public void shutdown() {
        //stop accepting connections
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        pool.shutdown();
        
        //close open connections
        for(Shutdownable conn : connectionMap.values()) {
            conn.shutdown();
        }
    }
}
