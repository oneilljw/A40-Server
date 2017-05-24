package oncserver;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import ourneighborschild.OSXAdapter;

public class ONCServer
{
	/**
	 * @param args
	 * @throws IOException 
	 */
	
	private static final String APPNAME = "Our Neighbor's Child Server v3.00";
	private static final String ONC_COPYRIGHT = "\u00A92017 John W. O'Neill";
	private ServerUI serverUI;	//User IF
	private ServerLoop serverIF; 	//Server loop
	private ClientManager clientMgr; //Manages all connected clients
	
	private boolean bServerRunning;
//	private Timer dbSaveTimer;
//	private List<ONCServerDB> dbList;
	
	//GUI Objects
	private JFrame oncFrame;
	private ServerMenuBar serverMenuBar;
	
	//Check if we are on Mac OS X.  This is crucial to loading and using the OSXAdapter class.
    private static boolean MAC_OS_X = (System.getProperty("os.name").toLowerCase().startsWith("mac os x"));

	ONCServer() throws IOException
	{
		//If running under MAC OSX, use the system menu bar and set the application title appropriately and
    	//set up our application to respond to the Mac OS X application menu
        if (MAC_OS_X) 
        {          	
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", APPNAME);
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();}
			catch (InstantiationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace(); }
			catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace(); }
			catch (UnsupportedLookAndFeelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace(); }
			
            // Generic registration with the Mac OS X application, attempts to register with the Apple EAWT
            // See OSXAdapter.java to see how this is done without directly referencing any Apple APIs
            try
            {
                // Generate and register the OSXAdapter, passing it a hash of all the methods we wish to
                // use as delegates for various com.apple.eawt.ApplicationListener methods
                OSXAdapter.setQuitHandler(this, getClass().getDeclaredMethod("quit", (Class[])null));
                OSXAdapter.setAboutHandler(this,getClass().getDeclaredMethod("about", (Class[])null));
 //             OSXAdapter.setPreferencesHandler(this, getClass().getDeclaredMethod("preferences", (Class[])null));
 //             OSXAdapter.setFileHandler(this, getClass().getDeclaredMethod("loadImageFile", new Class[] { String.class }));
            } 
            catch (Exception e)
            {
                System.err.println("Error while loading the OSXAdapter:");
                e.printStackTrace();
            }
        }
        
        //create mainframe window for the application and add button listeners to start/stop the sever
        createandshowGUI();
        serverUI.btnStartServer.addActionListener(new UIButtonListener());
        serverUI.btnStopServer.addActionListener(new UIButtonListener());
        
		//Set up client manager
		clientMgr = ClientManager.getInstance();
		
		//set up the database manager and load the data base from persistent store
		DBManager.getInstance(clientMgr.getAppIcon());

		//Create the client listener socket and start the loop		
