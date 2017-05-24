package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.ONCObject;
import ourneighborschild.WishDetail;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerWishDetailDB extends ServerPermanentDB
{
//	private static final int WISH_DETAIL_HEADER_LENGTH = 3;
	private static final int DETAIL_RECORD_LENGTH = 3;
	private static final String DETAIL_FILENAME = "WishDetailDB.csv";

//	private static List<WishDetailDBYear> wdDB;
	private static List<WishDetail> wdDB;
//	int nextID;
//	boolean bSaveRequired;
	private static ServerWishDetailDB instance = null;
	
	private ServerWishDetailDB() throws FileNotFoundException, IOException
	{
		//create the wish detail data bases for TOTAL_YEARS number of years
//		wdDB = new ArrayList<WishDetailDBYear>();
		wdDB = new ArrayList<WishDetail>();
/*														
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the wish detail list for year
			WishDetailDBYear detailDBYear = new WishDetailDBYear(year);
			
			//add the list for the year to the db
			wdDB.add(detailDBYear);
			
			//import the data from persistent store	
			importDB(year, String.format("%s/%dDB/WishDetailDB.csv", System.getProperty("user.dir"),
							year), "Wish Catalog", WISH_DETAIL_HEADER_LENGTH);
			
			//set the next ID
			detailDBYear.setNextID(getNextID(detailDBYear.getList()));
		}
*/		
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), DETAIL_FILENAME), "Wish Detail", DETAIL_RECORD_LENGTH);
		nextID = getNextID(wdDB);
		bSaveRequired = false;
	}
	
	public static ServerWishDetailDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerWishDetailDB();
		
		return instance;
	}
	
	String update(int year, String json)
	{
		//Create a wish detail object for the request wish detail update
		Gson gson = new Gson();
		WishDetail reqWishDetail = gson.fromJson(json, WishDetail.class);
		
		//Find the position for the current wish detail being updated
//		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
//		List<WishDetail> wishdetailAL = detailDBYear.getList();
		int index = 0;
		while(index < wdDB.size() && wdDB.get(index).getID() != reqWishDetail.getID())
			index++;
		
		//If wish detail is located, replace the current wish detail with the update.
		if(index == wdDB.size()) 
		{
			WishDetail currWishDetail = wdDB.get(index);
			return "UPDATE_FAILED" + gson.toJson(currWishDetail , WishDetail.class);
		}
		else
		{
			wdDB.set(index, reqWishDetail);
			bSaveRequired = true;
			return "UPDATED_WISH_DETAIL" + json;
		}
	}
	
	@Override
	String add(String json)
	{
		//Create a wish detail object
		Gson gson = new Gson();
		WishDetail addedWishDetail = gson.fromJson(json, WishDetail.class);
	
		//set the new id for the detail and add to the year data base
//		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		addedWishDetail.setID(nextID++);
		wdDB.add(addedWishDetail);
		bSaveRequired = true;
//		detailDBYear.setChanged(true);
		
		return "ADDED_WISH_DETAIL" + gson.toJson(addedWishDetail, WishDetail.class);
	}

	String delete(int year, String json)
	{
		//Create a wish detail object for the deleted wish detail request
		Gson gson = new Gson();
		WishDetail reqDelWishDetail = gson.fromJson(json, WishDetail.class);
		
		//find the partner in the db
//		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
//		List<WishDetail> wishdetailAL = detailDBYear.getList();
	
		int index = 0;
		while(index < wdDB.size() && wdDB.get(index).getID() != reqDelWishDetail.getID())
			index++;
		
		//partner must be present and have no ornaments assigned to be deleted
		if(index < wdDB.size())
		{
			wdDB.remove(index);
			bSaveRequired = true;
//			detailDBYear.setChanged(true);
			return "DELETED_WISH_DETAIL" + json;
		}
		else
			return "DELETE_FAILED" + json;
	}
	
	//Search the database for the family. Return a json if the family is found. 
	String getWishDetail(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<WishDetail>>(){}.getType();
			
		String response = gson.toJson(wdDB, listtype);
		
		return response;	
	}

	@Override
	void addObject(String[] nextLine)
	{
//		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
//		detailDBYear.add(new WishDetail(nextLine));
		wdDB.add(new WishDetail(nextLine));
	}
/*	
	private class WishDetailDBYear extends ServerDBYear
	{
		private List<WishDetail> wdList;
	    	
	    WishDetailDBYear(int year)
	    {
	    	super();
	    	wdList = new ArrayList<WishDetail>();
	    }
	    	
	    //getters
	    List<WishDetail> getList() { return wdList; }
	    	
	    void add(WishDetail addedDetail) {wdList.add(addedDetail); }
	}

	@Override
	void save(int year)
	{
		String[] header = {"Wish Detail ID", "Name", "Choices"};
		
		WishDetailDBYear detailDBYear = wdDB.get(year - BASE_YEAR);
		if(detailDBYear.isUnsaved())
		{
			String path = String.format("%s/%dDB/WishDetailDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(detailDBYear.getList(), header, path);
			detailDBYear.setChanged(false);
		}
	}

	@Override
	void save()
	{
		if(bSaveRequired)
		{
			String[] header = {"Wish Detail ID", "Name", "Choices"};
			
			String path = System.getProperty("user.dir") + DETAIL_FILENAME;
			File oncwritefile = new File(path);
			
			try 
			{
				CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
				writer.writeNext(header);
	    	
				for(WishDetail wd: wdDB)
					writer.writeNext(wd.getExportRow());	//Write server Apartment row
	    	
				writer.close();
				
				bSaveRequired = false;
			} 
			catch (IOException x)
			{
				System.err.format("IO Exception: %s%n", x);
			}
		}
	}
*/	
	@Override
	String[] getExportHeader() { return new String[] {"Wish Detail ID", "Name", "Choices"}; }
	
	@Override
	String getFileName() { return DETAIL_FILENAME; }
	
	@Override
	List<? extends ONCObject> getONCObjectList() { return wdDB; }
}
