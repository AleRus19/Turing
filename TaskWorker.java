import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JOptionPane;


//task
public class TaskWorker implements Runnable{
	private Socket mySock;
	private Socket notify;
	private String username;
	private ObjectInputStream fromClient1 ;	
	private ObjectOutputStream toClient1 ;
	private static String path="c:\\server/";
	
	//costruttore
	public TaskWorker(Socket Sock,Socket n) throws NullPointerException, IOException{
		this.mySock=Sock;
		this.fromClient1 = new ObjectInputStream(this.mySock.getInputStream());
		this.toClient1= new ObjectOutputStream(this.mySock.getOutputStream());
		this.notify=n;
	}

	public void Termination() throws IOException {
		NotifyTermination(this.username);
		toClient1.close();
		fromClient1.close();
		Server.LoggedUser.remove(this.username);
	}

		
	//funzione per inviare risposte al client
	void Reply(int ack,String msg) {
		Message reply=new Message();//nuovo messaggio
		reply.setMessage(msg);//settare il msg
		reply.setOperation(ack);//settare il codice di risposta
		try {
			this.toClient1.writeObject(reply);//inviare
			this.toClient1.flush();
		}
		catch(IOException e) {}//connessione scaduta
	}

	//operazione login
	public int Login(Message request) {
		try {
			String[] split = request.getMessage().split(" ", 2);
			String username = split[0];
			String password = split[1];
			System.out.println("Richiesta Login: "+ username +" "+ password); 
			if(TuringServicesImpl.RegisteredUser.containsKey(username)==false || (TuringServicesImpl.RegisteredUser.get(username).password.equals(password)==false)) {
				Reply(0,"Utente o password errati");
				this.mySock.close();
				this.notify.close();
				toClient1.close();
				fromClient1.close();
				return -1;
			}
			if(Server.LoggedUser.putIfAbsent(username, new LoginUserData(this.mySock,username,this.notify))==null) {
				Reply(1,"Login effettuato con succcesso");
				this.username=username;
				Message notification=new Message();
				synchronized(TuringServicesImpl.RegisteredUser.get(username).synchNotifiche) {
					int n_inv=TuringServicesImpl.RegisteredUser.get(username).synchNotifiche.size();
					notification.setSize(n_inv);
					this.toClient1.writeObject(notification);//invio il numero di inviti ricevuti offline
					this.toClient1.flush();
					for(int i=0;i<n_inv;i++) {
						Message notify=new Message();//invio ogni singolo invito
						notify.setMessage(TuringServicesImpl.RegisteredUser.get(username).synchNotifiche.get(i));
						this.toClient1.writeObject(notify);
						this.toClient1.flush();
					}
					TuringServicesImpl.RegisteredUser.get(username).synchNotifiche.clear();
				}
			}
			else {
				Reply(-1,"Utente già loggato");
				this.mySock.close();
				toClient1.close();
				this.notify.close();
				fromClient1.close();
				return 1;
			}
		}
		catch(Exception e) {}
		return 0;
	}

	//creazione documento
	void CreateDocument(Message request) throws IOException {
		String[] parts = request.getMessage().split(" ", 2);
		String document = parts[0];
		String sez = parts[1];
		try {
			int sezioni= Integer.parseInt(sez);
			if(Server.Document.putIfAbsent(this.username+"_"+document,new DocumentData(this.username,sezioni))==null) {
				System.out.println("Documento creato: "+ document + " da " + this.username); 
				DocumentData d=Server.Document.get(this.username+"_"+document);
				Path newDirPath = Paths.get(path+this.username);
				boolean exists = Files.exists(newDirPath,  LinkOption.NOFOLLOW_LINKS);
				if(exists==false)Files.createDirectories(newDirPath);
				//creo le sezioni del documento
				for(int j=0;j<sezioni;j++) {
					d.section.add(document+"_"+j);
					Path newFilePath = Paths.get(path+this.username+"/"+document+"_"+j+".txt");
					Files.createFile(newFilePath);
				}
				Reply(1,"Documento Creato"); 
			}
			else {
				Reply(-1,"Documento già esistente"); 		  	
			}
		}
		catch(IllegalArgumentException e) {
			Reply(-2,"Sezioni non valide"); 		  	
		}
	}

