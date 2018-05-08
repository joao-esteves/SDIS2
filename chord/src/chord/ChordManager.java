/**
 * 
 */
package chord;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.DatatypeConverter;

import communication.Client;
import database.Database;
import messages.MessageFactory;
import messages.MessageType;
import utils.SingletonThreadPoolExecutor;
import utils.Utils;

/**
 * @author anabela
 *
 */
public class ChordManager implements Runnable {

	private static final int M = 32;
	private PeerInfo peerInfo;
	private ArrayList<PeerInfo> fingerTable = new ArrayList<PeerInfo>();
	private AbstractPeerInfo predecessor;

	private String ASK_MESSAGE;
	private String SUCCESSOR_MESSAGE;
	private String LOOKUP_MESSAGE;
	private Database database;

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
		String id = DatatypeConverter.printHexBinary(hash);
		this.setPeerInfo(new PeerInfo(id, addr, port));
		
		ASK_MESSAGE = MessageFactory.getFirstLine(MessageType.ASK, "1.0", this.getPeerInfo().getId());
		SUCCESSOR_MESSAGE = MessageFactory.getFirstLine(MessageType.SUCCESSOR, "1.0", this.getPeerInfo().getId());

		for (int i = 0; i < getM(); i++) {
			getFingerTable().add(getPeerInfo());
			// TODO: null design pattern
		}
		predecessor = new NullPeerInfo();

