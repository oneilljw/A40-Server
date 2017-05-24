package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCVolunteer;
import ourneighborschild.ONCWarehouseVolunteer;

public class ServerWarehouseDB extends ServerSeasonalDB 
{
	private static final int WAREHOUSE_DB_HEADER_LENGTH = 5;
	
	private static List<WarehouseDBYear> warehouseDB;
	private static ServerWarehouseDB instance = null;
	
	private static ClientManager clientMgr;
	
	private ServerWarehouseDB() throws FileNotFoundException, IOException
	{
		//create the driver data bases for TOTAL_YEARS number of years
		warehouseDB = new ArrayList<WarehouseDBYear>();
		
		clientMgr = ClientManager.getInstance();

		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the child list for each year
			WarehouseDBYear warehouseDBYear = new WarehouseDBYear(year);
									
			//add the list of children for the year to the db
			warehouseDB.add(warehouseDBYear);
									
			//import the volunteers from persistent store
			importDB(year, String.format("%s/%dDB/WarehouseDB.csv",
					System.getProperty("user.dir"),
						year), "Warehouse DB", WAREHOUSE_DB_HEADER_LENGTH);
									
			//set the next id
			warehouseDBYear.setNextID(getNextID(warehouseDBYear.getList()));
		}
	}
	
	public static ServerWarehouseDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerWarehouseDB();
		
		return instance;
	}
	
	//Search the database for the volunteer. Return a sign-in list if the volunteer is found. 
	String getWarehouseSignInHistory(int year, String volunteerID)
	{
		List<ONCWarehouseVolunteer> volList = warehouseDB.get(year - BASE_YEAR).getList();
		List<ONCWarehouseVolunteer> histList = new ArrayList<ONCWarehouseVolunteer>();
		
		int searchVolID = Integer.parseInt(volunteerID);

		for(ONCWarehouseVolunteer vol : volList)
			if(searchVolID == -1 || vol.getVolunteerID() == searchVolID)
				histList.add(vol);
		
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCWarehouseVolunteer>>(){}.getType();
			
		String response = gson.toJson(histList, listtype);
		return response;	
	}
	
	void add(int year, ONCVolunteer addedVol)
	{
		WarehouseDBYear whDBYear = warehouseDB.get(year - BASE_YEAR);
		
		ONCWarehouseVolunteer addedWHVol = new ONCWarehouseVolunteer(whDBYear.getNextID(), 
															addedVol.getID(), addedVol.getGroup(),
															addedVol.getComment(), new Date());
		
		whDBYear.add(addedWHVol);
		whDBYear.setChanged(true);
		
		//notify all in year clients
		Gson gson = new Gson();
		clientMgr.notifyAllInYearClients(year, "ADDED_VOLUNTEER" + gson.toJson(addedWHVol, ONCWarehouseVolunteer.class));
	}

	@Override
	String add(int year, String userjson) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new  Warehouse data base year for the year provided in the newYear parameter
		//The warhouse db year list is initially empty prior to the import of volunteers, so all we
		//do here is create a new WarehouseDBYear for the newYear and save it.
		WarehouseDBYear warehouseDBYear = new WarehouseDBYear(newYear);
		warehouseDB.add(warehouseDBYear);
		warehouseDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		WarehouseDBYear warehouseDBYear = warehouseDB.get(year - BASE_YEAR);
		warehouseDBYear.add(new ONCWarehouseVolunteer(nextLine));	
	}

	@Override
	void save(int year)
	{
		 WarehouseDBYear warehouseDBYear = warehouseDB.get(year - BASE_YEAR);
		 
		 if(warehouseDBYear.isUnsaved())
		 {
			String[] warehouseHeader = {"Log ID", "Volunteer ID" , "Group", "Comment", "Timestamp"};
			 
			String path = String.format("%s/%dDB/WarehouseDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(warehouseDBYear.getList(), warehouseHeader, path);
			warehouseDBYear.setChanged(false);
		}
	}
	
	private class WarehouseDBYear extends ServerDBYear
	{
		private List<ONCWarehouseVolunteer> volList;
	    	
	    WarehouseDBYear(int year)
	    {
	    	super();
	    	volList = new ArrayList<ONCWarehouseVolunteer>();
	    }
	    
	    //getters
	    List<ONCWarehouseVolunteer> getList() { return volList; }
	    
	    void add(ONCWarehouseVolunteer addedVol) { volList.add(addedVol); }
	}
}
