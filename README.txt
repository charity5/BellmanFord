Programming Assignment 2

Name: Lin Su
UNI: ls3201

This is the distributed client using Bellman-Ford algorithm. The work is built using Java. Clients may be distributed across different machines and more than one client can be on the same machine.
Clients are identified by an <IP address, Port Number> tuple. Each client process gets the set of neighbors, the link weight and a timeout value from input txt file.
Client has a UDP socket to which it listens for incoming message and its neighbors know the number of that port.
Each client maintains a distance vector, that is a list of <destination, cost> tuples, where the cost the current estimated shortest path to the destination. Clients exchange distance vector information once the value of DV is changed.

Java version:
java version "1.7.0_67"
Java(TM) SE Runtime Environment (build 1.7.0_67-b01)
Java HotSpot(TM) 64-Bit Server VM (build 24.65-b04, mixed mode)
--------------------------------------------------------------------------------------
Instruction of invoking the client:
% make
% java BFclient ./client0.txt

client0.txt is the previous configured file to record the information of neighbors.
--------------------------------------------------------------------------------------
User Interface

You can also try HELP to get the input format of each command. Below is the example of each command.

1. LINKDOWN <IP> <Port>
This allows the user to destroy an existing link, i.e.,change the link cost to infinity to the mentioned neighbor.
example: LINKDOWN 160.39.146.96 4116

2. LINKUP <IP> <Port>
This allows the user to restore the link to the mentioned neighbor to the original value after it was destroyed by a LINKDOWN.
example: LINKUP 160.39.146.96 4116

3. CHANGECOST <IP> <Port> <cost>
This allows the user to change the cost of the link connected between the neighbors.
example: CHANGECOST 160.39.146.96 4116 50

4. SHOWRT
This allows the user to view the current routing table of the client.
Note: If other node doesn’t go online, the cost to that node will be INFINITY. Once the node goes online, the cost to that node will change to the cost of shortest path.

5. CLOSE
With this command the client process should close/shutdown.
And other client will detect its close after 3*TIMEOUT.

6. TRANSFER <filename> <IP> <Port>
This allows the user to transfer files (txt,jpg) among different clients. The file sent should be in the same directory as the BFclient java file. The received file will be in a new created directory “receive”.
example: TRANSFER ./test.jpg 160.39.146.96 4116

7. HELP
This will print out all available command.
