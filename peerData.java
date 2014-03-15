public class peerData {
	public Bitfield bitfield = new Bitfield();
	int messagesSinceLastRound;
	Socket socket;
	
	// these are likely duplicated elsewhere or unnecessary,
	// included here and now for completeness
	public int ID;
	boolean isInterested;
	boolean isInteresting;
	boolean isChoked; //== isSend-ee
	boolean isOptimisticallyUnchoked;
	boolean isSender;
}