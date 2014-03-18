import java.io.*;
import java.util.*;

public class peerProcess extends peerData {
	// bitfield representing file pieces owned by this peer
	// needs every relevant bit set to zero!
	// http://docs.oracle.com/cd/E16162_01/apirefs.1112/e17493/oracle/ide/util/BitField.html
	private BitSet internalBitfield = new BitSet();

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
	// check static requirement
	private static int peerID = 0;

	// dictionary for peerID::peerData mapping
	// used to store all peer-specific information
	// http://docs.oracle.com/javase/7/docs/api/java/util/Map.html
	private HashMap<Integer, peerData> peerDict = new HashMap<Integer, peerData>();

	// list of all peers known to have interesting pieces
	// http://docs.oracle.com/javase/7/docs/api/java/util/LinkedList.html
	private List<Integer> interestedList = new LinkedList<Integer>();

	// list of all peers known to be interested in our pieces
	private List<Integer> interestingList = new LinkedList<Integer>();

	// list of all peers sending us data
	private List<Integer> senderList = new LinkedList<Integer>(); 

	// list of all peers we're sending data to (preferred neighbors)
	private List<Integer> neighborList = new LinkedList<Integer>();

	// list of all requests for data in-flight
	private List requestsInFlight = new LinkedList();

	// THIS NEEDS SET ACCURATELY, DEAR GOD
	private int numberOfPieces = 0;
	
	//file within peer process directory ../peer [peerID]/
	private File f = new File("peer_"+String.valueOf(peerID));

	private File file = new File(f, "log_peer_["+String.valueOf(peerID)+"].log");

