package dslab.util;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class AesUtil {
    private final Key key;
    private final IvParameterSpec iv;
    private String keyError = "";
    private final PrintWriter out;
    private final BufferedReader in;

    //https://www.baeldung.com/java-aes-encryption-decryption
    public AesUtil(BufferedReader in, PrintWriter out) {
        this.out = out;
        this.in = in;
        this.key = null;
        this.iv = null;
    }
    public AesUtil(BufferedReader in, PrintWriter out, byte[] key, byte[] iv) {
        this.out = out;
        this.in = in;
        Key key1 = null;
        try {
            key1 = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            e.printStackTrace();
            keyError = e.toString();
        }

        this.key = key1;
        this.iv = new IvParameterSpec(iv);
    }
    
    //returns null if key works, else error description 
    public String keyWorks(){
        if(key!=null) {
            return null;
        }
        return keyError;
    }

    //secured = key of AES
    public String readLine() throws IOException {
        String text = in.readLine();
        if(key==null) {
            return text;
        }
        if(text==null) {
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            byte[] plainText = cipher.doFinal(Base64.getDecoder().decode(text));
            return new String(plainText);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }

        return null;
    }
    //secured = key of AES, returns null if worked, else error description
    public String println(String line) {
        if(key==null) {
            out.println(line);
            return null;
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            byte[] cipherText = cipher.doFinal(line.getBytes());
            String encrypted = Base64.getEncoder().encodeToString(cipherText);
//            System.out.println("println: "+encrypted);
            out.println(encrypted);
            return null;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }

        return "Could not encrypt";
    }
}
