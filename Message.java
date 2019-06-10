import java.io.Serializable;

public class Message implements Serializable {
	private static final long serialVersionUID = 5950169519310163575L;
	private int operation;
	private String message;
	private int size;
	
	public Message() {
		
	}
	
	public int getOperation() {
		return this.operation;
	}
	public void setOperation(int op) {
		this.operation = op;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public void setSize(int sz) {
		this.size = sz;
	}
	
	public int getSize() {
		return this.size;
	}

	public String toString() {
		return "Operation = " + getOperation() + " ; Message = " + getMessage();
	}
}