	//invia il messaggio di terminazione al thread delle notifiche
	public void NotifyTermination(String us) throws IOException {
		Message ack=new Message();
		ack.setMessage("-1");
		Server.LoggedUser.get(us).lock_not.tryLock();
		Server.LoggedUser.get(us).notify.writeObject(ack);
		Server.LoggedUser.get(us).notify.flush();
		Server.LoggedUser.get(us).lock_not.unlock();
	}


	//richiesta di invito
	public void SendInvito(Message request) {
		String[] arguments = request.getMessage().split(" ", 2);
		String doc = arguments[0];
		String member = arguments[1];
		try {
			if(Server.Document.containsKey(this.username+"_"+doc)==false) {
				Reply(-1,"Operazione fallita:\n\t Documento non esistente \n\t Non sei il creatore del documento ");
				return ;
			}
			Server.Document.get(this.username+"_"+doc).read.lock();
			if(TuringServicesImpl.RegisteredUser.containsKey(member)==false) {
				Reply(-2,"Operazione fallita:\n\t Membro non esistente");
				Server.Document.get(this.username+"_"+doc).read.unlock();
			}
			else if(Server.Document.get(this.username+"_"+doc).Collaborators.contains(member)==true) {
				Reply(-2,"Operazione fallita:\n\t Membro già invitato");	
				Server.Document.get(this.username+"_"+doc).read.unlock();
			}
			else if (Server.Document.get(this.username+"_"+doc).creator.equals(member)==true) {
				Reply(-2,"Operazione fallita:\n\t Sei già il creatore");	
				Server.Document.get(this.username+"_"+doc).read.unlock();
			}
			else {
				Server.Document.get(this.username+"_"+doc).read.unlock();
				Server.Document.get(this.username+"_"+doc).write.lock();
				Server.Document.get(this.username+"_"+doc).Collaborators.add(member);
				Server.Document.get(this.username+"_"+doc).write.unlock();
				System.out.println("Membro invitato: "+ member+ " in "+ doc); 
				
				//se è online invio immediatamente la notifica
				if(Server.LoggedUser.containsKey(member)==true) {
					Reply(1,"Documento condiviso con "+member+" con successo" );
					Message notif=new Message();
					notif.setMessage("Nuovo invito: \n\t Documento: "+doc+"\n\t Creatore: "+this.username);
					Server.LoggedUser.get(member).lock_not.tryLock();
					Server.LoggedUser.get(member).notify.writeObject(notif);
					Server.LoggedUser.get(member).notify.flush();
					Server.LoggedUser.get(member).lock_not.unlock();
				}
				else {
					//se non è online la salvo
					TuringServicesImpl.RegisteredUser.get(member).synchNotifiche.add("Nuovo invito: \n\t Documento: "+doc+"\n\t Creatore: "+this.username);		
					Reply(1,"Documento condiviso con "+member+" con successo" );
				}
			}
		}catch(Exception e ) {
			JOptionPane.showMessageDialog(null, "Errore operazione");
			try {
				Termination();	
				return ;
			}catch(IOException e1) {}
		}
	
	}

	//invia lista documenti a cui si partecipa
	public void SendList(Message request) throws IOException  {
		List<DocumentDataList> lst=new ArrayList<DocumentDataList>();
		//estraggo la lista dei documenti di cui faccio parte come creatore o membro
		Iterator<String> it = Server.Document.keySet().iterator();
		try {
			while(it.hasNext()){
				String key = it.next();
				Server.Document.get(key).read.lock();
				DocumentData d=Server.Document.get(key);
				if(d.creator.equals(this.username)==true) {
					lst.add(new DocumentDataList(d,key));	
				}
				else if(d.Collaborators.contains(this.username)==true){
					lst.add(new DocumentDataList(d,key));	
				}
				Server.Document.get(key).read.unlock();
			}
			//caso nessun documento di cui faccio parte
			if(lst.size()==0) {
				Message reply=new Message();
				reply.setOperation(0);
				this.toClient1.writeObject(reply);
				return ;
			}
			Message reply=new Message();
			reply.setOperation(1);
			reply.setSize(lst.size());
			this.toClient1.writeObject(reply);
			for(int i=0;i<lst.size();i++) {
				String risultato="";
				String[] arguments = lst.get(i).name.split("_", 2);
				String doc = arguments[1];
				risultato+="Documento: "+ doc +"\n"+ "Creatore: "+lst.get(i).doc.creator+"\n";
				String membri="";
				Server.Document.get(lst.get(i).doc.creator+"_"+doc).read.lock();
				for(int k=0;k<lst.get(i).doc.Collaborators.size();k++) {
					membri+=lst.get(i).doc.Collaborators.get(k)+"\n";
				}
				Server.Document.get(lst.get(i).doc.creator+"_"+doc).read.unlock();
				risultato+="Membri: \n\t\t"+ membri+"\n\n";
				Reply(1,risultato);//invio la lista 
			}
		}
		catch(Exception e ) {
			Reply(-1,"Errore");
		}
	}

