package dslab.monitoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.Arrays;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.transfer.TransferListenerThread;
import dslab.util.Config;

public class MonitoringServer implements IMonitoringServer {
    private UdpListenerThread listenerThread;
    private final Config config;
    private final Shell shell;
    private final MonitorData monitorData;
    
    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        monitorData = new MonitorData();

        //init shell
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
        //shell.setPrompt("");
    }

    @Override
    public void run() {
        if (!config.containsKey("udp.port")) {
            throw new RuntimeException("Config does not contain udp.port");
        }

        try {
            //make server socket, start Socket Listener in new thread
            DatagramSocket datagramSocket = new DatagramSocket(config.getInt("udp.port"));
            listenerThread = new UdpListenerThread(datagramSocket, monitorData);
            listenerThread.start();

            //wait for commands
            shell.run();
        } catch (IOException e) {
            shell.out().println("MonitoringServer thread IO error: "+e.toString());
            //e.printStackTrace();
        }
        shell.out().println("MonitoringServer finished");
    }

    @Override
    @Command
    public void addresses() {
        String[] toPrint = monitorData.getAddresses();
        for(String s : toPrint){
            shell.out().println(s);
        }
    }

    @Override
    @Command
    public void servers() {
        String[] toPrint = monitorData.getServers();
        for(String s : toPrint){
            shell.out().println(s);
        }
    }

    @Override
    @Command
    public void shutdown() {
        listenerThread.shutdown();
        
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        String component = args.length>0 ? args[0] : "monitoring";
        IMonitoringServer server = ComponentFactory.createMonitoringServer(component, System.in, System.out);
        server.run();
    }

}
