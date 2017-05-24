package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.ONCObject;
import ourneighborschild.ONCWish;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerWishCatalog extends ServerPermanentDB
{
//	private static final int WISH_CATALOG_HEADER_LENGTH = 7;
	private static final int CATALOG_RECORD_LENGTH = 7;
	private static final String CATALOG_FILENAME = "WishCatalog.csv";
//	private static List<WishCatalogDBYear> catalogDB;
	private static List<ONCWish> catalogDB;
//	private int nextID;
//	private boolean bSaveRequired;
	private static ServerWishCatalog instance = null;
	
	private ServerWishCatalog() throws FileNotFoundException, IOException
	{
		//create the wish catalog data bases for TOTAL_YEARS number of years
//		catalogDB = new ArrayList<WishCatalogDBYear>();
		catalogDB = new ArrayList<ONCWish>();
	
//		//populate the last TOTAL_YEARS
//		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
//		{
//			//create the wish catalog list for year
//			WishCatalogDBYear catalogDBYear = new WishCatalogDBYear(year);
//			
//			//add the list for the year to the db
//			catalogDB.add(catalogDBYear);
//						
//			importDB(year, String.format("%s/%dDB/WishCatalog.csv",
//						System.getProperty("user.dir"),
//							year), "Wish Catalog", WISH_CATALOG_HEADER_LENGTH);
//													
//			catalogDBYear.setNextID(getNextID(catalogDBYear.getList()));
//		}
		
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), CATALOG_FILENAME), "Wish Catalog", CATALOG_RECORD_LENGTH);
		nextID = getNextID(catalogDB);
		bSaveRequired = false;
	}
	
	public static ServerWishCatalog getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerWishCatalog();
		
		return instance;
	}
	
	//return the catalog list as a json
	String getWishCatalog(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCWish>>(){}.getType();
			
		String response = gson.toJson(catalogDB, listtype);
		return response;	
	}
	
	String update(int year, String json)
	{
		//Create a wish detail object for the request wish detail update
		Gson gson = new Gson();
		ONCWish reqWish = gson.fromJson(json, ONCWish.class);
		
		//Find the position for the current family being replaced
//		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
//		List<ONCWish> catAL = catalogDBYear.getList();
		int index = 0;
		while(index < catalogDB.size() && catalogDB.get(index).getID() != reqWish.getID())
			index++;
		
		//If catalog wish is located, replace the wish with the update.
		if(index == catalogDB.size()) 
		{
			ONCWish currWish = catalogDB.get(index);
			return "UPDATE_FAILED" + gson.toJson(currWish , ONCWish.class);
		}
		else
		{
			catalogDB.set(index, reqWish);
//			catalogDB.setChanged(true);
			bSaveRequired = true;
			return "UPDATED_CATALOG_WISH" + json;
		}
	}
	
	@Override
	String add(String json)
	{
		//Create a wish object to add to the catalog
		Gson gson = new Gson();
		ONCWish addWishReq = gson.fromJson(json, ONCWish.class);
	
//		//set the new ID for the catalog wish
//		WishCatalogDBYear catalogDBYear = catalogDB.get(year-BASE_YEAR);
//		addedWish.setID(catalogDBYear.getNextID());
//		
//		//add the new wish
//		catalogDBYear.add(addedWish);
//		catalogDBYear.setChanged(true);
		
		addWishReq.setID(nextID++);
		catalogDB.add(addWishReq);
		bSaveRequired = true;
		
		return "ADDED_CATALOG_WISH" + gson.toJson(addWishReq, ONCWish.class);
	}
	
	String delete(int year, String json)
	{
		//Create a catalog wish  object for the deleted catalog wish request
		Gson gson = new Gson();
		ONCWish reqDelWish = gson.fromJson(json, ONCWish.class);
	
//		//find the wish in the catalog
//		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
//		List<ONCWish> catAL = catalogDBYear.getList();
		
		int index = 0;
		while(index < catalogDB.size() && catalogDB.get(index).getID() != reqDelWish.getID())
			index++;
		
		//wish must be present in catalog to be deleted
		if(index < catalogDB.size())
		{
			catalogDB.remove(index);
			bSaveRequired = true;
//			catalogDBYear.setChanged(true);
			return "DELETED_CATALOG_WISH" + json;
		}
		else
			return "DELETE_CATALOG_WISH_FAILED" + json;
	}
	
	String findWishNameByID(int year, int wishID)
	{
//		//get list for year
//		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
//		List<ONCWish> wishList = catalogDBYear.getList();
		
		//find the ONCWish by ID
		int index = 0;
		while(index < catalogDB.size() && catalogDB.get(index).getID() != wishID)
			index++;
		
		if(index < catalogDB.size())
			return catalogDB.get(index).getName();
		else
			return "No Wish Found";
	}
	
	static int findWishIDByName(int year, String wishname)
	{
//		//get list for year
//		WishCatalogDBYear catalogDBYear = catalogDB.get(year - BASE_YEAR);
//		List<ONCWish> wishCatalog = catalogDBYear.getList();
				
		int index=0;
		while(index < catalogDB.size() &&!catalogDB.get(index).getName().equals(wishname))
			index++;
				
		if(index < catalogDB.size())
			return catalogDB.get(index).getID();
		else
			return -1;
	}

	@Override
	void addObject(String[] nextLine)
	{
		catalogDB.add(new ONCWish(nextLine));
	}
/*
	@Override
	void save()
	{
		if(bSaveRequired)
		{
			String[] header = {"Wish ID", "Name", "List Index", "Wish Detail 1 ID", 
		 			"Wish Detail 2 ID", "Wish Detail 3 ID", "Wish Detail 4 ID"};
			
			String path = System.getProperty("user.dir") + "/WishCatalog.csv";
			File oncwritefile = new File(path);
			
			try 
			{
				CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
				writer.writeNext(header);
	    	
				for(ONCWish w: catalogDB)
					writer.writeNext(w.getExportRow());	//Write server Apartment row
	    	
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
	String[] getExportHeader()
	{
		return new String[] {"Wish ID", "Name", "List Index", "Wish Detail 1 ID", 
	 						"Wish Detail 2 ID", "Wish Detail 3 ID", "Wish Detail 4 ID"};
	}
	
	@Override
	String getFileName() { return CATALOG_FILENAME; }
	
	@Override
	List<? extends ONCObject> getONCObjectList() { return catalogDB; }
}
