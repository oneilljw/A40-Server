package oncserver;

import ourneighborschild.ChatMessage;

import com.google.gson.Gson;

public class ServerChatManager
{
	private static ServerChatManager instance = null;
	private ClientManager clientMgr;
	
	private ServerChatManager()
	{
		 //Initialize the client manager interface
		clientMgr = ClientManager.getInstance();
	}
	
	public static ServerChatManager getInstance()
	{
		if(instance == null)
			instance = new ServerChatManager();
		
		return instance;
	}
	
	String requestChat(String json)
	{
		//parse json into ChatMessage object
		Gson gson = new Gson();
		ChatMessage chatReq = gson.fromJson(json, ChatMessage.class);
		
		//find client. If found, ask client manager to queue the request, else respond not found
		DesktopClient receiverClient = clientMgr.findClient(chatReq.getReceiverClientID());
		if(receiverClient != null)
		{
			clientMgr.notifyClient(receiverClient, "CHAT_REQUESTED" + json);
			return "CHAT_REQUEST_SENT";
		}
		else
			return "CHAT_REQUEST_FAILED";
	}
	String acceptChat(String json)
	{
		//parse json into ChatMessage object
		Gson gson = new Gson();
		ChatMessage chatReq = gson.fromJson(json, ChatMessage.class);
		
		//find client. If found, ask client manager to queue the acceptance, else respond not found
		DesktopClient receiverClient = clientMgr.findClient(chatReq.getReceiverClientID());
		if(receiverClient != null)
		{
			clientMgr.notifyClient(receiverClient, "CHAT_ACCEPTED" + json);
			return "CHAT_ACCEPTED_SENT";
		}
		else
			return "CHAT_ACCEPT_FAILED";
	}
	String forwardChatMessage(String json)
	{
		//parse json into ChatMessage object
		Gson gson = new Gson();
		ChatMessage chatMssg = gson.fromJson(json, ChatMessage.class);
		
		//find client. If found, ask client manager to queue the request, else respond not found
		DesktopClient receiverClient = clientMgr.findClient(chatMssg.getReceiverClientID());
		if(receiverClient != null)
		{
			clientMgr.notifyClient(receiverClient, "CHAT_MESSAGE" + json);
			return "CHAT_MSSG_SENT";
		}
		else
			return "CHAT_MSSG_FAILED";
	}
	
	String endChat(String json)
	{
		//parse json into ChatMessage object
		Gson gson = new Gson();
		ChatMessage chatEnd = gson.fromJson(json, ChatMessage.class);
		
		//find client. If found, ask client manager to queue the request, else respond not found
		DesktopClient receiverClient = clientMgr.findClient(chatEnd.getReceiverClientID());
		if(receiverClient != null)
		{
			clientMgr.notifyClient(receiverClient, "CHAT_ENDED" + json);
			return "CHAT_END_SENT";
		}
		else
			return "CHAT_END_FAILED";
	}
}
