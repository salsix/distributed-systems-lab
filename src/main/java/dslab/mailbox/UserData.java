package dslab.mailbox;

import dslab.util.Config;
import dslab.util.Mail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

class UserData {
    private final Config userPasswords;
    private final ConcurrentMap<String, Map<Long, Mail>> mails = new ConcurrentHashMap<>();
    private long idCounter = 0;
    private final String componentId;
    
    UserData(Config userPasswords, String componentId){
        this.userPasswords = userPasswords;
        this.componentId = componentId;

        //init empty mailbox
        for(String key : userPasswords.listKeys()){
            mails.put(key, new HashMap<>());
        }
    }
    
    //lookup user password
    String lookup(String username){
        synchronized (userPasswords) {
            if(!userPasswords.containsKey(username)){
                return null;
            }
            return userPasswords.getString(username);
        }
    }
    
    void saveMail(String user, Mail mail){
        if(!mails.containsKey(user)){
            return;
        }
        mails.get(user).put(++idCounter, mail);
    }

    String[] listMail(String user){
        if(!mails.containsKey(user)){
            return null;
        }
        Map<Long, Mail> map = mails.get(user);
        List<Long> ids = map.keySet().stream().sorted().collect(Collectors.toList());
        String[] res = new String[ids.size()];

        int index = 0;
        for(Long id : ids){
            Mail m = map.get(id);
            res[index] = id+" "+m.getFrom()+" "+m.getSubject();
            ++index;
        }

        return res;
    }

    String[] loadMail(String user, Long id){
        if(!mails.containsKey(user)){
            return null;
        }
        Map<Long, Mail> map = mails.get(user);
        if(!map.containsKey(id)) {
            return null;
        }
        return map.get(id).display();
    }

    boolean deleteMail(String user, Long id){
        if(!mails.containsKey(user)){
            return false;
        }
        Map<Long, Mail> map = mails.get(user);
        if(!map.containsKey(id)) {
            return false;
        }
        map.remove(id);
        return true;
    }

    public String getComponentId() {
        synchronized (componentId) {
            return componentId;
        }
    }
}