	//logout
	public void Logout() {
		try {
			if(Server.LoggedUser.containsKey(this.username)==true) {
				Reply(1,"Logout avvenuto con successo");
				Termination();
			}
			else {
				Reply(1,"Errore");
			}
			System.out.println("Logout: "+ this.username); 
		}
		catch(Exception e ) {
			try {
				Termination();		
				return ;
			}catch(IOException e1) {}
		}
		
	}


	public void SendGroup(String doc) throws IOException {
		boolean bl=false;
		//caso in cui ad un documento è già stato assegnato un indirizzo poichè in editing
		for(int i=0;i<Server.Pool.size() && bl==false;i++) {
			Gruppi g=Server.Pool.get(i);
			synchronized(g) {
				if(doc.equals(g.nomedoc) && g.uso==1) {
					Reply(1,g.indirizzo); 
					Message port=new Message();
					port.setSize(g.port);
					this.toClient1.writeObject(port);
					this.toClient1.flush();
					g.n_utenti++;
					bl=true;
				}
			}
		}
		if(bl==true)return ;
		bl=false;
		//caso in cui non vi è un gruppo attivo su quel documento e 
		//si cerca un indirizzo libero da assegnare
		for(int i=0;i<Server.Pool.size() && bl==false;i++) {
			Gruppi g=Server.Pool.get(i);
			synchronized(g) {
				if(g.uso==0) {
					Reply(1,g.indirizzo);
					Message port=new Message();
					port.setSize(g.port);
					this.toClient1.writeObject(port);
					this.toClient1.flush();
					g.n_utenti++;
					g.uso=1;
					g.nomedoc=doc;
					bl=true;
				}
			}
		}
		if(bl==true)return ;
		//caso in cui tutti gli indirizzi sono occupati
		if(bl==false) {
			Reply(-1,"");
			Message port=new Message();
			port.setSize(0);
			this.toClient1.writeObject(port);
			this.toClient1.flush();
		}
	}

	//funzione per liberare un gruppo se ha 0 utenti attivi 
	//oppure aggiornare il numero di utenti quando abbandonano il gruppo
	//in seguito ad una operazione di end-edit
	public void CheckGroup(String doc) throws IOException {
		boolean bl=false;
		for(int i=0;i<Server.Pool.size() && bl==false;i++) {
			Gruppi g=Server.Pool.get(i);
			if(g.nomedoc.equals(doc)==true) {
				synchronized(g) {
					g.n_utenti--;
					if(g.n_utenti==0) {
						g.nomedoc=null;
						g.uso=0;
					}
					bl=true;
				}
			}
		}
	}

	//funzione usata per notificare ad un client che una sezione si è liberata
	void FreeSection(String nome, DocumentData d_edit,int sezione) throws IOException {
		synchronized(d_edit.synchAspiranti.get(sezione)) {
			for(int i=0;i<d_edit.synchAspiranti.get(sezione).size();i++) {
				if(Server.LoggedUser.containsKey(d_edit.synchAspiranti.get(sezione).get(i))==true){
					Message notif=new Message();
					notif.setMessage("Documento: "+ nome+"Sezione libera: "+sezione);
					Server.LoggedUser.get(d_edit.synchAspiranti.get(sezione).get(i)).lock_not.tryLock();
					Server.LoggedUser.get(d_edit.synchAspiranti.get(sezione).get(i)).notify.writeObject(notif);
					Server.LoggedUser.get(d_edit.synchAspiranti.get(sezione).get(i)).notify.flush();
					Server.LoggedUser.get(d_edit.synchAspiranti.get(sezione).get(i)).lock_not.unlock();
					d_edit.synchAspiranti.get(sezione).remove(i);
				}
			}
		}
	}

