import java.io.*;
import java.util.Scanner;

public class peerProcess {
	// bitfield representing file pieces owned by this peer
	// needs every relevant bit set to zero!
	// http://docs.oracle.com/cd/E16162_01/apirefs.1112/e17493/oracle/ide/util/BitField.html
	private Bitfield internalBitfield = new Bitfield();

	// does this peer have the file completed?
	// needs set to true in the true case in setup!
	private boolean fileComplete = false;

	// send data to (numberOfPreferredNeighbors + 1) because of optimistic unchoke
	// needs set to value from Common.cfg!
	private int numberOfPreferredNeighbors = 0;

	// how many seconds between readjusting who you prefer to send messages to
	// needs set to value from Common.cfg!
	private int unchokingInterval = 100;

	// how many seconds before readjusting who you are optimistically sending messages to
	// needs set to value from Common.cfg!
	private int optimisticUnchokingInterval = 100;

	// name of file to be sent / received
	// needs set to value from Common.cfg!
	private String fileName = "";

	// size of file to be sent / received, in bytes
	// needs set to value from Common.cfg!
	private int fileSize = 0;

	// size of a piece (transmission segment, before being wrapped with headers), in bytes
	// needs set to value from Common.cfg!
	private int pieceSize = 0;

	// identifier for this peer; found in PeerInfo.cfg
	// needs set to correct value!
	private int peerID = 0;

	// dictionary for peerID::peerData mapping
	// used to store all peer-specific information
	// http://docs.oracle.com/javase/7/docs/api/java/util/Map.html
	private Map peerDict = new Map();

	// list of all peers known to have interesting pieces
	// http://docs.oracle.com/javase/7/docs/api/java/util/LinkedList.html
	private LinkedList interestedList = new LinkedList();

	// list of all peers known to be interested in our pieces
	private LinkedList interestingList = new LinkedList();

	// list of all peers sending us data
	private LinkedList senderList = new LinkedList();

	// list of all peers we're sending data to (preferred neighbors)
	private LinkedList neighborList = new LinkedList();

	// list of all requests for data in-flight
	private LinkedList RequestsInFlight = new LinkedList();

	public static void main(String [] args) {
		try {
    		args.get(0);
		} catch (IndexOutOfBoundsException e) {
    		print("Please pass in a PeerID!");
    		exit(1);
		}
		/*initialize();
		while(true){
			//for each peer:
			//implement as for-each loop on peerDict
				//http://www.javapractices.com/topic/TopicAction.do?Id=196
			//for(PeerData peer : peerDict){};
				handle_message();
		}*/
	}

	private void 

	public void initialize(int peerID) {

		File file = new File("Common.cfg");
		File file = new File("PeerInfo.cfg");
		try {
			Scanner scanner = new Scanner(file);
			
			scanner.next();
			numberOfPrefferedNeighbors = scanner.next();
			scanner.next();
			unchokingInterval = scanner.next();
			scanner.next();
			optimisticUnchokingInterval = scanner.next();
			scanner.next();
			fileName = scanner.next();
			scanner.next();
			fileSize = scanner.next();
			scanner.next();
			pieceSize = scanner.next();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		scanner.close();
		
		int peerID_Temp = 0;
		String host_Temp = null;
		int port_Temp = 0;
		int hasFile_Temp = 0;
		int i = 0;
		
		Scanner sc = new Scanner(file2);
		try {
			while (sc.hasNext()) {
				
				peerID_Temp = scanner.next();
				host_Temp = scanner.next();
				port_Temp = scanner.next();
				hasFile_Temp = scanner.next();
				
				peerDict.put(peerID_Temp, peerData(peerID_Temp, host_Temp,
													port_Temp, hasFile_Temp));
													
				
			
		
			}
		}
		catch {
			e.printStackTrace();
		}
		sc.close();
		
	}

	public void handleMessage(/*pass in message*/) {
	//actions in response to receiving message
	//unpack message and get msg type, here, assume type is int
	
	int messageType, interestStatus;
	//msg_type = extract from message
	switch (messageType) {
		//bitfield
		case 1:	updateBitfield(/*sender's peerID*/);
			updateInteresting(/*something*/);
			if (getInteresting(/*sender's peerID*/) 
				sendInterested();
			else
				sendNotInterested();
			break;
		//choke
		case 2:	removeSender();
			break;
		//unchoke
		case 3:	addSender();
			sendRequest(/*sender's peerID*/);
			break;
		//interested
		case 4:	addInterested();
			break;
		//not interested
		case 5:	removeInterested();
		//have
		case 6:	updateBitfield(/*sender's peerID*/);
			interestStatus = get_interest_status(); //
			switch (interestStatus) {
				//sender was interesting and still is
				case 1:	sendInterested();
					break;
				//sender was not interesting and now is
				case 2:	sendInterested();
					addInteresting();
					break;
				//sender remains uninteresting
				case 3:	break;
				default: //exception
			}
			break;
		//request
		case 7:	sendPiece(/*piece index*/);
			break;
		//piece
		case 8:	updateMyBitfield();
			sendHave();	//method will send to all peers
			incMsgReceived();
			updateInteresting();
			removeRequestsInFlight();
			checkCompletion();
			break;
		default://exception
	}

	private void addInterested(int localPeerID)
	{
		interestedList.add(localPeerID);
	}

	private void removeInterested(int localPeerID)
	{
		interestedList.remove(localPeerID);
	}

	private void addInteresting(int localPeerID)
	{
		interestingList.add(localPeerID);
	}

	private void removeInteresting(int localPeerID)
	{
		interestingList.remove(localPeerID);
	}

	public void handleMessage(/*pass in message*/) {
		//actions in response to receiving message
		//unpack message and get msg type, here, assume type is int
		
		int messageType, interestStatus;
		//messageType = extract from message
		switch (messageType) {
			//bitfield
			case 1:	updateBitfield(/*sender's peerID*/);
				updateInteresting(/*something*/);
				if (/*huh?*/) 
					sendInterested();
				else
					sendNotInterested();
				break;
			//choke
			case 2:	removeSender();
				break;
			//unchoke
			case 3:	addSender();
				sendRequest(/*sender's peerID*/);
				break;
			//interested
			case 4:	addInterested();
				break;
			//not interested
			case 5:	removeInterested();
			//have
			case 6:	updateBitfield(/*sender's peerID*/);
				interestStatus = get_interest_status(); //
				switch (interestStatus) {
					//sender was interesting and still is
					case 1:	sendInterested();
						break;
					//sender was not interesting and now is
					case 2:	sendInterested();
						addInteresting();
						break;
					//sender remains uninteresting
					case 3:	break;
					default: //exception
				}
				break;
			//request
			case 7:	sendPiece(/*piece index*/);
				break;
			//piece
			case 8:	updateMyBitfield();
				sendHave();	//method will send to all peers
				incMsgReceived();
				updateInteresting();
				removeRequestsInFlight();
				checkCompletion();
				break;
			default://exception
		}
	}//end handle_message
}
