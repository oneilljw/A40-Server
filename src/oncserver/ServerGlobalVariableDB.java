package oncserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.swing.JOptionPane;

import ourneighborschild.ONCObject;
import ourneighborschild.ServerGVs;

import com.google.gson.Gson;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class ServerGlobalVariableDB extends ServerSeasonalDB
{
	private static final int GV_HEADER_LENGTH = 7;
	private static final int GV_ALTERNATE_HEADER_LENGTH = 24;
	private static final String DEFAULT_ADDRESS = "6476+Trillium+House+Lane+Centreville,VA";
	
	private static ServerGlobalVariableDB instance = null;
	private List<GlobalVariableDBYear> globalDB;
	
	private ServerGlobalVariableDB() throws FileNotFoundException, IOException
	{
		//create the database
		globalDB = new ArrayList<GlobalVariableDBYear>();
		
		//populate the global variable data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the global variable object for each year
			String gvFile = String.format("%s/%dDB/GlobalVariables.csv", System.getProperty("user.dir"), year);
			GlobalVariableDBYear globalDBYear = new GlobalVariableDBYear(year, gvFile);
									
			//add the object for the year to the db
			globalDB.add(globalDBYear);	
		}
	}
	
	public static ServerGlobalVariableDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerGlobalVariableDB();
		
		return instance;
	}
	
	String getGlobalVariables(int year)
	{
		String gvjson = null;
		Gson gson = new Gson();
		
//		gvjson = gson.toJson(gvAL.get(year - BASE_YEAR), ServerGVs.class);
		gvjson = gson.toJson(globalDB.get(year - BASE_YEAR).getServerGVs(), ServerGVs.class);
		
		if(gvjson != null)
			return "GLOBALS" + gvjson;
		else
			return "NO_GLOBALS";
	}
	
	Date getSeasonStartDate(int year)
	{
		return globalDB.get(year - BASE_YEAR).getServerGVs().getSeasonStartDate();
	}
	
//	Date getDateGiftsRecivedBy(int year)
//	{
//		return globalDB.get(year - BASE_YEAR).getServerGVs().getGiftsReceivedDate();
//	}
	
	Calendar getDateGiftsRecivedDealdine(int year)
	{
		return globalDB.get(year - BASE_YEAR).getServerGVs().getGiftsReceivedDeadline();
	}
	
	Date getDeadline(int year, String deadline)
	{
		//check if year is in database. If it's not, return null
		if(year - BASE_YEAR < globalDB.size())
		{
			if(deadline.equals("Thanksgiving"))
				return globalDB.get(year - BASE_YEAR).getServerGVs().getThanksgivingDeadline();
			else if(deadline.equals("December"))
				return globalDB.get(year - BASE_YEAR).getServerGVs().getDecemberDeadline();
			else if(deadline.equals("Edit"))
				return globalDB.get(year - BASE_YEAR).getServerGVs().getFamilyEditDeadline();
			else
				return Calendar.getInstance().getTime();
		}
		else
			return null;
	}
