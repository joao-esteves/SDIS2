/**
 * 
 */
package communication;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.util.ArrayList;

import javax.net.ssl.SSLSocket;

import chord.AbstractPeerInfo;
import chord.ChordManager;
import chord.PeerInfo;
import database.BackupRequest;
import database.ChunkInfo;
import database.DBUtils;
import database.FileStoredInfo;
import messages.MessageFactory;
import messages.MessageType;
import program.Peer;
import utils.Utils;

/**
 * @author anabela
 *
 */
public class ParseMessageAndSendResponse implements Runnable {

	private byte[] readData;
	private SSLSocket socket;
	private Peer peer;
	private Connection dbConnection;
	private String myPeerID;


	public ParseMessageAndSendResponse(Peer peer, byte[] readData, SSLSocket socket) {
		super();
		this.readData = readData;
		this.socket = socket;
		this.peer = peer;
		this.dbConnection = peer.getChordManager().getDatabase().getConnection();
		this.myPeerID = peer.getChordManager().getPeerInfo().getId();
	}

	@Override
	public void run() {
		String response = parseMessage(readData);
		if (response != null) {
			sendResponse(socket, response);
		}

	}

	/**
	 * Parses the received request, processes it and returns the protocol response
	 * @param readData
	 * @return
	 */
	String parseMessage(byte[] readData) {
		String request = new String(readData,StandardCharsets.ISO_8859_1);
		Utils.LOGGER.finest("SSLServer: " + request);

		request = request.trim();
		String[] lines = request.split("\r\n");
		String[] firstLine = lines[0].split(" ");
		String[] secondLine = null;
		String thirdLine = null;//chunk body
		if (lines.length > 1) {
			secondLine = lines[1].split(" ");
		}
		if (lines.length > 2) {
			thirdLine = new String();
			for(int i = 3; i < lines.length; i++) {
				thirdLine += lines[i];
			}
		}
		String response = null;

		switch (MessageType.valueOf(firstLine[0])) {
		case DELETE:
			parseDelete(secondLine);
			break;
		case INITDELETE:
			parseInitDelete(firstLine, secondLine);
			break;
		case LOOKUP:
			if (secondLine != null) {
				response = peer.getChordManager().lookup(secondLine[0]);
			}else {
				Utils.LOGGER.warning("Invalid lookup message");
			}
			break;
		case PING:
			response = MessageFactory.getHeader(MessageType.OK, "1.0", myPeerID);
			break;
		case NOTIFY:
			parseNotifyMsg(firstLine,secondLine);
			response = MessageFactory.getHeader(MessageType.OK, "1.0", myPeerID);
			break;
		case PUTCHUNK:
			parsePutChunkMsg(secondLine, thirdLine);
			break;
		case KEEPCHUNK:
			parseKeepChunkMsg(secondLine, thirdLine);
			break;
		case STABILIZE:
			response = parseStabilize(firstLine);
			
			break;
		case STORED: {
			response = parseStoredMsg(secondLine);
			break;
		}
		case GETCHUNK: {
			response = parseGetChunkMsg(secondLine);
			break;
		}
		case CHUNK: {
			System.err.println("ESTOU A RECEBER O CHUNK");
			response = parseChunkMsg(secondLine,thirdLine);
			break;
		}
//		case UPDATETIME: {
//			response = parseUpdateTime(secondLine);
//			break;
//		}
		default:
			break;
		}
		return response;
	}
	
	

	private String parseStabilize(String[] firstLine) {
		String response = MessageFactory.getFirstLine(MessageType.PREDECESSOR, "1.0", myPeerID);
		return MessageFactory.appendLine(response, peer.getChordManager().getPredecessor().asArray());
	}

