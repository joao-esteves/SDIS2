package runnableProtocols;

import java.util.Arrays;
import communication.Client;
import communication.messages.MessageFactory;
import chord.ChordManager;
import chord.PeerInfo;

public class SendPutChunk implements Runnable {

	private String senderID = null;
	private String fileID = null;
	private int chunkNo = 0;
	private int replicationDeg = 0;
	private byte[] body = null;
	private ChordManager chord = null;
	
	public SendPutChunk (String fileID, int chunkNo, int replicationDeg, byte[] body, ChordManager chord) {
		this.senderID = chord.getPeerInfo().getId();
		this.fileID = fileID;
		this.chunkNo = chunkNo;
		this.replicationDeg = replicationDeg;
		this.body = Arrays.copyOf(body, body.length);
		
		this.chord = chord;
	}
	@Override
	public void run() {
		PeerInfo owner = chord.getChunkOwner(fileID);
		String putChunkMessage = MessageFactory.getPutChunk(senderID, owner.getAddr(),owner.getPort(), this.fileID, this.chunkNo, this.replicationDeg, this.body);
		Client.sendMessage(owner.getAddr(), owner.getPort(), putChunkMessage, false);

	}
	
}
