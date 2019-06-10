import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserData {
	public String password;
	List<String> synchNotifiche;//contiene le notifiche ricevute offline

	UserData(String password){
		this.password=new String(password);
		synchNotifiche=Collections.synchronizedList(new ArrayList<String>());
	}

}