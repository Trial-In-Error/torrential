import java.util.*;
import java.io.*;


public class peerData {
	public BitSet bitfield;
	public int piecesSinceLastRound;
	//public Socket socket;
	public DataInputStream inboundStream;
	public DataOutputStream outboundStream;
	public int portNumber;
	public String hostName;
	public boolean initiatedHandshake;
	
	// these are likely duplicated elsewhere or unnecessary,
	// included here and now for completeness
	public int ID;
	public int hasFile;
	public boolean isInterested;
	public boolean isInteresting;
	public boolean isChoked; //== isSend-ee
	public boolean isOptimisticallyUnchoked;
	public boolean isSender;
	
	public peerData(int peerID, String hostName, int portNumber, int hasFile) {
		this.ID = peerID;
		this.hostName = hostName;
		this.portNumber = portNumber;
		this.hasFile = hasFile;
		this.isInterested = false;
		this.isInteresting = false;
		this.isChoked = true;
		this.isOptimisticallyUnchoked = false;
		this.isSender = false;
		this.initiatedHandshake = false;
		this.piecesSinceLastRound = 0;
		this.bitfield = new BitSet();
	}
	
	public peerData() {
		this.ID = 0;
		this.hostName = null;
		this.portNumber = 0;
		this.hasFile = 0;
		this.isInterested = false;
		this.isInteresting = false;
		this.isChoked = true;
		this.isOptimisticallyUnchoked = false;
		this.isSender = false;
		this.initiatedHandshake = false;
		this.piecesSinceLastRound = 0;
		this.bitfield = new BitSet();
		
		
	}
	
	
}