	//operazione edit/end-edit
	public int Edit(Message request) {
		int sezione=-1;
		DocumentData d_edit=null;
		String doc_edit =null;
		String creatore =null;
		try {
			try {
				String[] parts = request.getMessage().split(" ", 3);
				doc_edit = parts[0];
				String sec = parts[1];
				creatore = parts[2];
				sezione=Integer.parseInt(sec);
				d_edit=Server.Document.get(creatore+"_"+doc_edit);

				if(d_edit==null) {
					Reply(0,"Documento non esistente");
					return 0;
				}
				Server.Document.get(creatore+"_"+doc_edit).read.lock();
				if(sezione>=d_edit.num_sez) {
					Reply(-2,"Sezioni non valide");	 
					Server.Document.get(creatore+"_"+doc_edit).read.unlock();		
					return 0;
				}
				if(d_edit.Collaborators.contains(this.username)==false && d_edit.creator.equals(this.username)==false) {
					Reply(-2,"Non puoi editare");	 
					Server.Document.get(creatore+"_"+doc_edit).read.unlock();
					return 0;
				}
				Server.Document.get(creatore+"_"+doc_edit).read.unlock();
				boolean esito=d_edit.lock_sect.get(sezione).tryLock();//lock sulla sezione
				if(esito==true) {//sezione libera,adesso occupata
					d_edit.lock_editing.get(sezione).tryLock();
					d_edit.sec_editing.set(sezione, 1);
					d_edit.lock_editing.get(sezione).unlock();
					this.mySock.setSoTimeout(3600000);  //60 minuti
					String file=d_edit.section.get(sezione);
					FileChannel inChannel =   FileChannel.open(Paths.get(path+creatore+"/"+file+".txt")); 
					
					long size=inChannel.size();
					ByteBuffer buffer = ByteBuffer.allocate((int) size); 
					inChannel.read(buffer); //leggo il contenuto della sezione da editare
					buffer.flip();
					String contenuto="";
					while (buffer.hasRemaining()) {            
						contenuto+=(char) buffer.get();         
					}    
					Message reply=new Message();
					reply.setOperation(1);
					reply.setMessage(contenuto);
					reply.setSize((int) size);
					try {
						toClient1.writeObject(reply);//invio il contenuto al client
						toClient1.flush();
						System.out.println("Sezione inviata a: "+ this.username); 
						
					} catch (IOException e2) {//caso in cui il client non risponde
						this.mySock.setSoTimeout(0); 
						Termination();		
						d_edit.lock_editing.get(sezione).tryLock();
						d_edit.sec_editing.set(sezione, 0);
						d_edit.lock_editing.get(sezione).unlock();
						d_edit.lock_sect.get(sezione).unlock();
						FreeSection(doc_edit,d_edit,sezione);
						CheckGroup(creatore+"_"+doc_edit);
						inChannel.close();
						return -1;
					}
					inChannel.close();
					//invio indirizzo gruppo multicast
					SendGroup(creatore+"_"+doc_edit);
					// Ricezione sezione modificata
					Message request_end=null;
					try {
						request_end=(Message) fromClient1.readObject();
					}
					catch(IOException e) {
						d_edit.lock_editing.get(sezione).tryLock();
						d_edit.sec_editing.set(sezione, 0);
						d_edit.lock_editing.get(sezione).unlock();
						d_edit.lock_sect.get(sezione).unlock();
						FreeSection(doc_edit,d_edit,sezione);
						Termination();
						return 1;
					}
					//il client decide di annullare l'operazione di editing
					if(request_end.getOperation()==-1) {
						this.mySock.setSoTimeout(0); //rilascio lock
						d_edit.lock_sect.get(sezione).unlock();
						d_edit.lock_editing.get(sezione).tryLock();
						d_edit.sec_editing.set(sezione, 0);
						d_edit.lock_editing.get(sezione).unlock();
						FreeSection(doc_edit,d_edit,sezione);
						Reply(1,"Operazione Annullata");
						CheckGroup(creatore+"_"+doc_edit);
						return 1;
					}
					this.mySock.setSoTimeout(0); 
					//controllo gruppo
					CheckGroup(creatore+"_"+doc_edit);//il client lascia il gruppo
					String[] part = request_end.getMessage().split(" ", 2);
					String doc_endedit = part[0];
					int sezione_endedit = Integer.parseInt(part[1]);
					Path newFilePath = Paths.get(path+creatore+"/"+doc_endedit+"_"+sezione_endedit+".txt");
					FileChannel outChannel = FileChannel.open(newFilePath,StandardOpenOption.WRITE);
					//ricevo sezione modificata da salvare
					Message edited=null;//ricevo sezione editata
					try {
						edited=(Message) fromClient1.readObject();
					}
					catch(IOException e3) {
						d_edit.lock_editing.get(sezione).tryLock();
						d_edit.sec_editing.set(sezione, 0);
						d_edit.lock_editing.get(sezione).unlock();
						d_edit.lock_sect.get(sezione).unlock();
						FreeSection(doc_edit,d_edit,sezione);
						Termination();
						return 1;
					}
					ByteBuffer buffer_end = ByteBuffer.allocate(edited.getSize()); 
					buffer_end.put(edited.getMessage().getBytes());
					buffer_end.flip();
					while(buffer_end.hasRemaining()) {
						outChannel.write(buffer_end);
					}
					outChannel.close();
					Reply(1,"Sezione salvata");
					System.out.println("Sezione salvata da: "+ this.username); 
					d_edit.lock_editing.get(sezione).tryLock();
					d_edit.sec_editing.set(sezione, 0);
					d_edit.lock_editing.get(sezione).unlock();
					d_edit.lock_sect.get(sezione).unlock();
					FreeSection(doc_edit,d_edit,sezione);
					return 1;
				}
				else {
					d_edit.synchAspiranti.get(sezione).add(this.username);//sezione occupata, mi ricordo dell'utente
					Reply(-3,"Sezione in editing da un altro utente");
					return 1;
				}
			}
			catch(IllegalArgumentException e) {
				Reply(-2,"Sezione errata");
				return 1;
			}
			catch (SocketTimeoutException e) {//scade timer per l'editing della sezione
				d_edit.lock_editing.get(sezione).tryLock();
				d_edit.sec_editing.set(sezione, 0);
				d_edit.lock_editing.get(sezione).unlock();
				d_edit.lock_sect.get(sezione).unlock();
				FreeSection(doc_edit,d_edit,sezione);
				try {
					if(Server.LoggedUser.containsKey(this.username)==true) {
						CheckGroup(creatore+"_"+doc_edit);
						Termination();		
						return -1;
					}
				}
				catch(Exception e1 ) {}
				
			}
		}
		catch(Exception e) {
			try {
				Termination();		
				return -1;
			}catch(IOException e1) {}
		}
		return 1;
	}

