package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.ONCAdult;

public class ServerAdultDB extends ServerSeasonalDB
{
	private static final int ADULT_DB_HEADER_LENGTH = 4;
	private static final int BASE_YEAR = 2012;
	
	private static ServerAdultDB instance = null;
	private static List<AdultDBYear> adultDB;
	
	private ServerAdultDB() throws FileNotFoundException, IOException
	{
		//create the adult data base
		adultDB = new ArrayList<AdultDBYear>();
						
		//populate the adult data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the meal list for each year
			AdultDBYear adultDBYear = new AdultDBYear(year);
							
			//add the list of adults for the year to the db
			adultDB.add(adultDBYear);
							
			//import the adults from persistent store
			importDB(year, String.format("%s/%dDB/AdultDB.csv",
					System.getProperty("user.dir"),
						year), "Meal DB", ADULT_DB_HEADER_LENGTH);
			
			//set the next id
			adultDBYear.setNextID(getNextID(adultDBYear.getList()));
		}
	}
	
	public static ServerAdultDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerAdultDB();
		
		return instance;
	}
	
	String getAdults(int year)
	{
		Gson gson = new Gson();
		Type listOfAdults = new TypeToken<ArrayList<ONCAdult>>(){}.getType();
		
		String response = gson.toJson(adultDB.get(year-BASE_YEAR).getList(), listOfAdults);
		return response;	
	}
	
	static HtmlResponse getAdultsInFamilyJSONP(int year, int famID, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfAdults = new TypeToken<ArrayList<ONCAdult>>(){}.getType();
		
		List<ONCAdult> searchList = adultDB.get(year-BASE_YEAR).getList();
		ArrayList<ONCAdult> responseList = new ArrayList<ONCAdult>();
		
		for(ONCAdult a: searchList)
			if(a.getFamID() == famID)
				responseList.add(a);
		
		String response = gson.toJson(responseList, listOfAdults);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}

	@Override
	String add(int year, String adultjson)
	{
		//Create an ONCAdult object for the added adult
		Gson gson = new Gson();
		ONCAdult addedAdult = gson.fromJson(adultjson, ONCAdult.class);
						
		//retrieve the adult data base for the year
		AdultDBYear adultDBYear = adultDB.get(year - BASE_YEAR);
								
		//set the new ID for the added ONCAdult
		addedAdult.setID(adultDBYear.getNextID());
		
		//add the new adult to the data base
		adultDBYear.add(addedAdult);
		adultDBYear.setChanged(true);
							
		return "ADDED_ADULT" + gson.toJson(addedAdult, ONCAdult.class);
	}
	
	ONCAdult add(int year, ONCAdult addedAdult)
	{			
		//retrieve the adult data base for the year
		AdultDBYear adultDBYear = adultDB.get(year - BASE_YEAR);
								
		//set the new ID for the added ONCAdult
		int adultID = adultDBYear.getNextID();
		addedAdult.setID(adultID);
		
		//add the new adult to the data base
		adultDBYear.add(addedAdult);
		adultDBYear.setChanged(true);
							
		return addedAdult;
	}
	
	String update(int year, String adultjson)
	{
		//Create a ONCAdult object for the updated adult
		Gson gson = new Gson();
		ONCAdult updatedAdult = gson.fromJson(adultjson, ONCAdult.class);
		
		//Find the position for the current adult being updated
		AdultDBYear adultDBYear = adultDB.get(year-BASE_YEAR);
		List<ONCAdult> adultAL = adultDBYear.getList();
		int index = 0;
		while(index < adultAL.size() && adultAL.get(index).getID() != updatedAdult.getID())
			index++;
		
		//Replace the current adult object with the update
		if(index < adultAL.size())
		{
			adultAL.set(index, updatedAdult);
			adultDBYear.setChanged(true);
			return "UPDATED_ADULT" + gson.toJson(updatedAdult, ONCAdult.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	String delete(int year, String adultjson)
	{
		//Create a ONCAdult object for the deleted adult
		Gson gson = new Gson();
		ONCAdult deletedAdult = gson.fromJson(adultjson, ONCAdult.class);
		
		//find and remove the deleted adult from the data base
		AdultDBYear adultDBYear = adultDB.get(year-BASE_YEAR);
		List<ONCAdult> adultAL = adultDBYear.getList();
		
		int index = 0;
		while(index < adultAL.size() && adultAL.get(index).getID() != deletedAdult.getID())
			index++;
		
		if(index < adultAL.size())
		{
			adultAL.remove(index);
			adultDBYear.setChanged(true);
			return "DELETED_ADULT" + adultjson;
		}
		else
			return "DELETE_FAILED";	
	}

	@Override
	void createNewYear(int year)
	{
		//create a new ONCAdult data base year for the year provided in the year parameter
		//The adult db year list is initially empty prior to the intake of families,
		//so all we do here is create a new AdultDBYear for the new year and save it.
		AdultDBYear adultDBYear = new AdultDBYear(year);
		adultDB.add(adultDBYear);
		adultDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		//Get the adult list for the year and add create and add the adult
		AdultDBYear adultDBYear = adultDB.get(year - BASE_YEAR);
		adultDBYear.add(new ONCAdult(nextLine));
	}

	@Override
	void save(int year)
	{
		String[] header = {"ID", "Fam ID", "Name", "Gender"};
		
		AdultDBYear adultDBYear = adultDB.get(year - BASE_YEAR);
		if(adultDBYear.isUnsaved())
		{
//			System.out.println(String.format("ServerAdultDB save() - Saving Adult DB, size= %d", adultDBYear.getList().size()));
			String path = String.format("%s/%dDB/AdultDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(adultDBYear.getList(), header, path);
			adultDBYear.setChanged(false);
		}
	}
	
	private class AdultDBYear extends ServerDBYear
    {
    	private List<ONCAdult> adultList;
    	
    	AdultDBYear(int year)
    	{
    		super();
    		adultList = new ArrayList<ONCAdult>();
    	}
    	
    	//getters
    	List<ONCAdult> getList() { return adultList; }
    	
    	void add(ONCAdult addedAdult) { adultList.add(addedAdult); }
    }
}
