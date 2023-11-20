package dslab.transfer;

import java.net.InetAddress;
import java.net.UnknownHostException;

class MonitorInfo {
    private final InetAddress address;
    private final int port;
    
    private final String registryHost;
    private final Integer registryPort;
    private final String rootId;

    MonitorInfo(String ip, int port, 
                String registryHost, Integer registryPort, String rootId) throws UnknownHostException {
        address = InetAddress.getByName(ip);
        this.port = port;
        
        this.registryHost = registryHost;
        this.registryPort = registryPort;
        this.rootId = rootId;
    }

    InetAddress getAddress() {
        return address;
    }

    int getPort() {
        return port;
    }

    public String getRegistryHost() {
        return registryHost;
    }

    public Integer getRegistryPort() {
        return registryPort;
    }

    public String getRootId() {
        return rootId;
    }
}
