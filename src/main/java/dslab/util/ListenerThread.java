package dslab.util;

import dslab.transfer.TransferClientConnection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ListenerThread extends Thread {
    /*private final ServerSocket serverSocket;
    private final ExecutorService pool;

    public ListenerThread(ServerSocket serverSocket, int poolSize) {
        this.serverSocket = serverSocket;
        pool = Executors.newFixedThreadPool(poolSize);
    }

    @Override
    public void run() {
        //accept client, make new "thread" for each client
        while (true) {
            try {
                Socket client = serverSocket.accept();
                //System.out.println("accepted client");

                Client c = new Client();
                c.setServerSocket(client);
                
                pool.execute(c);
            } catch (IOException e) {
                System.out.println("ListenerThread IO exception: " + e.toString());
                //e.printStackTrace();
                break;
            }
        }

        shutdown();
        System.out.println("ListenerThread finished");
    }

    public void shutdown() {
        System.out.println("ListenerThread shutdown");

        //stop accepting connections
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        pool.shutdown();
        //pool.shutdownNow();
    }*/
}
