package dslab.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.nameserver.AlreadyRegisteredException;
import dslab.nameserver.INameserverRemote;
import dslab.nameserver.InvalidDomainException;
import dslab.util.Config;

public class MailboxServer implements IMailboxServer, Runnable {
    private static final int DMTP_POOLSIZE = 8;
    private static final int DMAP_POOLSIZE = 8;

    private final String componentId;
    private final Config config;
    private Shell shell;

    private MailboxDmtpListenerThread dmtpListener;
    private MailboxDmapListenerThread dmapListener;
    
    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;

        //init shell
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        //check config keys
        String[] keys = {"dmtp.tcp.port", "dmtp.tcp.port", "domain", "users.config"};
        for(String key : keys){
            if (!config.containsKey(key)) {
                throw new RuntimeException("Config does not contain key '"+key+"'");
            }
            
        }
        String mailDomain = config.getString("domain");
        UserData userData = new UserData(new Config(config.getString("users.config")), componentId);

        //register this MailboxServer with Naming Service
        try {
            String regHost = config.getString("registry.host");
            int regPort = config.getInt("registry.port");
            String nsRootId = config.getString("root_id");
            String ip = InetAddress.getLocalHost().getHostAddress();
            String dmtpPort = config.getString("dmtp.tcp.port");
            String address = ip+":"+dmtpPort;

            Registry reg = LocateRegistry.getRegistry(regHost, regPort);
            INameserverRemote remNs = (INameserverRemote) reg.lookup(nsRootId);
            remNs.registerMailboxServer(mailDomain,address);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (AlreadyRegisteredException e) {
            e.printStackTrace();
        } catch (InvalidDomainException e) {
            e.printStackTrace();
        }


        try {
            //make server sockets, start Socket Listeners in new thread
            ServerSocket serverSocketDmtp = new ServerSocket(config.getInt("dmtp.tcp.port"));
            ServerSocket serverSocketDmap = new ServerSocket(config.getInt("dmap.tcp.port"));
            
            dmtpListener = new MailboxDmtpListenerThread(serverSocketDmtp, DMTP_POOLSIZE, mailDomain, userData);
            dmapListener = new MailboxDmapListenerThread(serverSocketDmap, DMAP_POOLSIZE, userData);
            
            dmtpListener.start();
            dmapListener.start();

            //wait for commands
            shell.run();
        } catch (IOException e) {
            shell.out().println("MailboxServer thread IO error: "+e.toString());
            //e.printStackTrace();
        }
        shell.out().println("MailboxServer finished");
    }

    @Override
    @Command
    public void shutdown() {
        dmtpListener.shutdown();
        dmapListener.shutdown();
        
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        String component = args.length>0 ? args[0] : "mailbox-earth-planet";
        IMailboxServer server = ComponentFactory.createMailboxServer(component, System.in, System.out);
        server.run();
    }
}
