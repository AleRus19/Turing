// Classes and support for RMI 
import java.rmi.RemoteException;
// Classes and support for RMI servers
import java.rmi.server.RemoteServer;
import java.util.concurrent.ConcurrentHashMap;

public class TuringServicesImpl extends RemoteServer implements TuringServices {
	private static final long serialVersionUID = 1L;
	public static ConcurrentHashMap<String,UserData> RegisteredUser;//utenti registrati

	TuringServicesImpl() throws RemoteException { 
		RegisteredUser = new ConcurrentHashMap<String, UserData>();
	}

	public int registerUser(String username,String password) throws RemoteException {
		if(RegisteredUser.putIfAbsent(username, new UserData(password))==null) {
			System.out.println("Utente registrato: "+username + " "+ password);
			return 1;
		}
		return -1;
	} 


}
