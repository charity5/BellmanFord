import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;


public class BFclient {	
	
	static int localPort = 0;
	static String localIP = "127.0.0.1";   //default to be 127.0.0.1, change for get local IP later
		
	static int timeout = 1;
	static Timer sendTimer;
	private static final int MAX_DATAGRAM_SIZE = 600;
	public static ArrayList<DistanceVector> nodeList = new ArrayList<DistanceVector>();
	
	static boolean sendFlag = true;
	
	private static final int MAX_SEGMENT_SIZE = 600;
	private static final int DATA_SIZE = MAX_SEGMENT_SIZE-100;
	public static ArrayList<Segment> segmentSendList = new ArrayList<Segment>();
	public static ArrayList<Segment> segmentRecList = new ArrayList<Segment>();

	
 	public static void main(String[] args) throws IOException{

		//File file = new File("./client4_2.txt");
 		File file = new File(args[0]);
		BufferedReader reader = null;
		try{
			reader = new BufferedReader(new FileReader(file));
			String tempString = null;
            tempString = reader.readLine();			
			String[] splitLocal = tempString.split(" ");
			localPort = Integer.parseInt(splitLocal[0]);
			timeout = Integer.parseInt(splitLocal[1]);
			/*get local IP*/
			localIP = Inet4Address.getLocalHost().getHostAddress();
			
			//System.out.println("local port is "+localPort);			
			while((tempString = reader.readLine())!=null){
				String[] splitNode1 = tempString.split(":");
				String nodeIP = splitNode1[0];				
				String[] splitNode2 = splitNode1[1].split(" ");
				int nodePort = Integer.parseInt(splitNode2[0]);
				float nodeWeight = Float.parseFloat(splitNode2[1]);
				
				DistanceVector node = new DistanceVector(nodeIP, nodePort, nodeWeight, true);
				nodeList.add(node);				
			}
			reader.close();		
		}catch(IOException e){
			e.printStackTrace();
		}finally{
			if(reader!=null){
				try{reader.close();
			}catch(IOException el){
				
			}
		}
		}
				
		//showNeighbor();	

		/*UDP socket to receive packet. */
		DatagramSocket receiveSocket = null;
		try{
			receiveSocket = new DatagramSocket(localPort);
		}catch(BindException e1){
			System.err.println("The port is already in use.");
			System.exit(-1);
		}catch(SocketException e2){
			e2.printStackTrace();
			System.exit(-1);
		}
				
		new UIThread().start();
		DVUpdateSend();
		sendTimer = new Timer();
	    sendTimer.schedule(new SendTask(sendTimer), timeout*1000);  

	    while(true){	    	    	
	    	byte[] receiveData = new byte[MAX_DATAGRAM_SIZE];
	    	DatagramPacket receivePacket = new DatagramPacket(receiveData,receiveData.length);

	    	try{
	    		//System.out.println("receive data ok?");
	    		receiveSocket.receive(receivePacket);
	    		
	    		/*extract header of file transfer*/
	    		byte[] command = new byte[100];
	    		System.arraycopy(receiveData, 0, command, 0, 100);
	    		
	    		String recCommand = new String(command);
	    		String[] splitCom = recCommand.split(" ");
	    		if(splitCom[0].equals("fileTransfer")){
	    			//System.out.println("!!!!receive!!! \n"+recCommand + "\n"+"lalala hahaha");	
	    			
	    			String sourceIP = splitCom[1];
	    			int sourcePort = Integer.parseInt(splitCom[2]);
	    			String destIP = splitCom[3];
	    			int destPort = Integer.parseInt(splitCom[4]);
	    			String fileType = splitCom[5];
    				int segmentNum = Integer.parseInt(splitCom[6]);
    				
    				//System.out.println("seqment num "+segmentNum);
    				//System.out.println("receive segment list length "+ segmentRecList.size());
    				
    				byte[] segContent = new byte[DATA_SIZE];
    				System.arraycopy(receiveData, 100, segContent, 0, DATA_SIZE);
    				Segment seg = new Segment(segContent);
    				segmentRecList.add(seg);
    				
    				/*file arrive at destination node, all segments have arrived, output all content */
    				if(segmentNum==segmentRecList.size() && localIP.equals(destIP) && localPort==destPort ){
    					//System.out.println("##### file arrive at dest node ####");
		
	    				byte[] fileContent = new byte[segmentNum*DATA_SIZE];
	    				int offset=0;
	    				for(int i=0; i<segmentNum;i++){
	    					System.arraycopy(segmentRecList.get(i).content, 0, fileContent, offset, segmentRecList.get(i).content.length);
	    					offset = offset + DATA_SIZE;	    					
	    				}
	    				
	    				File dir=new File("receiveFile");
	    		 		if(!dir.exists()){
	   		 			dir.mkdir();
	    		 		}
	    				FileOutputStream fos = new FileOutputStream("./receiveFile/receiveTest."+fileType);	
	    				fos.write(fileContent);
                        fos.close();
    					
						System.out.println("Packet received");
						System.out.println("Source = "+sourceIP+":"+sourcePort);
						System.out.println("Destination = "+destIP+":"+destPort);
						System.out.println("File received successfully");
						
						segmentRecList.clear();
						continue;	    				
    				}
    				
    				/*file transfer through intermediate node*/ 
	    			if( (segmentNum==segmentRecList.size()) && (!localIP.equals(destIP) || localPort!=destPort)){
	    				int destIndex = getNodeIndex(destIP, destPort);
	    				String nextHopIP = nodeList.get(destIndex).linkIP;
	    				int nextHopPort = nodeList.get(destIndex).linkPort;
	    				
	    				System.out.println("Packet received");
    					System.out.println("Source =  "+sourceIP+":"+sourcePort);
    					System.out.println("Destination = "+destIP+":"+destPort);
    					System.out.println("Nest hop = "+nextHopIP+":"+nextHopPort);

	    				intermediateSend(recCommand, nextHopIP, nextHopPort);

	    				continue;
	    			}
 	
	    			continue;
	    		}
	    		
	    		/*Bellman Ford construction*/
	    		String message = new String(receiveData);
	    		//System.out.println("!!!!receive!!! \n"+message + "\n"+"lalala hahaha");	    		
	    		
	    		String[] split = message.split(" ");
	    		if(split[0].equals("command")){
	    			receiveCommand(message);
	    			//printDVTable();
	    			continue;
	    		}
	    		makeDVTable(message);
	    		//printDVTable();
	    	}catch(Exception e){
	    		e.printStackTrace();
	    		break;
	    	}	    	
	    	
	    }
		receiveSocket.close();
		System.exit(0);

	}
	