	public static void main(String [] args) {
		try {
    		peerID = Integer.parseInt(args[0]);
		} catch (IndexOutOfBoundsException e) {
    		System.out.println("Please pass in a PeerID!");
    		System.exit(1);
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

	private void initialize() {
		setupConstants();
		setupConnections();
		f.mkdirs();
		try {
			file.createNewFile();
		}
		catch (IOException e) {
			e.printStackTrace();		
		}
	}

	public void setupConstants() {

		File file = new File("Common.cfg");
		File file2 = new File("PeerInfo.cfg");
		try {
			Scanner scanner = new Scanner(file);
			scanner.next();
			numberOfPreferredNeighbors = scanner.nextInt();
			scanner.next();
			unchokingInterval = scanner.nextInt();
			scanner.next();
			optimisticUnchokingInterval = scanner.nextInt();
			scanner.next();
			fileName = scanner.next();
			scanner.next();
			fileSize = scanner.nextInt();
			scanner.next();
			pieceSize = scanner.nextInt();
			scanner.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		
		int peerID_Temp = 0;
		String host_Temp = null;
		int port_Temp = 0;
		int hasFile_Temp = 0;
		try {
			Scanner sc = new Scanner(file2);
			while (sc.hasNext()) {
				
				peerID_Temp = sc.nextInt();
				host_Temp = sc.next();
				port_Temp = sc.nextInt();
				hasFile_Temp = sc.nextInt();
				
				setupDirectory(peerID_Temp);
				setupLogFiles(peerID_Temp);
				
				peerData tempObject = new peerData(peerID_Temp, host_Temp,
													port_Temp, hasFile_Temp);
				this.peerDict.put(peerID_Temp, tempObject);
				sc.close();
			}
		}
		catch(FileNotFoundException e)
		{
			System.err.println("Error: " + e.getMessage());
			System.exit(1);
		
		}
	}
	
	private void setupDirectory(int ID) {
		File dir = new File("/peer_[ID]");
		dir.mkdir();
	}
	
	private void setupLogFiles(int ID) {
		try {
			FileWriter fstream = new FileWriter("log_peer_[ID].log");
			BufferedWriter out = new BufferedWriter(fstream);
			out.write("Log File for peer "+ID+" has been generated.");
			out.close();
		}
		catch(Exception e) {
			System.err.println("Error: " + e.getMessage());
		}
	}
	
	private void setupConnections() {
	
		//Fill in...
	
	}

	public void handleMessage(/*pass in message*/) {
	//actions in response to receiving message
	//unpack message and get msg type, here, assume type is int
		// STUBS FOR COMPILATION PURPOSES
		int messageType = 0;
		int interestStatus = 0;


		int senderPeerID = 0;
		int stub = 0;
		
		//msg_type = extract from message
		switch (messageType) {
			//bitfield
			case 1:	updateBitfield(senderPeerID /*Pass a bitfield as well*/);
				updateInteresting(senderPeerID);
				if (interestingList.contains(senderPeerID))
					sendInterested(senderPeerID);
				else
					sendNotInterested(senderPeerID);
				break;
			//choke
			case 2:	removeSender(senderPeerID);
				break;
			//unchoke
			case 3:	addSender(senderPeerID);
				sendRequest(senderPeerID);
				break;
			//interested
			case 4:	addInterested(senderPeerID);
				break;
			//not interested
			case 5:	removeInterested(senderPeerID);
			//have
			case 6:	updateBitfield(senderPeerID);
				// is this REALLY a case of updateInteresting?
				// it was "interestStatus = get_interest_status" before
				 updateInteresting(senderPeerID);
				switch (interestStatus) {
					//sender was interesting and still is
					case 1:	sendInterested(senderPeerID);
						break;
					//sender was not interesting and now is
					case 2:	sendInterested(senderPeerID);
						addInteresting(senderPeerID);
						break;
					//sender remains uninteresting
					case 3:	break;
					default: //exception
				}
				break;
			//request
			case 7:	sendPiece(senderPeerID /* fix later */);
				break;
			//piece
			case 8:	updateMyBitfield();
				sendHave();	//method will send to all peers
				peerDict.get(senderPeerID).messagesSinceLastRound++;
				updateInteresting(senderPeerID);
				removeRequestsInFlight(senderPeerID);
				checkCompletion();
				break;
			default://exception
		}
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

	public void addSender(int sPeerID)
	{
		senderList.add(sPeerID);
	}


	public void removeSender(int sPeerID)
	{
		senderList.remove(sPeerID);
	}
	
	public void updateBitfield(int senderPeerID) {
		//work
	}
	
	public void updateMyBitfield() {
		//work
	}
	
	public void updateInteresting(int senderPeerID /*sender's bitfield*/) {
		BitSet theirs = this.peerDict.get(senderPeerID).bitfield;
		//compare personal bitfield and sender's bitfield
		//set sender's interesting variable to true or false
	}

	private void sendChoke(int localPID)
	{
		// send the choke message
		this.neighborList.remove(localPID);
	}

	private void sendUnchoke(int localPID)
	{
		// send the unChoke message
		this.neighborList.add(localPID);
	}

	private void sendInterested(int localPID)
	{
		// send the interested message
		this.updateInteresting(localPID);
	}

	private void sendNotInterested(int localPID)
	{
		// send the notInterested message
		this.interestedList.remove(localPID);
	}

	private void sendHave()
	{
		// send the have message
	}

	private void sendBitfield(int localPID)
	{
		// send the bitfield
	}

	private void sendRequest(int localPID)
	{
		// send the request
		// add request to 'requests in flight' list
	}

	private void sendPiece(int localPID)
	{
		// send the piece

	}

	private void sendHandshake(int localPID)
	{
		peerData temp = peerDict.get(localPID);
		temp.initiatedHandshake = true;
		// send the handshake
	}

	private void removeRequestsInFlight(int localPID)
	{
		// note: requestsInFlight does not yet support timing;
		// need a new class to store that encapsulates PID/time.
		this.requestsInFlight.remove(localPID);
	}

	private void checkCompletion()
	{
		// less dark bit-wise magic than before
		// if NOT(bitfield) has no 1's, then bitfield has no 0's
		int tempSize = this.internalBitfield.size();
		BitSet tempBitfield = new BitSet(tempSize);
		tempBitfield = (BitSet) internalBitfield.clone();
		tempBitfield.flip(0, tempSize-1);
		if(tempBitfield.isEmpty() && tempBitfield.size() == this.numberOfPieces)
		{
			this.fileComplete = true;
		}else{
			this.fileComplete = false;
		}
	}


	private void log(int peerID, int localPID, int event, int piece) {
		//write to logfile
		String msg;
		String lPID = String.valueOf(localPID);
		String myID = String.valueOf(peerID);
		GregorianCalendar cal = new GregorianCalendar();
		
		try {
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));

			switch (event) {
				case 1:	msg = "["+String.valueOf(cal.get(Calendar.HOUR))+":"+String.valueOf(cal.get(Calendar.MINUTE))+":"+String.valueOf(cal.get(Calendar.SECOND))+"]: "+"Peer ["+myID+"] makes a connection to Peer ["+lPID+"].";
					break;
				case 2: msg =  "["+String.valueOf(cal.get(Calendar.HOUR))+":"+String.valueOf(cal.get(Calendar.MINUTE))+":"+String.valueOf(cal.get(Calendar.SECOND))+"]: "+"Peer ["+myID+"] is connected from ["+lPID+"].";
					break;
				case 3:	msg =  "["+time()+"]: "+"Peer ["+myID+"] has the preferred neighbors ["+neighborList+"].";
					break;
				case 4:	msg =  "["+time()+"]: "+"Peer ["+myID+"] has the optimistically-unchoked neighbor ["+lPID+"].";
					break;
				case 5:	msg = "["+time()+"]: "+"Peer ["+myID+"] is unchoked by ["+lPID+"].";
					break;
				case 6:	msg = "["+time()+"]: "+"Peer ["+myID+"] is choked by ["+lPID+"]."; 
					break;
				case 7: msg = "["+time()+"]: "+"Peer ["+myID+"] received a 'have' message from ["+lPID+"] for the piece["+String.valueOf(piece)+"].";
					break;
				case 8:	msg = "["+time()+"]: "+"Peer ["+myID+"] receive an 'interested' message from ["+lPID+"].";
					break;      	
				case 9:	msg = "["+time()+"]: "+"Peer ["+myID+"] received a 'not interested' message from ["+lPID+"].";
					break;
				case 10: msg = "["+time()+"]: "+"Peer ["+myID+"] has downloaded the piece ["+String.valueOf(piece)+"] from ["+lPID+"].";
					break;
				default: msg = "-1";//exception
			}
			bw.write(msg);
			bw.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
			
	}

	/*time method gets compilation error when called
	'error: non-static method time() cannot be referenced from a static context'
	if used in log() like--> String msg = "["+time()+"]";*/

	private String time() {
		GregorianCalendar cal = new GregorianCalendar();
		return String.valueOf(cal.get(Calendar.HOUR))+":"+String.valueOf(cal.get(Calendar.MINUTE))+":"+String.valueOf	(cal.get(Calendar.SECOND));
	}
}