	//scarica sezione
	public void ShowSection(Message request) throws IOException {
		int sec=-1;
		DocumentData d_show_sec=null;
		String[] parts = request.getMessage().split(" ", 3);
		String doc_show_sec = parts[0];
		try {
			sec= Integer.parseInt(parts[1]);
		}
		catch(NumberFormatException n) {
			Reply(0,"Sezione Errata");
			return;
		}
		try {
			String creator = parts[2];
			d_show_sec=Server.Document.get(creator+"_"+doc_show_sec);
			if(d_show_sec==null) {
				Reply(0,"Documento non esistente");
				return;
			}
			Server.Document.get(creator+"_"+doc_show_sec).read.lock();
			if(sec>=d_show_sec.num_sez) {
				Reply(-2,"Sezioni non valide");
				Server.Document.get(creator+"_"+doc_show_sec).read.unlock();
				return;
			}

			if(d_show_sec.Collaborators.contains(this.username)==false && d_show_sec.creator.equals(this.username)==false) {
				Reply(-1,"Non puoi visualizzare");
				Server.Document.get(creator+"_"+doc_show_sec).read.unlock();
				return;
			}
			Server.Document.get(creator+"_"+doc_show_sec).read.unlock();
			FileChannel inChannel =   FileChannel.open(Paths.get(path+creator+"/"+doc_show_sec+"_"+sec+".txt")); 
			long size=inChannel.size();
			ByteBuffer buffer_show_sec = ByteBuffer.allocate((int) size); 
			inChannel.read(buffer_show_sec); 
			buffer_show_sec.flip();
			String contenuto_show_sec="";
			while (buffer_show_sec.hasRemaining()) {            
				contenuto_show_sec+=(char) buffer_show_sec.get();         
			}          
			Message reply=new Message();
			reply.setMessage(contenuto_show_sec);
			d_show_sec.lock_editing.get(sec).tryLock();
			if(d_show_sec.sec_editing.get(sec)==1) {reply.setOperation(3);}
			else{reply.setOperation(1);}
			d_show_sec.lock_editing.get(sec).unlock();
			reply.setSize((int) size);
			try {
				toClient1.writeObject(reply);
			} catch (IOException e2) {
				Termination();		
				inChannel.close();
				return ;
			}
			inChannel.close();
			System.out.println("Sezione inviata a: "+ this.username); 
			return;
		}
		catch(Exception e ) {
			try {
				Termination();		
				return ;
			}catch(IOException e1) {}
		}
	}

