import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import javax.swing.JOptionPane;


public class Client{
	
	private static String services="TURING-SERVER";
	private static int servicesPort=9003;

	public static void main (String args[]) { 
		TuringServices serverObject; 
		Remote RemoteObject;
		try {
			Registry r = LocateRegistry.getRegistry(servicesPort); 
			RemoteObject = r.lookup(services); 
			serverObject = (TuringServices) RemoteObject;//oggetto che espone i metodi offerti dal server
			Graphics client = new Graphics(serverObject);
			client.Go();
		}
		catch (Exception e) { 
			JOptionPane.showMessageDialog(null, "Impossibile aprire l'app");
		}
	}
}