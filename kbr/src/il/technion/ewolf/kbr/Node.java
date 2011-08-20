package il.technion.ewolf.kbr;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.concurrent.Future;

public interface Node {
	
	public Key getKey();
	public Future<Socket> openConnection(String tag) throws IOException;
	public Future<DatagramSocket> openUdpConnection(String tag) throws IOException;
	public OutputStream sendMessage(String tag) throws IOException;
	public byte[] sendMessage(String tag, byte[] message) throws IOException;
	
}
