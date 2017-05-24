package oncserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerLoop extends Thread 
{
	private static final int SERVER_TIMEOUT = 3000;	//three seconds
	private ClientManager clientMgr;
	private ServerSocket listener;
	
	private boolean bRunServer;
	
	ServerLoop(ClientManager cmgr)
	{
		clientMgr = cmgr;
		bRunServer = true;
		
		//Create the client listener socket
		try
		{
			listener = new ServerSocket(8901);
			listener.setSoTimeout(SERVER_TIMEOUT);
		} 
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
     * The run method of this thread.
     */
    public void run()
    {
    	bRunServer = true;
    	
    	while (bRunServer)
    	{
    		try 
        	{
    			//Server loops listening for clients to connect
    			Socket clientSocket = listener.accept();
    			clientMgr.addDesktopClient(clientSocket);
        	}
    		catch(java.io.InterruptedIOException e)
    		{
    			// Expect to get this whenever the server socket times out
    			//System.out.println("Server Loop Interrupted IO Exception");
    		}
    		catch (IOException e)
    		{
    			e.printStackTrace();
    			//System.out.println("Server Loop IO Exception");
    		}
    	}
    }
    
    void stopServer() { bRunServer = false;}
    
    boolean terminateServer()
    {
    	boolean bServerSocketClosed = false;
    	try 
    	{
			listener.close();
			bServerSocketClosed = true;
		} 
    	catch (IOException e) 
    	{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	return bServerSocketClosed;
    }
}
