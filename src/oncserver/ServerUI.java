package oncserver;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.text.DefaultCaret;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

public class ServerUI extends JPanel implements ListSelectionListener
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final int NUM_ROWS_TO_DISPLAY = 8;
	private static final int LOG_TEXT_FONT_SIZE = 13;
	
	private static ServerUI instance = null;	//Only one UI
	private transient ImageIcon imageIcons[];
	public JButton btnStartServer, btnStopServer, btnKillClient;
	private JTextArea logTA;
//	private StyledDocument logDoc;	//document that holds log text
	private JLabel lblNumClients;
	private JRadioButton rbStoplight;
//	private ONCTable clientTable;
	private JTable desktopClientTable, websiteClientTable;
	private DefaultTableModel desktopClientTableModel, websiteClientTableModel;
	private boolean bDesktopClientTableChanging, bWebsiteClientTableChanging;
	
	private ArrayList<DesktopClient> clientTableList;
	private ArrayList<WebClient> websiteTableList;
	
//	private static String[] columnToolTips = {"ID", "First Name", "Last Name", 
//		  										"Permission", "Client Status", "Heart Beat",
//		  										"Database Year Client is Connected To",
//		  										"Time Logged In" };

	private static String[] columns = {"ID", "First Name", "Last Name", 
		  								"Perm", "State", "HB", "Year", "Ver", "Time Stamp" };

	private static int[] colWidths = {40, 80, 80, 80, 80, 28, 40, 52, 140};

	private static int [] center_cols = {0, 3, 5};
	
	public static ServerUI getInstance()
	{
		if(instance == null)
			instance = new ServerUI();
		
		return instance;
	}
	
	private ServerUI()
	{
		//Layout User I/F
		initIcons();

		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
		JPanel statusPanelLeft = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JPanel statusPanelCenter = new JPanel(new FlowLayout(FlowLayout.CENTER));
		JPanel statusPanelRight = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		
		JLabel lblONCicon = new JLabel(imageIcons[0]);
		statusPanelLeft.add(lblONCicon);
		
		lblNumClients = new JLabel("Clients Connected: 0");
		statusPanelCenter.add(lblNumClients);
	    
		rbStoplight = new JRadioButton(imageIcons[4]);
    	rbStoplight.setToolTipText("");
    	statusPanelRight.add(rbStoplight);
    	
    	statusPanel.add(statusPanelLeft);
    	statusPanel.add(statusPanelCenter);
    	statusPanel.add(statusPanelRight);
    	
    	//Set up the desktop client table panel and wesite client table panel
    	JPanel desktoptablepanel = new JPanel();
    	desktoptablepanel.setLayout(new BorderLayout());
    	
    	bDesktopClientTableChanging = false;
//    	clientTable = new ONCTable(columnToolTips, new Color(240,248,255));
    	desktopClientTable = new JTable();

    	//Set up the table model. Cells are not editable
    	desktopClientTableModel = new DefaultTableModel(columns, 0) {
    		private static final long serialVersionUID = 1L;
    				
    		@Override
    		//All cells are locked from being changed by user
    		public boolean isCellEditable(int row, int column) {return false;}
    	};
    	
    	clientTableList = new ArrayList<DesktopClient>();	//List holds references of clients show in table

    	//Set the table model, select ability to select multiple rows and add a listener to 
    	//check if the user has selected a row. 
    	desktopClientTable.setModel(desktopClientTableModel);
    	desktopClientTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//    	clientTable.getSelectionModel().addListSelectionListener(this);
//    	clientTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
//    		@Override
//    		public void valueChanged(ListSelectionEvent arg0) {
//    			checkApplyChangesEnabled();	//Check to see if user postured to change delivery driver.	
//    		}
//    	});

    	//Set table column widths
    	int tablewidth = 0;
    	for(int i=0; i < colWidths.length; i++)
    	{
    		desktopClientTable.getColumnModel().getColumn(i).setPreferredWidth(colWidths[i]);
    		tablewidth += colWidths[i];
    	}
   		tablewidth += 24; 	//Account for vertical scroll bar

    	//Set up the table header
    	JTableHeader anHeader = desktopClientTable.getTableHeader();
    	anHeader.setForeground( Color.black);
    	anHeader.setBackground( new Color(161,202,241));

    	//mouse listener for table header click causes table to be sorted based on column selected
    	//uses family data base sort method to sort. Method requires ONCFamily array list to be sorted
    	//and column name
    	//anHeader.addMouseListener(new MouseAdapter() {
    	//	@Override
    	//    public void mouseClicked(MouseEvent e) {
    	//		//TODO: add table sorting code here
    	//   }
    	//});

    	//Center cell entries for specified cells
    	DefaultTableCellRenderer dtcr = new DefaultTableCellRenderer();    
    	dtcr.setHorizontalAlignment(SwingConstants.CENTER);
    	for(int i=0; i<center_cols.length; i++)
    		desktopClientTable.getColumnModel().getColumn(center_cols[i]).setCellRenderer(dtcr);

//    	clientTable.setBorder(UIManager.getBorder("Table.scrollPaneBorder"));

    	desktopClientTable.setFillsViewportHeight(true);
    	desktoptablepanel.add(anHeader, BorderLayout.NORTH);
    	desktoptablepanel.add(desktopClientTable, BorderLayout.CENTER);
    	desktoptablepanel.setPreferredSize(new Dimension(tablewidth, desktopClientTable.getRowHeight()*NUM_ROWS_TO_DISPLAY));
/*    	
    	//Create the scroll pane and add the table to it.
    	JScrollPane clientScrollPane = new JScrollPane(clientTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
    												JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

    	clientScrollPane.setPreferredSize(new Dimension(tablewidth, clientTable.getRowHeight()*NUM_ROWS_TO_DISPLAY));
*/ 
    	JPanel websitetablepanel = new JPanel();
    	websitetablepanel.setLayout(new BorderLayout());
    	
    	bWebsiteClientTableChanging = false;
//    	clientTable = new ONCTable(columnToolTips, new Color(240,248,255));
    	websiteClientTable = new JTable();

    	//Set up the table model. Cells are not editable
    	websiteClientTableModel = new DefaultTableModel(columns, 0) {
    		private static final long serialVersionUID = 1L;
    				
    		@Override
    		//All cells are locked from being changed by user
    		public boolean isCellEditable(int row, int column) {return false;}
    	};
    	
    	websiteTableList = new ArrayList<WebClient>();	//List holds references of clients show in table

    	//Set the table model, select ability to select multiple rows and add a listener to 
    	//check if the user has selected a row. 
    	websiteClientTable.setModel(websiteClientTableModel);
    	websiteClientTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
//    	clientTable.getSelectionModel().addListSelectionListener(this);
//    	clientTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
//    		@Override
//    		public void valueChanged(ListSelectionEvent arg0) {
//    			checkApplyChangesEnabled();	//Check to see if user postured to change delivery driver.	
//    		}
//    	});

//    	//Set table column widths
//    	tablewidth = 0;
    	for(int i=0; i < colWidths.length; i++)
    	{
    		websiteClientTable.getColumnModel().getColumn(i).setPreferredWidth(colWidths[i]);
//    		tablewidth += colWidths[i];
    	}
//   		tablewidth += 24; 	//Account for vertical scroll bar

    	//Set up the table header
    	anHeader = websiteClientTable.getTableHeader();
    	anHeader.setForeground( Color.black);
    	anHeader.setBackground( new Color(161,202,241));

    	//mouse listener for table header click causes table to be sorted based on column selected
    	//uses family data base sort method to sort. Method requires ONCFamily array list to be sorted
    	//and column name
    	//anHeader.addMouseListener(new MouseAdapter() {
    	//	@Override
    	//    public void mouseClicked(MouseEvent e) {
    	//		//TODO: add table sorting code here
    	//   }
    	//});

    	//Center cell entries for specified cells
    	dtcr = new DefaultTableCellRenderer();    
    	dtcr.setHorizontalAlignment(SwingConstants.CENTER);
    	for(int i=0; i<center_cols.length; i++)
    		websiteClientTable.getColumnModel().getColumn(center_cols[i]).setCellRenderer(dtcr);

//    	clientTable.setBorder(UIManager.getBorder("Table.scrollPaneBorder"));

    	websiteClientTable.setFillsViewportHeight(true);
    	websitetablepanel.add(anHeader, BorderLayout.NORTH);
    	websitetablepanel.add(websiteClientTable, BorderLayout.CENTER);
    	websitetablepanel.setPreferredSize(new Dimension(tablewidth, websiteClientTable.getRowHeight()*NUM_ROWS_TO_DISPLAY));
/*    	
    	//Create the scroll pane and add the table to it.
    	JScrollPane clientScrollPane = new JScrollPane(clientTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
    												JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

    	clientScrollPane.setPreferredSize(new Dimension(tablewidth, clientTable.getRowHeight()*NUM_ROWS_TO_DISPLAY));
*/   			
    	
    	//Set up the client log pane
    	logTA = new JTextArea();
    	logTA.setToolTipText("Server log");
  	   	logTA.setEditable(false);
  	   	
  	   	//create the document that holds chat text
//      logDoc = logTP.getStyledDocument();
        
        //create the paragraph attributes
        SimpleAttributeSet paragraphAttribs = new SimpleAttributeSet();  
        StyleConstants.setAlignment(paragraphAttribs , StyleConstants.ALIGN_LEFT);
        StyleConstants.setFontSize(paragraphAttribs, LOG_TEXT_FONT_SIZE);
        StyleConstants.setSpaceBelow(paragraphAttribs, 3);
        
        //set the paragraph attributes, disable editing and set the pane size
//      logTA.setParagraphAttributes(paragraphAttribs, true);
  	   	
        DefaultCaret caret = (DefaultCaret) logTA.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
  	   	
	    //Create the ODB Wish List scroll pane and add the Wish List text pane to it.
        JScrollPane logScrollPane = new JScrollPane(logTA);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Server Log"));
        logScrollPane.setPreferredSize(new Dimension(tablewidth, 180));
        
        //Set up the control panel
        JPanel cntlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        btnKillClient = new JButton("Kill Client");
        btnKillClient.setVisible(false);
        btnKillClient.setEnabled(false);
      
        btnStartServer = new JButton("Start Server");
        btnStartServer.setVisible(false);
        
        btnStopServer = new JButton("Stop Server");
//      btnStopServer.setVisible(false);
        
        cntlPanel.add(btnKillClient);
        cntlPanel.add(btnStopServer);
        cntlPanel.add(btnStartServer);
        
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); 
        this.add(statusPanel);
        this.add(desktoptablepanel);
        this.add(websitetablepanel);
