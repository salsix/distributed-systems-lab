package dslab.transfer;

import java.io.*;
import java.net.ServerSocket;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class TransferServer implements ITransferServer, Runnable {
    private static final int POOLSIZE = 8;
    
    private final Config config;
    private TransferListenerThread socketListener;
    
    private final Shell shell;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        
        //init shell
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        //check config keys
        String[] keys = {"tcp.port", "monitoring.host", "monitoring.port", "registry.host", "registry.host", "root_id"};
        for(String key : keys){
            if (!config.containsKey(key)) {
                throw new RuntimeException("Config does not contain key '"+key+"'");
            }

        }

        try {
            //make server socket, start Socket Listener in new thread
            int port = config.getInt("tcp.port");
            ServerSocket serverSocket = new ServerSocket(port);
            MonitorInfo monitorInfo = new MonitorInfo(
                    config.getString("monitoring.host"), 
                    config.getInt("monitoring.port"),
                    config.getString("registry.host"), 
                    config.getInt("registry.port"), 
                    config.getString("root_id"));
            socketListener = new TransferListenerThread(serverSocket, POOLSIZE, port, monitorInfo);
            socketListener.start();
            
            //wait for commands
            shell.run();
        } catch (IOException e) {
            shell.out().println("TransferServer thread IO error: "+e.toString());
            //e.printStackTrace();
        }
        shell.out().println("TransferServer finished");
    }
    
    @Override
    @Command
    public void shutdown() {
        shell.out().println("TransferServer starting shutdown");
        
        //close server socket
        socketListener.shutdown();
        
        //stop shell
        throw new StopShellException();
    }
    
    public static void main(String[] args) throws Exception {
        String component = args.length>0 ? args[0] : "transfer-1";
        ITransferServer server = ComponentFactory.createTransferServer(component, System.in, System.out);
        server.run();
    }

}
