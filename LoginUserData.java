import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LoginUserData {
	Socket mySock;
	ObjectOutputStream  notify;
	Lock lock_not;
	String username;
	//connessione
    
	LoginUserData(Socket sock,String username,Socket notify) throws IOException{
	  this.mySock=sock;	
	  this.lock_not=new ReentrantLock();
	  this.notify= new ObjectOutputStream(notify.getOutputStream());	
	  this.username=username;
	}

}

