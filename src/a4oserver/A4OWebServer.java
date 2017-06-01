package a4oserver;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import actforothers.WebsiteStatus;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;


public class A4OWebServer
{
	private static final int WEB_SERVER_PORT = 8904;
	
	private static A4OWebServer instance = null;
	private static WebsiteStatus websiteStatus;
/*	
	private A4OWebServer() throws IOException
	{
		ServerUI serverUI = ServerUI.getInstance();
		
		HttpServer server = HttpServer.create(new InetSocketAddress(WEB_SERVER_PORT), 0);
		A4OHttpHandler oncHttpHandler = new A4OHttpHandler();
		
		String[] contexts = {"/welcome", "/logout", "/login", "/dbStatus", "/agents", "/families", "/familystatus",
							"/getfamily", "/references", "/getagent", "/getmeal", "/children", "/familysearch", 
							"/adults", "/wishes", "/a4osplash", "/clearx", "/a4ologo", "/oncstylesheet", 
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
	
	public static A4OWebServer getInstance() throws IOException
	{
		if(instance == null)
			instance = new A4OWebServer();
		
		return instance;
	}
*/
	
	private A4OWebServer()
	{
		ServerUI serverUI = ServerUI.getInstance();
		
		// load certificate
		String keystoreFilename = System.getProperty("user.dir") + "/A4O/a4o.keystore";
		char[] storepass = "a4opass".toCharArray();
		char[] keypass = "a4opass".toCharArray();
		String alias = "test";
		
		FileInputStream fIn = null;
		try {
			fIn = new FileInputStream(keystoreFilename);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			System.out.println("Failed to open " + keystoreFilename);
		}
		
		KeyStore keystore = null;
		try {
			keystore = KeyStore.getInstance("JKS");
			keystore.load(fIn, storepass);
		} catch (KeyStoreException e) {
			System.out.println("KeyStoreException" );
		} catch (NoSuchAlgorithmException e) {
			System.out.println("NoSuchAlgorithmException" );
		} catch (CertificateException e) {
			System.out.println("CertificateException" );
		} catch (IOException e) {
			System.out.println("IOException" );;
		}
		
		
		// display certificate
		Certificate certificate;
		try {
			certificate = keystore.getCertificate(alias);
			System.out.println(certificate);
		} catch (KeyStoreException e) {
			System.out.println("KeyStoreException" );
		}
		
		
		// setup the key manager factory
		KeyManagerFactory kmf = null;
		try {
			kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(keystore, keypass);
		} catch (NoSuchAlgorithmException e) {
			System.out.println("NoSuchAlgorithmException" );
		} catch (UnrecoverableKeyException e) {
			System.out.println("UnrecoverableKeyException" );
		} catch (KeyStoreException e) {
			System.out.println("KeyStoreException" );
		}
		
		// setup the trust manager factory
		TrustManagerFactory tmf = null;
		try {
			tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(keystore);
		} catch (NoSuchAlgorithmException e) {
			System.out.println("NoSuchAlgorithmException" );
		} catch (KeyStoreException e) {
			System.out.println("KeyStoreException" );
		}
		
		
		// create https server
		HttpsServer server = null;
		try {
			server = HttpsServer.create(new InetSocketAddress(WEB_SERVER_PORT), 0);
		} catch (IOException e) {
			System.out.println("IOException" );
		}
		
		// create ssl context
		SSLContext sslContext = null;
		try {
			sslContext = SSLContext.getInstance("TLSv1");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// setup the HTTPS context and parameters
		
		server.setHttpsConfigurator(new HttpsConfigurator(sslContext) 
		{
			public void configure(HttpsParameters params) 
			{
				try 
				{
					//Initialize the SSL context
					System.out.println(params.getClientAddress().toString());
		   		 
					String[] protocols = params.getProtocols();
					if(protocols == null)
						System.out.println("protocols is null");
					else 
						System.out.println(params.getProtocols().length);
		   		 
					SSLContext c = SSLContext.getDefault();
					SSLEngine engine = c.createSSLEngine();
					params.setNeedClientAuth(false);
		             
					String[] cipherSuites = engine.getEnabledCipherSuites();
					if(cipherSuites == null)
						System.out.println("Cipher Suites is null");
					else 
						for(String cs : cipherSuites)
							System.out.println(String.format("CS: %s", cs));
					params.setCipherSuites(engine.getEnabledCipherSuites());
		             
					String[] engineProtocols = engine.getEnabledProtocols();
					if(engineProtocols == null)
						System.out.println("Engine Protocols is null");
					else 
						for(String ep : engineProtocols)
							System.out.println(String.format("EP: %s", ep));
					params.setProtocols(engine.getEnabledProtocols());
		             
					// get the default parameters
					SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
					params.setSSLParameters(defaultSSLParameters);
				} 
				catch (Exception ex) 
				{
					ex.printStackTrace();
					System.out.println("Failed to create HTTPS server");
				}
			}
		});
		
		server.createContext("/test", new MyHandler());

//		A4OHttpHandler oncHttpHandler = new A4OHttpHandler();
//		
//		String[] contexts = {"/welcome", "/logout", "/login", "/dbStatus", "/agents", "/families", "/familystatus",
//							"/getfamily", "/references", "/getagent", "/getmeal", "/children", "/familysearch", 
//							"/adults", "/wishes", "/a4osplash", "/clearx", "/a4ologo", "/oncstylesheet", 
//							"/oncdialogstylesheet", "/newfamily", "/reqchangepw", "/timeout",
//							"/address", "/referral", "/referfamily", "/familyupdate", "/updatefamily",
//							"/changepw", "/startpage", "/vanilla", "/getuser", "/getstatus",
//							"/profileunchanged", "/updateuser", "/driverregistration", "/registerdriver",
//							"/onchomepage", "/volunteersignin", "/signinvolunteer", "/contactinfo",
//							"/jquery.js", "/favicon.ico", "/groups", "/commonfamily.js"};
//		
//		HttpContext context;
//		Filter paramFilter = new ParameterFilter();
//		
//		for(String contextname:contexts)
//		{
//			context = server.createContext(contextname, oncHttpHandler);
//			context.getFilters().add(new ParameterFilter());
//			context.getFilters().add(paramFilter);
//		}
//
		server.setExecutor(null); // creates a default executor
		server.start();
	
		websiteStatus = new WebsiteStatus(true, "Online");
		
		serverUI.addLogMessage(String.format("Web Server started: %d contexts", 45));
	}
		
	public static A4OWebServer getInstance()
	{
		if(instance == null)
			instance = new A4OWebServer();
		
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
		A4OWebServer.websiteStatus = gson.fromJson(websiteStatusJson, WebsiteStatus.class);
		
		return "UPDATED_WEBSITE_STATUS" + websiteStatusJson;
	}
	
	static boolean isWebsiteOnline() { return websiteStatus.getWebsiteStatus(); }
	static String getWebsiteTimeBackOnline() { return websiteStatus.getTimeBackUp(); }
	
	public static class MyHandler implements HttpHandler 
    {
        @Override
        public void handle(HttpExchange t) throws IOException 
        {
        	System.out.println("Got to the handler");
            String response = "This is the response";
            HttpsExchange httpsExchange = (HttpsExchange) t;
            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
	
	public static class MyConfigurator extends HttpsConfigurator
	{
		public MyConfigurator(SSLContext context)
		{
			super(context);
		}
	}

}
