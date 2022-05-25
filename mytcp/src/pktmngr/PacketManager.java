package pktmngr;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class PacketManager {
	
	/*
	 * Structure of the my TCP packet: 
	 *
	 * +---+---+-----+---+---+-----+---+---+-----+------+
	 * | 0 | 1 | ... | 4 | 5 | ... | 8 | 9 | ... | 1023 |
	 * +---+---+-----+---+---+-----+---+---+-----+------+
	 *   ^  \________ __/ \________ __/ \___________ __/
	 *   |           V             V                V
	 *  flag        ACK      dataLength           data
	 *
	 * Flag byte meanings:
	 *		0 - refular packet
	 *		1 - SYN packet
	 *		2 - FIN packet
	 * 
	 * ACK field used by server to tell client WHICH PACKET IT WANTS TO RECEIVE
	 * ACK field used by client to tell server WHICH PACKET HE SENT
	*/

	public static DatagramPacket create(
			byte flag,
			int ack,
			String data,
			InetAddress address,
			int port
	) {
		byte[] packetBuffer = new byte[1024];

		// set flag
		packetBuffer[0] = flag;
		
		// set ack
		byte[] ackAsBytes = ByteBuffer.allocate(4).putInt(ack).array();
		for (int i = 0; i < 4; ++i) {
			packetBuffer[1 + i] = ackAsBytes[i];
		}
		
		// set dataLength
		byte[] dataLengthAsBytes = ByteBuffer.allocate(4).putInt(data.length()).array();
		for (int i = 0; i < 4; ++i) {
			packetBuffer[5 + i] = dataLengthAsBytes[i];
		}
		
		// set data
		byte[] dataAsBytes = data.getBytes();
		for (int i = 0; i < data.length(); ++i) {
			packetBuffer[9 + i] = dataAsBytes[i];
		}
		
		return new DatagramPacket(packetBuffer, 1024, address, port);
	}
	
	public static boolean isSynPacket(DatagramPacket pkt) {
		byte[] packetDataAsBytes = pkt.getData();
		if (packetDataAsBytes[0] == 1) {
			return true;
		}
		return false;
	}

	public static boolean isFinPacket(DatagramPacket pkt) {
		byte[] packetDataAsBytes = pkt.getData();
		if (packetDataAsBytes[0] == 2) {
			return true;
		}
		return false;
	}
	
	public static int getACK(DatagramPacket pkt) {
		byte[] packetDataAsBytes = pkt.getData();

		byte[] ackAsBytes = Arrays.copyOfRange(packetDataAsBytes, 1, 5);
		ByteBuffer wrapped = ByteBuffer.wrap(ackAsBytes);

		return wrapped.getInt();
	}
	
	public static String getData(DatagramPacket pkt) {
		byte[] packetDataAsBytes = pkt.getData();

		byte[] dataLengthAsBytes = Arrays.copyOfRange(packetDataAsBytes, 5, 9);
		ByteBuffer wrapped = ByteBuffer.wrap(dataLengthAsBytes);
		int dataLength = wrapped.getInt();

		byte[] dataAsBytes = Arrays.copyOfRange(packetDataAsBytes, 9, 9 + dataLength);

		return new String(dataAsBytes);
		
	}
	
	public static void printData(DatagramPacket pkt, String pktName) {

		System.out.printf(String.format("Packet \"%s\":%n", pktName));

		// print flag
		System.out.print("    FLAG = ");
		if (PacketManager.isSynPacket(pkt)) {
			System.out.println("1 (SYN)");
		}
		else if (PacketManager.isFinPacket(pkt)){
			System.out.println("2 (FIN)");	
		}
		else {
			System.out.println("0 (REGULAR)");
		}
		
		// print ack
		System.out.print("    ACK = ");
		System.out.println(PacketManager.getACK(pkt));

		// print data
		System.out.println("    DATA:");
		String data = PacketManager.getData(pkt);
		String[] splittedData = data.split("\\r?\\n");
		for (int i = 0; i < splittedData.length; ++i){
			if (i == 0){
				System.out.print("       \"");
			}
			else {
				System.out.print("        ");
			}
			System.out.print(splittedData[i]);
			if (i == splittedData.length - 1) {
				System.out.print("\"");
			}
			System.out.println();
		}
	}
}
