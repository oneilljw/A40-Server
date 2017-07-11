package a4oserver;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.Provider;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import actforothers.WebsiteStatus;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;


public class A4OWebServer
{
	private static final int WEB_SERVER_PORT = 8904;
	
	private static A4OWebServer instance = null;
	private static WebsiteStatus websiteStatus;
	
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

/*
	private A4OWebServer()
	{
		ServerUI serverUI = ServerUI.getInstance();
		
//		String keystoreFilename = System.getProperty("user.dir") + "/A4O/a4o.keystore";
		String keystoreFilename = System.getProperty("user.dir") + "/A4O/a4o.jks";
		char[] storepass = "mypassword".toCharArray();
		char[] keypass = "mypassword".toCharArray();		
		
		HttpsServer server = null;
		try 
		{
//			Provider[] providers = Security.getProviders();
//			for(Provider p : providers)
//				System.out.println(String.format("Provider: %s", p.getName()));
			
			// load certificate
			FileInputStream fIn = new FileInputStream(keystoreFilename);
			KeyStore keystore = KeyStore.getInstance("JKS");
			keystore.load(fIn, storepass);
			
			// display certificate
//			String alias = "alias";
//			Certificate certificate = keystore.getCertificate(alias);
//			System.out.println(certificate);
			
			// setup the key manager factory
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(keystore, keypass);
			
			// setup the trust manager factory
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(keystore);
			
			// create https server
			server = HttpsServer.create(new InetSocketAddress(WEB_SERVER_PORT), 0);
			
			// create ssl context
			SSLContext sslContext = SSLContext.getInstance("TLSv1");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			
			// setup the HTTPS context and parameters
			server.setHttpsConfigurator(new HttpsConfigurator(sslContext) 
			{
				public void configure(HttpsParameters params) 
				{
					try 
					{
						//Initialize the SSL context
						SSLContext c = SSLContext.getDefault();
						SSLEngine engine = c.createSSLEngine();
						params.setNeedClientAuth(false);
			             
						params.setCipherSuites(engine.getEnabledCipherSuites());
			             
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
		} 
		catch (KeyStoreException e) 
		{
			System.out.println("KeyStoreException" );
		} 
		catch (NoSuchAlgorithmException e)
		{
			System.out.println("NoSuchAlgorithmException" );
		} 
		catch (CertificateException e)
		{
			System.out.println("CertificateException" );
		} 
		catch (IOException e)
		{
			System.out.println("IOException" );
		}
		catch (UnrecoverableKeyException e)
		{
			System.out.println("UnrecoverableKeyException" );	
		} 
		catch (KeyManagementException e)
		{
			System.out.println("KeyManagementException" );
		}
		
		//create the handler and the contexts
		A4OHttpHandler a4oHttpHandler = new A4OHttpHandler();
		String[] contexts = {"/welcome", "/logout", "/login", "/dbStatus", "/agents", "/families", "/familystatus",
							"/getfamily", "/references", "/getagent", "/getmeal", "/children", "/familysearch", 
							"/adults", "/wishes", "/a4osplash", "/clearx", "/a4ologo", "/oncstylesheet", 
							"/oncdialogstylesheet", "/newfamily", "/reqchangepw", "/timeout",
							"/address", "/referral", "/referfamily", "/familyupdate", "/updatefamily",
							"/changepw", "/startpage", "/vanilla", "/getuser", "/getstatus",
							"/profileunchanged", "/updateuser", "/driverregistration", "/registerdriver",
							"/onchomepage", "/volunteersignin", "/signinvolunteer", "/contactinfo",
							"/jquery.js", "/favicon.ico", "/groups", "/commonfamily.js"};
		
		for(String contextname:contexts)
			server.createContext(contextname, a4oHttpHandler);

		//start the server
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
*/	
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
}
