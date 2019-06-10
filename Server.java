import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
	//Strutture usate
	public static ConcurrentHashMap<String,LoginUserData> LoggedUser; //utenti online
	public static ConcurrentHashMap<String,DocumentData> Document; //documenti presenti
	public static List<Gruppi> Pool; //pool indirizzi multicast

	private static String serverAddress="127.0.0.1";
	private static int portServer=4022;
	private static int portServer_not=4023;
	private static String groupAddress="239.1.1.";
	private static int groupPort=3000;
	public static String path="c:\\server";
	public static String services="TURING-SERVER";
	private static int servicesPort=9003;


	//eliminare vecchi file
	public static void deleteDir(File file) {
		File[] contents = file.listFiles();
		if (contents != null) {
			for (File f : contents) {
				if (! Files.isSymbolicLink(f.toPath())) {
					deleteDir(f);
				}
			}
		}
		file.delete();
	}

	public static void main (String args[]) throws IOException {
		//creare cartella server per contenere gli utenti con i rispettivi documenti
		Path newFilePath = Paths.get(path);
		if(Files.exists(newFilePath,new LinkOption[]{ LinkOption.NOFOLLOW_LINKS})==true){
			File f=new File(path);
			deleteDir(f);
			Files.createDirectories(newFilePath);
		}
		else {
			Files.createDirectories(newFilePath);
		}
		//inizializzo la struttura degli indirizzi multicast
		try { 
			Pool=new ArrayList<Gruppi>();
			for(int i=1;i<253;i++) {
				Gruppi g1=new Gruppi(groupAddress+i,groupPort);
				groupPort++;
				Pool.add(g1); 
			}
			LoggedUser = new ConcurrentHashMap<String, LoginUserData>();
			Document = new ConcurrentHashMap<String, DocumentData>();
			//esporto il servizio di registrazione RMI
			TuringServicesImpl statsService = new TuringServicesImpl();
			TuringServices stub = (TuringServices) UnicastRemoteObject.exportObject(statsService, 0);
			LocateRegistry.createRegistry(servicesPort);
			Registry r=LocateRegistry.getRegistry(servicesPort);
			r.rebind(services, stub);
			System.out.println("Server ready");
		} 
		catch (RemoteException e) { 
			System.out.println("Communication error " + e.toString());
		}
		Charset.defaultCharset();
		ExecutorService es=null; 
		try (ServerSocket server = new ServerSocket()) {
			//configuro il ServerSocket
			try(ServerSocket server_notify = new ServerSocket()){  
				server.bind(new InetSocketAddress(serverAddress, portServer)); 
				server_notify.bind(new InetSocketAddress(serverAddress, portServer_not)); 
				es= Executors.newCachedThreadPool();//attivo il threadpool 
				while (true){
					try { 
						Socket client=server.accept();
						Socket notify=server_notify.accept();
						TaskWorker worker = new TaskWorker(client,notify);//creo un nuovo task 
						es.execute(worker); //lo sottometto al pool che lo assegnerà ad un thread
					} catch (IOException e){ 
					}
				}
			} catch (UnknownHostException e) { 
			} catch (IOException e) { 
			} finally{  
				if (es != null) es.shutdown();//terminazione
			}
		}
	}
}