/*	
	Date getDecemberDeadline(int year)
	{
		return globalDB.get(year - BASE_YEAR).getServerGVs().getDecemberDeadline();
	}
	
	Date getFamilyEditDeadline(int year)
	{
		return globalDB.get(year - BASE_YEAR).getServerGVs().getFamilyEditDeadline();
	}
*/	
	private ServerGVs readGVs(String file) throws FileNotFoundException, IOException
	{
		ServerGVs gvs = null;
		
    	CSVReader reader = new CSVReader(new FileReader(file));
    	String[] nextLine, header;
    	
    	if((header = reader.readNext()) != null)	//Does file have records? 
    	{
    		//Read the User File
    		if(header.length == GV_HEADER_LENGTH || 
    				header.length == GV_ALTERNATE_HEADER_LENGTH)	//Does the record have the right # of fields? 
    		{
    			if((nextLine = reader.readNext()) != null)
    			{
    				//Read first line, it's the gv's
    				gvs = new ServerGVs(Long.parseLong(nextLine[0]),
    								Long.parseLong(nextLine[1]),
    								nextLine[2].isEmpty() ? DEFAULT_ADDRESS : nextLine[2],
    								Long.parseLong(nextLine[3]), Long.parseLong(nextLine[4]),
    								Long.parseLong(nextLine[5]), Long.parseLong(nextLine[6]));
    				
    				//Read the second line, it's the oncnumRegionRanges
//    				nextLine = reader.readNext();			
    			}	
    		}
    		else
    		{
    			String error = String.format("GlobalVariablesDB file corrupted, header lentgth = %d", header.length);
    	       	JOptionPane.showMessageDialog(null, error,  "Global Variables Corrupted",
    	       								JOptionPane.ERROR_MESSAGE);
    		}		   			
    	}
    	else
    	{
    		String error = String.format("GlobalVariablesDB file is empty");
    		JOptionPane.showMessageDialog(null, error,  "Global Variables DB Empty",
       								JOptionPane.ERROR_MESSAGE);
    	}
    	
    	reader.close();
    	
		return gvs;
	}
	
	String update(int year, String json)
	{
		//Create an object for the requested serverGV update
		Gson gson = new Gson();
		ServerGVs reqObj = gson.fromJson(json, ServerGVs.class);
		
		//Find the  requested object being replaced
		GlobalVariableDBYear gvDBYear = globalDB.get(year - BASE_YEAR);
		
		gvDBYear.setServerGVs(reqObj);
		gvDBYear.setChanged(true);
//		save(year);		//Server GV's are persistently stored immediately when changed -- NOT Anymore 1-7-15
		
		return "UPDATED_GLOBALS" + json;
	}

	@Override
	String add(int year, String userjson) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void createNewYear(int newYear) 
	{
		//create the new year ServerGVs
		Calendar seasonStartDate = Calendar.getInstance();
		seasonStartDate.set(Calendar.YEAR, newYear);
		seasonStartDate.set(Calendar.MONTH, Calendar.SEPTEMBER);
		seasonStartDate.set(Calendar.DAY_OF_MONTH, 1);
		seasonStartDate.set(Calendar.HOUR_OF_DAY, 0);
		seasonStartDate.set(Calendar.MINUTE, 0);
		seasonStartDate.set(Calendar.SECOND, 0);
		seasonStartDate.set(Calendar.MILLISECOND, 0);
		
		Calendar deliveryDate = Calendar.getInstance();
		deliveryDate.set(Calendar.YEAR, newYear);
		deliveryDate.set(Calendar.MONTH, Calendar.DECEMBER);
		deliveryDate.set(Calendar.DAY_OF_MONTH, 25);
		deliveryDate.set(Calendar.HOUR_OF_DAY, 0);
		deliveryDate.set(Calendar.MINUTE, 0);
		deliveryDate.set(Calendar.SECOND, 0);
		deliveryDate.set(Calendar.MILLISECOND, 0);
		
		Calendar giftsreceivedDate = Calendar.getInstance();
		giftsreceivedDate.set(Calendar.YEAR, newYear);
		giftsreceivedDate.set(Calendar.MONTH, Calendar.DECEMBER);
		giftsreceivedDate.set(Calendar.DAY_OF_MONTH, 25);
		giftsreceivedDate.set(Calendar.HOUR_OF_DAY, 0);
		giftsreceivedDate.set(Calendar.MINUTE, 0);
		giftsreceivedDate.set(Calendar.SECOND, 0);
		giftsreceivedDate.set(Calendar.MILLISECOND, 0);
		
		Calendar thanksgivingDeadline = Calendar.getInstance();
		thanksgivingDeadline.set(Calendar.YEAR, newYear);
		thanksgivingDeadline.set(Calendar.MONTH, Calendar.DECEMBER);
		thanksgivingDeadline.set(Calendar.DAY_OF_MONTH, 25);
		thanksgivingDeadline.set(Calendar.HOUR_OF_DAY, 0);
		thanksgivingDeadline.set(Calendar.MINUTE, 0);
		thanksgivingDeadline.set(Calendar.SECOND, 0);
		thanksgivingDeadline.set(Calendar.MILLISECOND, 0);
		
		Calendar decemberDeadline = Calendar.getInstance();
		decemberDeadline.set(Calendar.YEAR, newYear);
		decemberDeadline.set(Calendar.MONTH, Calendar.DECEMBER);
		decemberDeadline.set(Calendar.DAY_OF_MONTH, 25);
		decemberDeadline.set(Calendar.HOUR_OF_DAY, 0);
		decemberDeadline.set(Calendar.MINUTE, 0);
		decemberDeadline.set(Calendar.SECOND, 0);
		decemberDeadline.set(Calendar.MILLISECOND, 0);
		
		Calendar familyEditDeadline = Calendar.getInstance();
		familyEditDeadline.set(Calendar.YEAR, newYear);
		familyEditDeadline.set(Calendar.MONTH, Calendar.DECEMBER);
		familyEditDeadline.set(Calendar.DAY_OF_MONTH, 25);
		familyEditDeadline.set(Calendar.HOUR_OF_DAY, 0);
		familyEditDeadline.set(Calendar.MINUTE, 0);
		familyEditDeadline.set(Calendar.SECOND, 0);
		familyEditDeadline.set(Calendar.MILLISECOND, 0);

		
		ServerGVs newYearServerGVs = new ServerGVs(deliveryDate.getTime(), seasonStartDate.getTime(),
													DEFAULT_ADDRESS, giftsreceivedDate.getTime(),
													thanksgivingDeadline.getTime(), decemberDeadline.getTime(),
													familyEditDeadline.getTime());
		
		GlobalVariableDBYear newGVDBYear = new GlobalVariableDBYear(newYear, newYearServerGVs);
		globalDB.add(newGVDBYear);
		newGVDBYear.setChanged(true);
		
	}

	@Override
	void addObject(int year, String[] nextLine) {
		// TODO Auto-generated method stub
		
	}

	@Override
	void save(int year)
	{
		String[] header = {"Delivery Date", "Season Start Date", "Warehouse Address", "Gifts Received Deadline",
							"Thanksgiving Deadline", "December Deadline", "Info Edit Deadline"};
		
		GlobalVariableDBYear gvDBYear = globalDB.get(year - BASE_YEAR);
		if(gvDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/GlobalVariables.csv", System.getProperty("user.dir"), year);
			File oncwritefile = new File(path);
			
			try 
	    	{
	    		CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
	    	    writer.writeNext(header);
	    	    writer.writeNext(gvDBYear.getServerGVs().getExportRow());	//Write server gv row
	    	    writer.close();
	    	    
	    	    gvDBYear.setChanged(false);
	    	} 
	    	catch (IOException x)
	    	{
	    		System.err.format("IO Exception: %s%n", x);
	    	}
		}
	}
	
	private class GlobalVariableDBYear extends ServerDBYear
	{
		private ServerGVs serverGVs;
	    	
		GlobalVariableDBYear(int year, String file) throws FileNotFoundException, IOException
	    {
	    	super();
	    	this.serverGVs = readGVs(file);
	    }
		
		GlobalVariableDBYear(int year, ServerGVs serverGVs)
		{
			super();
			this.serverGVs = serverGVs;
		}

		@Override
		List<? extends ONCObject> getList() {
			// TODO Auto-generated method stub
			return null;
		}
	    
	    //getters
		ServerGVs getServerGVs() { return serverGVs; }	
		
		//setters
		void setServerGVs(ServerGVs servGVs) { serverGVs = servGVs; }
	}
}
