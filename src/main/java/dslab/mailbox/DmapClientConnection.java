package dslab.mailbox;

import dslab.Shutdownable;
import dslab.util.AesUtil;

import javax.crypto.Cipher;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DmapClientConnection implements Runnable, Shutdownable {
    private final Socket client;
    private final UserData userData;
    private final ConcurrentHashMap<Integer, Shutdownable> connectionMap;

    DmapClientConnection(Socket client, UserData userData, ConcurrentHashMap<Integer, Shutdownable> connectionMap){
        this.client = client;
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
            System.out.println("DmapClientConnection invalid client");
            return;
        }
        System.out.println("DmapClientConnection start " + client);

        try {
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            //connected message
            out.println("ok DMAP2.0");

            //set fields
            String user = null;
            AesUtil aesUtil = new AesUtil(in, out);
            while (!Thread.interrupted()) {
                //to, from, subject, data, send, quit
                String message = aesUtil.readLine();
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

                //System.out.println("DmapClientConnection: " + command + ":" + content);
                switch (command) {
                    case "startsecure": {
                        aesUtil.println("ok "+userData.getComponentId());
                        
                        //RSA: https://www.baeldung.com/java-rsa
                        try {
                            //get private key
                            File privateKeyFile = new File("keys/server/"+userData.getComponentId()+".der");
                            byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
                            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                            EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
                            Key privateKey = keyFactory.generatePrivate(privateKeySpec);
                            
                            String clientAnswer = aesUtil.readLine();
                            System.out.println("Client Answer: "+clientAnswer);
                            
                            //decrypt RSA answer from client with private key
                            Cipher decryptCipher = Cipher.getInstance("RSA");
                            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
                            byte[] decryptedMessageBytes = decryptCipher.doFinal(Base64.getDecoder().decode(clientAnswer));
                            String rsaAnswer = new String(decryptedMessageBytes, StandardCharsets.UTF_8);
                            System.out.println("RSA answer: "+rsaAnswer);
                            
                            //get new aesUtil from decrypted message
                            String[] rsaAnswerSplit = rsaAnswer.split(" ");
                            if(rsaAnswerSplit.length!=4 || !"ok".equals(rsaAnswerSplit[0])) {
                                client.close();
                                break;
                            }
                            byte[] aesKey = Base64.getDecoder().decode(rsaAnswerSplit[2]);
                            byte[] aesIv = Base64.getDecoder().decode(rsaAnswerSplit[3]);
                            
                            aesUtil = new AesUtil(in, out, aesKey, aesIv);
                            if(aesUtil.keyWorks()!=null) {
                                System.out.println("AesUtil key doesnt work: "+aesUtil.keyWorks());
                                client.close();
                                break;
                            }
                            
                            //AES encrypt with challenge (send message to client)
                            System.out.println("ok "+rsaAnswerSplit[1]);
                            aesUtil.println("ok "+rsaAnswerSplit[1]);
                            
                            //AES decrypt (read message from client)
                            String ok = aesUtil.readLine();
                            System.out.println("should be ok: "+ok);
                            if( !"ok".equals(ok) ) {
                                client.close();
                                break;
                            }
                        } catch (Exception e) {
                            System.out.println("DmapClientConnection: Exception "+e);
                            e.printStackTrace();
                        }
                        break;
                    }
                    case "login": {
                        String[] split = content.split(" ");
                        if(split.length!=2){
                            aesUtil.println("error syntax: 'login username password'");
                            continue;
                        }
                        
                        String pw = userData.lookup(split[0]);
                        
                        //user not found
                        if(pw==null){
                            aesUtil.println("error unknown user");
                            continue;
                        }
                        
                        //wrong pw
                        if(!pw.equals(split[1])){
                            aesUtil.println("error wrong password");
                            continue;
                        }
                        
                        user = split[0];
                        aesUtil.println("ok");
                        break;
                    }
                    case "list": {
                        if(user==null){
                            aesUtil.println("error not logged in");
                            continue;
                        }
                        String[] list = userData.listMail(user);
                        for(String s : list){
                            aesUtil.println(s);
                        }
                        aesUtil.println("ok");
                        break;
                    }
                    case "show": {
                        if(user==null){
                            aesUtil.println("error not logged in");
                            continue;
                        }
                        
                        if(!content.matches("\\d+")){
                            aesUtil.println("error wrong format for number: 'show number'");
                            continue;
                        }
                        
                        String[] list = userData.loadMail(user, Long.parseLong(content));
                        if(list==null){
                            aesUtil.println("error unknown message id");
                            continue;
                        }
                        
                        for(String s : list){
                            aesUtil.println(s);
                        }
                        aesUtil.println("ok");
                        break;
                    }
                    case "delete": {
                        if(user==null){
                            aesUtil.println("error not logged in");
                            continue;
                        }
                        boolean success = userData.deleteMail(user, Long.parseLong(content));
                        if(!success) {
                            aesUtil.println("error unknown message id");
                            continue;
                        }
                        
                        aesUtil.println("ok");
                        break;
                    }
                    case "logout": {
                        if(user==null){
                            aesUtil.println("error not logged in");
                            continue;
                        }
                        user = null;
                        aesUtil.println("ok");
                        break;
                    }
                    case "quit": {
                        aesUtil.println("ok bye");
                        client.close();
                        break;
                    }
                    default: {
                        aesUtil.println("error protocol error");
                        client.close();
                        return;
                    }
                }
            }
        } catch (SocketException e) {
            //socket closed
        } catch (IOException e) {
            //e.printStackTrace();
            System.out.println("DmapClientConnection: thread IO exception: " + e.toString());
        }

        //close client socket
        try {
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        connectionMap.remove(hashCode());
        System.out.println("DmapClientConnection finished " + client);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DmapClientConnection that = (DmapClientConnection) o;
        return Objects.equals(client, that.client);
    }

    @Override
    public int hashCode() {
        return Objects.hash(client);
    }
}
