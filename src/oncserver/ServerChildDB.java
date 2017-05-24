package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildWish;
import ourneighborschild.ONCWebChild;
import ourneighborschild.ONCPartner;
import ourneighborschild.WebChildWish;
import ourneighborschild.WishStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerChildDB extends ServerSeasonalDB
{
	private static final int CHILD_DB_HEADER_LENGTH = 12;
	private static final int NUMBER_WISHES_PER_CHILD = 3;
	
	private static List<ChildDBYear> childDB;
	private static ServerChildDB instance = null;
	
	private ServerChildDB() throws FileNotFoundException, IOException
	{
		childDB = new ArrayList<ChildDBYear>();
				
		//populate the family data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the child list for each year
			ChildDBYear cDBYear = new ChildDBYear(year);
					
			//add the list of children for the year to the db
			childDB.add(cDBYear);
					
			//import the children from persistent store
			importDB(year, String.format("%s/%dDB/ChildDB.csv",
					System.getProperty("user.dir"),
						year), "Child DB", CHILD_DB_HEADER_LENGTH);
					
			//set the next id
			cDBYear.setNextID(getNextID(cDBYear.getList()));
		}
	}
	
	public static ServerChildDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerChildDB();
		
		return instance;
	}
	
	//Search the database for the family. Return a json if the family is found. 
	String getChildren(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<ONCChild>>(){}.getType();
			
		String response = gson.toJson(childDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
	
	static HtmlResponse getChildrenInFamilyJSONP(int year, int famID, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfChildren = new TypeToken<ArrayList<ONCWebChild>>(){}.getType();
		
		List<ONCChild> searchList = childDB.get(year-BASE_YEAR).getList();
		ArrayList<ONCWebChild> responseList = new ArrayList<ONCWebChild>();
		
		for(ONCChild c: searchList)
			if(c.getFamID() == famID)
				responseList.add(new ONCWebChild(c));
		
		String response = gson.toJson(responseList, listOfChildren);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	static HtmlResponse getChildWishesJSONP(int year, int childID, String callbackFunction)
	{	
		//get references to wish catalog, partner data base
		ServerWishCatalog wishCatalog = null;
		ServerPartnerDB serverPartnerDB = null;
		try 
		{
			wishCatalog = ServerWishCatalog.getInstance();
			serverPartnerDB = ServerPartnerDB.getInstance();
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
		
		Gson gson = new Gson();
		Type listOfWishes = new TypeToken<ArrayList<WebChildWish>>(){}.getType();
		
		//get the child
		List<ONCChild> searchList = childDB.get(year-BASE_YEAR).getList();
		ArrayList<WebChildWish> responseList = new ArrayList<WebChildWish>();
		
		for(ONCChild c: searchList)
			if(c.getID() == childID)
			{
				//found child, now create child wish list
				for(int wn=0; wn < 3; wn++)
				{
					//has the wish been created?
					if(c.getChildWishID(wn) == -1)
					{
						responseList.add(new WebChildWish());	//not created
					}
					else		
					{
						ONCChildWish cw = ServerChildWishDB.getWish(year, c.getChildWishID(wn));
						int wishRestriction = cw.getChildWishIndicator();
						String[] restrictions = {" ", "*", "#"};
						String partner;
						if(cw.getChildWishAssigneeID() < 1)
						{
							partner = "";		
						}
						else
						{
							ONCPartner org = serverPartnerDB.getPartner(year,cw.getChildWishAssigneeID());
							partner = org.getName();
						}
						
						responseList.add(new WebChildWish(wishCatalog.findWishNameByID(year, cw.getWishID()),
															cw.getChildWishDetail(), 
															restrictions[wishRestriction], 
															partner, cw.getChildWishStatus()));
					}
				}
			}
		
		
		String response = gson.toJson(responseList, listOfWishes);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	String add(int year, String childjson)
	{
		//Create a child object for the updated child
		Gson gson = new Gson();
		ONCChild addedChild = gson.fromJson(childjson, ONCChild.class);
		
		//get the child data base for the year
		ChildDBYear cDBYear = childDB.get(year - BASE_YEAR);
		
		//set the new ID for the added child
		addedChild.setID(cDBYear.getNextID());
		
		//set the prior year history id for the child
		addedChild.setPriorYearChildID(searchForPriorYearMatch(year, addedChild));
		
		//add the new child to the data base
		cDBYear.add(addedChild);
		cDBYear.setChanged(true);
		
		return "ADDED_CHILD" + gson.toJson(addedChild, ONCChild.class);
		
	}
	
	ONCChild add(int year, ONCChild addedChild)
	{
		//get the child data base for the year
		ChildDBYear cDBYear = childDB.get(year - BASE_YEAR);
		
		//set the new ID for the added child
		int childID = cDBYear.getNextID();
		addedChild.setID(childID);
		
		//set the prior year history id for the child
		addedChild.setPriorYearChildID(searchForPriorYearMatch(year, addedChild));
		
		//add the new child to the data base
		cDBYear.add(addedChild);
		cDBYear.setChanged(true);
		
		return addedChild;
	}
	
	void searchForLastName(int year, String s, List<FamilyReference> resultAL)
    {	
		//create the child list for the year
		ChildDBYear childDBYear = childDB.get(year-BASE_YEAR);
		List<ONCChild> childAL = childDBYear.getList();

		int lastFamIDAdded = -1;	//prevent searching for same family id twice in a row
    	for(ONCChild c: childAL)
    		if(c.getFamID() != lastFamIDAdded && c.getChildLastName().toLowerCase().contains(s.toLowerCase()))
    		{
    			//check to see that the family hasn't already been found
    			//if it hasn't, add it. To do this, need to get Fam Ref ID
    			String refNum = ServerFamilyDB.getFamilyRefNum(year, c.getFamID());
    			int index=0;
    			while(refNum != null && index < resultAL.size() &&
    					!resultAL.get(index).getReferenceID().equals(refNum))
    				index++;
    			
    			if(index == resultAL.size())
    			{
    				resultAL.add(new FamilyReference(refNum)); 
    				lastFamIDAdded = c.getFamID();
    			}
    		}	
    }
	
	int searchForPriorYearMatch(int year, ONCChild child)
	{
		PriorYearDB pyDB = null;
		try {
			pyDB = PriorYearDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(pyDB != null)
		{
			return pyDB.searchForMatch(year, child);
//			System.out.println(String.format("ServerChildDB: addChild - new pycID: %d", pycID));
		}
		else
			return -1;
	}
	
	String deleteChild(int year, String childjson)
	{
		//Create a child object for the deleted child
		Gson gson = new Gson();
		ONCChild deletedChild = gson.fromJson(childjson, ONCChild.class);
		
		
		//check for and decrement partner wish assignment counts
		//and remove wishes for the deleted child
		ServerChildWishDB cwDB = null;
		ServerPartnerDB serverPartnerDB = null;
		try {
			cwDB = ServerChildWishDB.getInstance();
			serverPartnerDB = ServerPartnerDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(cwDB != null)
		{
			//decrement partner wish assignment counts
			for(int wn= 0; wn < NUMBER_WISHES_PER_CHILD; wn++)
			{
				int childWishID = deletedChild.getChildWishID(wn);
				
				if(childWishID != -1)	//does the wish exist?
				{
					ONCChildWish cw = ServerChildWishDB.getWish(year, childWishID);

					//if wish has been assigned, then we have to decrement the partner assignee count
					if(serverPartnerDB != null && cw != null && 
							cw.getChildWishStatus().compareTo(WishStatus.Assigned) >= 0)
					{
						int wishPartnerID = cw.getChildWishAssigneeID();
						serverPartnerDB.decrementGiftsAssignedCount(year, wishPartnerID);
					}
				}
			}
			
			//remove the child's wishes from the child wish data base
			cwDB.deleteChildWishes(year, deletedChild.getID());
		}
		
		//find and remove the deleted child from the data base
		ChildDBYear childDBYear = childDB.get(year-BASE_YEAR);
		List<ONCChild> cAL = childDBYear.getList();
		
		int index = 0;
		while(index < cAL.size() && cAL.get(index).getID() != deletedChild.getID())
			index++;
		
		if(index < cAL.size())
		{
			cAL.remove(index);
			childDBYear.setChanged(true);
			return "DELETED_CHILD" + childjson;
		}
		else
			return "DELETE_FAILED";	
	}
	
	String update(int year, String childjson)
	{
		//Create a child object for the updated child
		Gson gson = new Gson();
		ONCChild updatedChild = gson.fromJson(childjson, ONCChild.class);
		
		//Find the position for the current child being replaced
		ChildDBYear childDBYear = childDB.get(year-BASE_YEAR);
		List<ONCChild> cAL = childDBYear.getList();
		int index = 0;
		while(index < cAL.size() && cAL.get(index).getID() != updatedChild.getID())
			index++;
		
		//Replace the current child object with the update
		if(index < cAL.size())
		{
			ONCChild replChild = cAL.get(index);
//			System.out.println(String.format("repl DOB: %d", replChild.getChildDateOfBirth()));
//			System.out.println(String.format("updt DOB: %d", updatedChild.getChildDateOfBirth()));
//			
//			if(!replChild.getChildDOB().equals(updatedChild.getChildDOB()))
//				System.out.println(updatedChild.getChildDOB());
			
			//if the parameters that effect prior year child id search changed,
			//then conduct a search to attempt to match a prior year child
			if(!replChild.getChildLastName().equals(updatedChild.getChildLastName()) ||
				!replChild.getChildDOB().equals(updatedChild.getChildDOB()) ||
				!replChild.getChildGender().equals(updatedChild.getChildGender()))
			{
//				System.out.println("Searching for Prior Year Child");
				updatedChild.setPriorYearChildID(searchForPriorYearMatch(year, updatedChild));
			}
			
				cAL.set(index, updatedChild);
				childDBYear.setChanged(true);
				return "UPDATED_CHILD" + gson.toJson(updatedChild, ONCChild.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	/**************************************************************************************
	 * This method is called to update the wish id's of the child when a wish is changed.
	 * Each child object has three wishID fields that point to the three wishes for the
	 * child in the ChildWishDB. A value of -1 indicates no wish yet assigned. When a wish
	 * changes, the id corresponding to the changed wish number must be updated. 
	 * @param year - which year's data base is being changed
	 * @param addedWish - the child wish object that was changed
	 ************************************************************************************/
	void updateChildsWishID(int year, ONCChildWish addedWish)
	{
		//Find the child using the added wish child ID
		ChildDBYear childDBYear = childDB.get(year-BASE_YEAR);
		List<ONCChild> cAL = childDBYear.getList();
		int index = 0;
		while(index < cAL.size() && cAL.get(index).getID() != addedWish.getChildID())
			index++;
		
		//Replace the current childwishID with the newly added wish ID
		if(index < cAL.size())
		{
			ONCChild c = cAL.get(index);
			c.setChildWishID(addedWish.getID(), addedWish.getWishNumber());
			childDBYear.setChanged(true);
		}
	}
	
	static List<ONCChild> getChildList(int year, int famid)
	{
		List<ONCChild> cAL = childDB.get(year-BASE_YEAR).getList();
		ArrayList<ONCChild> fChildrenAL = new ArrayList<ONCChild>();
		
		for(ONCChild c:cAL)
			if(c.getFamID() == famid)
				fChildrenAL.add(c);
		
		return fChildrenAL;
	}
	
	int getChildsFamilyID(int year, int childid)
	{
		List<ONCChild> cAL = childDB.get(year-BASE_YEAR).getList();
		
		int index = 0;
		while(index < cAL.size() && cAL.get(index).getID() != childid)
			index++;
		
		if(index < cAL.size())
			return cAL.get(index).getFamID();
		else
			return -1;	
	}

	int getNumChildren(int year) { return childDB.get(year - BASE_YEAR).getList().size(); }
	
	List<ONCChild> getList(int year)
	{
		return childDB.get(year - BASE_YEAR).getList();
	}
/*	
	void exportFamilyDBToCSV(ArrayList<ONCChild>eAL, String path)
    {	
	    try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(path));
	    		
	    	String[] header = {"Child ID", "Family ID", "Child #", "First Name", "Last Name",
		 			"Gender", "DOB", "School", "Wish 1 ID", "Wish 2 ID",
		 			"Wish 3 ID", "Prior Year Child ID"};
	    	writer.writeNext(header);
	    	    
	    	for(ONCChild c:eAL)
	    		writer.writeNext(c.getDBExportRow());	//Get family object row
	    	 
	    	writer.close();
	    	       	    
	    } 
	    catch (IOException x)
	    {
	    		System.err.format("IO Exception: %s%n", x);
	    }
    }
*/    
	
	@Override
	void addObject(int year, String[] nextLine)
	{
		ChildDBYear childDBYear = childDB.get(year-BASE_YEAR);
		childDBYear.add(new ONCChild(year, nextLine));	
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new child data base year for the year provided in the newYear parameter
		//The child db year list is initially empty prior to the import of families, so all we
		//do here is create a new ChildDBYear for the newYear and save it.
		ChildDBYear childDBYear = new ChildDBYear(newYear);
		childDB.add(childDBYear);
		childDBYear.setChanged(true);	//mark this db for persistent saving on the next save event	
	}
	
	private class ChildDBYear extends ServerDBYear
    {
    	private List<ONCChild> cList;
    	
    	ChildDBYear(int year)
    	{
    		super();
    		cList = new ArrayList<ONCChild>();
    	}
    	
    	//getters
    	List<ONCChild> getList() { return cList; }
    	
    	void add(ONCChild addedChild) { cList.add(addedChild); }
    }

	@Override
	void save(int year)
	{
		ChildDBYear cDBYear = childDB.get(year - BASE_YEAR);
		if(cDBYear.isUnsaved())
		{
			String[] header = {"Child ID", "Family ID", "Child #", "First Name", "Last Name",
		 			"Gender", "DOB", "School", "Wish 1 ID", "Wish 2 ID",
		 			"Wish 3 ID", "Prior Year Child ID"};
			
//			System.out.println(String.format("ServerChildDB save() - Saving Server Child DB"));
			String path = String.format("%s/%dDB/ChildDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(cDBYear.getList(),  header, path);
			cDBYear.setChanged(false);
		}
	}
	
/*	
	void updateDOBs()
	{
		//get time zone at GMT
		TimeZone localTZ = TimeZone.getDefault();
		
		//for each year in the data base, update the dob's
		for(int year=2012; year < 2014; year++)
		{
			ChildDBYear cDBYear = childDB.get(year - BASE_YEAR);
			System.out.println(String.format("Processing %d Child Year DB", year));
			
			//for each child, update their dob by calculating their dob time zone
			//offset to GMT and adding it to their local time zone DOB that was previously saved
			for(ONCChild c : cDBYear.getList())
			{
				long currDOB = c.getChildDateOfBirth();
				int gmtOffset = localTZ.getOffset(currDOB);
				c.setChildDateOfBirth(currDOB + gmtOffset);
			}
		
			//mark the db for saving to persistent store
			cDBYear.setChanged(true);
		}
	}
*/	
}
