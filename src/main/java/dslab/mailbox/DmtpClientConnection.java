package dslab.mailbox;

import dslab.Shutdownable;
import dslab.util.Mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DmtpClientConnection implements Runnable, Shutdownable {
    private final Socket client;
    private final String mailDomain;
    private final UserData userData;
    private final ConcurrentHashMap<Integer, Shutdownable> connectionMap;

    DmtpClientConnection(Socket client, String mailDomain, UserData userData, ConcurrentHashMap<Integer, Shutdownable> connectionMap){
        this.client = client;
        this.mailDomain = mailDomain;
        this.userData = userData;
        this.connectionMap = connectionMap;
    }
    
    @Override
    public void shutdown() {
        try {
            client.close();
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void run() {
        //invalid client
        if (client == null || !client.isConnected() || client.isClosed()) {
            return;
        }
        System.out.println("DmtpClientConnection start "+client);

        try {
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            //connected message
            out.println("ok DMTP2.0");

            //must start with "begin"
            String begin = in.readLine();
            if (begin == null || !begin.equals("begin")) {
                out.println("error protocol error");
                client.close();
                return;
            }
            out.println("ok");

            //set fields
            Mail mail = new Mail();
            while (!Thread.interrupted()) {
                //to, from, subject, data, send, quit
                String message = in.readLine();
                String command, content;

                if(message==null){
                    break;
                }

                //message with parameters
                int firstSpaceIndex = message.indexOf(' ');
                if (firstSpaceIndex != -1) {
                    command = message.substring(0, firstSpaceIndex);
                    content = message.substring(firstSpaceIndex + 1);
                } else {
                    command = message;
                    content = "";
                }

                //System.out.println("TransferClientConnection: " + command + ":" + content);
                switch (command) {
                    case "begin": {
                        mail = new Mail();
                        out.println("ok");
                        break;
                    }
                    case "hash": {
                        mail.setHash(content);
                        out.println("ok");
                        break;
                    }
                    case "to": {
                        mail.setTo(content);

                        //find invalid email (no @), email count
                        int count = 0;
                        String[] emails = content.split(",");
                        boolean valid = true;
                        for (int i = 0; i < emails.length; ++i) {
                            String email = emails[i];
                            String[] split = email.split("@");
                            if (!Mail.validMail(email)) {
                                valid = false;
                                System.out.println("error invalid email " + email);
                                out.println("error invalid email " + email);
                                break;
                            }
                            
                            //valid mail: check domain
                            String domain = split[1];
                            if(!domain.equals(mailDomain)){
                                continue;
                            }

                            //check user - dont count if user does not exist
                            String user = split[0];
                            if(userData.lookup(user)==null){
                                //error
                                valid = false;
                                System.out.println("error unknown recipient " + user);
                                out.println("error unknown recipient "+user);
                                continue;
                            }
                            ++count;
                        }
                        if (!valid) {
                            continue;
                        }
                        if(count==0){
                            out.println("error no relevant recipient");
                            continue;
                        }

                        out.println("ok " + count);
                        break;
                    }
                    case "from": {
                        mail.setFrom(content);

                        //invalid email (no @)
                        if (!Mail.validMail(content)) {
                            out.println("error invalid sender email");
                            continue;
                        }

                        out.println("ok");
                        break;
                    }
                    case "subject": {
                        mail.setSubject(content);
                        out.println("ok");
                        break;
                    }
                    case "data": {
                        mail.setData(content);
                        out.println("ok");
                        break;
                    }
                    case "send": {
                        //cannot yet send
                        if (!mail.complete()) {
                            out.println("error " + mail.incompleteMessage());
                            continue;
                        }

                        //save mail
                        String[] emails = mail.getTo().split(",");
                        for (String email : emails) {
                            //save email to user
                            String[] split = email.split("@");
                            String user = split[0];
                            userData.saveMail(user, mail);
                            System.out.println("Received Mail: " + mail.toString());
                        }

                        //reset mail
                        mail = new Mail();
                        out.println("ok");
                        break;
                    }
                    case "quit": {
                        out.println("ok bye");
                        client.close();
                        break;
                    }
                    default: {
                        out.println("error protocol error");
                        client.close();
                        return;
                    }
                }
            }
        } catch (SocketException e) {
            //socket closed
        } catch (IOException e) {
            //e.printStackTrace();
            System.out.println("DmtpClientConnection: thread IO exception: " + e.toString());
        }

        //close client socket
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        connectionMap.remove(hashCode());
        System.out.println("DmtpClientConnection finished "+client);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DmtpClientConnection that = (DmtpClientConnection) o;
        return Objects.equals(client, that.client);
    }

    @Override
    public int hashCode() {
        return Objects.hash(client);
    }
}
