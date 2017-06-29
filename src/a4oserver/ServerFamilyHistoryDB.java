package a4oserver;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import actforothers.FamilyGiftStatus;
import actforothers.HistoryRequest;
import actforothers.A4OFamilyHistory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerFamilyHistoryDB extends ServerSeasonalDB
{
	private static final int FAMILY_HISTORY_DB_HEADER_LENGTH = 8;

	private static List<FamilyHistoryDBYear> famHistDB;
	private static ServerFamilyHistoryDB instance = null;
	
	private ServerFamilyHistoryDB() throws FileNotFoundException, IOException
	{
		//create the family history data bases for TOTAL_YEARS number of years
		famHistDB = new ArrayList<FamilyHistoryDBYear>();

		//populate the data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the family history list for each year
			FamilyHistoryDBYear delDBYear = new FamilyHistoryDBYear(year);
							
			//add the list of children for the year to the db
			famHistDB.add(delDBYear);
							
			//import the children from persistent store
			importDB(year, String.format("%s/A4O/%dDB/FamilyHistoryDB.csv",
					System.getProperty("user.dir"),
						year), "Delivery DB", FAMILY_HISTORY_DB_HEADER_LENGTH);
							
			//set the next id
			delDBYear.setNextID(getNextID(delDBYear.getList()));
		}
	}
	
	public static ServerFamilyHistoryDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerFamilyHistoryDB();
		
		return instance;
	}
	
	//Search the database for the family. Return a json if the family is found. 
	String getFamilyHistory(int year)
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<ArrayList<A4OFamilyHistory>>(){}.getType();
			
		String response = gson.toJson(famHistDB.get(year - BASE_YEAR).getList(), listtype);
		return response;	
	}
	
	A4OFamilyHistory getLastFamilyHistory(int year, int famID)
	{
		A4OFamilyHistory latestFamilyHistoryObj = null;
		
		FamilyHistoryDBYear histDBYear = famHistDB.get(year - BASE_YEAR);
		for(A4OFamilyHistory fhObj : histDBYear.getList())
		{
			if(fhObj.getFamID() == famID)
			{
				if(latestFamilyHistoryObj == null ||
				    fhObj.getTimestamp().after(latestFamilyHistoryObj.getTimestamp()))
				{
					latestFamilyHistoryObj = fhObj;
				}
			}
		}
		
		return latestFamilyHistoryObj;
	}

	@Override
	String add(int year, String json)
	{
		//Create a history object for the new delivery
		Gson gson = new Gson();
		A4OFamilyHistory addedHistoryObj = gson.fromJson(json, A4OFamilyHistory.class);
		
		//find the replaced history object. Search from the last item in the history list,
		//since it will be the most recent.
		A4OFamilyHistory oldFH = null;
		FamilyHistoryDBYear histDBYear = famHistDB.get(year - BASE_YEAR);
		List<A4OFamilyHistory> histList = histDBYear.getList();
		int index = histList.size() -1;
		while(index > -1 && histList.get(index).getFamID() != addedHistoryObj.getFamID())
			index--;
		
		if(index > -1)	//old history exists, check for status update
			checkForAutomaticGiftStatusChange(year, (oldFH = histList.get(index)), addedHistoryObj);
		
		//add the new object to the data base
		addedHistoryObj.setID(histDBYear.getNextID());
		addedHistoryObj.setDateChanged(Calendar.getInstance(TimeZone.getTimeZone("UTC")));
		
		//if family gift status is greater than FamilyGiftStatus.Assigned, retain the assignee
		if(oldFH != null && addedHistoryObj.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) > 0)
			addedHistoryObj.setPartnerID(oldFH.getPartnerID());
		
		//add the item to the proper year's list and mark the list as changed
		histDBYear.add(addedHistoryObj);
		histDBYear.setChanged(true);
		
		//notify the corresponding family the history object has changed and
		//check to see if new delivery assigned or removed a delivery from a driver
		ServerFamilyDB serverFamilyDB = null;
		try 
		{
			serverFamilyDB = ServerFamilyDB.getInstance();
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
		
		//get prior delivery for this family
//		ONCFamily fam = serverFamilyDB.getFamily(year, addedHistoryObj.getFamID());
//		ONCFamilyHistory priorHistoryObj = getHistory(year, fam.getDeliveryID());
//		
//		//if there was a prior history and the gift status was associated with a delivery, update the 
//		//status and counts
//		if(priorHistoryObj != null)
//		{
//			//If prior status == ASSIGNED && new status < ASSIGNED, decrement the prior delivery driver
//			//else if the prior status == ASSIGNED and new status == ASSIGNED and the driver number changed,
//			//Decrement the prior driver deliveries and increment the new driver deliveries. Else, if the 
//			//prior status < ASSIGNED and the new status == ASSIGNED, increment the new driver deliveries
//			if(priorHistoryObj.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) < 0 && 
//					addedHistoryObj.getGiftStatus() == FamilyGiftStatus.Assigned)
//			{
//				driverDB.updateDriverDeliveryCounts(year, null, addedHistoryObj.getPartnerID());
//			}
//			else if(priorHistoryObj.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) >= 0 && 
//					 addedHistoryObj.getGiftStatus() == FamilyGiftStatus.Assigned && 
//					  priorHistoryObj.getPartnerID() != addedHistoryObj.getPartnerID())
//			{
//				driverDB.updateDriverDeliveryCounts(year, priorHistoryObj.getPartnerID(), addedHistoryObj.getPartnerID());
//			}
//			else if(priorHistoryObj.getGiftStatus() == FamilyGiftStatus.Assigned && 
//					 addedHistoryObj.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) < 0)
//			{
//				driverDB.updateDriverDeliveryCounts(year, priorHistoryObj.getPartnerID(), null);
//			}
//		}
		//Update the family object with new delivery
		if(serverFamilyDB != null)
			serverFamilyDB.updateFamilyHistory(year, addedHistoryObj);
					
		return "ADDED_DELIVERY" + gson.toJson(addedHistoryObj, A4OFamilyHistory.class);
	}
	
	//used when adding processing automated call results
	String add(int year, A4OFamilyHistory addedHisoryObj)
	{
		//add the new object to the data base
		FamilyHistoryDBYear histDBYear = famHistDB.get(year - BASE_YEAR);
		addedHisoryObj.setID(histDBYear.getNextID());
		histDBYear.add(addedHisoryObj);
		histDBYear.setChanged(true);
		
		
		//notify the corresponding family that history object has changed and
		//check to see if the object was a new delivery assigned or removed a delivery from a driver
		ServerFamilyDB serverFamilyDB = null;
//		ServerVolunteerDB driverDB = null;
		try {
			serverFamilyDB = ServerFamilyDB.getInstance();
//			driverDB = ServerVolunteerDB.getInstance();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//get prior delivery for this family
//		A4OFamily fam = serverFamilyDB.getFamily(year, addedHisoryObj.getFamID());
//		A4OFamilyHistory priorDelivery = getHistory(year, fam.getHistoryID());
//		
//		//if there was a prior delivery, then update the status and counts
//		if(priorDelivery != null)
//		{
//			//If prior status == ASSIGNED && new status < ASSIGNED, decrement the prior delivery driver
//			//else if the prior status == ASSIGNED and new status == ASSIGNED and the driver number changed,
//			//Decrement the prior driver deliveries and increment the new driver deliveries. Else, if the 
//			//prior status < ASSIGNED and the new status == ASSIGNED, increment the new driver deliveries
//			if(priorDelivery.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) < 0 && 
//					addedHisoryObj.getGiftStatus() == FamilyGiftStatus.Assigned)
//			{
//				driverDB.updateDriverDeliveryCounts(year, null, addedHisoryObj.getPartnerID());
//			}
//			else if(priorDelivery.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) >= 0 && 
//					 addedHisoryObj.getGiftStatus() == FamilyGiftStatus.Assigned && 
//					  priorDelivery.getPartnerID() != addedHisoryObj.getPartnerID())
//			{
//				driverDB.updateDriverDeliveryCounts(year, priorDelivery.getPartnerID(), addedHisoryObj.getPartnerID());
//			}
//			else if(priorDelivery.getGiftStatus() == FamilyGiftStatus.Assigned && 
//					 addedHisoryObj.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) < 0)
//			{
//				driverDB.updateDriverDeliveryCounts(year, priorDelivery.getPartnerID(), null);
//			}
//		}
		
		//Update the family object with new delivery
		if(serverFamilyDB != null)
			serverFamilyDB.updateFamilyHistory(year, addedHisoryObj);

		Gson gson = new Gson();
		return "ADDED_DELIVERY" + gson.toJson(addedHisoryObj, A4OFamilyHistory.class);
	}
	
	/***
	 * Checks for partner changes and automatically changes the gift status. If current gift status
	 * is Requested and the partner is changing from unassigned to assigned, change the status as an 
	 * example
	 */
	void checkForAutomaticGiftStatusChange(int year, A4OFamilyHistory oldFH, A4OFamilyHistory newFH)
	{
		ServerPartnerDB serverPartnerDB = null;
		try 
		{
			serverPartnerDB = ServerPartnerDB.getInstance();
		} 
		catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(oldFH.getPartnerID() == -1 && newFH.getPartnerID() > -1)
		{
			newFH.setFamilyGiftStatus(FamilyGiftStatus.Assigned);
			serverPartnerDB.updateAssignedCounts(year, newFH.getPartnerID(), PartnerAction.INCREMENT_GIFT_COUNT);
		}
		else if(oldFH.getPartnerID() > -1 && newFH.getPartnerID() == -1)
		{
			newFH.setFamilyGiftStatus(FamilyGiftStatus.Requested);
			serverPartnerDB.updateAssignedCounts(year, oldFH.getPartnerID(), PartnerAction.DECREMENT_GIFT_COUNT);
		}
		else if(oldFH.getPartnerID() != newFH.getPartnerID())
		{
			newFH.setFamilyGiftStatus(FamilyGiftStatus.Assigned);
			serverPartnerDB.updateAssignedCounts(year, newFH.getPartnerID(), PartnerAction.INCREMENT_GIFT_COUNT);
			serverPartnerDB.updateAssignedCounts(year, oldFH.getPartnerID(), PartnerAction.DECREMENT_GIFT_COUNT);
		}
	}
	
	A4OFamilyHistory addFamilyHistoryObject(int year, A4OFamilyHistory addedFamHistObj)
	{
		ClientManager clientMgr = ClientManager.getInstance();
		//add the new object to the data base
		FamilyHistoryDBYear histDBYear = famHistDB.get(year - BASE_YEAR);
		
		addedFamHistObj.setID(histDBYear.getNextID());
		addedFamHistObj.setDateChanged(Calendar.getInstance(TimeZone.getTimeZone("UTC")));
		
		if(addedFamHistObj.getGiftStatus().compareTo(FamilyGiftStatus.Assigned) > 0)
		{
			//find the last object, there has to be one for the status > Assigned
			A4OFamilyHistory latestFHObj = getLastFamilyHistory(year, addedFamHistObj.getFamID());
			if(latestFHObj != null)
				addedFamHistObj.setPartnerID(latestFHObj.getPartnerID());
		}
		
		histDBYear.add(addedFamHistObj);
		histDBYear.setChanged(true);
		
		Gson gson = new Gson();
		clientMgr.notifyAllInYearClients(year, "ADDED_DELIVERY"+ gson.toJson(addedFamHistObj, A4OFamilyHistory.class));
		
		return addedFamHistObj;
	}
	
	A4OFamilyHistory getHistory(int year, int histID)
	{
		List<A4OFamilyHistory> histAL = famHistDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < histAL.size() && histAL.get(index).getID() != histID)
			index++;
		
		if(index < histAL.size())
			return histAL.get(index);
		else
			return null;
	}
	
	String update(int year, String json)
	{
		//Create an object for the updated family history
		Gson gson = new Gson();
		A4OFamilyHistory updatedHistory = gson.fromJson(json, A4OFamilyHistory.class);
		
		//Find the position for the current object being replaced
		FamilyHistoryDBYear histDBYear = famHistDB.get(year - BASE_YEAR);
		List<A4OFamilyHistory> histAL = histDBYear.getList();
		int index = 0;
		while(index < histAL.size() && histAL.get(index).getID() != updatedHistory.getID())
			index++;
		
		//Replace the current object with the update
		if(index < histAL.size())
		{
			histAL.set(index, updatedHistory);
			histDBYear.setChanged(true);
			return "UPDATED_DELIVERY" + gson.toJson(updatedHistory, A4OFamilyHistory.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	//Search the database for the family history. Return a json of List<ONCDelivery>
	String getFamilyHistory(int year, String reqjson)
	{
		//Convert list to json and return it
		Gson gson = new Gson();
		HistoryRequest histReq = gson.fromJson(reqjson, HistoryRequest.class);
			
		List<A4OFamilyHistory> famHistory = new ArrayList<A4OFamilyHistory>();
		List<A4OFamilyHistory> famHistAL = famHistDB.get(year - BASE_YEAR).getList();
			
		//Search for deliveries that match the delivery Family ID
		for(A4OFamilyHistory fh:famHistAL)
			if(fh.getFamID() == histReq.getID())
				famHistory.add(fh);
			
		//Convert list to json and return it
		Type listtype = new TypeToken<ArrayList<A4OFamilyHistory>>(){}.getType();
			
		String response = gson.toJson(famHistory, listtype);
		return response;
	}
	
	
	@Override
	void addObject(int year, String[] nextLine)
	{
		FamilyHistoryDBYear histDBYear = famHistDB.get(year - BASE_YEAR);
		histDBYear.add(new A4OFamilyHistory(nextLine));	
	}
	
	
	@Override
	void createNewYear(int newYear)
	{
		//create a new family history data base year for the year provided in the newYear parameter
		//The history db year list is initially empty prior to the import of families, so all we
		//do here is create a new FamilyHistoryDBYear for the newYear and save it.
		FamilyHistoryDBYear famHistDBYear = new FamilyHistoryDBYear(newYear);
		famHistDB.add(famHistDBYear);
		famHistDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
	}
	
	 private class FamilyHistoryDBYear extends ServerDBYear
	 {
		private List<A4OFamilyHistory> histList;
	    	
	    FamilyHistoryDBYear(int year)
	    {
	    	super();
	    	histList = new ArrayList<A4OFamilyHistory>();
	    }
	    
	    //getters
	    List<A4OFamilyHistory> getList() { return histList; }
	    
	    void add(A4OFamilyHistory addedHistObj) { histList.add(addedHistObj); }
	 }

	@Override
	void save(int year)
	{
		String[] header = {"History ID", "Family ID", "Family Status", "Gift Status", "Partner ID", 
	 			"Notes", "Changed By", "Time Stamp"};
		
		FamilyHistoryDBYear histDBYear = famHistDB.get(year - BASE_YEAR);
		if(histDBYear.isUnsaved())
		{
//			System.out.println(String.format("DeliveryDB save() - Saving Delivery DB"));
			String path = String.format("%s/A4O/%dDB/FamilyHistoryDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(histDBYear.getList(),  header, path);
			histDBYear.setChanged(false);
		}
	}
/*	
	 void convertDeliveryDBForStatusChanges(int year)
	    {
	    	String[] header, nextLine;
	    	List<FamilyHistory> histList = new ArrayList<FamilyHistory>();
	    	
	    	//open the current year file
	    	String path = String.format("%s/%dDB/DeliveryDB.csv", System.getProperty("user.dir"), year);
	    	CSVReader reader;
			try 
			{
				reader = new CSVReader(new FileReader(path));
				if((header = reader.readNext()) != null)	//Does file have records? 
		    	{
		    		//Read the User File
		    		if(header.length == FAMILY_HISTORY_DB_HEADER_LENGTH)	//Does the record have the right # of fields? 
		    		{
		    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
		    			{
		    				NewFamStatus nfs = getNewFamStatus(nextLine[2]);				
		    				histList.add(new FamilyHistory(Integer.parseInt(nextLine[0]), Integer.parseInt(nextLine[1]),
		    						nfs.getNewFamStatus(), nfs.getNewGiftStatus(), nextLine[3], nextLine[4], 
		    						nextLine[5], Long.parseLong(nextLine[6])));
		    			}
		    		}
		    		else
		    		{
		    			String error = String.format("%s file corrupted, header length = %d", path, header.length);
		    	       	JOptionPane.showMessageDialog(null, error,  path + "Corrupted", JOptionPane.ERROR_MESSAGE);
		    		}		   			
		    	}
		    	else
		    	{
		    		String error = String.format("%s file is empty", path);
		    		JOptionPane.showMessageDialog(null, error,  path + " Empty", JOptionPane.ERROR_MESSAGE);
		    	}
		    	
		    	reader.close();
		    	
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
			
			//now that we should have an output list of converted String[] for each family, write it
			
			String[] outHeader = {"History ID", "Family ID", "Fam Status", "Gift Status", "Del By", 
		 			"Notes", "Changed By", "Time Stamp"};
			
			System.out.println(String.format("FamilyHistoryDB saveDB - Saving %d New FamilyHistory DB", year));
			String outPath = String.format("%s/%dDB/FamilyHistoryDB.csv", System.getProperty("user.dir"), year);
			
			 try 
			    {
			    	CSVWriter writer = new CSVWriter(new FileWriter(outPath));
			    	writer.writeNext(outHeader);
			    	 
			    	for(FamilyHistory fh : histList)
			    		writer.writeNext(fh.getExportRow());
			    	
			    	writer.close();
			    	       	    
			    } 
			    catch (IOException x)
			    {
			    	System.err.format("IO Exception: %s%n", x);
			    }	
	    }	
	 
    NewFamStatus getNewFamStatus(String odels)
    {
    	int oldGiftStatus = Integer.parseInt(odels);
    	
    	if(oldGiftStatus == 0)
    		return new NewFamStatus(1,1);
    	else if(oldGiftStatus == 1)
    		return new NewFamStatus(2,2);
    	else if(oldGiftStatus == 2)
    		return new NewFamStatus(3,2);
    	else if(oldGiftStatus == 3)
    		return new NewFamStatus(3,6);
    	else if(oldGiftStatus == 4)
    		return new NewFamStatus(3,8);
    	else if(oldGiftStatus == 5)
    		return new NewFamStatus(3,9);
    	else if(oldGiftStatus == 6)
    		return new NewFamStatus(3,7);
    	else if(oldGiftStatus == 7)
    		return new NewFamStatus(3,10);
    	else
    		return null;
    }
    
    private class NewFamStatus
    {
    	private int famStatus;
    	private int giftStatus;
    	
    	NewFamStatus(int famStatus, int giftStatus)
    	{
    		this.famStatus = famStatus;
    		this.giftStatus = giftStatus;
    	}
    	
    	int getNewFamStatus() { return famStatus; }
    	int getNewGiftStatus() { return giftStatus; }
    }
    
    public class FamilyHistory
    {
    	//This class implements the data structure for Family History objects. When an ONC Family objects 
    	//FamilyStatus or FamilyGift Status changes, this object is created and stored to archive the change
    	
    	int histID;
    	int famID;
    	int famStatus;
    	int giftStatus;
    	String dDelBy;
    	String dNotes;
    	String dChangedBy;
    	long dDateChanged;

    	//Constructor used after separating ONC Deliveries from ONC Families
    	FamilyHistory(int id, int famid, int fStat, int dStat, String dBy, String notes, String cb, long dateChanged)
    	{
    		histID = id;
    		famID = famid;
    		famStatus = fStat;	
    		giftStatus = dStat;			
    		dDelBy = dBy;
    		dNotes = notes;		
    		dChangedBy = cb;
    		dDateChanged = dateChanged;
    	}
    	
    	public String[] getExportRow()
    	{
    		String[] exportRow = {Integer.toString(histID), Integer.toString(famID), 
    							  Integer.toString(famStatus), Integer.toString(giftStatus),
    							  dDelBy, dNotes, dChangedBy, Long.toString(dDateChanged)};
    		
    		return exportRow;
    		
    	}
    }
*/    
}
