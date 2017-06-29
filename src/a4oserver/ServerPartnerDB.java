package a4oserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import actforothers.Address;
import actforothers.ONCChildWish;
import actforothers.A4OPartner;
import actforothers.WishStatus;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerPartnerDB extends ServerSeasonalDB
{
	private static final int ORGANIZATION_DB_HEADER_LENGTH = 34;
	private static final int STATUS_CONFIRMED = 5;
	
	private static List<PartnerDBYear> partnerDB;
	
	private static ServerPartnerDB instance = null;
	
	private static ClientManager clientMgr;
	
	private ServerPartnerDB() throws FileNotFoundException, IOException
	{
		//create the partner data bases for TOTAL_YEARS number of years
		partnerDB = new ArrayList<PartnerDBYear>();
				
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the partner list for year
			PartnerDBYear partnerDBYear = new PartnerDBYear(year);
			
			//add the list for the year to the db
			partnerDB.add(partnerDBYear);
					
			importDB(year, String.format("%s/A4O/%dDB/OrgDB.csv",
						System.getProperty("user.dir"),
							year), "Partner DB", ORGANIZATION_DB_HEADER_LENGTH);
			
			//for partners, the leading 4 digits are the year the partner was added. So, must account
			//for that when determining the next ID number to assign
			int nextID = getNextID(partnerDBYear.getList());
			int nextIDinYear = year * 1000;
			if(nextID < nextIDinYear)
				partnerDBYear.setNextID(nextIDinYear);
			else
				partnerDBYear.setNextID(nextID);
		}
		
		clientMgr = ClientManager.getInstance();
	}
	
	public static ServerPartnerDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerPartnerDB();
		
		return instance;
	}
		
	String getPartners(int year)
	{
		Gson gson = new Gson();
		Type listOfPartners = new TypeToken<ArrayList<A4OPartner>>(){}.getType();
		
		String response = gson.toJson(partnerDB.get(year - BASE_YEAR).getList(), listOfPartners);
		return response;	
	}
	
	String getPartner(int year, String zID)
	{
		int id = Integer.parseInt(zID);
		int index = 0;
		
		List<A4OPartner> orgAL = partnerDB.get(year-BASE_YEAR).getList();
		
		while(index < orgAL.size() && orgAL.get(index).getID() != id)
			index++;
		
		if(index < orgAL.size())
		{
			Gson gson = new Gson();
			String partnerjson = gson.toJson(orgAL.get(index), A4OPartner.class);
			
			return "PARTNER" + partnerjson;
		}
		else
			return "PARTNER_NOT_FOUND";
	}
	
	A4OPartner getPartner(int year, int partID)
	{
		List<A4OPartner> oAL = partnerDB.get(year - BASE_YEAR).getList();
		
		int index = 0;
		while(index < oAL.size() && oAL.get(index).getID() != partID)
			index++;
		
		if(index < oAL.size())
		{	
			return oAL.get(index);
		}
		else
			return null;
	}
	
	String update(int year, String json)
	{
		//Create a organization object for the updated partner
		Gson gson = new Gson();
		A4OPartner reqOrg = gson.fromJson(json, A4OPartner.class);
		
		//Find the position for the current family being replaced
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<A4OPartner> oAL = partnerDBYear.getList();
		int index = 0;
		while(index < oAL.size() && oAL.get(index).getID() != reqOrg.getID())
			index++;
		
		//If partner is located, replace the current partner with the update. Check to 
		//ensure the partner status isn't confirmed with gifts assigned. If so, deny the change. 
		if(index == oAL.size() || (reqOrg.getStatus() != STATUS_CONFIRMED && 
									oAL.get(index).getNumberOfGiftFamsAssigned() > 0)) 
		{
			
			return "UPDATE_FAILED";
		}
		else
		{
			A4OPartner currOrg = oAL.get(index);
			//check if partner address has changed and a region update check is required
			if(currOrg.getStreetnum() != reqOrg.getStreetnum() ||
				!currOrg.getStreetname().equals(reqOrg.getStreetname()) ||
				 !currOrg.getCity().equals(reqOrg.getCity()) ||
				  !currOrg.getZipcode().equals(reqOrg.getZipcode()))
			{
//				System.out.println(String.format("PartnerDB - update: region change, old region is %d", currOrg.getRegion()));
				updateRegion(reqOrg);	
			}
			oAL.set(index, reqOrg);
			partnerDBYear.setChanged(true);
			return "UPDATED_PARTNER" + gson.toJson(reqOrg, A4OPartner.class);
		}
	}
	
	int updateRegion(A4OPartner updatedOrg)
	{
		int reg = 0; //initialize return value to no region found
		
		//address is new or has changed, update the region
		RegionDB regionDB = null;
		try {
			regionDB = RegionDB.getInstance(null);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(regionDB != null)
		{
			reg = RegionDB.searchForRegionMatch(new Address(Integer.toString(updatedOrg.getStreetnum()),
											updatedOrg.getStreetname(), "",  updatedOrg.getCity(),
											 updatedOrg.getZipcode()));
			updatedOrg.setRegion(reg);
		}
		
		return reg;
	}
	
	@Override
	String add(int year, String json)
	{
		//Create a organization object for the updated partner
		Gson gson = new Gson();
		A4OPartner addedPartner = gson.fromJson(json, A4OPartner.class);
	
		//set the new ID for the catalog wish
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		addedPartner.setID(partnerDBYear.getNextID());
		
		//set the region for the new partner
		ClientManager clientMgr = ClientManager.getInstance();
		RegionDB regionDB = null;
		try {
			regionDB = RegionDB.getInstance(clientMgr.getAppIcon());
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(regionDB != null)
			addedPartner.setRegion(RegionDB.searchForRegionMatch(new Address(Integer.toString(addedPartner.getStreetnum()), 
															addedPartner.getStreetname(), "", addedPartner.getCity(),
															addedPartner.getZipcode())));
		else
			addedPartner.setRegion(0);
		
		//add the partner to the proper database
		partnerDBYear.add(addedPartner);
		partnerDBYear.setChanged(true);
		
		return "ADDED_PARTNER" + gson.toJson(addedPartner, A4OPartner.class);
	}
	
	String delete(int year, String json)
	{
		//Create a organization object for the updated partner
		Gson gson = new Gson();
		A4OPartner reqDelPartner = gson.fromJson(json, A4OPartner.class);
	
		//find the partner in the db
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<A4OPartner> oAL = partnerDBYear.getList();
		int index = 0;
		while(index < oAL.size() && oAL.get(index).getID() != reqDelPartner.getID())
			index++;
		
		//partner must be present and have no ornaments assigned to be deleted
		if(index < oAL.size() && oAL.get(index).getStatus() != STATUS_CONFIRMED &&
				oAL.get(index).getNumberOfGiftFamsAssigned() == 0)
		{
			oAL.remove(index);
			partnerDBYear.setChanged(true);
			return "DELETED_PARTNER" + json;
		}
		else
			return "DELETE_PARTNER_FAILED" + json;
	}

	void updateGiftAssignees(int year, int oldPartnerID, int newPartnerID)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		
		//Find the the current partner &  decrement gift count if found
		if(oldPartnerID > 0)
		{
			A4OPartner oldPartner = getPartner(year, oldPartnerID);
			if(oldPartner != null)
			{
				oldPartner.decrementGiftsAssigned();
				partnerDBYear.setChanged(true);
			}
		}
	
		if(newPartnerID > 0)
		{
			//Find the the current partner &  increment gift count if found
			A4OPartner newPartner = getPartner(year, newPartnerID);
			if(newPartner != null)
			{
				newPartner.incrementGiftsAssigned();
				partnerDBYear.setChanged(true);
			}
		}	
	}
	
	void incrementGiftActionCount(int year, ONCChildWish addedWish)
	{	
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<A4OPartner> partnerList = partnerDBYear.getList();
		
		//Find the the current partner being decremented
		int index = 0;
		while(index < partnerList.size() && partnerList.get(index).getID() != addedWish.getChildWishAssigneeID())
			index++;
		
		//increment the gift received count for the partner being replaced
		if(index < partnerList.size())
		{  
			//found the partner, now determine which field to increment
			if(addedWish.getChildWishStatus() == WishStatus.Received)
			{
				ServerGlobalVariableDB gvDB = null;
				try 
				{
					gvDB = ServerGlobalVariableDB.getInstance();
					boolean bReceviedBeforeDeadline = addedWish.getChildWishDateChanged().before(gvDB.getDateGiftsRecivedDealdine(year));
//					partnerList.get(index).incrementOrnReceived(bReceviedBeforeDeadline);
				} 
				catch (FileNotFoundException e) 
				{
					// TODO Auto-generated catch block
				} 
				catch (IOException e) 
				{
					// TODO Auto-generated catch block
				}
				
			}
			else if(addedWish.getChildWishStatus() == WishStatus.Delivered)
				partnerList.get(index).incrementMealsAssigned();
			
			partnerDBYear.setChanged(true);
		}
	}
	
	void updateAssignedCounts(int year, int partnerID, PartnerAction pa)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		List<A4OPartner> partnerList = partnerDBYear.getList();
		int index=0;
		while(index < partnerList.size() && partnerList.get(index).getID() != partnerID)
			index ++;
		
		//if partner was found, decrement the count, marked the db as changed and notify
		//all in year clients. If not found, ignore the request
		if(index < partnerList.size())
		{
			A4OPartner updatedPartner = partnerList.get(index);
			
			if(pa == PartnerAction.INCREMENT_GIFT_COUNT)
				updatedPartner.incrementGiftsAssigned();
			else if(pa == PartnerAction.DECREMENT_GIFT_COUNT)
				updatedPartner.decrementGiftsAssigned();
			else if(pa == PartnerAction.INCREMENT_MEAL_COUNT)
				updatedPartner.incrementMealsAssigned();
			else if(pa == PartnerAction.DECREMENT_MEAL_COUNT)
				updatedPartner.decrementMealsAssigned();
			
			partnerDBYear.setChanged(true);
			
			//notify in year clients of change
			Gson gson = new Gson();
			String response = "UPDATED_PARTNER" + gson.toJson(updatedPartner, A4OPartner.class);
			clientMgr.notifyAllInYearClients(year, response);
		}
	}

	@Override
	void addObject(int year, String[] nextLine)
	{
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		partnerDBYear.add(new A4OPartner(nextLine));
	}

	@Override
	void createNewYear(int newYear)
	{
		//create a new partner data base year for the year provided in the newYear parameter
		//Then copy the prior years partners to the newly created partner db year list.
		//Reset each partners status to NO_ACTION_YET
		//Mark the newly created WishCatlogDBYear for saving during the next save event
				
		//get a reference to the prior years wish catalog
		List<A4OPartner> lyPartnerList = partnerDB.get(partnerDB.size()-1).getList();
				
		//create the new PartnerDBYear
		PartnerDBYear partnerDBYear = new PartnerDBYear(newYear);
		partnerDB.add(partnerDBYear);
				
		//add last years partners to the new season partner list
		for(A4OPartner lyPartner : lyPartnerList)
		{
			A4OPartner newPartner = new A4OPartner(lyPartner);	//makes a copy of last year
			
			newPartner.setStatus(0);	//reset status to NO_ACTION_YET
			
			//set prior year performance statistics
			newPartner.setPriorYearGiftFamsRequested(lyPartner.getNumberOfGiftFamsRequested());
			newPartner.setPriorYearGiftFamsAssigned(lyPartner.getNumberOfGiftFamsAssigned());
			newPartner.setPriorYearMealFamsRequested(lyPartner.getNumberOfMealFamsRequested());
			newPartner.setPriorYearMealFamsAssigned(lyPartner.getNumberOfMealFamsAssigned());
			
			//reset the new season performance statistics
			newPartner.setNumberOfGiftFamsRequested(0);	//reset requested to 0
			newPartner.setNumberOfGiftFamsAssigned(0);	//reset assigned to 0
			newPartner.setNumberOfMealFamsRequested(0);	//reset delivered to 0
			newPartner.setNumberOfMealFamsAssigned(0);	//reset received before to 0
			newPartner.setPriorYearGiftFamsRequested(lyPartner.getNumberOfGiftFamsRequested());
			
			partnerDBYear.add(newPartner);
		}
		
		//set the nextID for the newly created PartnerDBYear
		partnerDBYear.setNextID(newYear*1000);	//all partner id's start with current year
		
		//Mark the newly created DBYear for saving during the next save event
		partnerDBYear.setChanged(true);
	}
	
	/********************************************************************************************
	 * using prior year child wish data, set the prior year ornaments requested, assigned and received
	 * class variables for each partner
	 * @param newYear
	 * @param partnerDBYear
	 ******************************************************************************************/
