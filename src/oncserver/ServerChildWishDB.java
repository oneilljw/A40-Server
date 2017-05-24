package oncserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ourneighborschild.HistoryRequest;
import ourneighborschild.ONCChild;
import ourneighborschild.ONCChildWish;
import ourneighborschild.WishStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerChildWishDB extends ServerSeasonalDB
{
	private static final int CHILD_WISH_DB_HEADER_LENGTH = 10;
	private static final int BASE_YEAR = 2012;
	private static final int NUMBER_OF_WISHES_PER_CHILD = 3;
	private static ServerChildWishDB instance = null;

	private static List<ChildWishDBYear> childwishDB;
	private ServerChildDB childDB; //Reference used to update ChildWishID's 
	private ServerPartnerDB serverPartnerDB;
	
	private ServerChildWishDB() throws FileNotFoundException, IOException
	{
		//create the child wish data base
		childwishDB = new ArrayList<ChildWishDBYear>();
						
		//populate the child wish data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the child wish list for each year
			ChildWishDBYear cwDBYear = new ChildWishDBYear(year);
							
			//add the list of children for the year to the db
			childwishDB.add(cwDBYear);
							
			//import the children from persistent store
			importDB(year, String.format("%s/%dDB/ChildWishDB.csv",
					System.getProperty("user.dir"),
						year), "Child Wish DB", CHILD_WISH_DB_HEADER_LENGTH);
			
//			System.out.println(String.format("%d Wish objects imported: %d", year, cwDBYear.getList().size()));
			
			//set the next id
			cwDBYear.setNextID(getNextID(cwDBYear.getList()));
		}

		childDB = ServerChildDB.getInstance();
		serverPartnerDB = ServerPartnerDB.getInstance();
	}
	
	public static ServerChildWishDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerChildWishDB();
		
		return instance;
	}
	
	List<ONCChildWish> getChildWishList(int year)
	{
		return childwishDB.get(year - BASE_YEAR).getList();
	}
