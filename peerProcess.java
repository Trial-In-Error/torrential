import java.io.*;
import java.util.*;
import java.net.*;



/* Important notes & things to do:
		1. Remove the entry for "ourself" from the peerDict
		2. setupConnections => needs to initiate handshakes properly
		3. setupConnections => client side =>
			DO WE USE OUR PORT OR THEIRS? ASSUMING THEIRS!
*/

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
	
	//byte representation of file
	private byte[][] pieces;

	// THESE NEEDS SET ACCURATELY, DEAR GOD
	private int numberOfPieces = 0;
	private int portNumber = 0;
	
	//file within peer process directory ../peer [peerID]/
	private File f = new File("peer_"+String.valueOf(peerID));

	private File file = new File(f, "log_peer_["+String.valueOf(peerID)+"].log");

	public static void main(String [] args) {
		try {
    		peerID = Integer.parseInt(args[0]);
		} catch (IndexOutOfBoundsException e) {
    		System.out.println("Please pass in a PeerID!");
    		//System.exit(1);
		}
		/*initialize();
		while(true){
			//for each peer:
			//implement as for-each loop on peerDict
				//http://www.javapractices.com/topic/TopicAction.do?Id=196
			//for(PeerData peer : peerDict){};
				handle_message();
		}*/
		
		//need to place the class elsewhere to run the rest of the class methods while doing this 

		/*class unchoking extends TimerTask {

			public void run() {
				//analyze rate of transmission from each preferred neighbor and choke/unchoke appropriately
				log(-1, 3, -1);
			}
		}
		Timer timer = new Timer();
		timer.schedule(new unchoking(), 0, 2*1000);
		timer.cancel();*/
		
		//need to place elsewhere so that both unchoking() and optimisticUnchoking() can both work
		/*class optimisticUnchoking extends TimerTask {
			public void run() {
				//unchoke optimistically
				log(-1, 4, -1);
			}
		}
		Timer optimisticTimer = new Timer();
		optimisticTimer.schedule(new optimisticUnchoking(), 0, 5*1000);
		optimisticTimer.cancel(); */
		
		//do timer.cancel() wherever you end peerProcess or another method	
		
		
	}

	private void initialize() {
		setupConstants();
		setupConnections();
		setupPieces();
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
				
				if(peerID_Temp != this.peerID)
				{
					peerData tempObject = new peerData(peerID_Temp, host_Temp,
						port_Temp, hasFile_Temp);
					this.peerDict.put(peerID_Temp, tempObject);
				}else{
					this.portNumber = port_Temp;
				}


				//shouldn't the close be outside this pair of braces? like, down a line?
			
			}
			sc.close();
		}
		catch(FileNotFoundException e)
		{
			System.err.println("Error: " + e.getMessage());
			System.exit(1);
		
		}
	}
	
	private void setupPieces() {
		pieces = new byte[numberOfPieces][pieceSize + 4];
		File tempFile = new File(fileName);
		try {
			FileInputStream fs = new FileInputStream(tempFile);
			byte count = 0;
			for( int i = 0; i<numberOfPieces;i++) {
				//set index, byte has range 127 to -128 so each byte for index will only count up to 127 then the next byte will start counting from 0 to 127, this way gives limited index  up to 508 if each byte index is added together, up to 508 is sufficient for our purposes as our file only has 306 pieces
				if(i==128 || i == 255 || i == 382 || i == 509)
					count = 0;
				if(i < 128)	
					pieces[i][0] = count++;
				else if(i<255)  
					pieces[i][1] = count++;
				else if (i < 382)
					pieces[i][2] = count++;
				else 
					pieces[i][3] = count++;
					
				for(int j = 0; j<pieceSize; j++) {
					//fill in bytes of the piece
						//fs.read() will return -1 if reach end of file
						pieces[i][j+4] = (byte)fs.read();
				}
			}
			fs.close();
		}
		catch( Exception e) {
			e.printStackTrace();
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
		// for each peer in the dictionary...
		for(Map.Entry<Integer, peerData> entry: peerDict.entrySet())
		{
			peerData peer = entry.getValue();
			// if we appear first, then we must listen (server)
			if(this.peerID < peer.ID)
			{
				try {
					// Create a server socket
					ServerSocket serverSocket = new ServerSocket(this.portNumber);
					// Listen for a connection request
					Socket socket = serverSocket.accept();
					// Create input/output data streams
					DataInputStream inboundStream = new DataInputStream(socket.getInputStream());
					DataOutputStream outboundStream = new DataOutputStream(socket.getOutputStream());
					// Pack the peerData object with the new data streams
					peer.inboundStream = inboundStream;
					peer.outboundStream = outboundStream;
				}
				catch(IOException ex) {
					System.err.println(ex);
				}
			}
			//if we appear second, then we must send (client)
			else if(this.peerID > peer.ID)
			{
				try {
					// Create a client socket
					// DO WE USE OUR PORT OR THEIRS? ASSUMING THEIRS!
					Socket socket = new Socket(peer.hostName, peer.portNumber);
					// Create input/output data streams
					DataInputStream inboundStream = new DataInputStream(socket.getInputStream());
					DataOutputStream outboundStream = new DataOutputStream(socket.getOutputStream());
					// Pack the peerData object with the new data streams
					peer.inboundStream = inboundStream;
					peer.outboundStream = outboundStream;
					// INITIATE THE HANDSHAKE!!
					this.sendHandshake(peer.ID);
				}
				catch(IOException ex) {
					System.err.println(ex);
				}
			}
			// otherwise, it's the entry for us - oops - this shouldn't exist
			else
			{
				// raise an exception?
				return;
			}
		}
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
			//handshake
			case 0:
				if(peerDict.get(senderPeerID).initiatedHandshake)
				{
					sendBitfield(senderPeerID);
				}else{
					sendHandshake(senderPeerID);
				}
				break;
			//bitfield
			case 1:	updateBitfield(senderPeerID /*Pass a bitfield as well*/);
				updateInteresting(senderPeerID);
				if (interestingList.contains(senderPeerID))
					sendInterested(senderPeerID);
				else
					sendNotInterested(senderPeerID);
				break;
			//choke
			case 2:	log(senderPeerID, 6, -1);
				removeSender(senderPeerID);
				break;
			//unchoke
			case 3:	log(senderPeerID, 5, -1);
				addSender(senderPeerID);
				sendRequest(senderPeerID);
				break;
			//interested
			case 4:	log(senderPeerID, 8, -1);
				addInterested(senderPeerID);
				break;
			//not interested
			case 5:	log(senderPeerID, 9, -1);
				removeInterested(senderPeerID);
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
		peerData temp = peerDict.get(localPID);
		
		byte[] b = new byte[]{0,0,0,1,0};
		try {
			temp.outboundStream.write(b, 0, b.length);
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void sendUnchoke(int localPID)
	{
		// send the unChoke message
		this.neighborList.add(localPID);
		peerData temp = peerDict.get(localPID);
		
		byte[] b = new byte[]{0,0,0,1,1};
		
		try {
			temp.outboundStream.write(b, 0, b.length);
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void sendInterested(int localPID)
	{
		// send the interested message
		this.updateInteresting(localPID);
		peerData temp = peerDict.get(localPID);
		
		byte[] b = new byte[]{0,0,0,1,2};
		try {
			temp.outboundStream.write(b, 0, b.length);
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}

	private void sendNotInterested(int localPID)
	{
		// send the notInterested message
		this.interestedList.remove(localPID);
		peerData temp = peerDict.get(localPID);
		
		byte[] b = new byte[]{0,0,0,1,3};
		try {
			temp.outboundStream.write(b, 0, b.length);
		}
		catch(IOException e) {
			e.printStackTrace();
		}
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
		byte[] b = new byte[32];
		String str = "HELLO"+"00000000000000000000000";
		String str2 = String.valueOf(peerID);
		while(str.length() != 4) {
			str2 = "0"+str2;
		}
		str = str+str2;
		b = str.getBytes();
		try {
			temp.outboundStream.write(b, 0, b.length);
		}
		catch(IOException e) {
			e.printStackTrace();
		}
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


	private void log(int localPID, int event, int piece) {
		//write to logfile
		String msg;
		String lPID = String.valueOf(localPID);
		String myID = String.valueOf(peerID);
		GregorianCalendar cal = new GregorianCalendar();
		
		try {
			
			BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));

			switch (event) {
				case 1:	msg = "["+time()+"]: "+"Peer ["+myID+"] makes a connection to Peer ["+lPID+"].";
					break;
				case 2: msg =  "["+time()+"]: "+"Peer ["+myID+"] is connected from ["+lPID+"].";
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