	public static class DistanceVector{
		String destIP = null;
		int destPort = 0;
		float savedWeight = Float.POSITIVE_INFINITY;    //save the weight of the neighbor link
		float cost = Float.POSITIVE_INFINITY;           //once the neighbor is online, cost become savedWeight, otherwise is infinity
		boolean neighbor = false;
		
		float DV = Float.POSITIVE_INFINITY;
		String linkIP = null;      //in forwarding table, the first node in order to reach final destination
		int linkPort = 0;
		
		String preMessage;         //record the last message sent from this neighbor node		
		Timer closeTimer;
		
		public DistanceVector(String destIP, int destPort, float savedWeight, boolean neighbor){
			this.destIP = destIP;
			this.destPort = destPort;
			this.savedWeight = savedWeight;
			this.neighbor = neighbor;			
		}
		public DistanceVector(String destIP, int destPort, boolean neighbor, float DV, String linkIP, int linkPort){
			this.destIP = destIP;
			this.destPort = destPort;
			this.neighbor = neighbor;
			this.DV = DV;
			this.linkIP = linkIP;
			this.linkPort = linkPort;
		}
		public DistanceVector(String destIP, int destPort, float savedWeight, float cost, boolean neighbor, float DV, String linkIP, int linkPort, String preMessage){
			this.destIP = destIP;
			this.destPort = destPort;
			this.savedWeight = savedWeight;
			this.cost = cost;
			this.neighbor = neighbor;
			this.DV = DV;
			this.linkIP = linkIP;
			this.linkPort = linkPort;
			this.preMessage = preMessage;
		}
			
	}