/*	
	void updateWishIDs()
	{		
		ServerWishCatalog wishCatDB = null;
		try {
			 wishCatDB = ServerWishCatalog.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(int year=2012; year<2015; year++)
		{
			//get the wish catalog for the year
			List<ONCWish> wishCatYear = wishCatDB.getWishCatalogYear(year);
			
			//retrieve the child wish data base for the year
			ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
			
			//iterate thru each wish and match it to the wish in the catalog. Then
			//update the wishID
			for(ONCChildWish cw:cwDBYear.getList())
			{
				//search for wish name in the catalog matching the child wish base
				int index = 0;
//				while(index < wishCatYear.size() && 
//						!wishCatYear.get(index).getName().equals(cw.getChildWishBase()))
//					index++;
				
				if(index < wishCatYear.size())
				{
//					System.out.println(String.format("ServerChildWishDB.updateWishID: cwID: %d, wishName: %s, "
//							+ "wishID: %d", cw.getID(), cw.getChildWishBase(), wishCatYear.get(index).getID()));
					cw.setWishID(wishCatYear.get(index).getID());
				}
				else
				{
					cw.setWishID(-1);	//wish not found in wish catalog - should be impossible
				}
			}
			
			cwDBYear.setChanged(true);	//mark DB for saving
		}
			
	}
*/	
	@Override
	String add(int year, String wishjson)
	{
		//Create a child wish object for the added wish
		Gson gson = new Gson();
		ONCChildWish addedWish = gson.fromJson(wishjson, ONCChildWish.class);
		
		//retrieve the child wish data base for the year
		ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
				
		//set the new ID for the added child wish
		addedWish.setID(cwDBYear.getNextID());
				
		//retrieve the old wish being replaced
		ONCChildWish oldWish = getWish(year, addedWish.getChildID(), addedWish.getWishNumber());
		
		//Add the new wish to the proper data base
		cwDBYear.add(addedWish);
		cwDBYear.setChanged(true);
		
		//Update the child object with new wish
		childDB.updateChildsWishID(year, addedWish);
		
		//process new wish to see if other data bases require update. They do if the wish
		//status has caused a family status change or if a partner assignment has changed
		processWishAdded(year, oldWish, addedWish);
		
		return "WISH_ADDED" + gson.toJson(addedWish, ONCChildWish.class);
	}
	
	void processWishAdded(int year, ONCChildWish oldWish, ONCChildWish addedWish)
	{
		//test to see if the family status needs to change
		ServerFamilyDB serverFamilyDB = null;
		try {
			serverFamilyDB = ServerFamilyDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		serverFamilyDB.checkFamilyGiftStatusAndGiftCardOnlyOnWishAdded(year, addedWish.getChildID());
		
		//test to see if assignee are changing, if the old wish exists	
		if(oldWish != null && oldWish.getChildWishAssigneeID() != addedWish.getChildWishAssigneeID())
		{
			//assignee change -- need to adjust partner gift assignment counts in partner DB
			serverPartnerDB.updateGiftAssignees(year, oldWish.getChildWishAssigneeID(), 
												addedWish.getChildWishAssigneeID());
		}

		//test to see if wish status is changing to Received from Delivered or Shopping, or from Assigned to
		//Delivered. If, so, increment the associated gift count for the assigned partner. Once an ornament is
		//Delivered, don't decrement the Delivered count, as we expect the partner to provide the gift.
		//Similarly, once a gift is received from the partner, don't decrement the received gift count, even 
		//if we misplace the gift in the warehouse. 
		
		//That can give rise to the unusual condition that a gift is received twice. This can only happen if 
		//once a gift is received it goes missing, we are unable to find it, and have to shop for a replacement.
		//If this occurs, the total partner count of gifts received will be greater than the number of gifts
		//delivered to families. Refer to the ONC Child Wish Status Life Cycle for additional detail
		if(oldWish != null && addedWish.getChildWishStatus() == WishStatus.Received && 
		   (oldWish.getChildWishStatus() == WishStatus.Delivered || oldWish.getChildWishStatus() == WishStatus.Shopping))
		{
			serverPartnerDB.incrementGiftActionCount(year, addedWish);
		}
		else if(oldWish != null && addedWish.getChildWishStatus() == WishStatus.Delivered && oldWish.getChildWishStatus() == WishStatus.Assigned)
		{
			serverPartnerDB.incrementGiftActionCount(year, addedWish);
		}
	}
	
	void deleteChildWishes(int year, int childID)
	{
		ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
		List<ONCChildWish> cwAL = cwDBYear.getList();
		for(int index = cwAL.size()-1; index >= 0; index--) 
			if(cwAL.get(index).getChildID() == childID)
			{
				cwAL.remove(index);
				cwDBYear.setChanged(true);
			}
	}

	
	//Search the database for the child's wish history. Return a json of ArrayList<ONCChildWish>
	String getChildWishHistory(int year, String reqjson)
	{
		//Convert list to json and return it
		Gson gson = new Gson();
		HistoryRequest whRequest = gson.fromJson(reqjson, HistoryRequest.class);
		
		List<ONCChildWish> cwHistory = new ArrayList<ONCChildWish>();
		List<ONCChildWish> cwAL = childwishDB.get(year - BASE_YEAR).getList();
		
		//Search for child wishes that match the child id
		for(ONCChildWish cw: cwAL)
			if(cw.getChildID() == whRequest.getID() && cw.getWishNumber() == whRequest.getNumber())
				cwHistory.add(cw);
			
		//Convert list to json and return it
		Type listtype = new TypeToken<ArrayList<ONCChildWish>>(){}.getType();
		
		String response = gson.toJson(cwHistory, listtype);
		return response;
	}
	
	List<ONCChildWish> getChildWishHistory(int year, int childID, int wn)
	{
		//create the return list
		List<ONCChildWish> cwHistory = new ArrayList<ONCChildWish>();
		List<ONCChildWish> cwAL = childwishDB.get(year - BASE_YEAR).getList();
		
		//Search for child wishes that match the child id and wish number. For each 
		//wish that matches the child id and wish number, add it to the wish history list
		for(ONCChildWish cw: cwAL)
			if(cw.getChildID() == childID && cw.getWishNumber() == wn)
				cwHistory.add(cw);
		
		//sort the wish chronologically, most recent wish first in the list
		Collections.sort(cwHistory, new ChildWishDateChangedComparator());
		
		return cwHistory;
	}
	
	/***************
	 * Method searches the child wish data base indicated by the year parameter for a match
	 * with the wishID parameter. If the wish is found, it is returned, else null is returned
	 * @param year
	 * @param wishID
	 * @return
	 */
	static ONCChildWish getWish(int year, int wishID)
	{
		List<ONCChildWish> cwAL = childwishDB.get(year - BASE_YEAR).getList();	//Get the child wish AL for the year
		
		int index = cwAL.size() -1;	//Set the element to the last child wish in the array
		
		//Search from the bottom of the data base for speed. New wishes are added to the bottom
		while (index >= 0 && cwAL.get(index).getID() != wishID)
			index--;
		
		if(index == -1)
			return null;	//Wish wasn't found in data base
		else
			return cwAL.get(index);		
	}
	
	ONCChildWish getWish(int year, int childid, int wishnum)
	{
		List<ONCChildWish> cwAL = childwishDB.get(year - BASE_YEAR).getList();	//Get the child wish AL for the year
		
		int index = cwAL.size() -1;	//Set the element to the last child wish in the array
		
		//Search from the bottom of the data base for speed. New wishes are added to the bottom
		while (index >= 0 && (cwAL.get(index).getChildID() != childid || 
				cwAL.get(index).getWishNumber() != wishnum))
			index--;
		
		if(index == -1)
			return null;	//Wish wasn't found in data base
		else
			return cwAL.get(index);		
	}
	
	List<PriorYearPartnerPerformance> getPriorYearPartnerPerformanceList(int newYear)
	{
		//create the list to be returned
		List<PriorYearPartnerPerformance> pyPerformanceList = new ArrayList<PriorYearPartnerPerformance>();
		
		//get a Calendar object gifts received deadline
		
		//get the receive gift deadline for the prior year
		ServerGlobalVariableDB gvDB = null;
		try 
		{
			gvDB = ServerGlobalVariableDB.getInstance();
		}
		catch (FileNotFoundException e) 
		{
			// TODO Auto-generated catch block
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
		}
		
		//set the received gift deadline. All gifts must be received prior to this date/time
		
		//set up variables for each of the parameters of interest
		int pyPartnerReceivedBeforeID, pyPartnerReceivedAfterID, pyPartnerDeliveredID, pyPartnerAssignedID;
		
		//for each child wish, get the prior year wish history for the child's wish 
		for(ONCChild pyc: childDB.getList(newYear-1))
			for(int wn=0; wn < NUMBER_OF_WISHES_PER_CHILD; wn++)
			{
				//reset the assigned and received partner ID's
				pyPartnerReceivedBeforeID = -1;
				pyPartnerReceivedAfterID = -1;
				pyPartnerDeliveredID = -1;
				pyPartnerAssignedID = -1;
				
				//get the prior year wish history for the child and wish number
				List<ONCChildWish> pycWH = getChildWishHistory(newYear-1, pyc.getID(), wn);
				
				//work down the list from earliest wishes to most recent. For all wishes that were
				//created prior to the gifts received deadline, record the ID's of the partner who
				//the wish was last received from and last assigned to prior to the deadline. Each
				//id is recorded, however, as a newer id is found, the older id is overwritten
				int index = 0;
				while(index < pycWH.size())
				{
					if(pycWH.get(index).getChildWishStatus() == WishStatus.Assigned)
					{
						pyPartnerAssignedID = pycWH.get(index).getChildWishAssigneeID();
					}
					else if(pycWH.get(index).getChildWishStatus() == WishStatus.Delivered)
					{
						pyPartnerDeliveredID = pycWH.get(index).getChildWishAssigneeID();
					}
					else if(pycWH.get(index).getChildWishStatus() == WishStatus.Received)
					{
						if(pycWH.get(index).getChildWishDateChanged().compareTo(gvDB.getDateGiftsRecivedDealdine(newYear-1)) <= 0)
							pyPartnerReceivedBeforeID = pycWH.get(index).getChildWishAssigneeID();
						else
							pyPartnerReceivedAfterID = pycWH.get(index).getChildWishAssigneeID();
					}
				
					index++;
				}
				
				//create a new pyPartnerPerformance object if either the AssignedID, DelliveredID, or the Received
				//From ID is an actual partner ID and not -1 or 0 
				if(pyPartnerAssignedID > -1 || pyPartnerDeliveredID > -1 || 
					pyPartnerReceivedBeforeID > -1 || pyPartnerReceivedAfterID > -1)
				{
					pyPerformanceList.add(new PriorYearPartnerPerformance(pyc.getID(), wn, pyPartnerAssignedID,
										pyPartnerDeliveredID, pyPartnerReceivedBeforeID, pyPartnerReceivedAfterID));
				}
			}
		
//		for(PriorYearPartnerPerformance pyPartPerf : pyPerformanceList)
//			if(pyPartPerf.getPYPartnerWishReceivedAfterDeadlineID() > -1)
//				System.out.println(String.format("Child %d, Wish %d received after deadline from %d",
//						pyPartPerf.getPYChildID(), pyPartPerf.getPYPWishNumber(),
//						pyPartPerf.getPYPartnerWishReceivedAfterDeadlineID()));
		
		return pyPerformanceList;
	}

	@Override
	void addObject(int year, String[] nextLine) 
	{
		//Get the child wish AL for the year and add the object
		ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
		cwDBYear.add(new ONCChildWish(nextLine));
	}

	@Override
	void createNewYear(int newYear) 
	{
		//create a new child wish data base year for the year provided in the newYear parameter
		//The child wish db year list is initially empty prior to the selection of child wishes,
		//so all we do here is create a new ChildWishDBYear for the newYear and save it.
		ChildWishDBYear childwishDBYear = new ChildWishDBYear(newYear);
		childwishDB.add(childwishDBYear);
		childwishDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
	
	private class ChildWishDateChangedComparator implements Comparator<ONCChildWish>
	{
		@Override
		public int compare(ONCChildWish cw1, ONCChildWish cw2)
		{
			return cw1.getChildWishDateChanged().compareTo(cw2.getChildWishDateChanged());
		}
	}
	
	private class ChildWishDBYear extends ServerDBYear
    {
    	private List<ONCChildWish> cwList;
    	
    	ChildWishDBYear(int year)
    	{
    		super();
    		cwList = new ArrayList<ONCChildWish>();
    	}
    	
    	//getters
    	List<ONCChildWish> getList() { return cwList; }
    	
    	void add(ONCChildWish addedWish) { cwList.add(addedWish); }
    }

	@Override
	void save(int year)
	{
		String[] header = {"Child Wish ID", "Child ID", "Wish ID", "Detail",
	 			"Wish #", "Restrictions", "Status",
	 			"Changed By", "Time Stamp", "Org ID"};
		
		ChildWishDBYear cwDBYear = childwishDB.get(year - BASE_YEAR);
		if(cwDBYear.isUnsaved())
		{
//			System.out.println(String.format("ServerChildWishDB save() - Saving Child Wish DB"));
			String path = String.format("%s/%dDB/ChildWishDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(cwDBYear.getList(),  header, path);
			cwDBYear.setChanged(false);
		}
	}
	
	void convertWishStatus()
	{
		
	}
}