//      this.add(clientScrollPane);
        this.add(logScrollPane);
        this.add(cntlPanel);
        
        this.setMinimumSize(new Dimension(tablewidth, 600));
	}
	
	/** Initialize icons */
	void initIcons()
	{
		imageIcons = new ImageIcon[5];
		
		//ONC Server logo
		imageIcons[0] = createImageIcon("oncserverlogosmall.png", "ONC Logo");
		
		//Icons used for stop light status
		imageIcons[1] = createImageIcon("traffic-lights-green-icon.gif", "Green Light Icon");
		imageIcons[2] = createImageIcon("traffic-lights-yellow-icon.gif", "Yellow Light Icon");
		imageIcons[3] = createImageIcon("traffic-lights-red-icon.gif", "Red Light Icon");
		imageIcons[4] = createImageIcon("traffic-lights-off-icon.gif", "Off Light Icon");
		
		//Splash screen
//		imageIcons[15] = createImageIcon("oncsplash.gif", "ONC Full Screen Logo");
		
	}
	
	void displayDesktopClientTable(ArrayList<DesktopClient> cAL)
	{
		bDesktopClientTableChanging = true;
		
		clientTableList.clear();
		
		while (desktopClientTableModel.getRowCount() > 0)	//Clear the current table
			desktopClientTableModel.removeRow(0);
		
		for(DesktopClient ci:cAL)	//Build the new table
		{
			clientTableList.add(ci);
			desktopClientTableModel.addRow(ci.getClientTableRow());
		}
		
		bDesktopClientTableChanging = false;
		
		btnKillClient.setVisible(cAL.size() > 0);
		btnKillClient.setEnabled(false);
		
		lblNumClients.setText("Clients Connected: " + Integer.toString(cAL.size()));
	}
	
	void displayWebsiteClientTable(ArrayList<WebClient> cAL)
	{
		bWebsiteClientTableChanging = true;
		
		websiteTableList.clear();
		
		while (websiteClientTableModel.getRowCount() > 0)	//Clear the current table
			websiteClientTableModel.removeRow(0);
		
		for(WebClient ci:cAL)	//Build the new table
		{
			websiteTableList.add(ci);
			websiteClientTableModel.addRow(ci.getClientTableRow());
		}
		
		bWebsiteClientTableChanging = false;
	}
	
	//returns a reference to the client that is selected in the client table
	DesktopClient getClientTableSelection()
	{
		if(desktopClientTable.getSelectedRow() != -1)	//make sure row is selected
			return clientTableList.get(desktopClientTable.getSelectedRow());
			
		else
			return null;
	}
	
	void addLogMessage(String mssg)
	{
		Calendar timestamp = Calendar.getInstance();
		
		String line = new SimpleDateFormat("MM/dd/yy H:mm:ss").format(timestamp.getTime());
		
		logTA.append(line + ": " + mssg + "\n");
		logTA.setCaretPosition(logTA.getDocument().getLength());
	}
	
	void setStoplight(int pos)	//0-green, 1-yellow, 2-red, 3-off
	{
		if(pos >= 0 && pos < 4)
			rbStoplight.setIcon(imageIcons[pos+1]);
	}
	
	ImageIcon getIcon(int index) { return imageIcons[index]; }
	
	 /** Returns an ImageIcon, or null if the path was invalid. */
	ImageIcon createImageIcon(String path, String description)
	{
		java.net.URL imgURL = getClass().getResource(path);
		if (imgURL != null) { return new ImageIcon(imgURL, description); } 
		else { System.err.println("Couldn't find file: " + path); return null; }
	}

	@Override
	public void valueChanged(ListSelectionEvent lse)
	{
		if(!lse.getValueIsAdjusting() &&lse.getSource() == desktopClientTable.getSelectionModel() &&
				!bDesktopClientTableChanging)
		{
			btnKillClient.setEnabled(desktopClientTable.getSelectedRowCount() > 0);
		}
	}
}
