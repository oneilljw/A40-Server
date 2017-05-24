package oncserver;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.Timer;

import ourneighborschild.DBYear;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class DBManager 
{	
	private static final boolean DATABASE_AUTOSAVE_ENABLED = true;
	private static final int DBYEARSDB_NUM_OF_FIELDS = 2;
	
	private static final int DATABASE_SAVE_TIMER_RATE = 1000 * 60 * 5; //Five minutes
	
	private static DBManager instance = null;
	private Timer dbSaveTimer;
	private List<ServerPermanentDB> dbPermanentAutosaveList;
	private List<ServerSeasonalDB> dbSeasonalAutosaveList;
	private static List<DBYear> dbYearList;
	
	public static DBManager getInstance(ImageIcon appicon)
	{
		if(instance == null)
			instance = new DBManager(appicon);

		return instance;
	}
	
	private DBManager(ImageIcon appicon)
	{
		//Load the list of years and the keys contained in the database
		dbYearList = new ArrayList<DBYear>();
		
		try 
		{
			importDBYears(String.format("%s/PermanentDB/dbyears.csv", System.getProperty("user.dir")), "Data base Years");
		} 
		catch (FileNotFoundException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//load the encryption keys
		ServerEncryptionManager.getInstance();
	
		//Load the component data bases from persistent store;
		//for the component data bases that are periodically saved, add them to a auto save list
		dbPermanentAutosaveList = new ArrayList<ServerPermanentDB>();	//list of data bases stored thru timer event
		dbSeasonalAutosaveList = new ArrayList<ServerSeasonalDB>();	//list of data bases stored thru timer event
		try
		{
			dbPermanentAutosaveList.add(ServerUserDB.getInstance());
			RegionDB.getInstance(appicon);	//never changed
			ApartmentDB.getInstance(); //saved when new season created, never changed during season
			dbSeasonalAutosaveList.add(ServerGlobalVariableDB.getInstance());
			dbSeasonalAutosaveList.add(ServerPartnerDB.getInstance());
			dbSeasonalAutosaveList.add(ServerChildDB.getInstance());
			dbSeasonalAutosaveList.add(ServerChildWishDB.getInstance());
			dbSeasonalAutosaveList.add(ServerFamilyDB.getInstance());
//			dbPermanentAutosaveList.add(ServerAgentDB.getInstance());
			dbPermanentAutosaveList.add(ServerGroupDB.getInstance());
			dbSeasonalAutosaveList.add(ServerVolunteerDB.getInstance());
			dbSeasonalAutosaveList.add(ServerWarehouseDB.getInstance());
			dbSeasonalAutosaveList.add(ServerFamilyHistoryDB.getInstance());
			dbPermanentAutosaveList.add(ServerWishCatalog.getInstance());
			dbPermanentAutosaveList.add(ServerWishDetailDB.getInstance());
			dbSeasonalAutosaveList.add(ServerMealDB.getInstance());
			dbSeasonalAutosaveList.add(ServerAdultDB.getInstance());
			dbSeasonalAutosaveList.add(PriorYearDB.getInstance());	//never changed once created each season
			dbPermanentAutosaveList.add(ServerInventoryDB.getInstance()); //saved only once, not yearly content
		}
		catch (FileNotFoundException fnf) 
		{
			String error = String.format("Database FileNotFoundException %s", fnf.getMessage());
			       						JOptionPane.showMessageDialog(null, error,  "FileNotFoundException",
			       						JOptionPane.ERROR_MESSAGE, appicon);  
		} 
		catch (IOException ioe)
		{
			String error = String.format("Database I/O exception %s", ioe.getMessage());
			        JOptionPane.showMessageDialog(null, error,  "FileNotFoundException",
								JOptionPane.ERROR_MESSAGE, appicon);  
			        
		}
				
		//Create the data base save timer and enable it if DATABASE_AUTOSAVE_ENABLED
		dbSaveTimer = new Timer(DATABASE_SAVE_TIMER_RATE, new SaveTimerListener());
		if(DATABASE_AUTOSAVE_ENABLED)
			dbSaveTimer.start();
	}
	
	//create a json of DBYear objects
	String getDatabaseStatusList()
	{		
		Gson gson = new Gson();
		Type listOfDBs = new TypeToken<ArrayList<DBYear>>(){}.getType();
			
		String response = "DB_STATUS" + gson.toJson(dbYearList, listOfDBs);
		return response;		
	}
	
	static HtmlResponse getDatabaseStatusJSONP(String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfDBs = new TypeToken<ArrayList<DBYear>>(){}.getType();
	
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + gson.toJson(dbYearList, listOfDBs) +")", HTTPCode.Ok);		
	}
/*	
	static String getDatabaseStatusJSON()
	{		
		Gson gson = new Gson();
		Type listOfDBs = new TypeToken<ArrayList<DBYear>>(){}.getType();
			
		String response = gson.toJson(dbYearList, listOfDBs);
		return response;		
	}
*/	
	String updateDBYear(int year, String json)
	{
		Gson gson = new Gson();
		DBYear updatedDBYear = gson.fromJson(json, DBYear.class);
		
		//find the year in the dbYearList and update the lock status
		int index=0;
		while(index < dbYearList.size() && dbYearList.get(index).getYear() != updatedDBYear.getYear())
			index++;
		
		if(index < dbYearList.size())
		{
			dbYearList.get(index).setLock(updatedDBYear.isLocked());
			exportDBYearsList();
			return "UPDATED_DBYEAR" + gson.toJson(dbYearList.get(index));
		}
		else
			return "UPDATE_FAILED";
	}
	
	void processUpdatedDBYear()
	{
		
	}
	String createNewYear()
	{
		String response;
		
		//determine the year. See if it already exists. If it doesn't create it.
		//Only the current year can be created. For example, if the current date is not in 
		//2015, the 2015 data base can not be created.
		Calendar today = Calendar.getInstance();
		int newYear = today.get(Calendar.YEAR);
		
		//check to see if current year is in data base, it would have to be the last one
		//if its not create a new DBYear and add it to the dbYearList
		DBYear lastDBYear = dbYearList.get(dbYearList.size()-1);
		if(lastDBYear.getYear() < newYear)
		{
			//add a new component year to each of the component data bases. We can
			//conveniently use the dbAutosaveList to accomplish this. First however, we
			//need to create the directory that the files will be written to
			
			//create the directory in the file structure for the new database, it shouldn't exist
			String directoryPath = String.format("%s/%dDB", System.getProperty("user.dir"), newYear);
			
			if(new File(directoryPath).mkdir())
			{
				//Lock all prior years
				for(DBYear dbYear: dbYearList)
					dbYear.setLock(true);
				
				//add the new year to the list of db years
				DBYear newDBYear = new DBYear(newYear, false); //add a new db year to the list
				dbYearList.add(newDBYear);
				
				//update the DBYears file and create the db component files
				exportDBYearsList();
				
				//for each seasonal component database, ask it to create the new year
				for(ServerSeasonalDB componentDB: dbSeasonalAutosaveList)
					componentDB.createNewYear(newYear);
			
				//return the new DBYear json
				Gson gson = new Gson();
				Type listOfDBYears = new TypeToken<ArrayList<DBYear>>(){}.getType();
				response = "ADDED_DBYEAR" + gson.toJson(dbYearList, listOfDBYears);
			}
			else
				response = String.format("ADD_DBYEAR_FAILED%d server data base directory creation failed", newYear);
		}
		else
			response = String.format("ADD_DBYEAR_FAILED%d server data base already exists", newYear);
		
		return response;
	}
	
	static int getNumberOfYears() { return dbYearList.size(); }
	
	static int getCurrentYear()
	{
		if(dbYearList.isEmpty())
			return -1;
		else
			return dbYearList.get(dbYearList.size()-1).getYear();
	}
	
	static String getMostCurrentYear()
	{
		return getCurrentYear() > -1 ? Integer.toString(dbYearList.get(dbYearList.size()-1).getYear()) : "No Years";
	}
	
	void exportDBYearsList()
	{
		String[] header = {"Year", "Locked?"};
		String path = String.format("%s/PermanentDB/dbyears.csv", System.getProperty("user.dir"));
		 try 
		    {
		    	CSVWriter writer = new CSVWriter(new FileWriter(path));
		    	writer.writeNext(header);
		    	
		    	String[] exportRow = new String[header.length];
		    	for(DBYear dbYear:dbYearList)
		    	{
		    		exportRow[0] = Integer.toString(dbYear.getYear());
		    		exportRow[1] = dbYear.isLocked() ? "yes" : "no";
		    		writer.writeNext(exportRow);	//Get family object row
		    	}
		    	
		    	writer.close();
		    	       	    
		    } 
		    catch (IOException x)
		    {
		    	System.err.format("IO Exception: %s%n", x);
		    }
	}
	
	void importDBYears(String path, String name) throws FileNotFoundException, IOException
	{
    	CSVReader reader = new CSVReader(new FileReader(path));
    	String[] nextLine, header;  		
    		
    	if((header = reader.readNext()) != null)	//Does file have records? 
    	{
    		//Read the data base years file
    		if(header.length == DBYEARSDB_NUM_OF_FIELDS)	//Does the header have the right # of fields? 
    		{
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
    			{
    				int year = (!nextLine[0].isEmpty() && nextLine[0].matches("-?\\d+(\\.\\d+)?")) ? Integer.parseInt(nextLine[0]) : 0;
    				boolean bLocked = nextLine[1].equals("yes");
    				dbYearList.add(new DBYear(year, bLocked));
    			}	
    		}
    		else
    		{
    			String error = String.format("%s file corrupted, header lentgth = %d", name, header.length);
    	       	JOptionPane.showMessageDialog(null, error,  name + "Corrupted", JOptionPane.ERROR_MESSAGE);
    		}		   			
    	}
    	else
    	{
    		String error = String.format("%s file is empty", name);
    		JOptionPane.showMessageDialog(null, error,  name + " Empty", JOptionPane.ERROR_MESSAGE);
    	}
    	
    	reader.close();
	}
	
	private class SaveTimerListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent arg0) 
		{
			//For each year in the database that is unlocked, command each of the component 
			//databases that are periodically saved to save data to persistent store
			for(DBYear dbYear: dbYearList)	
				if(!dbYear.isLocked())
				{
					for(ServerPermanentDB permDB: dbPermanentAutosaveList)
						permDB.save();
					
					for(ServerSeasonalDB seasonalDB: dbSeasonalAutosaveList)
						seasonalDB.save(dbYear.getYear());
				}
		}	
	}
}
