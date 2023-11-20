package dslab.monitoring;

import java.util.*;
import java.util.stream.Collectors;

class MonitorData {
    private final Map<String, Integer> addresses = new HashMap<>();
    private final Map<String, Integer> servers = new HashMap<>();

    /**
     * 
     * @param server ip:port
     * @param address email address
     */
    synchronized void add(String server, String address) {
        int a = 0;
        if(addresses.containsKey(address)){
            a = addresses.get(address);
        }
        addresses.put(address, a+1);
        
        int s = 0;
        if(servers.containsKey(server)){
            s = servers.get(server);
        }
        servers.put(server, s+1);
    }
    
    synchronized String[] getAddresses(){
        return get(addresses);
    }

    synchronized String[] getServers(){
        return get(servers);
    }
    
    private String[] get(Map<String, Integer> container){
        //Set<String> keys = container.keySet();
        //String[] keys = (String[])container.keySet().stream().sorted(Comparator.comparingInt(container::get)).toArray();
        List<String> keys = container.keySet().stream().sorted((a, b) -> container.get(b)-container.get(a)).collect(Collectors.toList());
        //Set<String> keys = container.keySet();
        String[] ret = new String[keys.size()];
        int index = 0;
        
        for(String key : keys) {
            ret[index++] = key + " " + container.get(key);
        }
        
        return ret;
    }
}
