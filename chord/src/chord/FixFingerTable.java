/**
 * 
 */
package chord;

import communication.Client;
import utils.UnsignedByte;

/**
 * @author anabela
 *
 */
public class FixFingerTable implements Runnable {

	private ChordManager chord;
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		fix_fingerTable();
		printFingerTable();
	}

	private void printFingerTable() {
		System.out.println("Tabela de dedos: "+chord.getPeerInfo().getId());
			chord.getFingerTable().forEach((v) -> { System.out.println("\t"+v.getId()); } );
	}

	public void fix_fingerTable() {
		for(int i = 0; i < ChordManager.getM(); i++) {
			String response = chord.lookup(new UnsignedByte((short) ((chord.getPeerInfo().getId() + Math.pow(2, i))% Math.pow(2, ChordManager.getM()))));
			response = response.trim();
			PeerInfo info = new PeerInfo(response);
			while(response.startsWith("Ask")) {
				response = Client.sendMessage(info.getAddr(), info.getPort(), "lookup "+ (short)(chord.getPeerInfo().getId() + Math.pow(2, i)% Math.pow(2, ChordManager.getM())));
				response = response.trim();
				info = new PeerInfo(response);
			}
			chord.getFingerTable().set(i, info);
		}
	}

	public FixFingerTable(ChordManager chord) {
		super();
		this.chord = chord;
	}
}