		System.err.println(MessageFactory.appendLine(SUCCESSOR_MESSAGE, this.getPeerInfo().asArray()));

	}

	@Override
	public void run() {
		CheckPredecessor checkPredecessorThread = new CheckPredecessor(predecessor);
		SingletonThreadPoolExecutor.getInstance().get().scheduleAtFixedRate(checkPredecessorThread, 4000, 10000, TimeUnit.MILLISECONDS);
		FixFingerTable fixFingerTableThread = new FixFingerTable(this);
		SingletonThreadPoolExecutor.getInstance().get().scheduleAtFixedRate(fixFingerTableThread, 2000, 10000, TimeUnit.MILLISECONDS);

		Stabilize stabilizeThread = new Stabilize(this);
		SingletonThreadPoolExecutor.getInstance().get().scheduleAtFixedRate(stabilizeThread, 0, 10000, TimeUnit.MILLISECONDS);

	}

	public void join(InetAddress addr, int port) {
		String lookupMessage = MessageFactory.getFirstLine(MessageType.LOOKUP, "1.0",getPeerInfo().getId());
		lookupMessage = MessageFactory.appendLine(lookupMessage, new String[]{""+getPeerInfo().getId()});
		String response = Client.sendMessage(addr, port, lookupMessage, true);
		response = response.trim();

		PeerInfo nextPeer = new PeerInfo(response);

		while (response.startsWith("Ask")) {
			Utils.LOGGER.finest("\t" + response);
			response = Client.sendMessage(nextPeer.getAddr(), nextPeer.getPort(), lookupMessage, true);
			if (response == null) {
				System.err.println("Could not join the network");
				return;
			}
			response = response.trim();
			nextPeer = new PeerInfo(response);
		}
		this.getFingerTable().set(0, nextPeer);
	}

	/**
	 * Returna o successor da key, ou a quem perguntor
	 * 
	 * @param key
	 *            a procurar
	 * @return
	 */
	public String lookup(String key) {
		if (Utils.inBetween(this.predecessor.getId(), this.getPeerInfo().getId(), key)) {
			return MessageFactory.appendLine(SUCCESSOR_MESSAGE, this.getPeerInfo().asArray());
		}
		if (Utils.inBetween(this.getPeerInfo().getId(), this.getFingerTable().get(0).getId(), key)) {
			return MessageFactory.appendLine(SUCCESSOR_MESSAGE, this.getFingerTable().get(0).asArray());
		}
		for (int i = getM() - 1; i > 0; i--) {
			if (Utils.inBetween(this.getPeerInfo().getId(), key, this.getFingerTable().get(i).getId())) {
				return MessageFactory.appendLine(ASK_MESSAGE, this.getFingerTable().get(i).asArray());
			}
		}
		return MessageFactory.appendLine(ASK_MESSAGE, this.getFingerTable().get(getM() - 1).asArray());
	}

	public boolean stabilize(PeerInfo predecessor) {

		PeerInfo successor = this.fingerTable.get(0);

		if (Utils.inBetween(this.peerInfo.getId(), successor.getId(), predecessor.getId())) {
			setSuccessor(0, predecessor);
			return true;
		}
		return false;
	}

	/**
	 * Notify newly found closer successor node that this node is now its predecessor.
	 * @param newSuccessorId Closer successor than previous successor.
	 */
	public void notify(PeerInfo newSuccessor) {
		if (predecessor.isNull() || Utils.inBetween(predecessor.getId(), this.getPeerInfo().getId(), newSuccessor.getId())) {
			String message = MessageFactory.getFirstLine(MessageType.NOTIFY, "1.0", this.getPeerInfo().getId());
			message = MessageFactory.appendLine(message, new String[] {"" + this.getPeerInfo().getPort()});
			String response = Client.sendMessage(newSuccessor.getAddr(), newSuccessor.getPort(), message, true).trim();
			String expectedResponse = MessageFactory.getHeader(MessageType.OK, "1.0", newSuccessor.getId()).trim();
			if (!expectedResponse.equals(response)) {
				System.err.println("Expected: " + expectedResponse);
				System.err.println("ChordManager notify(): Error on NOTIFY message reply: " + response);
			}
		}
	}

	public PeerInfo getChunkOwner(String key) {

		if (Utils.inBetween(this.predecessor.getId(), this.getPeerInfo().getId(), key)) {
			return this.peerInfo;
		}
		if (Utils.inBetween(this.getPeerInfo().getId(), this.getFingerTable().get(0).getId(), key)) {
			return this.getFingerTable().get(0);
		}
		
		InetAddress addr = null;
		int port = -1;
		
		for (int i = getM() - 1; i > 0; i--) {
			if (Utils.inBetween(this.getPeerInfo().getId(), key, this.getFingerTable().get(i).getId())) {
				addr = this.getFingerTable().get(i).getAddr();
				port = this.getFingerTable().get(i).getPort();
			}
		}
		if(port == -1) {
			addr = this.getFingerTable().get(getM() - 1).getAddr();
			port = this.getFingerTable().get(getM() - 1).getPort();
		}

		String lookupMessage = MessageFactory.getLookup(this.peerInfo.getId(), key);
		String response = Client.sendMessage(addr, port, lookupMessage, true);
		response = response.trim();

		PeerInfo owner = new PeerInfo(response);

		while (response.startsWith("Ask")) {
			Utils.LOGGER.finest("\t" + response);
			response = Client.sendMessage(owner.getAddr(), owner.getPort(), lookupMessage, true);
			if (response == null) {
				System.err.println("Could not join the network");
				return null;
			}
			response = response.trim();
			owner = new PeerInfo(response);
		}

		return owner;
	}

	/**
	 * @return the m
	 */
	public static int getM() {
		return M;
	}

	public PeerInfo getSuccessor(int index) {
		return this.fingerTable.get(index);
	}

	public void setSuccessor(int index, PeerInfo successor) {
		this.fingerTable.set(index, successor);
	}

	public AbstractPeerInfo getPredecessor() {
		return this.predecessor;
	}

	public void setPredecessor(PeerInfo newPred) {
		predecessor = newPred;
	}

	/**
	 * @return the peerInfo
	 */
	public PeerInfo getPeerInfo() {
		return peerInfo;
	}

	/**
	 * @param peerInfo
	 *            the peerInfo to set
	 */
	public void setPeerInfo(PeerInfo peerInfo) {
		this.peerInfo = peerInfo;
	}

	/**
	 * @return the fingerTable
	 */
	public ArrayList<PeerInfo> getFingerTable() {
		return fingerTable;
	}

	/**
	 * @param fingerTable
	 *            the fingerTable to set
	 */
	public void setFingerTable(ArrayList<PeerInfo> fingerTable) {
		this.fingerTable = fingerTable;
	}

	/**
	 * @return the database
	 */
	public Database getDatabase() {
		return database;
	}

	/**
	 * @param database the database to set
	 */
	public void setDatabase(Database database) {
		this.database = database;
	}
}
