package dslab.client;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.AesUtil;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.*;

public class MessageClient implements IMessageClient, Runnable {

    private final Shell shell;
    private final Config clientConfig;
    private AesUtil aesUtil;
    private Socket socket;

    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.shell = new Shell(in,out);
        this.shell.setPrompt(componentId+"@mail-client>");
        this.shell.register(this);

        this.clientConfig = config;
    }

    @Override
    public void run() {
        // connect to DMAP server
        try {
            socket = new Socket(clientConfig.getString("mailbox.host"), clientConfig.getInt("mailbox.port"));
            // create a reader to retrieve messages send by the server
            BufferedReader serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // create a writer to send messages to the server
            PrintWriter serverWriter = new PrintWriter(socket.getOutputStream(), true);

            if (!serverReader.readLine().equals("ok DMAP2.0")) {
                System.out.println("error protocol error");
                shutdownSocket();
                return;
            }

            // C (plain): startsecure
            serverWriter.println("startsecure");
            String[] answer = serverReader.readLine().split(" ");

            if (!answer[0].equals("ok")) {
                System.out.println("error couldn't establish secure connection");
                shutdown();
            }

            String serverCompId = answer[1];

            //C (RSA): ok <client-challenge> <secret-key> <iv>
            SecureRandom secureRandom = new SecureRandom();
            byte[] clientChallengeByte = new byte[32];
            byte[] initVectorByte = new byte[16];

            secureRandom.nextBytes(clientChallengeByte);
            secureRandom.nextBytes(initVectorByte);
            String clientChallengeString = Base64.getEncoder().encodeToString(clientChallengeByte);
            String initVectorString = Base64.getEncoder().encodeToString(initVectorByte);

            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            SecretKey plainKey = keyGenerator.generateKey();
            byte[] plainKeyByte = plainKey.getEncoded();
            String plainKeyString = Base64.getEncoder().encodeToString(plainKeyByte);

            aesUtil = new AesUtil(serverReader, serverWriter, plainKeyByte, initVectorByte);

            String plainMessage = "ok " + clientChallengeString + " " + plainKeyString + " " + initVectorString;

            PublicKey pubKey = Keys.readPublicKey(new File("keys/client/"+serverCompId+"_pub.der"));

            Cipher encryptCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, pubKey);
            byte[] encryptedMessageBytes = encryptCipher.doFinal(plainMessage.getBytes(StandardCharsets.UTF_8));
            String encryptedMessageString = Base64.getEncoder().encodeToString(encryptedMessageBytes);

            serverWriter.println(encryptedMessageString);

            byte[] solvedClientChallengeByte = Base64.getDecoder().decode(aesUtil.readLine().split(" ")[1]);

            if (Arrays.equals(solvedClientChallengeByte, clientChallengeByte)) {
                aesUtil.println("ok");
            } else {
                shell.out().println("error challenge failed");
                shutdown();
            }

            // login command
            aesUtil.println("login "
                    + clientConfig.getString("mailbox.user")
                    + " "
                    + clientConfig.getString("mailbox.password")
            );

            if (!aesUtil.readLine().equals("ok")) {
                shell.out().println("error login error");
                shutdownSocket();
                return;
            } else {
                shell.out().println("success: securely connected to " + serverCompId);
            }

        } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | InvalidKeyException e) {
            shell.out().println("error secure connection to mailbox server failed");
            shutdownSocket();
            return;
        }

        shell.run();
    }

    @Override
    @Command
    public void inbox() {
        // show all of the users messages

        try {
            aesUtil.println("list");
            List<Integer> idList = new ArrayList<Integer>();
            String line = null;

            while (!(line = aesUtil.readLine()).equals("ok")) {
                idList.add(Integer.parseInt(line.split(" ")[0]));
            }

            for (Integer id : idList) {
                shell.out().println(makeMessageString(id));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String makeMessageString(int id) {
        aesUtil.println("show " + id);
        String sender, recipient, subject, data, hash;
        try {
            recipient = aesUtil.readLine();
            if(recipient.startsWith("error")) {
                //shell.out().println(recipient);
                return recipient;
            }
            
            sender = aesUtil.readLine();
            subject = aesUtil.readLine();
            data = aesUtil.readLine();
            hash = aesUtil.readLine();
            //read ok
            aesUtil.readLine();

            return id + " | " + sender+" | "+recipient+" | "+subject+" | "+data;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    @Command
    public void delete(String id) {
        try {
            aesUtil.println("delete " + id);
            shell.out().println(aesUtil.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    @Command
    public void verify(String id) {
        aesUtil.println("show " + id);
        String from, to, subject, data, hash;
        try {
            to = aesUtil.readLine();
            if(to.startsWith("error")) {
                shell.out().println(to);
                return;
            }
            to = to.split(" ",2)[1];
            
            from = aesUtil.readLine().split(" ",2)[1];
            subject = aesUtil.readLine().split(" ",2)[1];
            data = aesUtil.readLine().split(" ",2)[1];
            hash = aesUtil.readLine().split(" ",2)[1];
            aesUtil.readLine(); //read ok

            if (Arrays.equals(Base64.getDecoder().decode(hash),calcHash(String.join("\n", from, to, subject, data)))) {
                shell.out().println("ok");
            } else {
                shell.out().println("error");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Array index out of bounds. "+ e);
            e.printStackTrace();
        }
    }

    @Override
    @Command
    public void msg(String to, String subject, String data) {
        // establish DMTP connection
        Socket transferSocket = null;
        try {
            String from = clientConfig.getString("transfer.email");
            transferSocket = new Socket(clientConfig.getString("transfer.host"), clientConfig.getInt("transfer.port"));
            BufferedReader serverReader = new BufferedReader(new InputStreamReader(transferSocket.getInputStream()));
            // create a writer to send messages to the server
            PrintWriter serverWriter = new PrintWriter(transferSocket.getOutputStream(), true);

            File sharedSecretFile = new File("keys/hmac.key");
            SecretKey sharedSecretKey = Keys.readSecretKey(sharedSecretFile);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(sharedSecretKey);

            serverReader.readLine();

            serverWriter.println("begin");
            serverReader.readLine();
            serverWriter.println("from " + from);
            serverReader.readLine();
            serverWriter.println("to " + to);
            serverReader.readLine();
            serverWriter.println("subject " + subject);
            serverReader.readLine();
            serverWriter.println("data " + data);
            serverReader.readLine();
            serverWriter.println("hash " + Base64.getEncoder().encodeToString(calcHash(String.join("\n", from, to, subject, data))));
            serverReader.readLine();
            serverWriter.println("send");
            String ok = serverReader.readLine();
            if(!"ok".equals(ok)) {
                shell.out().println("error "+ok);
            } else {
                shell.out().println("ok");
            }
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            shell.out().println("error could not send");
        }
        
        try {
            if(transferSocket!=null) {
                transferSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public byte[] calcHash(String msg) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        File sharedSecretFile = new File("keys/hmac.key");
        SecretKey sharedSecretKey = Keys.readSecretKey(sharedSecretFile);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(sharedSecretKey);

        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);

        return mac.doFinal(msgBytes);
    }

    @Override
    @Command
    public void shutdown() {
        shutdownSocket();
        throw new StopShellException();
    }
    
    public void shutdownSocket() {
        try {
            if(socket!=null) {
                socket.close();
            }
        } catch (IOException e) {
            shell.out().println("error couldn't properly shutdown");
        }
    }

    public static void main(String[] args) throws Exception {
        String component = args.length>0 ? args[0] : "client-arthur";
        IMessageClient client = ComponentFactory.createMessageClient(component, System.in, System.out);
        client.run();
    }
}
