/**
 * 
 */
package chord;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import communication.Client;
import utils.UnsignedByte;
import utils.Utils;

/**
 * @author anabela
 *
 */
public class ChordManager implements Runnable {

	private static final int M = 8;
	private PeerInfo peerInfo;
	private ArrayList<PeerInfo> fingerTable = new ArrayList<PeerInfo>();
	private AbstractPeerInfo predecessor;
	private ScheduledThreadPoolExecutor scheduledPool = new ScheduledThreadPoolExecutor(4);

	public void join(InetAddress addr, int port) {
		String response = Client.sendMessage(addr, port, "lookup " + peerInfo.getId());
		response = response.trim();

		PeerInfo info = new PeerInfo(response);

		if(response.startsWith("Ask")) {
			//TODO: Repeat to the new Node
		} else {
			this.fingerTable.set(0, info);
		}
	}

	public ChordManager(Integer port) {
		
		InetAddress addr;
		try {
			addr = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return;
		}
		
		byte[] hash = digest.digest(("" + addr + port).getBytes(StandardCharsets.ISO_8859_1));
		UnsignedByte id = new UnsignedByte(ByteBuffer.wrap(hash).getShort());
		this.peerInfo = new PeerInfo(id,addr, port);
		
		
		for (int i = 0; i < getM(); i++) {
			fingerTable.add(peerInfo);
//			TODO: null design pattern
		}
		predecessor = new NullPeerInfo(); 

	}

	@Override
	public void run() {
		CheckPredecessor checkPredecessorThread = new CheckPredecessor(predecessor);
		scheduledPool.scheduleAtFixedRate(checkPredecessorThread, 0, 1000, TimeUnit.MILLISECONDS);
	}

	/**
	 * Returna o successor da key, ou a quem perguntor
	 * @param key a procurar
	 * @return 
	 */
	public String lookup(UnsignedByte key) {
		if(Utils.inBetween(this.predecessor.getId(),this.peerInfo.getId(), key.get())) {
			return "Successor "+ this.peerInfo.toString();
		}
		if(Utils.inBetween(this.peerInfo.getId(), this.fingerTable.get(0).getId(), key.get())) {
			return "Successor "+ this.fingerTable.get(0).toString();
		}
		for(int i = getM()-1; i >= 0; i--) {
			if(Utils.inBetween(this.peerInfo.getId(), key.get(), this.fingerTable.get(i).getId())) {
				return "Ask "+ this.fingerTable.get(i).toString();
			}
		}
		return "Ask "+ this.fingerTable.get(getM()-1).toString();
	}

	/**
	 * @return the m
	 */
	public static int getM() {
		return M;
	}


}