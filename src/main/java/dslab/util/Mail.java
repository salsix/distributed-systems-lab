package dslab.util;

import java.util.ArrayList;

public class Mail {
    private String to;
    private String from;
    private String subject;
    private String data;
    private String hash;
    
    //returns true if given mail address is valid
    public static boolean validMail(String email){
        //no '@'
        if(!email.contains("@")){
            return false;
        }
        //contains ' '
        if(email.contains(" ")){
            return false;
        }
        //only 1 '@'
        return email.indexOf('@') == email.lastIndexOf('@');
    }

    private String hashDisplay() {
        if(hash==null) return "";
        return hash;
    }
    
    //array of messages to send to replicate same mail
    public String[] messages(){
        return new String[]{
                "begin",
                "to " + getTo(),
                "from " + getFrom(),
                "subject " + getSubject(),
                "data " + getData(),
                "hash " + hashDisplay(),
                "send",
                "quit",
        };
    }
    public String[] display(){
        return new String[]{
                "to " + getTo(),
                "from " + getFrom(),
                "subject " + getSubject(),
                "data " + getData(),
                "hash " + hashDisplay(),
        };
    }

    public Mail copy() {
        Mail mail = new Mail();
        
        mail.setFrom(getFrom());
        mail.setTo(getTo());
        mail.setSubject(getSubject());
        mail.setData(getData());
        mail.setHash(getHash());
        
        return mail;
    }
    

    /**
     * @return true if all fields are set
     */
    public boolean complete(){
        return to!=null && from!=null && subject!=null && data!=null;
    }

    /**
     * @return message with all incomplete fields
     */
    public String incompleteMessage(){
        if(complete()) return "";
        ArrayList<String> message = new ArrayList<>();
        
        if(to==null) message.add("no sender");
        if(from==null) message.add("no recipients");
        if(subject==null) message.add("no subject");
        if(data==null) message.add("no data");
        
        return String.join(",", message);
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
