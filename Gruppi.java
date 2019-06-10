//classe che descrive i gruppi
public class Gruppi {
  public String indirizzo;//indirizzo multicast
  public String nomedoc;//nome documento in editing a cui è assegnato l'indirizzo
  public int port;//porta multicast
  public int n_utenti;//numero utenti che stanno editando la sezione
  public int uso;//indirizzo attivo oppure no
	
  Gruppi(String indirizzo,int port){
    this.indirizzo=indirizzo;
    this.port=port;
    this.n_utenti=0;
    this.uso=0;
  }

}
