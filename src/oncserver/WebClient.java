package oncserver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import ourneighborschild.ONCServerUser;

public class WebClient
{
	private ClientState state;
	private UUID sessionID;
	private long loginTimestamp;
	private long lastTimestamp;
	private ONCServerUser webUser;
	
	WebClient(UUID sessionID, ONCServerUser webUser)
	{
		this.state = ClientState.Connected;
		this.sessionID = sessionID;
		loginTimestamp = new Date().getTime();
		lastTimestamp = loginTimestamp;
		this.webUser = webUser;
	}
	
	//getters
	ClientState getClientState() { return state; }
	String getSessionID() { return sessionID.toString(); }
	long getloginTimeStamp() { return loginTimestamp; }
	long getLastTimeStamp() { return lastTimestamp; }
	ONCServerUser getWebUser() { return webUser; }
	
	//setters
	void setClientState(ClientState state) {this.state = state; }
	void updateTimestamp() {this.lastTimestamp = new Date().getTime(); }
	
	String[] getClientTableRow()
    {
    	String[] row = new String[9];
    	row[0] = Long.toString(0);
    	
    	if(webUser != null)	//if server user is known, user their name
    	{
    		row[1] = webUser.getFirstname();
    		row[2] = webUser.getLastname();
    		row[3] = webUser.getPermission().toString();	
    	}
    	else
    	{
    		row[1] = "Anonymous";
    		row[2] = "Anonymous";
    		row[3] = "U";
    	}
    	
    	row[4] = state.toString();
    	row[5] = "O";	//get string of 1st character
    	
    	Calendar timestamp = Calendar.getInstance();
    	row[6] = Integer.toString(timestamp.get(Calendar.YEAR));
    	row[7] = "Web";
    	
    	timestamp.setTimeInMillis(loginTimestamp);
    	row[8] = new SimpleDateFormat("MM/dd H:mm:ss").format(timestamp.getTime());
    		
    	return row;
    }
}
