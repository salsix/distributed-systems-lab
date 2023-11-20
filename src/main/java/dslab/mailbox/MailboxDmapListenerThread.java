package dslab.mailbox;

import dslab.Shutdownable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MailboxDmapListenerThread extends Thread {
    private final ServerSocket serverSocket;
    private final ExecutorService pool;
    private final UserData userData;
    private final ConcurrentHashMap<Integer, Shutdownable> connectionMap = new ConcurrentHashMap<>();
    
    MailboxDmapListenerThread(ServerSocket serverSocket, int poolSize, UserData userData){
        this.serverSocket = serverSocket;
        pool = Executors.newFixedThreadPool(poolSize);
        this.userData = userData;
    }
    
    @Override
    public void run() {
        //accept client, make new "thread" for each client
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                //System.out.println("MailboxDmapListenerThread: accepted client");

                DmapClientConnection clientConnection = new DmapClientConnection(client, userData, connectionMap);
                pool.execute(clientConnection);
                connectionMap.put(clientConnection.hashCode(), clientConnection);
            } catch (SocketException e) {
                //socket closed
            } catch (IOException e) {
                System.out.println("MailboxDmapListenerThread IO exception: " + e.toString());
                //e.printStackTrace();
                break;
            }
        }

        shutdown();
        System.out.println("MailboxDmapListenerThread finished");
    }

    public void shutdown(){
        //stop accepting connections
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        pool.shutdown();
        //pool.shutdownNow();
        
        //close open connections
        for(Shutdownable conn : connectionMap.values()) {
            conn.shutdown();
        }
    }
}