//		serverUI.setStoplight(0);	//Set server status to green - started
		startServer();	//Start the server on app start up
    }
	
	 // General quit handler; fed to the OSXAdapter as the method to call when a system quit event occurs
    // A quit event is triggered by Cmd-Q, selecting Quit from the application or Dock menu, or logging out
    public boolean quit()
    {
    	if(bServerRunning)	//Did user forget to stop the server prior to quitting? 
    		serverIF.terminateServer();
    	return true;
    }
    
    // General info dialog; fed to the OSXAdapter as the method to call when 
    // "About OSXAdapter" is selected from the application menu   
    public void about()
    {
    	JOptionPane.showMessageDialog(oncFrame, APPNAME + "\n" + ONC_COPYRIGHT, "About the ONC Server",
    			JOptionPane.INFORMATION_MESSAGE, clientMgr.getAppIcon());
    }
    
    private void createandshowGUI()
	{
    	serverMenuBar = new ServerMenuBar();
    	serverMenuBar.countsMI.addActionListener(new MenuBarListener());
    	serverMenuBar.convertStatusMI.addActionListener(new MenuBarListener());
    	serverMenuBar.createHistMI.addActionListener(new MenuBarListener());
    	serverMenuBar.updateUserNameMI.addActionListener(new MenuBarListener());
    	serverUI = ServerUI.getInstance();
    	
    	oncFrame =  new JFrame(APPNAME);
    	oncFrame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent we)
			 {
				if(bServerRunning)	//Did user forget to stop the server prior to quitting? 
		    		serverIF.terminateServer();

				System.exit(0);	  
			 }});
        oncFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);	//On close, user is prompted to confirm
        oncFrame.setMinimumSize(new Dimension(200, 200));
        oncFrame.setLocationByPlatform(true);
        
        oncFrame.setJMenuBar(serverMenuBar);
        oncFrame.setContentPane(serverUI); 
        oncFrame.setSize(524, 520);
        oncFrame.setVisible(true);
	}
    
    void startServer()
    {
    	//Create and start the server loop
    	serverIF = new ServerLoop(clientMgr);
    	
    	serverIF.start();
		serverUI.setStoplight(0);	//Set server status to green - running
		
		serverUI.addLogMessage("App Server Interface Loop started");
		
		serverUI.btnStartServer.setVisible(false);
		serverUI.btnStopServer.setVisible(true);
		
		bServerRunning = true;
    }
    
    void stopServer()
    {
		serverIF.stopServer();
		try 
		{
			serverIF.join();
			bServerRunning = serverIF.terminateServer();
		} 
		catch (InterruptedException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		serverUI.setStoplight(2);	//Set server status to green - running
		
		serverUI.addLogMessage("Server Interface Loop stopped");
		
		serverUI.btnStartServer.setVisible(true);
		serverUI.btnStopServer.setVisible(false);
    }
    
    private class UIButtonListener implements ActionListener
    {
    	public void actionPerformed(ActionEvent e)
    	{
    		if(e.getSource() == serverUI.btnStartServer)
    			startServer();
    		else if(e.getSource() == serverUI.btnStopServer)
    			stopServer();
    	}	
    }

    public static void main(String args[])
	{    	
		 SwingUtilities.invokeLater(new Runnable() {
			 public void run()
			 {
				 
				try
				{
					new ONCServer();
					ONCWebServer.getInstance();
				} 
				catch (IOException e)
				{
					e.printStackTrace();
				}
				
		}});	 
	}
   
    private class MenuBarListener implements ActionListener
    {
		@Override
		public void actionPerformed(ActionEvent e) 
		{
			if(e.getSource() == serverMenuBar.countsMI)
			{
//				//update py performance
//				try {
//					ServerPartnerDB serverPartnerDB = ServerPartnerDB.getInstance();
//					serverPartnerDB.determinePriorYearPerformance(2016);
//					clientMgr.addLogMessage("Partner PY Performance Updated");
//				} catch (FileNotFoundException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
			}
			else if(e.getSource() == serverMenuBar.convertStatusMI)
			{
//				//update py performance
//				try {
//					ServerFamilyDB serverFamilyDB = ServerFamilyDB.getInstance();
//					serverFamilyDB.convertFamilyDBForStatusChanges(2016);
//					clientMgr.addLogMessage(String.format("%d ServerDB Performance Updated", 2016));
//					serverFamilyDB.convertFamilyDBForStatusChanges(2015);
//					clientMgr.addLogMessage(String.format("%d ServerDB Performance Updated", 2015));
//					serverFamilyDB.convertFamilyDBForStatusChanges(2014);
//					clientMgr.addLogMessage(String.format("%d ServerDB Performance Updated", 2014));
//					serverFamilyDB.convertFamilyDBForStatusChanges(2013);
//					clientMgr.addLogMessage(String.format("%d ServerDB Performance Updated", 2013));
//					serverFamilyDB.convertFamilyDBForStatusChanges(2012);
//					clientMgr.addLogMessage(String.format("%d ServerDB Performance Updated", 2012));
//				} catch (FileNotFoundException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
			}			
			else if(e.getSource() == serverMenuBar.createHistMI)
			{
				//update py performance
//				try {
//					ServerFamilyHistoryDB serverFamilyHistoryDB = ServerFamilyHistoryDB.getInstance();
//					serverFamilyHistoryDB.convertDeliveryDBForStatusChanges(2016);
//					clientMgr.addLogMessage(String.format("%d Family History DB Created", 2016));
//					serverFamilyHistoryDB.convertDeliveryDBForStatusChanges(2015);
//					clientMgr.addLogMessage(String.format("%d Family History DB Created", 2015));
//					serverFamilyHistoryDB.convertDeliveryDBForStatusChanges(2014);
//					clientMgr.addLogMessage(String.format("%d Family History DB Created", 2014));
//					serverFamilyHistoryDB.convertDeliveryDBForStatusChanges(2013);
//					clientMgr.addLogMessage(String.format("%d Family History DB Created", 2013));
//					serverFamilyHistoryDB.convertDeliveryDBForStatusChanges(2012);
//					clientMgr.addLogMessage(String.format("%d Family History DB Created", 2012));
//				} catch (FileNotFoundException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
			}	
			else if(e.getSource() == serverMenuBar.updateUserNameMI)
			{
//				//update py performance
//				try 
//				{
//					ServerUserDB userDB = ServerUserDB.getInstance();
//					clientMgr.addLogMessage(String.format("%d User Names updated", userDB.updateUserNames()));
//				} catch (FileNotFoundException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				} catch (IOException e1) {
//					// TODO Auto-generated catch block
//					e1.printStackTrace();
//				}
			}
		}
    }
}