/*	
	void determinePriorYearPerformance(int year)
	{
		//get the child wish data base reference
		ServerChildWishDB serverChildWishDB = null;
		try {
			serverChildWishDB = ServerChildWishDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		//iterate thru the list of prior year performance objects updating counts for each partner 
		//accordingly. The list contains one object for each wish from the prior year. Each object 
		//contains an assigned ID, a deliveredID, and a receivedID indicating which partner was responsible
		//for fulfilling the wish and who ONC actually received the fulfilled wish (gift) from
		List<PriorYearPartnerPerformance> pyPartnerPerformanceList = serverChildWishDB.getPriorYearPartnerPerformanceList(year+1);
		List<A4OPartner> pyPerfPartnerList = new ArrayList<A4OPartner>();
		
		//populate the current partner list
		for(A4OPartner p: partnerDB.get(year - BASE_YEAR).getList())
		{
			//make a copy of the partner and set their assigned, delivered and received counts to zero
			A4OPartner pyPerfPartner = new A4OPartner(p);
			pyPerfPartner.setNumberOfOrnamentsAssigned(0);
			pyPerfPartner.setNumberOfOrnamentsDelivered(0);
			pyPerfPartner.setNumberOfOrnamentsReceivedBeforeDeadline(0);
			pyPerfPartner.setNumberOfOrnamentsReceivedAfterDeadline(0);
			pyPerfPartnerList.add(pyPerfPartner);
		}
		
		for(PriorYearPartnerPerformance pyPerf: pyPartnerPerformanceList)
		{
			//find the partner the wish was assigned to and increment their prior year assigned count
			if(pyPerf.getPYPartnerWishAssigneeID() > -1)
			{
				A4OPartner wishAssigneePartner = (A4OPartner) find(pyPerfPartnerList, pyPerf.getPYPartnerWishAssigneeID());
				if(wishAssigneePartner != null)
					wishAssigneePartner.incrementOrnAssigned();
			}
			//find the partner the wish was delivered to and increment their prior year assigned count
			if(pyPerf.getPYPartnerWishDeliveredID() > -1)
			{
				A4OPartner wishDeliveredPartner = (A4OPartner) find(pyPerfPartnerList, pyPerf.getPYPartnerWishDeliveredID());
				if(wishDeliveredPartner != null)
					wishDeliveredPartner.incrementOrnDelivered();
			}
			
			//find the partner the wish was received from before deadline and increment their prior year received count
			if(pyPerf.getPYPartnerWishReceivedBeforeDeadlineID() > -1)
			{
				A4OPartner wishReceivedBeforePartner = (A4OPartner) find(pyPerfPartnerList, pyPerf.getPYPartnerWishReceivedBeforeDeadlineID());;
				if(wishReceivedBeforePartner != null)
					wishReceivedBeforePartner.incrementOrnReceived(true);	
			}
			//find the partner the wish was received from after deadline and increment their prior year received count
			if(pyPerf.getPYPartnerWishReceivedAfterDeadlineID() > -1)
			{
				A4OPartner wishReceivedAfterPartner = (A4OPartner) find(pyPerfPartnerList, pyPerf.getPYPartnerWishReceivedAfterDeadlineID());;
				if(wishReceivedAfterPartner != null)
					wishReceivedAfterPartner.incrementOrnReceived(false);	
			}
		}
		
		savePYPartnerPerformace(pyPerfPartnerList);
		
//		for(ONCPartner confPart : confPartList)
//			System.out.println(String.format("%s requested %d, assigned: %d, delivered: %d, received before: %d received after: %d",
//					confPart.getName(), confPart.getNumberOfOrnamentsRequested(), confPart.getNumberOfOrnamentsAssigned(), 
//					confPart.getNumberOfOrnamentsDelivered(), confPart.getNumberOfOrnamentsReceivedBeforeDeadline(),
//					confPart.getNumberOfOrnamentsReceivedAfterDeadline()));	
	}
*/	
	void savePYPartnerPerformace(List<A4OPartner> pyPerformancePartnerList)
	{
		String[] header = {"Part ID", "Status", "Type", "Gift Collection","Name", "Orn Delivered",
				"Street #", "Street", "Unit", "City", "Zip", "Region", "Phone",
	 			"Gifts Requested", "Gifts Assigned", "Meals Requested", "Meals Assigned",
	 			"Gifts Received After", "Other", "Deliver To", "Special Notes",
	 			"Contact", "Contact Email", "Contact Phone",
	 			"Contact2", "Contact2 Email", "Contact2 Phone",
	 			"Time Stamp", "Changed By", "Stoplight Pos", "Stoplight Mssg", "Stoplight C/B",
	 			"PY Gifts Requested",	"PY Gifts Assigned", "PY Meals Requested",
	 			"PY Meals Assigned"};
		
		String path = String.format("A4O/%s/PartnerPerformance.csv", System.getProperty("user.dir"));
		exportDBToCSV(pyPerformancePartnerList, header, path);
	}
	
	private class PartnerDBYear extends ServerDBYear
	{
		private List<A4OPartner> pList;
	    	
	    PartnerDBYear(int year)
	    {
	    	super();
	    	pList = new ArrayList<A4OPartner>();
	    }
	    	
	    	//getters
	    	List<A4OPartner> getList() { return pList; }
	    	
	    	void add(A4OPartner addedOrg) { pList.add(addedOrg); }
	}

	@Override
	void save(int year)
	{
		String[] header = {"Org ID", "Status", "Type", "Gift Collection", "Name",
				"Street #", "Street", "Unit", "City", "Zip", "Region", "Phone",
	 			"Gifts Requested", "Gifts Assigned", "Meals Requested", "Meals Assigned",
	 			"Other", "Deliver To", "Special Notes",
	 			"Contact", "Contact Email", "Contact Phone",
	 			"Contact2", "Contact2 Email", "Contact2 Phone",
	 			"Time Stamp", "Changed By", "Stoplight Pos", "Stoplight Mssg", "Stoplight C/B",
	 			"PY Gifts Requested",	"PY Gifts Assigned", "PY Meals Reqeusted",
	 			"PY Meals Assigned"};
		
		PartnerDBYear partnerDBYear = partnerDB.get(year - BASE_YEAR);
		if(partnerDBYear.isUnsaved())
		{
//			System.out.println(String.format("PartnerDB save() - Saving Partner DB"));
			String path = String.format("%s/A4O/%dDB/OrgDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(partnerDBYear.getList(),  header, path);
			partnerDBYear.setChanged(false);
		}
	}
}
