package dslab.transfer;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TransferClientConnection implements Runnable, Shutdownable {
    private final Socket client;
    private final ExecutorService pool;
    private final int port;
    private final MonitorInfo monitorInfo;
    private final String ip;
    private final ConcurrentHashMap<Integer, Shutdownable> connectionMap;

    TransferClientConnection(Socket client, int port, MonitorInfo monitorInfo, String ip,
                             ConcurrentHashMap<Integer, Shutdownable> connectionMap) {
        this.client = client;
        this.port = port;
        this.monitorInfo = monitorInfo;
        this.ip = ip;
        this.connectionMap = connectionMap;

        pool = Executors.newFixedThreadPool(2);
    }

    @Override
    public void shutdown() {
        try {
            client.close();
            pool.shutdown();
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void run() {
        //invalid client
        if (client == null || !client.isConnected() || client.isClosed()) {
            System.out.println("invalid client");
            return;
        }
        System.out.println("TransferClientConnection start "+ client);

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
            while (!Thread.interrupted() && client.isConnected()) {
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

                        //find invalid email (no @)
                        String[] emails = content.split(",");
                        boolean valid = true;
                        for (int i = 0; i < emails.length; ++i) {
                            String email = emails[i];
                            if (!Mail.validMail(email)) {
                                valid = false;
                                out.println("error invalid recipient email (nr. " + (i + 1) + ": '" + email + "')");
                                break;
                            }
                        }
                        if (!valid) {
                            continue;
                        }

                        out.println("ok " + emails.length);
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

                        //send mail
                        pool.execute(new TransferSender(mail, port, monitorInfo, ip));

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
            System.out.println("TransferClientConnection: thread IO exception: " + e.toString());
        }

        //close client socket
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //pool close
        try {
            boolean finished = pool.awaitTermination(60L, TimeUnit.SECONDS);
            if(!finished) {
                pool.shutdownNow();
            } else {
                System.out.println("TransferClientConnection finished after waiting "+client);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        connectionMap.remove(hashCode());
        System.out.println("TransferClientConnection finished "+ client);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransferClientConnection that = (TransferClientConnection) o;
        return Objects.equals(client, that.client);
    }

    @Override
    public int hashCode() {
        return Objects.hash(client);
    }
}
