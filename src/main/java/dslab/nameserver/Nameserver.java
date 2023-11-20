package dslab.nameserver;

import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class Nameserver implements INameserver, INameserverRemote {
    private final Config config;
    private final Shell shell;
    private Registry registry;
    private final boolean root;
    
    private final ConcurrentHashMap<String, INameserverRemote> nameservers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> mailboxes = new ConcurrentHashMap<>();

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) {
        this.root = "ns-root".equals(componentId);
        this.config = config;
        
        //init shell
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        if(root) {
            System.out.println("root setting up registry");
            //only root nameserver registers
            try {
                // create and export the registry instance on localhost at the specified port
                registry = LocateRegistry.createRegistry(config.getInt("registry.port"));

                // create a remote object of this server object
                INameserverRemote remote = (INameserverRemote) UnicastRemoteObject.exportObject(this, 0);
                // bind the obtained remote object on specified binding name in the registry
                registry.bind(config.getString("root_id"), remote);
            } catch (RemoteException e) {
                throw new RuntimeException("Error while starting server.", e);
            } catch (AlreadyBoundException e) {
                throw new RuntimeException("Error while binding remote object to registry.", e);
            }
        } else {
            //register at root nameserver
            try {
                INameserverRemote remote = (INameserverRemote) UnicastRemoteObject.exportObject(this, 0);

                try {
                    // obtain registry that was created by the server
                    Registry registry = LocateRegistry.getRegistry(
                            config.getString("registry.host"),
                            config.getInt("registry.port")
                    );
                    // look for the bound server remote-object implementing the IServerRemote interface
                    INameserverRemote server = (INameserverRemote) registry.lookup(config.getString("root_id"));
                    server.registerNameserver(config.getString("domain"), remote);
                } catch (RemoteException e) {
                    throw new RuntimeException("Error while obtaining registry/server-remote-object.", e);
                } catch (NotBoundException e) {
                    throw new RuntimeException("Error while looking for server-remote-object.", e);
                } catch (InvalidDomainException e) {
                    e.printStackTrace();
                } catch (AlreadyRegisteredException e) {
                    System.out.println();
                    e.printStackTrace();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        
        shell.run();
    }

    @Override
    @Command
    public void nameservers() {
        int i=0;
        for(String domain : nameservers.keySet().stream().sorted().collect(Collectors.toList())) {
            ++i;
            shell.out().println(i+". "+domain);
        }
    }

    @Command
    @Override
    public void addresses() {
        int i=0;
        for(String domain : mailboxes.keySet().stream().sorted().collect(Collectors.toList())) {
            ++i;
            shell.out().println(i+". "+domain+" "+mailboxes.get(domain));
        }
    }

    @Command
    @Override
    public void shutdown() {
        try {
            // unexport the previously exported remote object
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException e) {
            System.err.println("Error while unexporting object: " + e.getMessage());
        }
        
        if(root) {
            try {
                // unbind the remote object so that a client can't find it anymore
                registry.unbind(config.getString("root_id"));
                UnicastRemoteObject.unexportObject(registry,true);
            } catch (Exception e) {
                System.err.println("Error while unbinding object: " + e.getMessage());
            }
        }
        
        //stop shell
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        String componentName = args.length>0 ? args[0] : "ns-root";
        INameserver component = ComponentFactory.createNameserver(componentName, System.in, System.out);
        component.run();
    }

    //split toSplit at last dot '.', [LAST_DOMAIN, rest of domains] - care end of string is first position
    private String[] splistLastDomainRest(String toSplit) {
        int index = toSplit.lastIndexOf('.');
        if(index==-1) {
            return new String[]{toSplit};
        }
        return new String[]{toSplit.substring(index+1), toSplit.substring(0,index)};
    }
    
    @Override
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        System.out.println("Registering Nameserver: "+domain);
        String[] split = splistLastDomainRest(domain);

        INameserverRemote next = nameservers.get(split[0]);
        if(split.length>1) {
            //go further
            if(next==null) {
                throw new InvalidDomainException("Nameserver for domain '"+split[0]+"' does not exist.");
            }
            next.registerNameserver(split[1], nameserver);
        } else {
            //save in this nameservers
            if(next!=null) {
                throw new AlreadyRegisteredException("Nameserver for domain '"+split[0]+"' already exists.");
            }
            nameservers.put(split[0], nameserver);
        }
    }

    @Override
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        System.out.println("Registering Mailbox: "+domain);
        String[] split = splistLastDomainRest(domain);

        if(split.length>1) {
            //go further
            INameserverRemote next = nameservers.get(split[0]);
            if(next==null) {
                throw new InvalidDomainException("Registering Mailbox: Nameserver for domain '"+split[0]+"' does not exist.");
            }
            System.out.println("Registering Mailbox next: "+split[1]);
            next.registerMailboxServer(split[1], address);
        } else {
            //save in this nameservers
            if(mailboxes.get(split[0])!=null) {
                throw new AlreadyRegisteredException("Mailbox for domain '"+split[0]+"' already exists.");
            }
            mailboxes.put(split[0], address);
        }
    }

    @Override
    public INameserverRemote getNameserver(String zone) {
        return nameservers.get(zone);
    }

    @Override
    public String lookup(String username) {
        return mailboxes.get(username);
    }
}
