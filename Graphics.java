import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class Graphics{
	private TuringServices serverObject; 
	private String us_id;
	private static String ServerAddress="127.0.0.1";
	private static int serverPort=4022;
	private static int serverPort_not=4023;
	ObjectOutputStream toServer ;
	ObjectInputStream fromServer;
	private static String path="c:\\client/";


	public Graphics(TuringServices serverObject ) throws IOException{
		this.serverObject=serverObject;
		Path newFilePath = Paths.get(path);
		if(Files.exists(newFilePath,new LinkOption[]{ LinkOption.NOFOLLOW_LINKS})==false){//creo una cartella in locale sul client
			Files.createDirectories(newFilePath);
		}
	}


	public void Cleanup() throws IOException {
		this.toServer.close();
		this.fromServer.close();
	}

	//spostarsi tra le finestre: tornare indietro
	public void Back(JFrame old_finestra,JFrame current_finestra,String msg) {
		JOptionPane.showMessageDialog(null,msg);
		current_finestra.dispose();
		old_finestra.setVisible(true);
	}

	public void Go() {	
		JFrame finestra;
		JButton registra;
		JButton login;

		finestra = new JFrame("Avvio");
		finestra.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		finestra.setSize(300, 100);
		finestra.setLayout(new FlowLayout());

		JPanel pannello_avvio1 = new JPanel();
		finestra.setBounds(0,0,50,700);

		JLabel  namelabel= new JLabel("Username: ");
		pannello_avvio1.add(namelabel);
		JTextField userText = new JTextField(6);
		pannello_avvio1.add(userText);

		JPanel pannello_avvio2 = new JPanel();
		JLabel  passwordLabel = new JLabel("Password: ");
		pannello_avvio2.add(passwordLabel);
		JPasswordField passwordText = new JPasswordField(6);
		pannello_avvio2.add(passwordText);

		JPanel pannello_avvio3 = new JPanel();
		registra = new JButton("Registra");
		login = new JButton("Login");

		pannello_avvio3.add(registra);
		pannello_avvio3.add(login);	
		finestra.setLayout(new GridLayout(4,1,3,3)); 
		finestra.add(pannello_avvio1);
		finestra.add(pannello_avvio2);
		finestra.add(pannello_avvio3);
		finestra.pack();
		finestra.setVisible(true);

		//operazione di registrazione
		registra.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						if(userText.getText().length()==0 || passwordText.getPassword().length==0) {
							JOptionPane.showMessageDialog(null, "Campi vuoti non validi" );   	
							return ;
						}
						if(userText.getText().contains(" ")==true || passwordText.getPassword().toString().contains(" ")==true) {
							JOptionPane.showMessageDialog(null, "I campi non possono contenere spazi");   	
							return ;	
						}
						try {
							if(serverObject.registerUser(userText.getText(),new String(passwordText.getPassword()))==-1) {
								JOptionPane.showMessageDialog(null, "Username già esistente" );   
							}
							else {
								us_id=userText.getText();
								JOptionPane.showMessageDialog(null, "Registrazione eseguita con successo.");     
							}
						} catch (RemoteException e1) {
							JOptionPane.showMessageDialog(null, "Servizio non disponibile");     
						}
					}  
				}    
				);
		login.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						try {
							
							if(userText.getText().length()==0 || passwordText.getPassword().length==0) {
								JOptionPane.showMessageDialog(null, "Campi vuoti non validi" );   	
								return ;
							}
							if(userText.getText().contains(" ")==true || passwordText.getPassword().toString().contains(" ")==true) {
								JOptionPane.showMessageDialog(null, "I campi non possono contenere spazi");   	
								return ;	
							}
							String username= userText.getText();
							String password=new String(passwordText.getPassword());
					
							Socket socket = new Socket(ServerAddress, serverPort);//comunicazione client-thread worker
							Socket notify = new Socket(ServerAddress, serverPort_not);//thread per ascoltare varie notifiche
							toServer = new ObjectOutputStream(socket.getOutputStream());//stream per inviare al server richieste
							fromServer = new ObjectInputStream(socket.getInputStream());//stream per ricervere dal server risposte
							Message request = new Message();
							request.setOperation(0);
							request.setMessage(username+" "+password);
							toServer.writeObject(request);
							toServer.flush();
							Message reply = (Message) fromServer.readObject();
							//login con successo
							if(reply.getOperation()==1) {
								//apro il thread che rimane in ascolto sulle notifiche
								ListenNotify l=new ListenNotify(notify);
								Thread listener= new Thread(l);
								listener.start();
								us_id=userText.getText();
								JOptionPane.showMessageDialog(null, reply.getMessage());
								Message notification = (Message) fromServer.readObject();
								//invio eventuali inviti ricevuti mentre era offline
								String not ="";
								for(int i=0;i<notification.getSize();i++) {
									Message list = (Message) fromServer.readObject();
									not+=list.getMessage()+"\n";
								}
								if(not!="") {JOptionPane.showMessageDialog(null,not);}
								finestra.dispose();
								Operation(socket);
								return ;
							}
							else {
								JOptionPane.showMessageDialog(null,reply.getMessage());  
								Cleanup();
								socket.close();
								notify.close();
							}
						}
						catch(UnknownHostException ex) {
							JOptionPane.showMessageDialog(null,"Indirizzo Ip non determinato");  
							finestra.dispose();
							return;
						}
						catch(IOException e1){
							JOptionPane.showMessageDialog(null,"Errore");  
							finestra.dispose();
							return ;
						} catch (ClassNotFoundException e1) {
							JOptionPane.showMessageDialog(null,"Versione non compatibile");  
							finestra.dispose();
							return ;
						}
					}
				}
				);
	}


	//invio richiesta documenti
	public void RequestCreateDocument(JFrame finestra) {
		JFrame finestra_create = new JFrame("Creazione");
		finestra_create.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		finestra_create.setSize(600, 300);
		finestra_create.setLayout(new FlowLayout());
		JPanel pannello_create = new JPanel();
		JButton crea = new JButton("Crea");
		JButton indietro = new JButton("Indietro");
		JLabel  namelabel= new JLabel("Nome Documento: ");
		pannello_create.add(namelabel);
		JTextField docText = new JTextField(6);
		pannello_create.add(docText);
		pannello_create.add(crea);
		JLabel  sectionlabel= new JLabel("Numero Sezioni: ");
		pannello_create.add(sectionlabel);
		JTextField sectionText = new JTextField(6);
		pannello_create.add(sectionText);
		pannello_create.add(crea);
		pannello_create.add(indietro);
		finestra_create.add(pannello_create);
		finestra_create.setVisible(true);
		crea.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						String nomedoc= docText.getText();
						String sezioni= sectionText.getText();
						Message request = new Message();
						request.setOperation(1);
						request.setMessage(nomedoc+" "+sezioni);
						try {
							//invio richiesta
							toServer.writeObject(request);
							toServer.flush();
						} 
						catch (IOException e2) {
							Back(finestra,finestra_create,"Connessione scaduta");
							Go();
							return ;
						}
						try {
							//attesa risposta
							Message ack = (Message) fromServer.readObject();
							if(ack.getOperation()==1) {
								Back(finestra,finestra_create,ack.getMessage());
							}
							else {
								Back(finestra,finestra_create,ack.getMessage());
							}
						} catch (IOException | ClassNotFoundException e1) {
							Back(finestra,finestra_create,"Impossibile Creare il documento");
						}
					}
				}
				);
		indietro.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						Back(finestra,finestra_create,"Operazione Annullata");
					}
				}
				);
	}

	//invito membri ad un documemto
	public void RequestInviteMember(JFrame finestra) {
		JFrame finestra_invite = new JFrame("Invito");
		finestra_invite.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		finestra_invite.setSize(600, 100);
		finestra_invite.setLayout(new FlowLayout());
		JPanel pannello_invite = new JPanel();
		JPanel pannello_invite2 = new JPanel();
		JButton inv = new JButton("Invita");
		JButton indietro = new JButton("Indietro");
		JLabel  namelabel= new JLabel("Nome Documento: ");
		pannello_invite.add(namelabel);
		JTextField docText = new JTextField(6);
		pannello_invite.add(docText);
		JLabel  memberlabel= new JLabel("Nome membro: ");
		pannello_invite.add(memberlabel);
		JTextField memberText = new JTextField(6);
		pannello_invite.add(memberText);
		pannello_invite2.add(inv);
		pannello_invite2.add(indietro);
		finestra_invite.add(pannello_invite);
		finestra_invite.add(pannello_invite2);
		finestra_invite.setVisible(true);
		inv.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						String nomedoc= docText.getText();
						String member= memberText.getText();
						Message request = new Message();
						request.setOperation(2);
						request.setMessage(nomedoc+" "+member);
						try {
							//invio richiesta
							toServer.writeObject(request);
							toServer.flush();
						} catch (IOException e2) {
							Back(finestra,finestra_invite, "Connessione scaduta");
							Go();
							return ;
						}
						try {
							//attesa risposta
							Message ack =(Message) fromServer.readObject();
							Back(finestra,finestra_invite, ack.getMessage());
						}
						catch (IOException | ClassNotFoundException e1) {
							JOptionPane.showMessageDialog(null,"Operazione temporaneamente non disponibile");
							return;
						}
					}

				}
				);
		indietro.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						Back(finestra,finestra_invite,"Operazione Annullata");
					}
				}
				);
	}

	//richiesta di logout
	public int RequestLogout(JFrame finestra) {
		Message request = new Message();
		request.setOperation(8);
		try {
			toServer.writeObject(request);
			toServer.flush();
		} catch (IOException e2) {
			return -1;
		}
		try {
			Message ack = (Message)fromServer.readObject();
			JOptionPane.showMessageDialog(null,ack.getMessage());
			if(ack.getOperation()==1) {
				finestra.dispose();
				return 1;
			}
		}
		catch(Exception e1) {
			JOptionPane.showMessageDialog(null,"Errore");
		}
		return 0;
	}

	//invio richiesta lista documenti a cui si partecipa
	public int RequestListDocument(JFrame finestra) {
		Message request = new Message();
		request.setOperation(3);
		try {
			toServer.writeObject(request);
			toServer.flush();
		} catch (IOException e2) {
			JOptionPane.showMessageDialog(null, "Connessione scaduta");
			return -1;
		}
		String risultato = "";
		try {
			//attesa risposta dal server
			Message ack = (Message)fromServer.readObject();
			if(ack.getOperation()==1) {
				for(int i=0;i<ack.getSize();i++) {
					Message list = (Message)fromServer.readObject();
					risultato+=list.getMessage();
				}
				JOptionPane.showMessageDialog(null,risultato);
			}
			else {
				JOptionPane.showMessageDialog(null, "Nessun documento di cui fai parte");
			}
		}
		catch(Exception e1) {
			JOptionPane.showMessageDialog(null,"Errore");
			return -1;
		}
		return 1;
	}

	//invio richiesta di editing di una sezione
	public int RequestEdit(JFrame finestra, Socket s) {
		JFrame finestra_edit = new JFrame("Edit");
		finestra_edit.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		finestra_edit.setSize(800, 150);
		finestra_edit.setLayout(new FlowLayout());
		JPanel pannello_edit = new JPanel();
		JButton ed = new JButton("Edit");
		JButton indietro = new JButton("Indietro");	
		JLabel  namelabel_edit= new JLabel("Nome Documento: ");
		pannello_edit.add(namelabel_edit);
		JTextField docText_edit = new JTextField(6);
		pannello_edit.add(docText_edit);
		JLabel  sectionlabel_edit= new JLabel("Numero Sezione: ");
		pannello_edit.add(sectionlabel_edit);
		JTextField sectionText_edit = new JTextField(6);
		pannello_edit.add(sectionText_edit);
		JLabel  creator_edit= new JLabel("Creatore: ");
		pannello_edit.add(creator_edit);
		JTextField creatoreText_edit = new JTextField(6);
		pannello_edit.add(creatoreText_edit);
		pannello_edit.add(ed);
		pannello_edit.add(indietro);
		finestra_edit.add(pannello_edit);
		finestra_edit.setVisible(true);
		indietro.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						Back(finestra,finestra_edit,"Operazione Annullata");						
					}
				}
				);
		ed.addActionListener(
				new ActionListener() {
					String nomedoc;
					public void actionPerformed(ActionEvent e) {
						nomedoc= docText_edit.getText();
						String sezione= sectionText_edit.getText();
						String creatore= creatoreText_edit.getText();
						//invio richiesta di editing di una sezione
						Message request = new Message();
						request.setOperation(4);
						request.setMessage(nomedoc+" "+sezione+" "+creatore);
						try {
							toServer.writeObject(request);
							toServer.flush();
						} catch (IOException e2) {
							Back(finestra,finestra_edit,"Connessione scaduta");
							Go();
							return ;
						}
						try {
							//risposta dal server
							Message ack = (Message)fromServer.readObject();
							if(ack.getOperation()==1) {
								JOptionPane.showMessageDialog(null, "Accesso alla sezione");
								//apro un file in locale
								Path newFilePath = Paths.get(path+nomedoc+"_c"+sezione+".txt");
								if(Files.exists(newFilePath,new LinkOption[]{ LinkOption.NOFOLLOW_LINKS})==false){
									Files.createFile(newFilePath);
								}
								else {
									Files.delete(newFilePath);
									Files.createFile(newFilePath);
								}
								FileChannel outChannel = FileChannel.open(newFilePath,StandardOpenOption.WRITE);
								ByteBuffer buffer = ByteBuffer.allocate(ack.getSize()); 
								//salvo nel byte buffer il contenuto della sezione
								buffer.put( ack.getMessage().getBytes());
								buffer.flip();
								//scrivo il contenuto del byte buffer nel file
								while(buffer.hasRemaining()) {
									outChannel.write(buffer);
								}
								outChannel.close();
								finestra_edit.dispose();
								//attendo indirizzo per partecipare al gruppo multicast
								Message ack_group = (Message)fromServer.readObject();//indirizzo gruppo
								Message ack_group_port = (Message)fromServer.readObject();//porta gruppo
								JFrame finestra2=new JFrame("End-Edit");
								finestra2.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
								finestra2.setSize(20000, 20000);
								finestra2.setLayout(new FlowLayout());
								JPanel pannello2 = new JPanel();
								JButton end = new JButton("End-Edit");
								JButton annulla = new JButton("Annulla");

								JLabel  namelabel_end= new JLabel("Nome Documento: ");
								pannello2.add(namelabel_end);
								JTextField docText_end = new JTextField(6);
								pannello2.add(docText_end);
								JLabel  seclabel_end= new JLabel("Sezione: ");
								pannello2.add(seclabel_end);
								JTextField sectionText_end = new JTextField(6);
								pannello2.add(sectionText_end);
								pannello2.add(end);
								pannello2.add(annulla);
								JPanel pannello1 = new JPanel();
								JLabel  Messaggi= new JLabel("Messaggi: ");
								pannello1.add(Messaggi);
								JTextArea userText = new JTextArea(20,50);
								userText.setVisible(true);
								userText.setEditable(false);
								JScrollPane scrollPane = new JScrollPane(userText);
								scrollPane.setPreferredSize(new Dimension(400, 300));
								pannello1.add(scrollPane, BorderLayout.CENTER);
								JPanel pannello3 = new JPanel();
								JLabel  passwordLabel = new JLabel("Scrivi: ");
								pannello3.add(passwordLabel);
								JTextField passwordText = new JTextField(10);
								JButton send=new JButton("Invia");
								pannello3.add(passwordText);
								pannello3.add(send);
								if(ack_group.getOperation()!=-1) {
									finestra2.add(pannello1);
									finestra2.add(pannello3);
								}
								finestra2.add(pannello2);
								finestra2.pack();
								finestra2.setVisible(true);
								finestra2.setVisible(true);
								//inizializzazione chat multicast
								int clientPort = ack_group_port.getSize();
								InetAddress multicastGroup = null;
								try {
									multicastGroup = InetAddress.getByName(ack_group.getMessage());
								} catch (UnknownHostException e1) {
									JOptionPane.showMessageDialog(null, "Chat non disponibile");
								}
								//apro il socket multicast
								MulticastSocket socket = new MulticastSocket(clientPort);
								socket.setTimeToLive(1);//confinare il traffico
								//se la chat è disponibile crea il thread che sta in ascolto dei messaggi
								if(ack_group.getOperation()!=-1) {
									ChatWorker c=new ChatWorker(socket,multicastGroup,userText,us_id);
									Thread t=new Thread(c);
									t.start();
								}
								InetAddress multicastGrou = null;
								try {
									multicastGrou = InetAddress.getByName(ack_group.getMessage());
								} catch (UnknownHostException e2) {
									JOptionPane.showMessageDialog(null, "Chat non disponibile");
								}
								final InetAddress multicastGrou1=multicastGrou;
								//invio messaggio al gruppo
								send.addActionListener(
										new ActionListener() {
											public void actionPerformed(ActionEvent e) {

												String msgs= "["+us_id+"] "+passwordText.getText()+"\n";
												if(passwordText.getText().equals(us_id+"_exit")) {
													JOptionPane.showMessageDialog(null, "Non puoi inviare questo messaggio");
													return;
												}
												DatagramPacket multicastPacket;
												try {
													multicastPacket = new DatagramPacket(msgs.getBytes("UTF-8"), msgs.getBytes("UTF-8").length, multicastGrou1, clientPort);
													socket.send(multicastPacket); 
												} catch ( IOException e1) {
													JOptionPane.showMessageDialog(null, "Chat non disponibile");
												} 
											}
										}
										);
								//annullare l'operazione di edit
								annulla.addActionListener(
										new ActionListener() {
											public void actionPerformed(ActionEvent e) {
												Message reply = new Message();
												reply.setOperation(-1);
												try {
													toServer.writeObject(reply);
													Message ack1 = (Message)fromServer.readObject();
													JOptionPane.showMessageDialog(null, ack1.getMessage());
													if(ack_group.getOperation()!=-1) {
														String exit=us_id+"_exit";
														try {
															DatagramPacket multicastPacket;
															multicastPacket = new DatagramPacket(exit.getBytes("UTF-8"), exit.getBytes("UTF-8").length, multicastGrou1, clientPort);
															socket.send(multicastPacket); 											
															socket.leaveGroup(InetAddress.getByName(ack_group.getMessage()));
														} catch (IOException e4) {
															JOptionPane.showMessageDialog(null, "Errore improvviso: riprova");
															return;
														}
														socket.close();
													}
													finestra2.dispose();
													finestra_edit.dispose();
													finestra.setVisible(true);
												} catch (IOException e3) {
													JOptionPane.showMessageDialog(null, "Connessione scaduta");
													try {
														toServer.close();
														fromServer.close();	
														s.close();
														finestra2.dispose();
														finestra_edit.dispose();
														finestra.dispose();
														Go();
														return ;
													} catch (IOException e1) {
														finestra2.dispose();
														finestra_edit.dispose();
														finestra.dispose();
														Go();
													}
												} catch (ClassNotFoundException e1) {
													JOptionPane.showMessageDialog(null, "Versione non compatibile");
													try {
														toServer.close();
														fromServer.close();	
														s.close();
														Go();
														return ;
													} catch (IOException e2) {
													}
													return ;
												}
											}
										}
										);
								//operazione di end-edit
								end.addActionListener(
										new ActionListener
										() {
											public void actionPerformed(ActionEvent e) {
												String nomedoc_end= docText_end.getText();
												String sezione_end= sectionText_end.getText();
												try {
													if(Integer.parseInt(sezione_end)!=Integer.parseInt(sezione)) {
														JOptionPane.showMessageDialog(null, "Errore sezione errata");
														return ;
													}
												}
												catch(NumberFormatException n){
													JOptionPane.showMessageDialog(null, "Errore sezione errata");
													return ;	
												}
												Path newFilePath = Paths.get(path+nomedoc_end+"_c"+sezione_end+".txt");
												if(Files.exists(newFilePath,new LinkOption[]{ LinkOption.NOFOLLOW_LINKS})==false || nomedoc.equals(nomedoc_end)==false){
													JOptionPane.showMessageDialog(null, "Errore file non esistente");
													return ;
												}
												//prelevo il contenuto della sezione dal file locale
												FileChannel inChannel = null;
												try {
													inChannel = FileChannel.open(Paths.get(path+nomedoc_end+"_c"+sezione_end+".txt"));
												} catch (IOException e1) {
													JOptionPane.showMessageDialog(null, "Errore file non esistente");
													//gestione errore server
												} 
												long size = 0;
												//controllo la dimensione della sezione
												try {
													size = inChannel.size();
													if(size>100*Math.pow(2, 10)) {
														JOptionPane.showMessageDialog(null,"Dimensione massima superata");
														inChannel.close();
														return ;
													}
												} catch (IOException e1) {}
												//procedura di terminazione della chat multicast, ovvero lasciare il gruppo
												if(ack_group.getOperation()!=-1) {
													try {
														String exit=us_id+"_exit";
														DatagramPacket multicastPacket;
														multicastPacket = new DatagramPacket(exit.getBytes("UTF-8"), exit.getBytes("UTF-8").length, multicastGrou1, clientPort);
														socket.send(multicastPacket); 											
														socket.leaveGroup(InetAddress.getByName(ack_group.getMessage()));
													} catch (IOException e4) {
														JOptionPane.showMessageDialog(null, "Errore improvviso: riprova");
														return;
													}
													socket.close();
												}
												//invio la richiesta di end-edit
												Message reply = new Message();
												reply.setOperation(5);
												reply.setMessage(nomedoc_end+" "+sezione_end);
												try {
													toServer.writeObject(reply);
												} catch (IOException e3) {
													JOptionPane.showMessageDialog(null, "Connessione scaduta");
													try {
														toServer.close();
														fromServer.close();	
														finestra2.dispose();
														finestra.dispose();
														s.close();
														Go();
														return ;
													} catch (IOException e1) {}
												}
												ByteBuffer buffer = ByteBuffer.allocate((int) size);
												//sposto nel buffer il contenuto del file
												try {
													inChannel.read(buffer);
													inChannel.close();
												} catch (IOException e1) {
													return;
												} 
												buffer.flip();
												String contenuto="";
												while (buffer.hasRemaining()) {            
													contenuto+=(char) buffer.get();         
												}
												//invio sezione modificata
												Message reply1=new Message();
												reply1.setSize((int) size);
												reply1.setMessage(contenuto);
												try {
													toServer.writeObject(reply1);
												} catch (IOException e2) {
													try {
														finestra2.dispose();
														finestra.dispose();
														s.close();
														Go();
														return;
													} catch (IOException e1) {
														e1.printStackTrace();
													}
													return ;
												}
												try {
													//attesa risposta dal server
													Message ack1=(Message)fromServer.readObject();
													if(ack1.getOperation()==1) {
														JOptionPane.showMessageDialog(null, "Sezione salvata");
													}
													finestra2.dispose();
													finestra_edit.dispose();
													finestra.setVisible(true);
												} catch (IOException e1) {
												} catch (ClassNotFoundException e1) {
													JOptionPane.showMessageDialog(null, "Versione message.java non compatibile");
													try {
														fromServer.close();	
														s.close();
														Go();
														return ;
													}catch(IOException e2) {}
												}
											}
										}
										);
							}
							else {
								JOptionPane.showMessageDialog(null, ack.getMessage());
								return ;
							}
						} catch (IOException e1) {
							JOptionPane.showMessageDialog(null, "Sezione errata");
							return ;
						} catch (ClassNotFoundException e3) {
							JOptionPane.showMessageDialog(null, "Versione message.java non compatibile");
							return ;
						}
					}
				}
				);
		return 0;
	}
	
	//invio richiesta di visualizzare una sezione
	public void RequestShowSection(JFrame finestra) {
		JFrame finestra_show_sec = new JFrame("Show Section");
		finestra_show_sec.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		finestra_show_sec.setSize(800, 150);
		finestra_show_sec.setLayout(new FlowLayout());
		JPanel pannello_show_sec = new JPanel();
		JButton show = new JButton("Show");
		JButton indietro = new JButton("Indietro");		
		JLabel  namelabel= new JLabel("Nome Documento: ");
		pannello_show_sec.add(namelabel);
		JTextField docText = new JTextField(6);
		pannello_show_sec.add(docText);
		JLabel  sectionlabel= new JLabel("Numero Sezione: ");
		pannello_show_sec.add(sectionlabel);
		JTextField sectionText = new JTextField(6);
		pannello_show_sec.add(sectionText);
		JLabel  creatorlabel= new JLabel("Creatore: ");
		pannello_show_sec.add(creatorlabel);
		JTextField creatorText = new JTextField(6);
		pannello_show_sec.add(creatorText);
		pannello_show_sec.add(show);
		pannello_show_sec.add(indietro);
		finestra_show_sec.add(pannello_show_sec);
		finestra_show_sec.setVisible(true);
		indietro.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						Back(finestra,finestra_show_sec,"Operazione Annullata");
					}
				}
				);

		show.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						String nomedoc= docText.getText();
						String sezione= sectionText.getText();
						String creator= creatorText.getText();
						//invio richiesta
						Message request=new Message();
						request.setOperation(6);
						request.setMessage(nomedoc+" "+sezione+" "+creator);
						try {
							toServer.writeObject(request);
						} catch (IOException e2) {
							Back(finestra,finestra_show_sec,"Connessione scaduta");
							Go();
							return ;
						}
						try {
							//atesa risposta
							Message ack = (Message) fromServer.readObject();
							if(ack.getOperation()==1 || ack.getOperation()==3) {
								if(ack.getOperation()==1) {
									JOptionPane.showMessageDialog(null, "Sezione scaricata");
								}
								else
									JOptionPane.showMessageDialog(null, "Sezione scaricata già in editing");
								//apro un file in locale e scarico la sezione
								Path newFilePath = Paths.get(path+nomedoc+"_c"+sezione+".txt");
								if(Files.exists(newFilePath,new LinkOption[]{ LinkOption.NOFOLLOW_LINKS})==true){
									Files.delete(newFilePath);
								}
								Files.createFile(newFilePath);
								FileChannel outChannel = FileChannel.open(newFilePath,StandardOpenOption.WRITE);
								ByteBuffer buffer = ByteBuffer.allocate(ack.getSize()); 
								buffer.put( ack.getMessage().getBytes());
								buffer.flip();
								while(buffer.hasRemaining()) {
									outChannel.write(buffer);
								}
								outChannel.close();
								finestra_show_sec.dispose();
								finestra.setVisible(true);
							}
							else {
								Back(finestra,finestra_show_sec,ack.getMessage());
							}
						}
						catch (IOException e1) {
							Back(finestra,finestra_show_sec,"Connessione scaduta");
							Go();
							return ;
						} catch (ClassNotFoundException e1) {
							Back(finestra,finestra_show_sec,"Connessione scaduta");
							Go();
							return ;
						}
					}
				}
				);
	}

	//invio richiesta visualizza into documento
	public void RequestShowAll(JFrame finestra) {
		JFrame finestra_show_all = new JFrame("Show Document");
		finestra_show_all.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		finestra_show_all.setSize(700, 100);
		finestra_show_all.setLayout(new FlowLayout());
		JPanel pannello_show_all = new JPanel();
		JButton show = new JButton("Show");
		JButton indietro = new JButton("Indietro");

		JLabel  namelabel= new JLabel("Nome Documento: ");
		pannello_show_all.add(namelabel);

		JTextField docText = new JTextField(6);
		pannello_show_all.add(docText);

		JLabel  creatorlabel= new JLabel("Creatore : ");
		pannello_show_all.add(creatorlabel);

		JTextField creatorText = new JTextField(6);
		pannello_show_all.add(creatorText);

		pannello_show_all.add(show);
		pannello_show_all.add(indietro);

		finestra_show_all.add(pannello_show_all);
		finestra_show_all.setVisible(true);
		indietro.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						Back(finestra,finestra_show_all,"Operazione annullata");
					}
				}
				);

		show.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						String nomedoc= docText.getText();
						String creator= creatorText.getText();
						//invio richiesta
						Message request=new Message();
						request.setOperation(7);
						request.setMessage(nomedoc+" "+creator);
						try {
							toServer.writeObject(request);
						} catch (IOException e2) {
							Back(finestra,finestra_show_all,"Connessione scaduta");
							Go();
							return ;
						}
						try {
							//attesa risposta del server
							Message ack = (Message) fromServer.readObject();
							if(ack.getOperation()==1) {
								if(ack.getOperation()==1) {
									JOptionPane.showMessageDialog(null, "Documento scaricato");
								}
								//salvo l'intero documento in un file
								Path newFilePath = Paths.get(path+nomedoc+"_c"+".txt");
								if(Files.exists(newFilePath,new LinkOption[]{ LinkOption.NOFOLLOW_LINKS})==true){
									Files.delete(newFilePath);
								}
								Files.createFile(newFilePath);
								FileChannel outChannel = FileChannel.open(newFilePath,StandardOpenOption.WRITE);
								ByteBuffer buffer = ByteBuffer.allocate(ack.getSize()); 
								buffer.put( ack.getMessage().getBytes());
								buffer.flip();
								while(buffer.hasRemaining()) {
									outChannel.write(buffer);
								}
								outChannel.close();
								finestra_show_all.dispose();
								//ricevo informazioni sulle possibili sezioni in editing
								Message ack1 = (Message) fromServer.readObject();
								if(ack1.getMessage().equals("")) {JOptionPane.showMessageDialog(null, "Nessuna sezione in editing" );}
								else {JOptionPane.showMessageDialog(null, "Sezioni in editing sono: "+ack1.getMessage() );}	
								finestra_show_all.dispose();
								finestra.setVisible(true);
							}
							else {
								JOptionPane.showMessageDialog(null, "Documento non esistente");	
							}
						}
						catch (IOException e1) {
							Back(finestra,finestra_show_all,"Connessione scaduta");
							Go();
							return ;
						} catch (ClassNotFoundException e1) {
							Back(finestra,finestra_show_all,"Connessione scaduta");
							Go();
							return ;
						}
					}
				}
				);
	}


	public void Operation(Socket socket) throws IOException {
		Charset.defaultCharset();
		JFrame finestra = new JFrame("Menù");
		finestra.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		finestra.setSize(300, 400);
		finestra.setLayout(new FlowLayout());
		JPanel pannello2 = new JPanel();
		JPanel pannello3 = new JPanel();
		JPanel pannello4 = new JPanel();
		JPanel pannello5 = new JPanel();
		JPanel pannello6 = new JPanel();
		JPanel pannello7 = new JPanel();
		JPanel pannello8 = new JPanel();

		JButton creaDoc = new JButton("Crea Documento");
		JButton invita = new JButton("Invita membri");
		JButton listaDoc = new JButton("Lista Documenti");
		JButton edit = new JButton("Edit");
		JButton show_sec = new JButton("Show section");
		JButton show_all = new JButton("Show Document");
		JButton logout = new JButton("Logout");

		finestra.setBounds(0,0,500,300);
		finestra.setVisible(true);

		pannello2.add(creaDoc);
		pannello3.add(invita);
		pannello4.add(listaDoc);
		pannello5.add(edit);
		pannello6.add(show_sec);
		pannello7.add(show_all);
		pannello8.add(logout);

		finestra.add(pannello2);
		finestra.add(pannello3);
		finestra.add(pannello4);
		finestra.add(pannello5);
		finestra.add(pannello6);
		finestra.add(pannello7);
		finestra.add(pannello8);

		creaDoc.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						finestra.setVisible(false);
						RequestCreateDocument(finestra);
					}
				}
				);

		invita.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						finestra.setVisible(false);
						RequestInviteMember(finestra);
					}
				}
				);
		listaDoc.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						if(RequestListDocument(finestra)==-1) {
							try {
								socket.close();
								finestra.dispose();
								Go();
								return ;
							} catch (IOException e1) {
								JOptionPane.showMessageDialog(null, "Errore");	
								return;
							}
						}
					}
				}
				);
		edit.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						finestra.setVisible(false);
						RequestEdit(finestra,socket);

					}
				}
				);
		show_sec.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						finestra.setVisible(false);
						RequestShowSection(finestra);
					}
				}
				);
		show_all.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						finestra.setVisible(false);
						RequestShowAll(finestra);
					}
				}
				);
		logout.addActionListener(
				new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						if(RequestLogout(finestra)==1) {
							try {
								finestra.dispose();
								socket.close();
								Go();
								return ;
							} catch (IOException e1) {
								JOptionPane.showMessageDialog(null, "Errore");	
								return;
							}

						}
					}
				}
				);
	}
}



