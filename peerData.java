public class peerData {
	public Bitfield bitfield = new Bitfield();
	public int messagesSinceLastRound;
	public Socket socket;
	public int portNumber;
	public String hostName;
	
	// these are likely duplicated elsewhere or unnecessary,
	// included here and now for completeness
	public int ID;
	public int hasFile;
	public boolean isInterested;
	public boolean isInteresting;
	public boolean isChoked; //== isSend-ee
	public boolean isOptimisticallyUnchoked;
	public boolean isSender;
	
	public peerData(peerID, hostName, portNumber, hasFile) {
		this.ID = peerID;
		this.hostName = hostName;
		this.portNumber = portNumber;
		this.hasFile = hasFile;
	}
	
	
}