	private String parseChunkMsg(String[] secondLine, String body) {

		byte [] body_bytes = body.getBytes(StandardCharsets.ISO_8859_1);

		String file_id = secondLine[0].trim();
		int chunkNo = Integer.parseInt(secondLine[1]);

		BackupRequest b = DBUtils.getBackupRequested(dbConnection, file_id);

		Path filepath = Peer.getPath().resolve("restoreFile-" + b.getFilename());

		try {
			Files.createFile(filepath);
		} catch(FileAlreadyExistsException e) {
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}


		AsynchronousFileChannel channel;
		try {
			channel = AsynchronousFileChannel.open(filepath,StandardOpenOption.WRITE);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

		CompletionHandler<Integer, ByteBuffer> writter = new CompletionHandler<Integer, ByteBuffer>() {
			@Override
			public void completed(Integer result, ByteBuffer buffer) {
				System.out.println("Finished writing!");
			}

			@Override
			public void failed(Throwable arg0, ByteBuffer arg1) {
				System.err.println("Error: Could not write!");
			}

		};
		ByteBuffer src = ByteBuffer.allocate(body_bytes.length);
		src.put(body_bytes);
		src.flip();
		channel.write(src, chunkNo*Utils.MAX_LENGTH_CHUNK, src, writter);

		return null;
	}

	private String parseGetChunkMsg(String[] secondLine) {
		InetAddress addr;
		try {
			addr = InetAddress.getByName(secondLine[0]);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return null;
		}
		Integer port = Integer.valueOf(secondLine[1]);
		String fileID = secondLine[2];
		Integer chunkNo = Integer.valueOf(secondLine[3]);

		ChunkInfo chunkInfo = new ChunkInfo(chunkNo, fileID);
		if(DBUtils.checkStoredChunk(dbConnection, chunkInfo )) { //Tenho o chunk
			String body = Utils.readFile(Peer.getPath().resolve(chunkInfo.getFilename()).toString());
			String message = MessageFactory.getChunk(this.myPeerID, fileID, chunkNo, body.getBytes(StandardCharsets.ISO_8859_1));
			Client.sendMessage(addr, port, message, false);
		} else { //ReSend GETCHUNK to successor
			String message = MessageFactory.getGetChunk(this.myPeerID, addr, port, fileID, chunkNo);
			Client.sendMessage(this.peer.getChordManager().getSuccessor(0).getAddr(),
					this.peer.getChordManager().getSuccessor(0).getPort(), message, false);
		}
		return null;
	}


	private void deleteFile(String fileToDelete, int repDegree) {
		System.out.println("Received Delete for file: " + fileToDelete + ". Rep Degree: " + repDegree);
		boolean isFileStored = DBUtils.isFileStored(dbConnection, fileToDelete);
		if (isFileStored) {
			ArrayList<ChunkInfo> allChunks = DBUtils.getAllChunksOfFile(dbConnection, fileToDelete);
			allChunks.forEach(chunk -> {
				Utils.deleteFile(Peer.getPath().resolve(chunk.getFilename()));
				Peer.decreaseStorageUsed(chunk.getSize());
			});
			DBUtils.deleteFile(dbConnection, fileToDelete);
			repDegree--;
			System.out.println("Deleted file: " + fileToDelete);
		}
		
		if (repDegree > 1 || !isFileStored) {
			String message = MessageFactory.getDelete(myPeerID, fileToDelete, repDegree);
			PeerInfo successor = peer.getChordManager().getSuccessor(0);
			Client.sendMessage(successor.getAddr(), successor.getPort(), message, false);
			System.out.println("Forwarded delete: " + fileToDelete);
		}
		
	}
	
	private void parseDelete(String [] secondLine) {
		String fileToDelete = secondLine[0];
		int repDegree = Integer.parseInt(secondLine[1]);
		deleteFile(fileToDelete,repDegree);
	}
	private void parseInitDelete(String[] firstLine, String[] secondLine) {
		String fileToDelete = secondLine[0];
		int repDegree = DBUtils.getMaxRepDegree(dbConnection, fileToDelete);
		DBUtils.deleteFileFromBackupsRequested(dbConnection, fileToDelete);
		deleteFile(fileToDelete,repDegree);
	}


	private String parseStoredMsg(String[] lines) {
		System.out.println("Stored Received");
		String fileID = lines[0];
		Integer chunkNo = Integer.valueOf(lines[1]);
		Integer repDegree = Integer.valueOf(lines[2]);
		
		boolean iAmResponsible = DBUtils.amIResponsible(dbConnection, fileID);
		ChunkInfo chunkInfo = new ChunkInfo(chunkNo,fileID);
		boolean chunkExists = DBUtils.checkStoredChunk(dbConnection, chunkInfo);
		
		if(iAmResponsible) {
			
			PeerInfo peerWhichRequested = DBUtils.getPeerWhichRequestedBackup(dbConnection, fileID);
			if(chunkExists) { //Exists
				System.out.println("Chunk exists");
				repDegree++; // I am also storing the chunk
				chunkInfo.setActualRepDegree(repDegree);
				DBUtils.updateStoredChunkRepDegree(dbConnection, chunkInfo);
			} else {
				chunkInfo.setActualRepDegree(repDegree);
				chunkInfo.setSize(-1);
				DBUtils.insertStoredChunk(dbConnection, chunkInfo); //size -1 means that I do not have stored the chunk
			}
			
			if(peerWhichRequested != null) {
				String message = MessageFactory.getConfirmStored(myPeerID, fileID, chunkNo, repDegree);
				Client.sendMessage(peerWhichRequested.getAddr(), peerWhichRequested.getPort(), message, false);
			} else {
				System.out.println("ERROR: could not get peer which requested backup!");
				Utils.LOGGER.severe("ERROR: could not get peer which requested backup!");
			}
			return null;
		}
		if (chunkExists) {
			repDegree++;
		}
		AbstractPeerInfo predecessor = peer.getChordManager().getPredecessor();
		if (predecessor.isNull()) {
			System.err.println("Null predecessor");
		}else {
			String message = MessageFactory.getStored(myPeerID, fileID, chunkNo, repDegree);
			Client.sendMessage(predecessor.getAddr(),predecessor.getPort(), message, false);
		}
		return null;
	}

	private void parsePutChunkMsg(String[] header, String body) {

		ChordManager chordManager = peer.getChordManager();
		byte [] body_bytes = body.getBytes(StandardCharsets.ISO_8859_1);

		String id = header[0].trim();
		InetAddress addr = null;
		try {
			addr = InetAddress.getByName(header[1]);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		int port = Integer.parseInt(header[2].trim());

		String fileID = header[3];
		int chunkNo = Integer.parseInt(header[4]);
		int replicationDegree = Integer.parseInt(header[5]);

		Path filePath = Peer.getPath().resolve(fileID + "_" + chunkNo);

		PeerInfo peerThatRequestedBackup = new PeerInfo(id,addr,port);
		DBUtils.insertPeer(dbConnection, peerThatRequestedBackup);
		FileStoredInfo fileInfo = new FileStoredInfo(fileID, true);
		fileInfo.setPeerRequesting(peerThatRequestedBackup.getId());
		fileInfo.setDesiredRepDegree(replicationDegree);
		DBUtils.insertStoredFile(dbConnection, fileInfo);
		

		if(id.equals(myPeerID)) {//sou o dono do ficheiro que quero fazer backup...
			//nao faz senido guardarmos um ficheiro com o chunk, visto que guardamos o ficheiro
			//enviar o KEEPCHUNK
			PeerInfo nextPeer = chordManager.getSuccessor(0);
			String message = MessageFactory.getKeepChunk(id, addr, port, fileID, chunkNo, replicationDegree, body_bytes);
			Client.sendMessage(nextPeer.getAddr(),nextPeer.getPort(), message, false);
			return;
		}

		if(!Peer.capacityExceeded(body_bytes.length)) { //tem espaco para fazer o backup
			System.out.println("VOU GUARDAR");
			try {
				Utils.writeToFile(filePath, body_bytes);
				DBUtils.insertStoredChunk(dbConnection, new ChunkInfo(chunkNo,fileID, body_bytes.length));
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(replicationDegree == 1) {//sou o ultimo a guardar
				//enviar STORE ao que pediu o backup
				String message = MessageFactory.getStored(chordManager.getPeerInfo().getId(), fileID, chunkNo, 1);
				Client.sendMessage(addr, port, message, false);
				return;
			} else {
				//enivar KEEPCHUNK para o sucessor
				String message = MessageFactory.getKeepChunk(id, addr, port, fileID, chunkNo, replicationDegree - 1, body_bytes);
				Client.sendMessage(chordManager.getSuccessor(0).getAddr(),chordManager.getSuccessor(0).getPort(), message, false);
			}
		} else {
			//enviar KEEPCHUNK para o seu sucessor
			String message = MessageFactory.getKeepChunk(id, addr, port, fileID, chunkNo, replicationDegree, body_bytes);
			Client.sendMessage(chordManager.getSuccessor(0).getAddr(),chordManager.getSuccessor(0).getPort(), message, false);
			System.out.println("NAO TENHO ESPACO");

		}
	}

	private void parseKeepChunkMsg(String[] header, String body) {
		System.out.println("Received Keep Chunk");
		ChordManager chordManager = peer.getChordManager();
		byte [] body_bytes = body.getBytes(StandardCharsets.ISO_8859_1);

		String id_request = header[0].trim();
		InetAddress addr_request = null;
		try {
			addr_request = InetAddress.getByName(header[1]);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		int port_request = Integer.parseInt(header[2].trim());

		String fileID = header[3];
		int chunkNo = Integer.parseInt(header[4]);
		int replicationDegree = Integer.parseInt(header[5]);

		Path filePath = Peer.getPath().resolve(fileID + "_" + chunkNo);
		if(DBUtils.amIResponsible(dbConnection, fileID)) {//a mensagem ja deu uma volta completa. repDeg nao vai ser o desejado
			//enviar STORE para o predecessor
			System.out.println("SOU RESPONSAVEL_KEEP ");
			PeerInfo predecessor = (PeerInfo) chordManager.getPredecessor();
			String message = MessageFactory.getStored(myPeerID, fileID, chunkNo, 0);
			Client.sendMessage(predecessor.getAddr(), predecessor.getPort(), message, false);
			return;
		}
		if(id_request.equals(myPeerID)) {//I AM ASKING FOR THE BACKUP sou dono do ficheiro
			System.out.println("SOU DONO");
			//reencaminhar a mensagem para o proximo
			//TODO: Reencaminhar esta mal
			PeerInfo nextPeer = chordManager.getSuccessor(0);
			String message = MessageFactory.getKeepChunk(id_request, addr_request, port_request, fileID, chunkNo, replicationDegree, body_bytes); //TODO: check
			Client.sendMessage(nextPeer.getAddr(),nextPeer.getPort(), message, false);
			return;
		}

		if(!Peer.capacityExceeded(body_bytes.length)) { //tem espaco para fazer o backup
			System.out.println("VOU GUARDAR");
			DBUtils.insertStoredFile(dbConnection, new FileStoredInfo(fileID, false));
			DBUtils.insertStoredChunk(dbConnection, new ChunkInfo(chunkNo,fileID, body_bytes.length));
			System.out.println(body_bytes.length);
			try {
				Utils.writeToFile(filePath, body_bytes);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if(replicationDegree == 1) {//sou o ultimo a guardar
				//enviar STORE para o predecessor
				String message = MessageFactory.getStored(chordManager.getPeerInfo().getId(), fileID, chunkNo, 1);
				Client.sendMessage(chordManager.getPredecessor().getAddr(),chordManager.getPredecessor().getPort(), message, false);

			} else {
				//enivar KEEPCHUNK para o sucessor
				String message = MessageFactory.getKeepChunk(id_request, addr_request, port_request, fileID, chunkNo, replicationDegree - 1, body_bytes);
				Client.sendMessage(chordManager.getSuccessor(0).getAddr(),chordManager.getSuccessor(0).getPort(), message, false);
			}
			return;
		} else {
			System.out.println("NAO ESPACO");
			//reencaminhar KEEPCHUNK para o seu sucessor
			String message = MessageFactory.getKeepChunk(id_request, addr_request, port_request, fileID, chunkNo, replicationDegree, body_bytes);
			Client.sendMessage(chordManager.getSuccessor(0).getAddr(),chordManager.getSuccessor(0).getPort(), message, false);
			return;
		}
	}


	private void parseNotifyMsg(String[] firstLine, String[] secondLine) {
		String id = firstLine[2];
		InetAddress addr = socket.getInetAddress();
		int port = Integer.parseInt(secondLine[0].trim());
		PeerInfo potentialNewPredecessor = new PeerInfo(id, addr, port);
		AbstractPeerInfo previousPredecessor = peer.getChordManager().getPredecessor();
		if (previousPredecessor.isNull() || Utils.inBetween(previousPredecessor.getId(), myPeerID, potentialNewPredecessor.getId())) {
			Utils.LOGGER.info("Updated predecessor to " + potentialNewPredecessor.getId());
			this.peer.getChordManager().setPredecessor(potentialNewPredecessor);
		}
	}

	/**
	 * 
	 * @param socket
	 * @param response
	 */
	void sendResponse(SSLSocket socket, String response) {
		OutputStream sendStream;
		try {
			sendStream = socket.getOutputStream();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		byte[] sendData = response.getBytes(StandardCharsets.ISO_8859_1);
		try {
			sendStream.write(sendData);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

}
