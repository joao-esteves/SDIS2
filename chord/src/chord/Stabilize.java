package chord;

import communication.Client;
import communication.messages.MessageFactory;
import communication.messages.MessageType;
import utils.Utils;

public class Stabilize implements Runnable {
	private ChordManager chordManager;

	public Stabilize(ChordManager chordManager) {
		this.chordManager = chordManager;
	}

	private AbstractPeerInfo parseResponse(String response) { 
		//response should be a string of PREDECESSOR Type
		try {
			response = response.trim();
			String[] args = response.split("\r\n")[1].split(" ");
			if (args.length == 3) {
				return new PeerInfo(response);
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return new NullPeerInfo();
	}

	@Override
	public void run() {
		try {
			String myPeerId = this.chordManager.getPeerInfo().getId();
			Utils.LOGGER.finest("Running Stabilize\n");
			PeerInfo nextPeer = this.chordManager.getNextPeer();
			String stabilizeMessage = MessageFactory.getHeader(MessageType.STABILIZE, "1.0", myPeerId);
			String response = Client.sendMessage(nextPeer.getAddr(), nextPeer.getPort(), stabilizeMessage, true);
			if(response == null) {
				Utils.LOGGER.warning("Next peer dropped");
				this.chordManager.popNextPeer();
				return;
			}
			
			AbstractPeerInfo x = parseResponse(response);
			this.chordManager.stabilize(x); //might update successor
			nextPeer = this.chordManager.getNextPeer();
			this.chordManager.notify(nextPeer); //notify my successor that I might be his predecessor
			
			// send my nextPeers to my predecessor.
			AbstractPeerInfo predecessor = chordManager.getPredecessor();
			if (predecessor.isNull()) return;
			if(myPeerId.equals(predecessor.getId())) return; //do not send to myself
			String successorsMsg = MessageFactory.getSuccessors(myPeerId, chordManager.getNextPeers());
			Client.sendMessage(predecessor.getAddr(), predecessor.getPort(), successorsMsg, false);
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}