	/*class used for file transfer*/
	public static class Segment{
		byte[] content;
		
		public Segment(byte[] content){
			this.content = content;
		}
	}

	/* class of sending update route */
	static class SendTask extends TimerTask{		
		Timer timer;
        public SendTask(Timer timer){
        	this.timer = timer;
        }
        
        public void run(){        	    	
        	DatagramSocket sendSocket = null;
        	try {
				sendSocket = new DatagramSocket();
			} catch (SocketException e) {
				e.printStackTrace();
			}
        	
        	for(int i=0; i<nodeList.size(); i++){
        		if(nodeList.get(i).neighbor){      			
        		    InetAddress destIP = null;		    
					try {
						destIP = InetAddress.getByName(nodeList.get(i).destIP);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}					
        			int destPort = nodeList.get(i).destPort;
        			String neighborInfo = nodeList.get(i).savedWeight+" "+makeDVSend(nodeList.get(i).destIP, destPort);
        			byte[] sendData = neighborInfo.getBytes();
        			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,destIP, destPort);

        			try{
        				sendSocket.send(sendPacket);        				
        				//System.out.println("send successfully");
        				//System.out.println(neighborInfo+" to "+destIP+" "+destPort);
        				
        			}catch(IOException e){
        				e.printStackTrace();
        			}
        			
        		}
        	}
        	/*restart the sending process */
        	timer.schedule(new SendTask(timer), timeout*1000); 
        }
		
	}
	
	static class CloseTask extends TimerTask{
		int index;
		public CloseTask(String fromIP, int fromPort){
			int temp = getNodeIndex(fromIP, fromPort);
			this.index = temp;
		}
		
		public void run(){
			//System.out.println("****** into CloseTask");
			//System.out.println("!close "+ nodeList.get(index).destPort);
			
			nodeList.get(index).cost = Float.POSITIVE_INFINITY;
			nodeList.get(index).DV = Float.POSITIVE_INFINITY;
			nodeList.get(index).neighbor = false;
			nodeList.get(index).linkIP = null;
			nodeList.get(index).linkPort = 0;
			sendFlag = true;
			
			updateFlagSend();
			DVUpdateSend();
			renewDVTable();			
			//printDVTable();
			System.out.println("( "+nodeList.get(index).destIP+" : "+nodeList.get(index).destPort+" )  is closed!");
		}
	}
	
	static class UIThread extends Thread{
		public void run(){
			BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
			String command = null;			
			
			while(true){
				try {
					command = input.readLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				String[] split = command.split(" ");
				String commandWord = split[0];
				//System.out.println(commandWord);
				
				if(commandWord.equals("LINKDOWN")){
					if(split.length != 3){
						System.out.println("Input format incorrect! Try HELP for reference.");
						continue;
					}
					String nodeIP = split[1];
					int nodePort = Integer.parseInt(split[2]);
										
					if(nodeIP.equals(localIP) && nodePort == localPort){
						System.out.println("You type the IP and port number of yourself!");
						continue;
					}
					
					int nodeIndex = getNodeIndex(nodeIP, nodePort);
					if(nodeIndex == -1){
						System.out.println(nodeIP+" "+nodePort+"  doesn't exsit!");
						continue;
					}
					if(!nodeList.get(nodeIndex).neighbor){
						System.out.println(nodeIP+" "+nodePort+"  is not your neighbor!");
						continue;
					}
					if(nodeList.get(nodeIndex).neighbor){
						//System.out.println("into LINKDOWN if");

						nodeList.get(nodeIndex).cost = Float.POSITIVE_INFINITY;
						nodeList.get(nodeIndex).neighbor = false;
						nodeList.get(nodeIndex).DV = Float.POSITIVE_INFINITY;
						nodeList.get(nodeIndex).linkIP = null;
						nodeList.get(nodeIndex).linkPort = 0;
						commandSend("LINKDOWN", nodeIP, nodePort);
						
						updateFlagSend();  //make other node to set previous DV table to infinity
						DVUpdateSend();    //send new info
						renewDVTable();    //first set itself previous DV table to infinity
		
					}
					DVUpdateSend();
					//printDVTable();
					System.out.println("linkdown accomplish!");
					continue;
	
				}
				
				if(commandWord.equals("LINKUP")){
					if(split.length != 3){
						System.out.println("Input format incorrect! Try HELP for reference.");
						continue;
					}
					String nodeIP = split[1];
					int nodePort = Integer.parseInt(split[2]);

					if(nodeIP.equals(localIP) && nodePort == localPort){
						System.out.println("You type the IP and port number of yourself!");
						continue;
					}
					
					int nodeIndex = getNodeIndex(nodeIP, nodePort);
					if(nodeIndex == -1){
						System.out.println(nodeIP+" "+nodePort+"  doesn't exsit!");
						continue;
					}
					if(nodeList.get(nodeIndex).neighbor){
						System.out.println(nodeIP+" "+nodePort+"  is already your neighbor!");
						continue;
					}
					if(!nodeList.get(nodeIndex).neighbor){
						//System.out.println("into LINKUP if");

						nodeList.get(nodeIndex).cost = nodeList.get(nodeIndex).savedWeight;
						nodeList.get(nodeIndex).neighbor = true;
						nodeList.get(nodeIndex).DV = nodeList.get(nodeIndex).cost;
						commandSend("LINKUP", nodeIP, nodePort);

						updateFlagSend();  //make other node to set previous DV table to infinity
						DVUpdateSend();    //send new info to neighbor
						renewDVTable();    //first set itself previous DV table to infinity					
					}
					DVUpdateSend();
					//printDVTable();
					System.out.println("linkup accomplish!");
					continue;

				}
				
                if(commandWord.equals("CHANGECOST")){
                	if(split.length != 4){
						System.out.println("Input format incorrect! Try HELP for reference.");
						continue;
					}
					String nodeIP = split[1];
					int nodePort = Integer.parseInt(split[2]);
					float cost = Float.parseFloat(split[3]);
					
					if(nodeIP.equals(localIP) && nodePort == localPort){
						System.out.println("You type the IP and port number of yourself!");
						continue;
					}
					
					int nodeIndex = getNodeIndex(nodeIP, nodePort);
					if(nodeIndex == -1){
						System.out.println(nodeIP+" "+nodePort+"  doesn't exsit!");
						continue;
					}
					if(!nodeList.get(nodeIndex).neighbor){
						System.out.println(nodeIP+" "+nodePort+"  is not your neighbor!");
						continue;
					}
					if(nodeList.get(nodeIndex).neighbor){
						//System.out.println("into CHANGECOST if");
												
						nodeList.get(nodeIndex).savedWeight = cost;
						nodeList.get(nodeIndex).cost = cost;						
						nodeList.get(nodeIndex).DV = Float.POSITIVE_INFINITY;

						updateFlagSend();  //make other node to set previous DV table to infinity
						DVUpdateSend();    //send new info
						renewDVTable();    //first set previous DV table to infinity

					}
					DVUpdateSend();
					System.out.println("changecost accomplish!");
					continue;
					
				}
                
                if(commandWord.equals("SHOWRT")){
                	DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		            Calendar cal = Calendar.getInstance();
		            System.out.println(dateFormat.format(cal.getTime()));
                	System.out.println("Distance vector list is: ");
					for(int i=0; i<nodeList.size(); i++){
						System.out.println("Destination = "+nodeList.get(i).destIP+":"+nodeList.get(i).destPort+", Cost = "+nodeList.get(i).DV+", Link = ("+nodeList.get(i).linkIP+":"+nodeList.get(i).linkPort+")");
						
						}
					continue;
					
				}
                
                if(commandWord.equals("CLOSE")){
                	if(split.length != 1){
						System.out.println("Input format incorrect! Try HELP for reference.");
						continue;
					}
					
						System.out.println("successfully close!");
						System.exit(1);
				}
                
                if(commandWord.equals("TRANSFER")){
                	if(split.length!=4){
						System.out.println("Input format incorrect! Try HELP for reference.");
						continue;
					}
					String fileName = split[1];
					String destIP = split[2];
					int destPort = Integer.parseInt(split[3]);
					
					if(destIP.equals(localIP) && destPort==localPort){
						System.out.println("Can not transfer file to yourself.");
						continue;
					}
					
					int destIndex = getNodeIndex(destIP, destPort);
					if(destIndex == -1){
						System.out.println(destIP+" "+destPort+" doesn't exist!");
						continue;
					}

					if(nodeList.get(destIndex).DV == Float.POSITIVE_INFINITY ){
						System.out.println("can not reach destination node!");
						continue;
					}
										
					String[] splitFileName = fileName.split("\\.");
					String fileType = splitFileName[splitFileName.length-1];					
					int segmentNum = fileFragment(fileName);
					//System.out.println(fileName);
					//System.out.println(fileType);
					//System.out.println(segmentNum);
					
					String header = "fileTransfer "+localIP+" "+localPort+" "+destIP+" "+destPort+" "+fileType+" "+segmentNum+" ";
					String nextHopIP = nodeList.get(destIndex).linkIP;
					int nextHopPort = nodeList.get(destIndex).linkPort;
					segmentSend(header, nextHopIP, nextHopPort);
					System.out.println("nexthop is "+nextHopIP+":"+nextHopPort);					
					continue;
 	
				}
                
                if(commandWord.equals("HELP")){
                	System.out.println("All acceptable command:");
					System.out.println("1. LINKDOWN <IPAddress> <Port>");
					System.out.println("2. LINKUP <IPAddress> <Port>");
					System.out.println("3. CHANGECOST <IPAddress> <Port> <cost>");
					System.out.println("4. SHOWRT");
					System.out.println("5. CLOSE");
					System.out.println("6. TRANSFER <filename> <destIP> <destPort>");
					continue;
				}
                
                else{
                	System.out.println("Command doesn't exist! Try HELP for reference.");
                }
				
				
			}
		}
	}
	
	
	
	@SuppressWarnings("resource")
	static public int fileFragment(String fileName){
		segmentSendList.clear();		
		File file = new File(fileName);
		FileInputStream fis = null;
		byte[] bFile = new byte[(int) file.length()];
		
		try{
			fis = new FileInputStream(file);
			fis.read(bFile);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		int fileLength = bFile.length;		
		int segmentNum = 0;
		int offset = 0;
		
		/*file length less than one DATA_SIZE*/
		if(fileLength<=DATA_SIZE){
			byte[] segmentContent = new byte[DATA_SIZE];
			System.arraycopy(bFile, 0, segmentContent, 0, fileLength);			
			//System.out.println(segmentContent.length);			
			Segment segment = new Segment(segmentContent);
			segmentSendList.add(segment);
			segmentNum = 1;
			return segmentNum;
		}
		
		/*fragmentation*/
		for(int i= fileLength; i>DATA_SIZE; i=i-DATA_SIZE){
			byte[] segmentContent = new byte[DATA_SIZE];
			System.arraycopy(bFile, offset, segmentContent, 0, DATA_SIZE);
			Segment segment = new Segment(segmentContent);
			segmentSendList.add(segment);
			segmentNum++;
			offset = offset +DATA_SIZE;
		}
		
		if(offset<fileLength-1){
			byte[] segmentContent = new byte[DATA_SIZE];
			System.arraycopy(bFile, offset, segmentContent, 0, fileLength-offset);
			Segment segment = new Segment(segmentContent);
			segmentSendList.add(segment);
			segmentNum++;
		}
		return segmentNum;	
	}
	
	static public void segmentSend(String header, String nextHopIP, int nextHopPort){
		//System.out.println("into segmentSend");

		DatagramSocket sendSocket = null;
    	try {
			sendSocket = new DatagramSocket();	
		} catch (SocketException e) {
			e.printStackTrace();
		}
    	
    	InetAddress toIP = null;	    
		try {
			toIP = InetAddress.getByName(nextHopIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		int toPort = nextHopPort;		
		byte[] headerByte = header.getBytes();
		for(int i=0; i<segmentSendList.size();i++){
			byte[] message = new byte[MAX_SEGMENT_SIZE];		
			System.arraycopy(headerByte, 0, message, 0, headerByte.length);
			//System.out.println(segmentSendList.get(i).content.length);
			System.arraycopy(segmentSendList.get(i).content, 0, message, 100, segmentSendList.get(i).content.length);
			
			DatagramPacket sendPacket = new DatagramPacket(message, message.length,toIP, toPort);
			try{
				sendSocket.send(sendPacket);				
//				System.out.println("one segment sent");
//				System.out.println(segmentSendList.size());
//				System.out.println(header.length());
//				System.out.println(segmentSendList.size());
//	    		System.out.println("@@@@"+message);
				
			}catch(IOException e){
				e.printStackTrace();
			}   	
		}
		
		System.out.println("Next Hop = "+nextHopIP+":"+nextHopPort);
		System.out.println("File sent successfully");
			
	}
	
	static public void intermediateSend(String header, String nextHopIP, int nextHopPort){
		//System.out.println("into segmentSend");
		DatagramSocket sendSocket = null;
    	try {
			sendSocket = new DatagramSocket();	
		} catch (SocketException e) {
			e.printStackTrace();
		}
    	
    	InetAddress toIP = null;	    
		try {
			toIP = InetAddress.getByName(nextHopIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		int toPort = nextHopPort;		
		byte[] headerByte = header.getBytes();
		for(int i=0; i<segmentRecList.size();i++){
			byte[] message = new byte[MAX_SEGMENT_SIZE];
			System.arraycopy(headerByte, 0, message, 0, headerByte.length);
			System.arraycopy(segmentRecList.get(i).content, 0, message, 100, segmentRecList.get(i).content.length);
			
			DatagramPacket sendPacket = new DatagramPacket(message, message.length,toIP, toPort);
			try{
				sendSocket.send(sendPacket);				

			}catch(IOException e){
				e.printStackTrace();
			}   	
		}		
		//System.out.println("intermediate node send accomplish");
		
	}
	
	
 	static public String makeDVSend(String toIP, int toPort){
		//System.out.println("into makeDVSend");
		String dataSend = null;
		try{
			dataSend = localIP+" "+localPort+" "+localIP+" "+localPort+" "+ 0 +" ";
			for(int i=0; i<nodeList.size();i++){
				String destIP = nodeList.get(i).destIP;
				int destPort = nodeList.get(i).destPort;
				float DV = nodeList.get(i).DV;	
				
				//add posion reverse				
				if( toIP.equals(nodeList.get(i).linkIP) && toPort == nodeList.get(i).linkPort /*&& !( destIP.equals(toIP) && destPort==toPort )*/ ){
					//System.out.println("#####use posion reverse######");
					DV = Float.POSITIVE_INFINITY;					
				}

				String temp = destIP+" "+destPort+" "+DV+" ";
				dataSend= dataSend+temp;				
			}
		}catch(ArrayIndexOutOfBoundsException e){
			System.err.println("The size of Distance Vector Table is out of bound.");
		}
		return dataSend;
	}
	
	
	static public void DVUpdateSend(){    	
    	//System.out.println("into DVUpdateSend");    	
    	DatagramSocket sendSocket = null;
    	try {
			sendSocket = new DatagramSocket();	
		} catch (SocketException e) {
			e.printStackTrace();
		}
    	
    	for(int i=0; i<nodeList.size(); i++){
    		if(nodeList.get(i).neighbor){      			
    		    InetAddress destIP = null;	    
				try {
					destIP = InetAddress.getByName(nodeList.get(i).destIP);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				
    			int destPort = nodeList.get(i).destPort;
    			String neighborInfo = nodeList.get(i).savedWeight+" "+makeDVSend(nodeList.get(i).destIP, destPort);
    			byte[] sendData = neighborInfo.getBytes();
    			DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,destIP, destPort);
    			try{
    				sendSocket.send(sendPacket);   				
    				//System.out.println("send successfully");
    				//System.out.println(localPort+" send "+neighborInfo+" to "+destPort);
    				
    			}catch(IOException e){
    				e.printStackTrace();
    			}
    		}
    	}
	}
	
	
	static public void makeDVTable(String message){
		Boolean changeFlag = false;
		String[] split = message.split(" ");
		float savedWeight = Float.parseFloat(split[0]);
		String fromIP = split[1];
		int fromPort = Integer.parseInt(split[2]);
		int neighborIndex = getNodeIndex(fromIP, fromPort);

		/*online neighbor node doesn't exist in node list*/
		if(neighborIndex==-1){
			DistanceVector node = new DistanceVector(fromIP, fromPort, savedWeight, savedWeight,true,savedWeight, fromIP, fromPort, message);
			nodeList.add(node);
			node.closeTimer = new Timer();
			node.closeTimer.schedule(new CloseTask(fromIP, fromPort), timeout*3000);			
			DVUpdateSend();
			return;
		}

		/*restart close timer*/
		try {
			nodeList.get(neighborIndex).closeTimer.cancel();
			nodeList.get(neighborIndex).closeTimer = new Timer();
			nodeList.get(neighborIndex).closeTimer.schedule(new CloseTask(fromIP, fromPort), timeout * 3000);
		} catch (ArrayIndexOutOfBoundsException e) {
		} catch (NullPointerException e) {
			nodeList.get(neighborIndex).closeTimer = new Timer();
			nodeList.get(neighborIndex).closeTimer.schedule(new CloseTask(fromIP, fromPort), timeout * 3000);
		}
		
        if(message.equals(nodeList.get(neighborIndex).preMessage) && !sendFlag){			
			sendFlag = false;
			return;
		}

        
		if(savedWeight != nodeList.get(neighborIndex).savedWeight){
			System.out.println("weight change!");
			nodeList.get(neighborIndex).savedWeight = savedWeight;
			nodeList.get(neighborIndex).cost = savedWeight;
			changeFlag = true;
		}
		
		//make link(local, neighbor) online, cost=savedWeight;
		if(nodeList.get(neighborIndex).cost==Float.POSITIVE_INFINITY){
			nodeList.get(neighborIndex).cost = nodeList.get(neighborIndex).savedWeight;
			nodeList.get(neighborIndex).neighbor = true;
		}
		float cost = nodeList.get(neighborIndex).cost;

		
		/*deal with destination node in DV */
		for(int i=3; i<split.length-1;i=i+3){
			String destIP = split[i];
			int destPort = Integer.parseInt(split[i+1]);
			float DV = Float.parseFloat(split[i+2]);
			int destIndex = getNodeIndex(destIP, destPort);			

			/*destination node doesn't exist, create new node*/
			if(destIndex==-1 && (!localIP.equals(destIP) || localPort!=destPort)){
				DistanceVector node = new DistanceVector(destIP, destPort, false, cost+DV, fromIP, fromPort);
				nodeList.add(node);
				changeFlag = true;
				continue;
			}

			if(localIP.equals(destIP) && localPort==destPort){
				continue;
			}
			
			
			if((cost+DV) < nodeList.get(destIndex).DV){
					nodeList.get(destIndex).DV = cost + DV;
					nodeList.get(destIndex).linkIP = fromIP;
					nodeList.get(destIndex).linkPort = fromPort;
					changeFlag = true;
				}
		}
		
		if((!message.equals(nodeList.get(neighborIndex).preMessage)) || changeFlag){
			DVUpdateSend();
		}
		
		nodeList.get(neighborIndex).preMessage = message;
		
	}
	
	static public void receiveCommand(String message){

		String[] split = message.split(" ");
		String command = split[1];
		if(command.equals("renewDV")){
			renewDVTable(); 
			sendFlag = true;
		}
		if(command.equals("LINKDOWN")){
			String fromIP = split[2];
			int fromPort = Integer.parseInt(split[3]);
			int fromIndex = getNodeIndex(fromIP, fromPort);
			nodeList.get(fromIndex).cost = Float.POSITIVE_INFINITY;
			nodeList.get(fromIndex).neighbor = false;
			nodeList.get(fromIndex).DV = Float.POSITIVE_INFINITY;
			nodeList.get(fromIndex).linkIP = null;
			nodeList.get(fromIndex).linkPort = 0;
			DVUpdateSend();
			sendFlag = true;
		}
		if(command.equals("LINKUP")){
			String fromIP = split[2];
			int fromPort = Integer.parseInt(split[3]);
			int fromIndex = getNodeIndex(fromIP, fromPort);
			nodeList.get(fromIndex).cost = nodeList.get(fromIndex).savedWeight;
			nodeList.get(fromIndex).neighbor = true;
			nodeList.get(fromIndex).DV = nodeList.get(fromIndex).cost;
			DVUpdateSend();
			sendFlag = true;
		}
		
	}
	
	static public void renewDVTable(){
		for(int i=0; i<nodeList.size();i++){
			nodeList.get(i).DV = Float.POSITIVE_INFINITY;
		}
	}
	
	static public void commandSend(String command, String toIP, int toPort){
		DatagramSocket sendSocket = null;
    	try {
			sendSocket = new DatagramSocket();			
		} catch (SocketException e) {
			e.printStackTrace();
		}
    	
    	InetAddress sendIP = null;	    
		try {
			sendIP = InetAddress.getByName(toIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
		String message = "command "+command+" "+localIP+" "+localPort+" ";
		byte[] sendCommand = message.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendCommand, sendCommand.length, sendIP, toPort);
		
		try{
			sendSocket.send(sendPacket);		
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	static public void updateFlagSend(){
		for(int i =0; i<nodeList.size(); i++){			
				//System.out.println("into updateFlagSend");
				String toIP = nodeList.get(i).destIP;
				int toPort = nodeList.get(i).destPort;
				DatagramSocket sendSocket = null;
		    	try {
					sendSocket = new DatagramSocket();
					
				} catch (SocketException e) {
					e.printStackTrace();
				}
		    	
		    	InetAddress sendIP = null;	    
				try {
					sendIP = InetAddress.getByName(toIP);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
				
				String message = "command renewDV ";
				byte[] sendCommand = message.getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendCommand, sendCommand.length, sendIP, toPort);
				
				try{
					sendSocket.send(sendPacket);		
				}catch(IOException e){
					e.printStackTrace();
				}
			
		}
	}
	
	
	
	static public int getNodeIndex(String IP, int port){
		for(int i = 0; i<nodeList.size();i++){
			if(nodeList.get(i).destIP.equals(IP) && nodeList.get(i).destPort==port){
				return i;
			}
		}		
		return -1;
	}


//	static public void showNeighbor(){
//		System.out.println("neighbor client information:");
//		System.out.println(nodeList.size());
//		for(int i=0;i<nodeList.size();i++){
//			if(nodeList.get(i).neighbor){
//				System.out.println(nodeList.get(i).destIP +" "+ nodeList.get(i).destPort + " "+ nodeList.get(i).savedWeight);
//			}
//		}
//	}
	
	static public void printDVTable(){
		System.out.println("DVTable:");
		System.out.println(nodeList.size());
		for(int i=0;i<nodeList.size();i++){
			System.out.println("destination node: "+ nodeList.get(i).destIP+":"+ nodeList.get(i).destPort);
			System.out.println("Shortest Path Cost(DV): "+nodeList.get(i).DV+" link node: "+nodeList.get(i).linkIP+":"+nodeList.get(i).linkPort);
			
		}
	}

}
	
	





	
	
