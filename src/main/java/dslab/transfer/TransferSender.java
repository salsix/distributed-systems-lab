package dslab.transfer;

import dslab.nameserver.AlreadyRegisteredException;
import dslab.nameserver.INameserverRemote;
import dslab.nameserver.InvalidDomainException;
import dslab.util.Mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.regex.Pattern;

public class TransferSender implements Runnable {
    private final Mail mail;
    private final int port;
    private final MonitorInfo monitorInfo;
    private final String ip;

    TransferSender(Mail mail, int port, MonitorInfo monitorInfo, String ip) {
        this.mail = mail;
        this.port = port;
        this.monitorInfo = monitorInfo;
        this.ip = ip;
    }

    @Override
    public void run() {
        System.out.println("TransferSender start ("+mail.getSubject()+": "+mail.getTo()+")");
        Set<String> sentDomains = new HashSet<>();
        Set<String> failDomains = new HashSet<>();
        boolean failure = false;
        StringBuilder failData = new StringBuilder();
        ArrayList<String> failMails = new ArrayList<>();
        
        //get domain counts
        String[] recipients = mail.getTo().split(",");
        
        //send to each domain
        for (String recipient : recipients) {
            String[] split = recipient.split("@");
            String domain = split[1];

            //already forwarded email to this domain
            if (failDomains.contains(domain)) {
                failMails.add(recipient);
                continue;
            }
            if (sentDomains.contains(domain)) {
                continue;
            }
            sentDomains.add(domain);

            //send message
            String sendMessage = sendMessage(domain, mail);
            if (sendMessage != null) {
                failure = true;
                failDomains.add(domain);
                
                failData.append(sendMessage);
                failMails.add(recipient);
                
                System.out.println("Send failed to mail '"+recipient+"': " + sendMessage);
            }
        }

        //failure: write back to sender
        if (failure) {
            Mail fail = new Mail();
            fail.setTo(mail.getFrom());
            fail.setFrom("mailer@" + ip);
            fail.setSubject("Could not send mail subject '" + mail.getSubject() + "'");
            fail.setData("Could not send to mails: "+ String.join(",", failMails) + " Details: " + failData.toString());

            //send fail message
            String[] split = fail.getTo().split("@");
            String domain = split[1];
            sendMessage(domain, fail);
        }

        //monitoring server
        try {
            DatagramSocket socket = new DatagramSocket();
            String msg = ip+":"+port+" "+mail.getFrom();
            byte[] buf = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(buf, buf.length, monitorInfo.getAddress(), monitorInfo.getPort());
            socket.send(packet);
        } catch (IOException e) {
            System.out.println("TransferSender error: " + e);
        }
        System.out.println("TransferSender finished. ("+mail.getSubject()+": "+mail.getTo()+")");
    }

    /**
     * Opens a new socket to domain and send this message
     *
     * @param domain domain to send message to
     * @param mail   mail to send to domain
     * @return null if everything went correctly, else a error describing string
     */
    private String sendMessage(String domain, Mail mail) {
        //make new socket to domain (lookup domain)
        String lookup;
        try {
            // obtain registry that was created by the server
            Registry registry = LocateRegistry.getRegistry(
                    monitorInfo.getRegistryHost(),
                    monitorInfo.getRegistryPort()
            );
            // look for the bound server remote-object implementing the IServerRemote interface
            INameserverRemote server = (INameserverRemote) registry.lookup(monitorInfo.getRootId());

            //iteratively
            String[] split = domain.split("\\.");
            System.out.println("Send Mail to domain '"+domain+"'");
            for(int i=split.length-1; i>0; --i) {
                server = server.getNameserver(split[i]);
                if(server==null) {
                    return "Nameserver '" + split[i] + "' for domain '" + domain + "' not found. ";
                }
            }
            lookup = server.lookup(split[0]);
        } catch (RemoteException | NotBoundException e) {
            //RemoteException: throw new RuntimeException("Error while obtaining registry/server-remote-object.", e);
            //NotBoundException: throw new RuntimeException("Error while looking for server-remote-object.", e);
            return "Domain '" + domain + "' not found. ";
        }

        if(lookup==null) {
            return "Domain '" + domain + "' not found. ";
        }
        String[] ipPort = lookup.split(":");
        Socket socket = null;
        try {
            socket = new Socket(ipPort[0], Integer.parseInt(ipPort[1]));

            //check connected
            if (!socket.isConnected()) {
                socket.close();
                return "Could not reach Domain '" + domain + "' at " + lookup + ' ';
            }

            //repeat protocol
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            //other must start with ok DMTP
            String protocol = in.readLine();
            if (!protocol.equals("ok DMTP2.0")) {
                out.println("quit");
                socket.close();
                return "Wrong Domain Protocol '" + domain + "'. ";
            }

            //send all mail infos
            String[] messages = mail.messages();
            for (String message : messages) {
                out.println(message);

                String response = in.readLine();

                //response not ok - quit and report failure
                if (!response.startsWith("ok")) {
                    //other error
                    out.println("quit");
                    socket.close();
                    return "Wrong Domain Response at '" + domain + "' after message '" + message + "'. ";
                }
            }

            //close socket
            socket.close();
        } catch (SocketException e) {
            //close socket
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } catch (IOException e) {
            System.out.println("Mail error: " + e);

            //close socket
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            return e.toString();
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
