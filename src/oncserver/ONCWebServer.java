package oncserver;

import java.io.IOException;
import java.net.InetSocketAddress;

import ourneighborschild.WebsiteStatus;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;

public class ONCWebServer
{
	private static final int WEB_SERVER_PORT = 8902;
	
	private static ONCWebServer instance = null;
	private static WebsiteStatus websiteStatus;
	
	private ONCWebServer() throws IOException
	{
		ServerUI serverUI = ServerUI.getInstance();
		
		HttpServer server = HttpServer.create(new InetSocketAddress(WEB_SERVER_PORT), 0);
		ONCHttpHandler oncHttpHandler = new ONCHttpHandler();
		
		String[] contexts = {"/welcome", "/logout", "/login", "/dbStatus", "/agents", "/families", "/familystatus",
							"/getfamily", "/references", "/getagent", "/getmeal", "/children", "/familysearch", 
							"/adults", "/wishes", "/oncsplash", "/clearx", "/onclogo", "/oncstylesheet", 
							"/oncdialogstylesheet", "/newfamily", "/reqchangepw", "/timeout",
							"/address", "/referral", "/referfamily", "/familyupdate", "/updatefamily",
							"/changepw", "/startpage", "/vanilla", "/getuser", "/getstatus",
							"/profileunchanged", "/updateuser", "/driverregistration", "/registerdriver",
							"/onchomepage", "/volunteersignin", "/signinvolunteer", "/contactinfo",
							"/jquery.js", "/favicon.ico", "/groups", "/commonfamily.js"};
		
		HttpContext context;
//		Filter paramFilter = new ParameterFilter();
		
		for(String contextname:contexts)
		{
			context = server.createContext(contextname, oncHttpHandler);
			context.getFilters().add(new ParameterFilter());
//			context.getFilters().add(paramFilter);
		}

		server.setExecutor(null); // creates a default executor
		server.start();
		
		websiteStatus = new WebsiteStatus(true, "Online");
		
		serverUI.addLogMessage(String.format("Web Server started: %d contexts", contexts.length));
	}
	
	public static ONCWebServer getInstance() throws IOException
	{
		if(instance == null)
			instance = new ONCWebServer();
		
		return instance;
	}
	
	static String getWebsiteStatusJson()
	{
		//build websiteStatusJson
		Gson gson = new Gson();
		String websiteJson = gson.toJson(websiteStatus, WebsiteStatus.class);
		return "WEBSITE_STATUS" + websiteJson;
	}
	
	static String setWebsiteStatus(String websiteStatusJson)
	{ 
		Gson gson = new Gson();
		ONCWebServer.websiteStatus = gson.fromJson(websiteStatusJson, WebsiteStatus.class);
		
		return "UPDATED_WEBSITE_STATUS" + websiteStatusJson;
	}
	
	static boolean isWebsiteOnline() { return websiteStatus.getWebsiteStatus(); }
	static String getWebsiteTimeBackOnline() { return websiteStatus.getTimeBackUp(); }
}