	//scarica intero documento
	public void ShowDocument(Message request) {
		DocumentData d_show_sec=null;
		String[] parts = request.getMessage().split(" ", 2);
		String doc_show_sec = parts[0];
		String creator= parts[1];
		d_show_sec=Server.Document.get(creator+"_"+doc_show_sec);
		try {
			if(d_show_sec==null) {
				Reply(0,"Documento non esistente");
				return;
			}
			Server.Document.get(creator+"_"+doc_show_sec).read.lock();
			if(d_show_sec.Collaborators.contains(this.username)==false && d_show_sec.creator.equals(this.username)==false) {
				Reply(-1,"Non puoi visualizzare");
				Server.Document.get(creator+"_"+doc_show_sec).read.unlock();			
				return;
			}
			Server.Document.get(creator+"_"+doc_show_sec).read.unlock();
			String contenuto_show_sec="";
			long size=0;
			//concateno in una stringa l'intero contenuto delle singole sezioni
			for(int i=0;i<d_show_sec.num_sez;i++) {
				FileChannel inChannel =   FileChannel.open(Paths.get(path+creator+"/"+doc_show_sec+"_"+i+".txt")); 
				size+=inChannel.size();
				ByteBuffer buffer_show_sec = ByteBuffer.allocate((int) inChannel.size()); 
				inChannel.read(buffer_show_sec); 
				buffer_show_sec.flip();
				while (buffer_show_sec.hasRemaining()) {            
					contenuto_show_sec+=(char) buffer_show_sec.get();         
				}
				inChannel.close();
			}
			System.out.println(contenuto_show_sec);
			Message reply=new Message();
			reply.setMessage(contenuto_show_sec);
			reply.setOperation(1);
			reply.setSize((int) size);
			try {
				toClient1.writeObject(reply);
			} catch (IOException e2) {
				Termination();		
				return ;
			}
			//invio un messaggio dove sono indicate le ezioni in editing
			Message reply1=new Message();
			String editing="";
			for(int i=0;i<d_show_sec.sec_editing.size();i++) {
				d_show_sec.lock_editing.get(i).tryLock();
				if(d_show_sec.sec_editing.get(i)==1) {
					editing+=i+" ";	
				}
				d_show_sec.lock_editing.get(i).unlock();
			}
			reply1.setMessage(editing);
			try {
				toClient1.writeObject(reply1);
				System.out.println("Documento inviato a: "+ this.username); 
			} catch (IOException e2) {
				Termination();		
			}
		}
		catch(Exception e ) {return ;}
		}
	
	

	public void run() {
		try {
			boolean bl=false;
			while(bl==false) {
				int operation=-1;
				this.mySock.setSoTimeout(6000000);  //100 minuti timer inattività client
				Message request = (Message) this.fromClient1.readObject();
				this.mySock.setSoTimeout(0);  
				operation=request.getOperation();
				switch(operation) {
				case  0:
					if(Login(request)==-1) {bl=true;}
					break;		
				case  1:
					CreateDocument(request);
					break;
				case  2:
					SendInvito(request);
					break;		
				case  3:
					SendList(request);
					break;		
				case 4:
					Edit(request);		
					break;
				case 6:
					ShowSection(request);		
					break;
				case 7:
					ShowDocument(request);		
					break;	
				case 8:
					Logout();
					bl=true;
					break;	
				}

			}
		}
		catch (SocketTimeoutException e) {//scade timer inattività client
			try {
				if(Server.LoggedUser.containsKey(this.username)==true) {
					this.mySock.setSoTimeout(0);  
					Termination();		
				}
				return;
			} catch (IOException e1) {}
			return ;
		}
		catch(IOException e){
			try {
				toClient1.close();
				fromClient1.close();
				Server.LoggedUser.remove(this.username);
				this.notify.close();
				return;
			}catch (IOException e1) {}
		} catch (ClassNotFoundException e1) {
			return;
		}
	}
}



