package messages;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;

import chord.PeerInfo;
import utils.Utils;

public class MessageFactory {

	private static String END_HEADER = "\r\n\r\n";
	private static String NEW_LINE = "\r\n";
	
	public static String getFirstLine(MessageType messageType, String version, String id) {
		return messageType.getType() + " " + version + " " + id + " " + NEW_LINE;
	}
	
	public static String getHeader(MessageType messageType, String version, String senderId) {
		return getFirstLine(messageType,version,senderId) + NEW_LINE;
	}
	
	public static String appendLine(String message, Object args[]) {
		for (Object arg: args) {
			message += arg.toString() + " ";
		}
		message += END_HEADER;
		return message;
	}
	public static String appendBody(String message, byte [] body) throws UnsupportedEncodingException {
		String bodyStr = new String(body, Utils.ENCODING_TYPE);
		message += bodyStr;
		return message;
	}
	public static String getLookup(String senderId, String key) {
		String msg = getFirstLine(MessageType.LOOKUP,"1.0",senderId);
		return appendLine(msg, new String[] {""+key});
	}
	public static String getSuccessor(String senderId, PeerInfo peer) {
		String msg = getFirstLine(MessageType.SUCCESSOR,"1.0",senderId);
		return appendLine(msg, new Object[] {peer.getId(),peer.getAddr().getHostAddress(),peer.getPort()});
	}
	public static String getPredecessor(String senderId, PeerInfo peer) {
		String msg = getFirstLine(MessageType.PREDECESSOR,"1.0",senderId);
		return appendLine(msg, new Object[] {peer.getId(),peer.getAddr().getHostAddress(),peer.getPort()});
	}
	public static String getAsk(String senderId, PeerInfo peer) {
		String msg = getFirstLine(MessageType.ASK,"1.0",senderId);
		return appendLine(msg, new Object[] {peer.getId(),peer.getAddr().getHostAddress(),peer.getPort()});
	}
	public static String getPutChunk(String id, InetAddress addr, int port, String fileID, int chunkNo, int replicationDeg, byte[] body) {
		String msg = getFirstLine(MessageType.PUTCHUNK,"1.0",id);
		String msg2 = appendLine(msg, new Object[] {id, addr.getHostAddress(), port, fileID, chunkNo, replicationDeg});
		try {
			return appendBody(msg2, body);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			
			return null;
		}
	}
	public static String getKeepChunk(String senderId, InetAddress addr, int port, String fileID, int chunkNo, int replicationDeg, byte[] body) {
		String msg = getFirstLine(MessageType.KEEPCHUNK,"1.0",senderId);
		String msg2 = appendLine(msg, new Object[] {senderId, addr.getHostName(), port, fileID, chunkNo, replicationDeg});
		try {
			return appendBody(msg2, body);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}
	public static String getStored(String senderId, String fileID, int chunkNo, int replicationDeg) {
		String msg = getFirstLine(MessageType.STORED,"1.0",senderId);
		String msg2 = appendLine(msg, new Object[] {fileID, chunkNo, replicationDeg});
		return msg2;
	}

	public static String getConfirmStored(String senderId, String fileID, int chunkNo, int replicationDeg) {
		String msg = getFirstLine(MessageType.CONFIRMSTORED,"1.0",senderId);
		String msg2 = appendLine(msg, new Object[] {fileID, chunkNo, replicationDeg});
		return msg2;
	}
}
