package dslab.monitoring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;

public class UdpListenerThread extends Thread {
    private final DatagramSocket datagramSocket;
    private final MonitorData monitorData;
    
    UdpListenerThread(DatagramSocket datagramSocket, MonitorData monitorData){
        this.datagramSocket = datagramSocket;
        this.monitorData = monitorData;
    }
    
    @Override
    public void run() {
        byte[] buffer;
        DatagramPacket packet;
        try {
            while (!Thread.interrupted()) {
                buffer = new byte[1024];
                
                // create a datagram packet of specified length (buffer.length)
                /*
                 * Keep in mind that, in UDP, packet delivery is not guaranteed,
                 * and the order of the delivery/processing is also not guaranteed.
                 */
                packet = new DatagramPacket(buffer, buffer.length);

                // wait for incoming packets from client
                datagramSocket.receive(packet);
                // get the data from the packet
                String request = new String(packet.getData(),0, packet.getLength());

                //System.out.println("Received request-packet from client size "+request.length()+": " + request+". test");
                
                // check if request has the correct format
                if(!request.matches("(\\d+\\.){3}\\d+:\\d+ .*@.*")){
                    System.out.println("request does not match pattern: "+request);
                    continue;
                }

                //get parts
                String[] parts = request.split(" ");
                //System.out.println("adding '"+parts[0]+"' with '"+parts[1]+"' adsaddsadsadsasdasdasd");
                monitorData.add(parts[0], parts[1]);
            }
        } catch (SocketException e) {
            // when the socket is closed, the send or receive methods of the DatagramSocket will throw a SocketException
            System.out.println("SocketException while waiting for/handling packets: " + e.getMessage());
        } catch (IOException e) {
            // other exceptions should be handled correctly in your implementation
            throw new UncheckedIOException(e);
        } finally {
            if (datagramSocket != null && !datagramSocket.isClosed()) {
                datagramSocket.close();
            }
        }

    }
    
    void shutdown(){
        datagramSocket.close();
    }
}
