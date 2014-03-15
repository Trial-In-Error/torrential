public class peerProcess {
	// bitfield representing file pieces owned by this peer
	// needs every relevant bit set to zero!
	// http://docs.oracle.com/cd/E16162_01/apirefs.1112/e17493/oracle/ide/util/BitField.html
	Bitfield internalBitfield = new Bitfield();

	// does this peer have the file completed?
	// needs set to true in the true case in setup!
	boolean fileComplete = false;

	// send data to (numberOfPreferredNeighbors + 1) because of optimistic unchoke
	// needs set to value from Common.cfg!
	int numberOfPreferredNeighbors = 0;

	// how many seconds between readjusting who you prefer to send messages to
	// needs set to value from Common.cfg!
	int unchokingInterval = 100;

	// how many seconds before readjusting who you are optimistically sending messages to
	// needs set to value from Common.cfg!
	int optimisticUnchokingInterval = 100;

	// name of file to be sent / received
	// needs set to value from Common.cfg!
	String fileName = "";

	// size of file to be sent / received, in bytes
	// needs set to value from Common.cfg!
	int fileSize = 0;

	// size of a piece (transmission segment, before being wrapped with headers), in bytes
	// needs set to value from Common.cfg!
	int pieceSize = 0;

	public static void main(String [] args) {

	}
}