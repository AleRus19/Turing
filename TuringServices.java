import java.rmi.Remote;
import java.rmi.RemoteException;

//interfaccia metodi esposti
public interface TuringServices extends Remote{
  int registerUser(String username,String password) throws RemoteException;
}
