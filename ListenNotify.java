import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;

import javax.swing.JOptionPane;

public class ListenNotify implements Runnable{
	private Socket notify;

	public ListenNotify(Socket Sock) throws NullPointerException{
		this.notify=Sock;
	}

	public void run() {
		ObjectInputStream fromServer = null;
		try {
			fromServer = new ObjectInputStream(notify.getInputStream());
		} catch (IOException e1) {
			
			e1.printStackTrace();
		}
		try {
			while(true) {
				Message ack=(Message) fromServer.readObject();
				if(ack.getMessage().equals("-1")) {
					notify.close();
					return ;
				}
				else {
					JOptionPane.showMessageDialog(null,"\n"+ack.getMessage());
				}
			}
		}
		catch (IOException | ClassNotFoundException e) {
			
		} 
		
	}
}

