import java.io.*;
import java.nio.*;
import java.util.*;
import java.net.*;
import java.lang.*;

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
	private File downloadFile;
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
	private File f;

	private File file;

	private BufferedWriter bw;

	public static void main(String [] args) {
		peerProcess localPeer = new peerProcess();
		try {
    		localPeer.peerID = Integer.parseInt(args[0]);
 //   		localPeer.f = new File("peer_"+String.valueOf(localPeer.peerID));
  //  		localPeer.file = new File(localPeer.f, "log_peer_["+String.valueOf(localPeer.peerID)+"].log");
    		//peerID = localPeer.peerID;
		} catch (IndexOutOfBoundsException e) {
    		System.out.println("Please pass in a PeerID!");
    		System.exit(1);
		}
		localPeer.initialize();
		long timeUnchoke = System.currentTimeMillis();
		long timeOp = System.currentTimeMillis();
		while(true){

			for (Map.Entry<Integer, peerData> entry : localPeer.peerDict.entrySet()) {
				// construct a message from the byte stream coming in, then pass it to handleMessage
				localPeer.buildMessage(entry.getValue());
				//System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
			}
			
			if (System.currentTimeMillis() > timeUnchoke + 1000*localPeer.unchokingInterval) {
				
				localPeer.unchokingUpdate();
				timeUnchoke = System.currentTimeMillis();
			}
			
			if (System.currentTimeMillis() > timeOp + 1000*localPeer.optimisticUnchokingInterval) {
			
				localPeer.optimisticUnchokingUpdate();
				timeOp = System.currentTimeMillis();
			}
		}		
	}

	private void buildMessage(peerData entry) {
		try{
			if(entry.inboundStream.available() > 0)
			{
				System.out.println("Bytes waiting to be processed: "+entry.inboundStream.available());
			}
			if(entry.inboundStream.available() >= 5)
			{
				byte[] temp = new byte[5];		//message length and type
				byte[] payload = new byte[pieceSize+4];	
				entry.inboundStream.read(temp, 0, 5);
				for(int i = 0; i < 5; i++){
					System.out.println(temp[i]+",   "+(char)temp[i]);
				}
				if(temp[0] == (byte)'H' && temp[1] == (byte)'E' && temp[2] == (byte)'L'
					&& temp[3] == (byte)'L' && temp[4] == (byte) 'O'){
					// note: ID is calculated from socket, NOT from the actual message
					// the message's ID field can be bogus...
					System.out.println("This here's a handshake we just received.");
					byte[] garbage = new byte[27];
					entry.inboundStream.read(garbage, 0, 27);
					handleMessage(8, entry.ID, null);
				} else {
					ByteBuffer buf = ByteBuffer.wrap(temp);
					int tempLength = buf.getInt(0);
					if(tempLength > 1){
						entry.inboundStream.read(payload, 0, tempLength-1);
					} else {
						//compile time error!
						payload = null;
					}
					int tempType = (int)temp[4];
					System.out.println("Just received a message of type "+tempType+" and length "+tempLength);
					if(payload!=null)
					{
						System.out.println("Payload length: "+payload.length);
						System.out.print("[");
						for(int i = 0; i < payload.length; i++)
						{
							if(true)
							{
								System.out.print(payload[i]);
								System.out.print(", ");
							}
						}
						System.out.print("]\n");
						
					}
					handleMessage(tempType, entry.ID, payload);
				}
			}
		} catch( Exception e) {
			System.out.println("Error in build message; maybe stream writing to null byte[] buffer?");
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private void initialize() {
		this.setupConstants();
		this.setupConnections();
		this.setupPieces();
		//this.f.mkdirs();
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
			//e.printStackTrace();
			System.out.println("Error in setupConstants; couldn't read config files.");
			System.exit(-1);
		}
		this.numberOfPieces = (int)(this.fileSize / this.pieceSize) + 1;
		
		int peerID_Temp = this.peerID;
		String host_Temp = null;
		int port_Temp = 0;
		int hasFile_Temp = 0;
		setupDirectory(peerID_Temp);
		setupLogFiles(peerID_Temp);
		try {
			Scanner sc = new Scanner(file2);
			while (sc.hasNext()) {
				peerID_Temp = sc.nextInt();
				host_Temp = sc.next();
				port_Temp = sc.nextInt();
				hasFile_Temp = sc.nextInt();
				
				if(peerID_Temp != this.peerID)
				{
					peerData tempObject = new peerData(peerID_Temp, host_Temp,
						port_Temp, hasFile_Temp);
					System.out.println("Made a new peerData object.");
					System.out.println("\tPeerID: "+peerID_Temp);
					System.out.println("\tHostname: "+host_Temp);
					System.out.println("\tPortNumber: "+port_Temp);
					System.out.println("\thasFile: "+hasFile_Temp);
					this.peerDict.put(peerID_Temp, tempObject);
				}else{
					this.portNumber = port_Temp;
					if(hasFile_Temp == 1)
					{
						this.internalBitfield.set(0, this.numberOfPieces);
						internalBitfield.set(0, this.numberOfPieces);
						//System.out.println("internalBitField cardinality: "+this.internalBitfield.get(0));	
					}
					System.out.println("internalBitField cardinality: "+this.internalBitfield.cardinality());
				}	
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
		//an array for holding the payloads of piece messages - 4-byte index + contents
		pieces = new byte[numberOfPieces][pieceSize + 4];
		File tempFile = new File(fileName);
		try {
			FileInputStream fs = new FileInputStream(tempFile);
			for( int i = 0; i<numberOfPieces;i++) {
				//indices will not have a purely ascending or descending order only because byte uses 2's complement, irrelevant to us since only the program needs to know the indices
				ByteBuffer buf = ByteBuffer.allocate(4);
				//put an int in the first 4 bytes of the piece for index
				buf.putInt(0, i);
				System.arraycopy(buf.array(), 0, pieces[i], 0, 4);
					
				//fills in the rest of the bytes in the piece with contents
				for(int j = 4; j<pieceSize+4; j++) {
					//fill in bytes of the piece
						//fs.read() will return -1 if reach end of file
					
						pieces[i][j] = (byte)fs.read();
						//System.out.println(pieces[i][j] + " pieces");
				}
			}
			fs.close();
		}
		catch( Exception e) {
			//e.printStackTrace();
			System.out.println("Error in setupPieces.");
			System.exit(-1);
		}
	}
	private void setupDirectory(int ID) {
		//File dir = new File("/peer_["+ID+"]");
		//dir.mkdir();

		new File("./peer_["+ID+"]").mkdirs();
		//this.f = new File("peer_"+String.valueOf(this.peerID));
		try {
	    	this.file = new File(this.f, "./peer_["+ID+"]/log_peer_["+String.valueOf(this.peerID)+"].log");
			this.downloadFile = new File("./peer_["+ID+"]/output");
		//	this.downloadFile = new File(this.f, fileName);
	    	System.out.println("AUGH!");
			//FileWriter temp = new FileWriter(this.file, true);
			this.bw = new BufferedWriter(new FileWriter(this.file, false));
			log(this.peerID, 11, -1);
			this.bw.flush();
			System.out.println("UGH!");
		} catch(Exception e) {
			System.err.println("Error: " + e.getMessage());
			System.exit(-1);
		}
	}
	
	private void setupLogFiles(int ID) {
			//file.createNewFile();
			//out.close();	

	}
	
	private void setupConnections() {
		// for each peer in the dictionary...
		List<peerData> sortedPeers = new ArrayList<peerData>(peerDict.values());
		Collections.sort(sortedPeers, new Comparator<peerData>() {
		
			public int compare(peerData peer1, peerData peer2) {
				return peer1.ID - peer2.ID;
			}		
		});
		sortedPeers.remove(Integer.valueOf(this.peerID));
		for(peerData peer: sortedPeers)
		{
			//peerData peer = entry.getValue();
			// if we appear first, then we must listen (server)
			System.out.println(peer.ID);
			if(this.peerID < peer.ID)
			{
				try {
					// Create a server socket
					System.out.println(peer.portNumber);
					ServerSocket serverSocket = new ServerSocket(peer.portNumber);
					// Listen for a connection request
					Socket socket = serverSocket.accept();
					// Create input/output data streams
					DataInputStream inboundStream = new DataInputStream(socket.getInputStream());
					DataOutputStream outboundStream = new DataOutputStream(socket.getOutputStream());
					// Pack the peerData object with the new data streams
					peer.inboundStream = inboundStream;
					peer.outboundStream = outboundStream;
					this.log(peer.ID, 1, -1);
				}
				catch(IOException ex) {
					ex.printStackTrace();
					System.err.println(ex);
					System.err.println(peer.ID);
					System.exit(-1);
				}
			}
			//if we appear second, then we must send (client)
			else if(this.peerID > peer.ID)
			{
				try {
					// Create a client socket
					// DO WE USE OUR PORT OR THEIRS? ASSUMING THEIRS!
					Socket socket = new Socket(peer.hostName, this.portNumber);
					// Create input/output data streams
					DataInputStream inboundStream = new DataInputStream(socket.getInputStream());
					DataOutputStream outboundStream = new DataOutputStream(socket.getOutputStream());
					// Pack the peerData object with the new data streams
					peer.inboundStream = inboundStream;
					peer.outboundStream = outboundStream;
					// INITIATE THE HANDSHAKE!!
					this.sendHandshake(peer.ID);
					peer.initiatedHandshake = true;
					this.log(peer.ID, 1, -1);
				}
				catch(IOException ex) {
					ex.printStackTrace();
					System.err.println(ex);
					System.err.println(peer.ID);
					System.exit(-1);
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

	public void handleMessage(int messageType, int senderPeerID, byte[] payload) {

		//actions in response to receiving message
		//unpack message and get msg type, here, assume type is int
		// STUBS FOR COMPILATION PURPOSES
		//int messageType = 0;
		int interestStatus = 0;

		//int senderPeerID = 0;
		int stub = 0;
		int index; //payload of request and have
		ByteBuffer buf;
		
		
		//msg_type = extract from message
		switch (messageType) {
			//handshake
			case 8:
			System.out.println("GOTCHYA!");
				if(peerDict.get(senderPeerID).initiatedHandshake)
				{
					System.out.println("Send bitfield.");
					sendBitfield(senderPeerID);
				}else{
					System.out.println("Send handshake part 2.");
					sendHandshake(senderPeerID);
				}
				log(senderPeerID, 2, -1);
				break;
			//bitfield
			case 5:
				updateBitfield(senderPeerID, 0, payload);
				updateInteresting(senderPeerID);
				if (!this.peerDict.get(senderPeerID).initiatedHandshake){
					sendBitfield(senderPeerID);
				} else{
					if (interestingList.contains(senderPeerID)){
						sendInterested(senderPeerID);
					} else {
						sendNotInterested(senderPeerID);
					}
				}
				log(senderPeerID, 12, -1);
				break;
			//choke
			case 0:	log(senderPeerID, 6, -1);
				removeSender(senderPeerID);
				break;
			//unchoke
			case 1:	log(senderPeerID, 5, -1);
				addSender(senderPeerID);
				//fix input types
				//LBL A
				sendRequest(senderPeerID, this.choosePieceToRequest(senderPeerID));
				break;
			//interested
			case 2:	log(senderPeerID, 8, -1);
				addInterested(senderPeerID);
				peerDict.get(senderPeerID).isInterested = true;
				break;
			//not interested
			case 3:	log(senderPeerID, 9, -1);
				removeInterested(senderPeerID);
				break;
			//have
			case 4:	updateBitfield(senderPeerID, 1, payload);
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
			case 6:	buf = ByteBuffer.wrap(payload);
						//get index or payload starting from byte 5 of message
						index = buf.getInt(0);
						System.out.println("index "+index);
				sendPiece(senderPeerID, index);
				break;
			//piece
			case 7:
				buf = ByteBuffer.wrap(payload);
				index = buf.getInt(0);
				insertPiece(index, payload);
				updateMyBitfield(index);
				sendHave(senderPeerID,index);	//method will send to all peers so parameter cant be senderPeerID
				peerDict.get(senderPeerID).piecesSinceLastRound++;
				log(senderPeerID, 10, index);
				if(updateInteresting(senderPeerID)){
					sendRequest(senderPeerID, this.choosePieceToRequest(senderPeerID));
				}else{
					sendNotInterested(senderPeerID);
				}
				/*System.out.println("before removerequestsinflight");
				removeRequestsInFlight(senderPeerID);*/
				checkCompletion();
				System.out.println("file complete "+this.fileComplete);
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
		interestedList.remove(Integer.valueOf(localPeerID));
	}

	private void addInteresting(int localPeerID)
	{
		interestingList.add(localPeerID);
	}

	private void removeInteresting(int localPeerID)
	{

		interestingList.remove(Integer.valueOf(localPeerID));
	}

	public void addSender(int sPeerID)
	{
		senderList.add(sPeerID);
	}


	public void removeSender(int sPeerID)
	{
		senderList.remove(Integer.valueOf(sPeerID));
	}
	
	public void updateBitfield(int senderPeerID, int msgType, byte[] payload) {
		//msgType is 0 if payload is bitfield and 1 if payload is have index
		peerData tmpPeer = this.peerDict.get(senderPeerID);
		BitSet bits = tmpPeer.bitfield;
		/*if(payload == null){
			return;
		}*/
		for(int i=0; i<payload.length;i++){
			System.out.println((int)payload[i]);
		}
		ByteBuffer buf = ByteBuffer.wrap(payload);
		int num = buf.getInt(0);
		if(msgType == 0) {
			if(num >0)
				bits.set(0,numberOfPieces);
		}
		else
			log(senderPeerID, 7, num);
			bits.set(num);	
	}
	
	public void updateMyBitfield(int index) {
		internalBitfield.set(index);
	}
	
	public boolean updateInteresting(int senderPeerID) {
		BitSet theirs = (BitSet) this.peerDict.get(senderPeerID).bitfield.clone();
		BitSet ours = (BitSet) internalBitfield.clone();
		ours.flip(0, this.numberOfPieces);
		ours.and(theirs);
		if(ours.cardinality() > 0){
			addInteresting(senderPeerID);
			return true;
		} else {
			removeInteresting(senderPeerID);
			return false;
		}
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
		temp.outboundStream.write(b,0,b.length);
		System.out.println("Sent Choke message to peer "+localPID+".");
		}
		catch(IOException ex) {
			System.out.println(ex.toString());
			System.out.println("SEND CHOKE FAILED");
			System.exit(-1);
		}
		
	}

	private void sendUnchoke(int localPID)
	{
		// send the unChoke message
		this.neighborList.add(localPID);
		peerData temp = peerDict.get(localPID);
		
		byte[] b = new byte[]{0,0,0,1,1};
		try {
		temp.outboundStream.write(b,0,b.length);
		System.out.println("Sent Unchoke message to peer "+localPID+".");
		}
		catch(IOException ex) {
			System.out.println(ex.toString());
			System.out.println("SEND UNCHOKE FAILED");
			System.exit(-1);
		}
	}

	private void sendInterested(int localPID)
	{
		// send the interested message
		//this.updateInteresting(localPID);
		peerData temp = peerDict.get(localPID);
		
		byte[] b = new byte[]{0,0,0,1,2};
		try {
		temp.outboundStream.write(b,0,b.length);
		System.out.println("Sent Interested message to peer "+localPID+".");
		}
		catch(IOException ex) {
			System.out.println(ex.toString());
			System.out.println("SEND INTERESTED FAILED");
			System.exit(-1);
			
		}
	}

	private void sendNotInterested(int localPID)
	{
		// send the notInterested message
		this.interestedList.remove(Integer.valueOf(localPID));
		peerData temp = peerDict.get(localPID);
		
		byte[] b = new byte[]{0,0,0,1,3};
		try {

		temp.outboundStream.write(b,0,b.length);
		System.out.println("Sent Not Interested message to peer "+localPID+".");
		}
		catch(IOException ex) {
			System.out.println(ex.toString());
			System.out.println("SEND NOT INTERESTED");
			System.exit(-1);

		}
	
	}

	private void sendHave(int localPID, int pieceID)
	{
	
		byte[] a = new byte[]{0,0,0,5,4};
		ByteBuffer buf = ByteBuffer.allocate(4);
		byte[] b = buf.putInt(pieceID).array();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			outputStream.write(a);
			outputStream.write(b);
		}
		catch(IOException ex) {
			System.out.println(ex.toString());
			System.out.println("SEND HAVE BYTE MANIP FAILED");
			System.exit(-1);
		}	
		byte[] c = outputStream.toByteArray();
		for (Map.Entry<Integer, peerData> entry : this.peerDict.entrySet()) {
				// construct a message from the byte stream coming in, then pass it to handleMessage
				peerData temp = entry.getValue();
				System.out.println("Sent Have message to peer "+temp.ID+".");
				for(int i=0; i<c.length;i++){
					System.out.println("\t"+c[i]);
				}
				//System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
			try {
				temp.outboundStream.write(c,0,c.length);
			}
			catch(IOException ex) {
				System.out.println(ex.toString());
				System.out.println("SEND HAVE FAILED");
				System.exit(-1);
			}
		}
	}

	private void sendBitfield(int localPID)
	{
		//sends all 0's or all 1's because our situation is that no one joins late, have to change this to send current bitfield for peers entering late
		peerData temp = peerDict.get(localPID);
		byte[] msg = new byte[9];
		//message length is 5
		ByteBuffer buf = ByteBuffer.wrap(msg);
		buf.putInt(5);
		
		//can only change position back before current position, can't do forward
		buf.position(5);
		msg[4] = (byte)5;
		//bitfield is empty - all 0's
		if(internalBitfield.length() == 0) 
			buf.putInt(0);
		else
		//bitfield is full so all 1's in payload
			buf.putInt(65535);

		try {
			temp.outboundStream.write(msg,0,msg.length);
		}
		catch(IOException ex) {
			System.out.println(ex.toString());
			System.out.println("SEND BITFIELD FAILED");
			System.exit(-1);
		}
	}

	private void sendRequest(int localPID, int pieceID)
	{
		byte[] a = new byte[]{0,0,0,5,6};
		peerData temp = peerDict.get(localPID);
		ByteBuffer buf = ByteBuffer.allocate(4);
		byte[] b = buf.putInt(pieceID).array();
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try {
			outputStream.write(a);
			outputStream.write(b);
			System.out.println("Sent Request message to peer "+localPID+".");
		}
		catch(IOException ex) {
			System.out.println(ex.toString());
			System.out.println("SEND REQUEST BYTE MANIP FAILED");
			System.exit(-1);
		}
		
		byte[] c = outputStream.toByteArray();
		
		try {
		temp.outboundStream.write(c,0,c.length);
		}
		catch(IOException ex) {
			System.out.println(ex.toString());
			System.out.println("SEND REQ FAILED");
			System.exit(-1);
		}
	}

	private void sendPiece(int localPID, int index)
	{
		//index is from the request message that was received
		//retrieve piece
		byte[] piece = pieces[index];
		/*if sending the last piece and it is a shorter piece, cut off the extra bytes(will read as -1) that aren't supposed to be part of the piece, this assumes that the last byte of the piece isn't -1*/
		System.out.println("piece length"+piece.length);
		int tmpIndex = piece.length-1;
		if(index == numberOfPieces-1) {
			
			while(piece[tmpIndex] == -1) {
				tmpIndex--;
			}
		}
		byte[] msg = new byte[pieceSize+9];
		ByteBuffer buf = ByteBuffer.wrap(msg);
		//message length is tmpIndex-4+1
		buf.putInt(pieceSize+5);
		System.out.println("tmpindex"+tmpIndex);
		//message type
		msg[4] = (byte)7;
		System.arraycopy(piece, 0, msg, 5, tmpIndex+1);
		for(int i = 0;i<msg.length;i++) {
			System.out.println("msg "+msg[i]);
		}
		peerData temp = peerDict.get(localPID);
		try {
		
			temp.outboundStream.write(msg, 0, msg.length);
		}
		catch(IOException e) {
			System.out.println("Error in sendPiece().");
			//e.printStackTrace();
			System.exit(-1);
		}
		/*catch(ArrayIndexOutOfBoundsException a) {
			System.out.println("tempindex "+tmpIndex);
			System.out.println("msglength"+msg.length);
		}*/
	}

	private int choosePieceToRequest(int localPID)
	{
		BitSet theirs = (BitSet) this.peerDict.get(localPID).bitfield.clone();
		BitSet ours = (BitSet) internalBitfield.clone();
		ours.flip(0, this.numberOfPieces);
		ours.and(theirs);
		//int[] temp = new int[this.numberOfPieces];
		LinkedList<Integer> temp = new LinkedList<Integer>();
		for(int i=0; i<this.numberOfPieces; i++)
		{
			if(ours.get(i))
			{
				temp.add(i);
			}
		}

		int temp_index = (int)(Math.random()*temp.size());
		int piece = temp.get(temp_index);
		return piece;
	}

	private void sendHandshake(int localPID)
	{
		peerData temp = peerDict.get(localPID);
		//temp.initiatedHandshake = true;
		// send the handshake

		//temp.outboundStream.write(/*handshake-header, zero bits, PID*/);

		byte[] b = new byte[32];
		String str = "HELLO"+"00000000000000000000000";
		String str2 = String.valueOf(peerID);
		while(str2.length() != 4) {
			str2 = "0"+str2;
		}
		str = str+str2;
		b = str.getBytes();
		try {
			temp.outboundStream.write(b, 0, b.length);
		}
		catch(IOException e) {
			System.out.println("Error in sendHandshake; could not write to stream.");
			//e.printStackTrace();
			System.exit(-1);
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
		int tempSize = this.numberOfPieces;
		BitSet tempBitfield = new BitSet(tempSize);
		tempBitfield = (BitSet) internalBitfield.clone();
		tempBitfield.flip(0, tempSize);
		if(tempBitfield.isEmpty() /*&& tempBitfield.size() == this.numberOfPieces*/)
		{
			this.fileComplete = true;
			System.out.println("FILE! COMPLETE! WOW! SUCH MAGIC! VERY DARK!");
			log(this.peerID,13,1);
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

			switch (event) {
				case 1:	msg = "["+time()+"]: "+"Peer ["+myID+"] makes a connection to Peer ["+lPID+"].";
					break;
				case 2: msg =  "["+time()+"]: "+"Peer ["+myID+"] is connected from ["+lPID+"].";
					break;
				case 3:	msg =  "["+time()+"]: "+"Peer ["+myID+"] has the preferred neighbors "+this.neighborList+".";
					break;
				case 4:	msg =  "["+time()+"]: "+"Peer ["+myID+"] has the optimistically-unchoked neighbor ["+lPID+"].";
					break;
				case 5:	msg = "["+time()+"]: "+"Peer ["+myID+"] is unchoked by ["+lPID+"].";
					break;
				case 6:	msg = "["+time()+"]: "+"Peer ["+myID+"] is choked by ["+lPID+"]."; 
					break;
				case 7: msg = "["+time()+"]: "+"Peer ["+myID+"] received a 'have' message from ["+lPID+"] for the piece ["+String.valueOf(piece)+"].";
					break;
				case 8:	msg = "["+time()+"]: "+"Peer ["+myID+"] receive an 'interested' message from ["+lPID+"].";
					break;      	
				case 9:	msg = "["+time()+"]: "+"Peer ["+myID+"] received a 'not interested' message from ["+lPID+"].";
					break;
				case 10: msg = "["+time()+"]: "+"Peer ["+myID+"] has downloaded the piece ["+String.valueOf(piece)+"] from ["+lPID+"].";
					break;
				case 11: msg = "["+time()+"]: "+"Log File has been generated for Peer "+myID+".";
					break;
				case 12: msg = "["+time()+"]: "+"Bitfield update on ["+myID+"]";
					break;
				case 13: msg = "["+time()+"]: "+"Peer ["+myID+"] has downloaded the complete file.";
					break;
				default: msg = "-1";//exception
			}
			msg = msg+"\n";
			this.bw.write(msg);
			this.bw.flush();
			//this.bw.close();
		}
		catch (IOException e) {
			System.out.println("Error in logging.");
			//e.printStackTrace();
			System.exit(-1);
		}
			
	}

	/*time method gets compilation error when called
	'error: non-static method time() cannot be referenced from a static context'
	if used in log() like--> String msg = "["+time()+"]";*/

	private String time() {
		TimeZone tm = TimeZone.getDefault();
		GregorianCalendar cal = new GregorianCalendar(tm);
		return String.valueOf(cal.get(Calendar.HOUR))+":"+String.valueOf(cal.get(Calendar.MINUTE))+":"+String.valueOf	(cal.get(Calendar.SECOND));
	}
	
	private void unchokingUpdate() {
		
		System.out.println("Entered unchoking update.");
		List<peerData> sortedPeers = new ArrayList<peerData>(peerDict.values());
		Collections.sort(sortedPeers, new Comparator<peerData>() {
		
			public int compare(peerData peer1, peerData peer2) {
				return peer1.piecesSinceLastRound - peer2.piecesSinceLastRound;
			}		
		});
			//Send unchoke messages to the best neighbors
		for (int i = 0; i < sortedPeers.size(); i++)
		{
			if(!sortedPeers.get(i).isInterested)
			{
				sortedPeers.remove(i);
			}
		}
		if(sortedPeers.size()>0){
			for (int i = 0; i < numberOfPreferredNeighbors; i++) { 
				if (sortedPeers.get(i).isChoked) {
					System.out.println("Unchoking a peer.");
					sendUnchoke(sortedPeers.get(i).ID);
					peerDict.get(sortedPeers.get(i).ID).isChoked = false;
				}	if (sortedPeers.get(i).isOptimisticallyUnchoked) {
						peerDict.get(sortedPeers.get(i).ID).isOptimisticallyUnchoked = false;
				}
			
			}
			log(this.peerID,3,1);
		}
			//Send Choke to the rest
		for (int i = numberOfPreferredNeighbors; i < sortedPeers.size(); i++) {
			if (!sortedPeers.get(i).isChoked && !sortedPeers.get(i).isOptimisticallyUnchoked) {
				sendChoke(sortedPeers.get(i).ID);
				peerDict.get(sortedPeers.get(i).ID).isChoked = true;
			}
			
		}
		
		
	}
	
	private void optimisticUnchokingUpdate() {
		System.out.println("Entered Optimistic Update");
		List<peerData> peers = new ArrayList<peerData>(peerDict.values());
		long seed = System.nanoTime();
		Collections.shuffle(peers, new Random(seed));
		for (int i = 0; i < peers.size(); i++) {
			
			if(peers.get(i).isChoked && peers.get(i).isInterested) {
			
				sendUnchoke(peers.get(i).ID);
				peerDict.get(peers.get(i).ID).isOptimisticallyUnchoked = true;
				log(this.peerID,4,1);
						
			}
			
		}
	
	}
	
	private void insertPiece(int index, byte[] payload) {
	//insert piece into it's proper place in the file
		try{
			RandomAccessFile rf = new RandomAccessFile(downloadFile, "rw");
			rf.seek(index*pieceSize);
			rf.write(payload, 4, pieceSize);	
			rf.close();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}
	
}
