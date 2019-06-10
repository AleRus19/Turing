import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DocumentData {
	public String creator;
	public List<String> Collaborators;
	public List<List<String>> synchAspiranti;
	
	private ReentrantReadWriteLock readWriteLock = new
			ReentrantReadWriteLock(); 
	public Lock read; 
	public Lock write; 
	public int num_sez;
	public List<String> section;
	public List<ReentrantLock> lock_editing;
    public List<Integer> sec_editing;
	public List<ReentrantLock> lock_sect;
    
	DocumentData(String creator,int sezioni){
	 
	  this.Collaborators=new ArrayList<String>();
	  read = readWriteLock.readLock();
	  write = readWriteLock.writeLock();
	  this.sec_editing=new ArrayList<Integer>(sezioni);
	  this.synchAspiranti=Collections.synchronizedList(new ArrayList<List<String>>());
	  for(int i=0;i<sezioni;i++) {
		this.synchAspiranti.add(Collections.synchronizedList(new ArrayList<String>()));  
	  }
	  this.creator=creator;	
	  this.num_sez=sezioni;
      this.section=new ArrayList<String>(this.num_sez);	  
      this.lock_sect=new ArrayList<ReentrantLock>(this.num_sez);
      this.lock_editing=new ArrayList<ReentrantLock>(this.num_sez);
      
      for(int i=0;i<this.num_sez;i++) {
        this.lock_sect.add(new ReentrantLock());	  
      }
      for(int i=0;i<this.num_sez;i++) {
          this.sec_editing.add(0);	  
        }
      for(int i=0;i<this.num_sez;i++) {
          this.lock_editing.add(new ReentrantLock());	  
      }
  }

}

