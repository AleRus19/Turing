import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import javax.swing.JTextArea;

public class ChatWorker implements Runnable{
	private JTextArea area ;
	private MulticastSocket socket;
	private InetAddress multicastGroup ;
	private String us_id ;

	public ChatWorker(MulticastSocket socket,InetAddress multicast,JTextArea t,String id) throws NullPointerException{
		this.socket=socket;
		this.multicastGroup=multicast;
		this.area=t;
		this.us_id=id;
	}

	public void run() {
		final int LENGTH = 512; 
		byte buff[] = new byte[LENGTH];
		try  {
			socket.joinGroup(multicastGroup);
			DatagramPacket packet = new DatagramPacket(buff, buff.length); 
			while(true) { 
				socket.receive(packet); 
				String time = new String(packet.getData(), 0, packet.getLength(), "UTF-8"); 
				if(time.equals(us_id+"_exit")==true) {return;}
				this.area.append(time);
			}
		}
		catch (IOException e) { 
			return ;
		}

	}
}

