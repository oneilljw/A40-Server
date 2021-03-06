package a4oserver;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.swing.JOptionPane;

import actforothers.Address;
import actforothers.AdultGender;
import actforothers.BritepathFamily;
import actforothers.FamilyGiftStatus;
import actforothers.FamilyStatus;
import actforothers.ONCAdult;
import actforothers.ONCChild;
import actforothers.A4OFamilyHistory;
import actforothers.A4OFamily;
import actforothers.ONCMeal;
import actforothers.ONCUser;
import actforothers.ONCWebsiteFamily;
import actforothers.ONCWebsiteFamilyExtended;
import actforothers.ServerGVs;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ServerFamilyDB extends ServerSeasonalDB
{
	private static final int FAMILYDB_HEADER_LENGTH = 42;
	private static final int FAMILY_STOPLIGHT_RED = 2;
	private static final String ODB_FAMILY_MEMBER_COLUMN_SEPARATOR = " - ";
	private static final int DEFAULT_PARTNER_ID = 1700000;
	
	private static List<FamilyDBYear> familyDB;
	private static ServerFamilyDB instance = null;
	private static int highestRefNum;
	private static Map<Integer, Integer> highestA4ONumMap;
//	private static List<int[]> oncNumRanges;
	
	private static ServerFamilyHistoryDB familyHistoryDB;
	private static ServerUserDB userDB;
	private static ServerChildDB childDB;
	private static ServerAdultDB adultDB;
	
	private static ClientManager clientMgr;
	
	//THIS IS A TEMPORARY HACK FOR 2016 - NEED TO HAVE ONC NUM RANGES GENERATED AUTOMATICALLY
	int[] oncnumRegionRanges = {1299,100,125,140,240,490,494,525,585,700,733,755,760,763,766,
							773,900,920,1065,1066,1071,1080,1115,1145,1160,1260,1261};
	
	private ServerFamilyDB() throws FileNotFoundException, IOException
	{
		//create the family data bases for the years of ONC Server operation starting with 2012
//		oncNumRanges = new ArrayList<int[]>();
		
		familyDB = new ArrayList<FamilyDBYear>();
		
		//initialize the highest A4O number map
		highestA4ONumMap = new HashMap<Integer, Integer>();
		
		//populate the family data base for the last TOTAL_YEARS from persistent store
		for(int year = BASE_YEAR; year < BASE_YEAR + DBManager.getNumberOfYears(); year++)
		{
			//create the family list for year
			FamilyDBYear fDBYear = new FamilyDBYear(year);
			
			//add the family list for the year to the db
			familyDB.add(fDBYear);
			
			//import the families from persistent store
			importDB(year, String.format("%s/A4O/%dDB/NewFamilyDB.csv",
					System.getProperty("user.dir"),
						year), "FamilyDB", FAMILYDB_HEADER_LENGTH);
			
			//set the next id
			fDBYear.setNextID(getNextID(fDBYear.getList()));
			
			//set the highest number in the Map
			highestA4ONumMap.put(year, initializeHighestA4ONumber(fDBYear));
		}
		
		//set the reference number
		highestRefNum = initializeHighestReferenceNumber();
		
		//set references to associated data bases
		familyHistoryDB = ServerFamilyHistoryDB.getInstance();
		childDB = ServerChildDB.getInstance();
		adultDB = ServerAdultDB.getInstance();
		userDB = ServerUserDB.getInstance();

		clientMgr = ClientManager.getInstance();
	}
	
	public static ServerFamilyDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerFamilyDB();

		return instance;
	}
		
	String getFamilies(int year)
	{
		Gson gson = new Gson();
		Type listOfFamilies = new TypeToken<ArrayList<A4OFamily>>(){}.getType();
		
		String response = gson.toJson(familyDB.get(year-BASE_YEAR).getList(), listOfFamilies);
		return response;	
	}
	
	static HtmlResponse getFamiliesJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfWebsiteFamilies = new TypeToken<ArrayList<ONCWebsiteFamily>>(){}.getType();
		
		List<A4OFamily> searchList = familyDB.get(year-BASE_YEAR).getList();
		ArrayList<ONCWebsiteFamily> responseList = new ArrayList<ONCWebsiteFamily>();
		
		for(int i=0; i<searchList.size(); i++)
		{
			ONCWebsiteFamily webFam = new ONCWebsiteFamily(searchList.get(i));
			responseList.add(webFam);
		}
		
		//sort the list by HoH last name
		Collections.sort(responseList, new ONCWebsiteFamilyLNComparator());
		
		String response = gson.toJson(responseList, listOfWebsiteFamilies);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	static HtmlResponse getFamiliesJSONP(int year, int agentID, int groupID, String callbackFunction)
	{	
		Gson gson = new Gson();
		Type listOfWebsiteFamilies = new TypeToken<ArrayList<ONCWebsiteFamily>>(){}.getType();
		
		List<A4OFamily> searchList = familyDB.get(year-BASE_YEAR).getList();
		ArrayList<ONCWebsiteFamily> responseList = new ArrayList<ONCWebsiteFamily>();
		
		if(agentID > -1)
		{
			//add only the families referred by that agent
			for(A4OFamily f : searchList)
				if(f.getAgentID() == agentID)
					responseList.add(new ONCWebsiteFamily(f));
		}
		else if(agentID == -1 && groupID == -1)
		{
			//add all families referred in that year
			for(A4OFamily f : searchList)
				responseList.add(new ONCWebsiteFamily(f));
		}
		else if(groupID > -1)
		{
			//add only the families referred by each agent in the group that referred that year
			for(Integer userID : userDB.getUserIDsInGroup(groupID))
				if(didAgentReferInYear(year, userID))
					for(A4OFamily f : searchList)
						if(f.getAgentID() == userID)
							responseList.add(new ONCWebsiteFamily(f));	
		}
		
		//sort the list by HoH last name
		Collections.sort(responseList, new ONCWebsiteFamilyLNComparator());
		
		String response = gson.toJson(responseList, listOfWebsiteFamilies);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	static HtmlResponse getFamilyReferencesJSONP(int year, String callbackFunction)
	{		
		Gson gson = new Gson();
		Type listOfFamilyReferences = new TypeToken<ArrayList<FamilyReference>>(){}.getType();
		
		List<A4OFamily> searchList = familyDB.get(year-BASE_YEAR).getList();
		ArrayList<FamilyReference> responseList = new ArrayList<FamilyReference>();
		
		//sort the search list by ONC Number
		Collections.sort(searchList, new ONCFamilyONCNumComparator());
		
		for(int i=0; i<searchList.size(); i++)
			responseList.add(new FamilyReference(searchList.get(i).getReferenceNum()));
		
		String response = gson.toJson(responseList, listOfFamilyReferences);

		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	static HtmlResponse searchForFamilyReferencesJSONP(int year, String s, String callbackFunction)
	{
    	
		Gson gson = new Gson();
		Type listOfFamilyReferences = new TypeToken<ArrayList<FamilyReference>>(){}.getType();
		
    	List<A4OFamily> oncFamAL = familyDB.get(year-BASE_YEAR).getList();
    	List<FamilyReference> resultList = new ArrayList<FamilyReference>();
    	
		//Determine the type of search based on characteristics of search string
		if(s.matches("-?\\d+(\\.\\d+)?") && s.length() < 5)
		{
			for(A4OFamily f: oncFamAL)
	    		if(s.equals(f.getONCNum()))
	    			resultList.add(new FamilyReference(f.getReferenceNum()));	
		}
		else if((s.matches("-?\\d+(\\.\\d+)?")) && s.length() < 7)
		{
			for(A4OFamily f: oncFamAL)
	    		if(s.equals(f.getReferenceNum()))
	    			resultList.add(new FamilyReference(f.getReferenceNum())); 
		}
		else if((s.startsWith("C") && s.substring(1).matches("-?\\d+(\\.\\d+)?")) && s.length() < 7)
		{
			for(A4OFamily f: oncFamAL)
	    		if(s.equals(f.getReferenceNum()))
	    			resultList.add(new FamilyReference(f.getReferenceNum())); 
		}
		else if((s.startsWith("W") && s.substring(1).matches("-?\\d+(\\.\\d+)?")) && s.length() < 6)
		{
			for(A4OFamily f: oncFamAL)
	    		if(s.equals(f.getReferenceNum()))
	    			resultList.add(new FamilyReference(f.getReferenceNum())); 
		}
		else if(s.matches("-?\\d+(\\.\\d+)?") && s.length() < 13)
		{
			for(A4OFamily f:oncFamAL)
	    	{
	    		//Ensure just 10 digits, no dashes in numbers
	    		String hp = f.getHomePhone().replaceAll("-", "");
	    		String op = f.getOtherPhon().replaceAll("-", "");
	    		String target = s.replaceAll("-", "");
	    		
	    		if(hp.contains(target) || op.contains(target))
	    			resultList.add(new FamilyReference(f.getReferenceNum()));
	    	}
		}
		else
		{
			//search the family db
	    	for(A4OFamily f: oncFamAL)
	    		if(f.getClientFamily().toLowerCase().contains(s.toLowerCase()))
	    			resultList.add(new FamilyReference(f.getReferenceNum()));
	    	
	    	//search the child db
	    	childDB.searchForLastName(year, s, resultList);
		}
		
		String response = gson.toJson(resultList, listOfFamilyReferences);
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	String update(int year, String familyjson, boolean bAutoAssign)
	{
		//Create a family object for the updated family
		Gson gson = new Gson();
		A4OFamily updatedFamily = gson.fromJson(familyjson, A4OFamily.class);
		
		//Find the position for the current family being replaced
		FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
		List<A4OFamily> fAL = fDBYear.getList();
		int index = 0;
		while(index < fAL.size() && fAL.get(index).getID() != updatedFamily.getID())
			index++;
		
		//replace the current family object with the update. First, check for address change.
		//if address has changed, update the region. 
		if(index < fAL.size())
		{
			A4OFamily currFam = fAL.get(index);
			
			//check if the reference number has changed to a Cxxxxx number greater than
			//the current highestReferenceNumber. If it is, reset highestRefNum
			if(updatedFamily.getReferenceNum().startsWith("C") && 
				!currFam.getReferenceNum().equals(updatedFamily.getReferenceNum()))
			{
				int updatedFamilyRefNum = Integer.parseInt(updatedFamily.getReferenceNum().substring(1));
				if(updatedFamilyRefNum > highestRefNum)
					highestRefNum = updatedFamilyRefNum;
			}
			
			//check if the address has changed and a region update check is required
			if(!currFam.getHouseNum().equals(updatedFamily.getHouseNum()) ||
				!currFam.getStreet().equals(updatedFamily.getStreet()) ||
				 !currFam.getCity().equals(updatedFamily.getCity()) ||
				  !currFam.getZipCode().equals(updatedFamily.getZipCode()))
			{
//				System.out.println(String.format("FamilyDB - update: region change, old region is %d", currFam.getRegion()));
				updateRegion(updatedFamily);	
			}
			
			//check if the update is requesting automatic assignment of an ONC family number
			//can only auto assign if ONC Number is not number and is not "DEL" and the
			//region is valid
			if(bAutoAssign && !updatedFamily.getONCNum().equals("DEL") && updatedFamily.getRegion() != 0 &&
						  !Character.isDigit(updatedFamily.getONCNum().charAt(0)))
			{
				updatedFamily.setONCNum(generateA4ONumber(year));
			}
			
			//check to see if either family or gift status is changing, if so, add a history item
			if(currFam != null && 
				(currFam.getFamilyStatus() != updatedFamily.getFamilyStatus() || currFam.getGiftStatus() != updatedFamily.getGiftStatus()))
			{
				//get current history item
				A4OFamilyHistory currFH = familyHistoryDB.getHistory(year, currFam.getHistoryID());
				
				int histID = addHistoryItem(year, updatedFamily.getID(), updatedFamily.getFamilyStatus(), 
											updatedFamily.getGiftStatus(), 
											currFH == null ? -1 : currFH.getPartnerID(), 
											"Status Changed", updatedFamily.getChangedBy());
				
				updatedFamily.setHistoryID(histID);
			}
			
			fAL.set(index, updatedFamily);
			fDBYear.setChanged(true);
			return "UPDATED_FAMILY" + gson.toJson(updatedFamily, A4OFamily.class);
		}
		else
			return "UPDATE_FAILED";
	}
	
	A4OFamily update(int year, A4OFamily updatedFamily, boolean bAutoAssign)
	{
		//Find the position for the current family being replaced
		FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
		List<A4OFamily> fAL = fDBYear.getList();
		int index = 0;
		while(index < fAL.size() && fAL.get(index).getID() != updatedFamily.getID())
			index++;
		
		//replace the current family object with the update. First, check for address change.
		//if address has changed, update the region. 
		if(index < fAL.size())
		{
			A4OFamily currFam = fAL.get(index);
			
			//check if the address has changed and a region update check is required
			if(!currFam.getHouseNum().equals(updatedFamily.getHouseNum()) ||
				!currFam.getStreet().equals(updatedFamily.getStreet()) ||
				 !currFam.getCity().equals(updatedFamily.getCity()) ||
				  !currFam.getZipCode().equals(updatedFamily.getZipCode()))
			{
//				System.out.println(String.format("FamilyDB - update: region change, old region is %d", currFam.getRegion()));
				updateRegion(updatedFamily);	
			}
			
			//check if the update is requesting automatic assignment of an ONC family number
			//can only auto assign if ONC Number is not number and is not "DEL" and the
			//region is valid
			if(bAutoAssign && !updatedFamily.getONCNum().equals("DEL") && updatedFamily.getRegion() != 0 &&
						  !Character.isDigit(updatedFamily.getONCNum().charAt(0)))
			{
				updatedFamily.setONCNum(generateA4ONumber(year));
			}
			
			//check to see if either status is changing, if so, add a history item
			if(currFam != null && 
				(currFam.getFamilyStatus() != updatedFamily.getFamilyStatus() || currFam.getGiftStatus() != updatedFamily.getGiftStatus()))
			{
				A4OFamilyHistory currFH = familyHistoryDB.getHistory(year, currFam.getHistoryID());
				
				int histID = addHistoryItem(year, updatedFamily.getID(), updatedFamily.getFamilyStatus(), 
						updatedFamily.getGiftStatus(), currFH.getPartnerID(), "Status Changed", updatedFamily.getChangedBy());
				updatedFamily.setHistoryID(histID);
			}
			
			fAL.set(index, updatedFamily);
			fDBYear.setChanged(true);
			return updatedFamily;
		}
		else
			return null;
	}
	
	int updateRegion(A4OFamily updatedFamily)
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
			reg = RegionDB.searchForRegionMatch(new Address(updatedFamily.getHouseNum(),
											updatedFamily.getStreet(), updatedFamily.getUnitNum(),
											  updatedFamily.getCity(), updatedFamily.getZipCode()));
			
			updatedFamily.setRegion(reg);
		}
		
		return reg;
	}
	
	@Override
	String add(int year, String json)
	{
		//Create a family object for the add family request
		Gson gson = new Gson();
		A4OFamily addedFam = gson.fromJson(json, A4OFamily.class);
		
		if(addedFam != null)
		{
			//get the family data base for the correct year
			FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
			
			//check to see if the reference number is provided, if not, generate one
			if(addedFam.getReferenceNum().equals("NNA"))
				addedFam.setReferenceNum(generateReferenceNumber());
			
			//check to see if family is already in the data base, if so, mark it as
			//a duplicate family. 
			
			
			//set region for family
			int region = updateRegion(addedFam);
			addedFam.setRegion(region);
		
			//create the A4O number
			String oncNum = generateA4ONumber(year);
			addedFam.setONCNum(oncNum);
			
			//set the new ID for the added family
			addedFam.setID(fDBYear.getNextID());
			
			//add to the family history 
			int histID = addHistoryItem(year, addedFam.getID(), addedFam.getFamilyStatus(), addedFam.getGiftStatus(),
										-1, "Family Referred", addedFam.getChangedBy());
			addedFam.setHistoryID(histID);
			
			//add to the family data base
			fDBYear.add(addedFam);
			fDBYear.setChanged(true);
		
			//return the new family
			return "ADDED_FAMILY" + gson.toJson(addedFam, A4OFamily.class);
		}
		else
			return "ADD_FAMILY_FAILED";
	}
	
	String addFamilyGroup(int year, String familyGroupJson, DesktopClient currClient)
	{
		//create the response list of jsons
		List<String> jsonResponseList = new ArrayList<String>();
		
		//create list of Britepath family objects to add
		Gson gson = new Gson();
		Type listOfBritepathFamilies = new TypeToken<ArrayList<BritepathFamily>>(){}.getType();		
		List<BritepathFamily> bpFamilyList = gson.fromJson(familyGroupJson, listOfBritepathFamilies);
		
		//for each family in the list, parse it and add it to the component databases
		for(BritepathFamily bpFam:bpFamilyList)
		{
			//potentially add the referring agent as a user to the Server User DB
			ImportONCObjectResponse userResponse = userDB.processImportedReferringAgent(bpFam, currClient);
			
			//if a user was added or updated, add the change to the response list
			if(userResponse.getImportResult() != USER_UNCHANGED)
				jsonResponseList.add(userResponse.getJsonResponse());
			
			//add the family to the Family DB
			A4OFamily reqAddFam = new A4OFamily(bpFam.referringAgentName, bpFam.referringAgentOrg,
				bpFam.referringAgentTitle, bpFam.clientFamily, bpFam.headOfHousehold,
				bpFam.familyMembers, bpFam.referringAgentEmail, bpFam.clientFamilyEmail, 
				bpFam.clientFamilyPhone, bpFam.referringAgentPhone, bpFam.dietartyRestrictions,
				bpFam.schoolsAttended, bpFam.details, bpFam.assigneeContactID,
				bpFam.deliveryStreetAddress, bpFam.deliveryAddressLine2, bpFam.deliveryCity,
				bpFam.deliveryZip, bpFam.deliveryState, bpFam.adoptedFor, bpFam.numberOfAdults,
				bpFam.numberOfChildren, bpFam.wishlist, bpFam.speaksEnglish, bpFam.language,
				bpFam.hasTransportation, bpFam.batchNum, new Date(), -1, "NNA", -1, 
				currClient.getClientUser().getLNFI(), -1);
			
			A4OFamily addedFam = add(year, reqAddFam);
			if(addedFam != null)
			{
				//if the family was added successfully, add the adults and children
				jsonResponseList.add("ADDED_FAMILY" + gson.toJson(addedFam, A4OFamily.class));
		
				String[] members = bpFam.getFamilyMembers().trim().split("\n");					
				for(int i=0; i<members.length; i++)
				{
					if(!members[i].isEmpty() && members[i].toLowerCase().contains("adult"))
					{
						//create the add adult request object
						String[] adult = members[i].split(ODB_FAMILY_MEMBER_COLUMN_SEPARATOR, 3);
						if(adult.length == 3)
						{
							//determine the gender, could be anything from Britepaths!
							AdultGender gender;
							if(adult[1].toLowerCase().contains("female") || adult[1].toLowerCase().contains("girl"))
								gender = AdultGender.Female;
							else if(adult[1].toLowerCase().contains("male") || adult[1].toLowerCase().contains("boy"))
								gender = AdultGender.Male;
							else
								gender = AdultGender.Unknown;
												
							ONCAdult reqAddAdult = new ONCAdult(-1, addedFam.getID(), adult[0], gender);
							
							//interact with the server to add the adult
							String addedAdultJson = gson.toJson(adultDB.add(year, reqAddAdult));
							jsonResponseList.add("ADDED_ADULT" + addedAdultJson);
						}
					}
					else if(!members[i].isEmpty())
					{
						//create the add child request object
						ONCChild reqAddChild = new ONCChild(-1, addedFam.getID(), members[i], year);
							
						//interact with the server to add the child
						String addedChildJson = gson.toJson(childDB.add(year, reqAddChild));
						jsonResponseList.add("ADDED_CHILD" + addedChildJson);
					}
				}
			}
		}
		
		//notify all other clients of the imported agent, family, adult and child objects
		clientMgr.notifyAllOtherInYearClients(currClient, jsonResponseList);
		
		Type listOfChanges = new TypeToken<ArrayList<String>>(){}.getType();
		return "ADDED_BRITEPATH_FAMILIES" + gson.toJson(jsonResponseList, listOfChanges);
	}
	
	A4OFamily add(int year, A4OFamily addedFam)
	{
		if(addedFam != null)
		{
			//get the family data base for the correct year
			FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
			
			//set region for family
			int region = updateRegion(addedFam);
			addedFam.setRegion(region);
			
			//create the A4O number
			String a4ONum = generateA4ONumber(year);
			addedFam.setONCNum(a4ONum);
			
			//set the new ID for the added family
			int famID = fDBYear.getNextID();
			addedFam.setID(famID);
			
			String targetID = addedFam.getReferenceNum();
			if(targetID.contains("NNA") || targetID.equals(""))
			{
				targetID = generateReferenceNumber();
				addedFam.setReferenceNum(targetID);
			}
			
			//add to the family history 
			int histID = addHistoryItem(year, addedFam.getID(), addedFam.getFamilyStatus(), addedFam.getGiftStatus(),
										-1, "Family Referred", addedFam.getChangedBy());
			addedFam.setHistoryID(histID);
			
			//add to the family data base
			fDBYear.add(addedFam);
			fDBYear.setChanged(true);
			
			//return the new family
			return addedFam;
		}
		else
			return null;
	}
	
	String addFamilyHistoryList(int year, String historyGroupJson)
	{
		ClientManager clientMgr = ClientManager.getInstance();
		
		//un-bundle to list of ONCFamilyHistory objects
		Gson gson = new Gson();
		Type listOfHistoryObjects = new TypeToken<ArrayList<A4OFamilyHistory>>(){}.getType();
		
		List<A4OFamilyHistory> famHistoryList = gson.fromJson(historyGroupJson, listOfHistoryObjects);
		
		ServerFamilyHistoryDB famHistDB;
		try 
		{
			famHistDB = ServerFamilyHistoryDB.getInstance();
			
			//for each history object in the list, add it to the database and notify all clients that
			//it was added
			for(A4OFamilyHistory reqFamHistoryObj:famHistoryList)
			{
				A4OFamilyHistory addedFamHistObj = famHistDB.addFamilyHistoryObject(year, reqFamHistoryObj);
				
				//find the family
				FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
				A4OFamily updatedFam = (A4OFamily) find(fDBYear.getList(), addedFamHistObj.getFamID());
				
				if(updatedFam != null)
				{
					updatedFam.setHistoryID(addedFamHistObj.getID());
					updatedFam.setFamilyStatus(addedFamHistObj.getFamilyStatus());
					fDBYear.setChanged(true);
					
					//if add was successful, need to q the change to all in-year clients
					//notify in year clients of change
					String response = "UPDATED_FAMILY" + gson.toJson(updatedFam, A4OFamily.class);
					clientMgr.notifyAllInYearClients(year, response);
				}
			}
			
			return "ADDED_GROUP_DELIVERIES";
		}
		catch (FileNotFoundException e) 
		{
			return "ADD_GROUP_DELIVERIES_FAILED";
		} 
		catch (IOException e) 
		{
			return "ADD_GROUP_DELIVERIES_FAILED";
		}
	}
	
	String checkForDuplicateFamily(int year, String json, ONCUser user)
	{
		//Create a family object for the family to check
		Gson gson = new Gson();
		A4OFamily reqFamToCheck = gson.fromJson(json, A4OFamily.class);
		
		String result = "UNIQUE_FAMILY";
		
		//verify requested family to check is in database
		A4OFamily famToCheck = getFamily(year, reqFamToCheck.getID());
		if(famToCheck != null)
		{
			//get the children to check from the family to check
			List<ONCChild> famChildrenToCheck = ServerChildDB.getChildList(year, famToCheck.getID());
			
			A4OFamily dupFamily = getDuplicateFamily(year, famToCheck, famChildrenToCheck);
			if(dupFamily != null)
			{
				//family to check is a duplicate, mark them as such and notify clients
				//update the other. Use reference # to determine
				famToCheck.setONCNum("DEL");
				famToCheck.setDNSCode("DUP");
				famToCheck.setStoplightPos(FAMILY_STOPLIGHT_RED);
				famToCheck.setStoplightMssg("DUP of " + dupFamily.getReferenceNum());
				famToCheck.setStoplightChangedBy(user.getLNFI());
				famToCheck.setReferenceNum(dupFamily.getReferenceNum());
				
				//notify all in year clients of change to famToCheck
				String famToCheckJson = gson.toJson(famToCheck, A4OFamily.class);
				clientMgr.notifyAllInYearClients(year, "UPDATED_FAMILY" + famToCheckJson);
				
				result = "DUPLICATE_FAMILY";
			}		
		}
		
		return result;
			
	}
	
	String getFamily(int year, String zFamID)
	{
		int oncID = Integer.parseInt(zFamID);
		List<A4OFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getID() != oncID)
			index++;
		
		if(index < fAL.size())
		{
			Gson gson = new Gson();
			String familyjson = gson.toJson(fAL.get(index), A4OFamily.class);
			
			return "FAMILY" + familyjson;
		}
		else
			return "FAMILY_NOT_FOUND";
	}
	
	static HtmlResponse getFamilyJSONP(int year, String targetID, String callbackFunction)
	{		
		Gson gson = new Gson();
		String response;
	
		List<A4OFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		
		int index=0;
		while(index<fAL.size() && !fAL.get(index).getReferenceNum().equals(targetID))
			index++;
		
		if(index<fAL.size())
		{
			A4OFamily fam = fAL.get(index);
			ONCWebsiteFamilyExtended webFam = new ONCWebsiteFamilyExtended(fam,RegionDB.getRegion(fam.getRegion()));
			response = gson.toJson(webFam, ONCWebsiteFamilyExtended.class);
		}
		else
			response = "";
		
		//wrap the json in the callback function per the JSONP protocol
		return new HtmlResponse(callbackFunction +"(" + response +")", HTTPCode.Ok);		
	}
	
	A4OFamily getFamily(int year, int id)	//id number set each year
	{
		List<A4OFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getID() != id)
			index++;
		
		if(index < fAL.size())
			return fAL.get(index);
		else
			return null;
	}
	
	static String getFamilyRefNum(int year, int id)	//id number set each year
	{
		List<A4OFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getID() != id)
			index++;
		
		if(index < fAL.size())
			return fAL.get(index).getReferenceNum();
		else
			return null;
	}
	
	A4OFamily getFamilyByMealID(int year, int mealID)
	{
		List<A4OFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < fAL.size() && fAL.get(index).getMealID() != mealID)
			index++;
		
		if(index < fAL.size())
			return fAL.get(index);
		else
			return null;
	}

	
	A4OFamily getFamilyByTargetID(int year, String targetID)	//Persistent odb, wfcm or onc id number string
	{
		List<A4OFamily> fAL = familyDB.get(year-BASE_YEAR).getList();
		int index = 0;	
		while(index < fAL.size() && !fAL.get(index).getReferenceNum().equals(targetID))
			index++;
		
		if(index < fAL.size())
			return fAL.get(index);
		else
			return null;
	}
	
//	void checkFamilyGiftStatusAndGiftCardOnlyOnWishAdded(int year, int childid)
//	{
//		int famID = childDB.getChildsFamilyID(year, childid);
//		
//		ONCFamily fam = getFamily(year, famID);
//		
//	    //determine the proper family gift status for the family after adding the wish. If the
//		//family gifts have already been packaged, then don't perform the test
//	    FamilyGiftStatus newGiftStatus;
//	    if(fam.getGiftStatus().compareTo(FamilyGiftStatus.Exported) < 0)
//	    	newGiftStatus = getLowestGiftStatus(year, famID);
//	    else
//	    	newGiftStatus = fam.getGiftStatus();
	    
	    //determine if the families gift card only status after adding the wish
//	    boolean bNewGiftCardOnlyFamily = isGiftCardOnlyFamily(year, famID);
	   
	    //if gift status has changed, update the data base and notify clients
//	    if(newGiftStatus != fam.getGiftStatus() || bNewGiftCardOnlyFamily != fam.isGiftCardOnly())
//	    {
//	    	if(newGiftStatus != fam.getGiftStatus())
//	    	{
//	    		//create a family history change
//	    		fam.setGiftStatus(newGiftStatus);
//	    		
//	    		ONCFamilyHistory currFH = familyHistoryDB.getHistory(year, fam.getDeliveryID());
//	    		
//	    		fam.setDeliveryID(addHistoryItem(year, fam.getID(), fam.getFamilyStatus(), newGiftStatus, 
//						currFH.getPartnerID(), "Gift Status Change", fam.getChangedBy()));
//	    	}
//	    	
//	    	if(bNewGiftCardOnlyFamily != fam.isGiftCardOnly())
//	    		fam.setGiftCardOnly(bNewGiftCardOnlyFamily);
//	    	
//	    	familyDB.get(year - BASE_YEAR).setChanged(true);
//	    	
//	    	Gson gson = new Gson();
//	    	String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
//	    	clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
//	    }
//	}
	
	int addHistoryItem(int year, int famID, FamilyStatus fs, FamilyGiftStatus fgs, int partnerID,
						String reason, String changedBy)
	{
		A4OFamilyHistory reqFamHistObj = new A4OFamilyHistory(-1, famID, fs, fgs, partnerID, reason,
				 changedBy, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
		
		A4OFamilyHistory addedFamHistory = familyHistoryDB.addFamilyHistoryObject(year, reqFamHistObj);
		
		return addedFamHistory.getID();
	}
	
	/********
	 * Whenever meal is added, the meal database notifies the family database to update
	 * the id for the families meal and the meal status field.
	 * The meal status field is redundant in the ONC Family object for performance considerations. 
	 * @param year
	 * @param addedMeal
	 */
	void updatedFamilyMeal(int year, ONCMeal addedMeal)
	{
		A4OFamily fam = getFamily(year, addedMeal.getFamilyID());
		if(fam != null)
		{
			//check to see if the change affected family gift card status
			ServerFamilyHistoryDB fhDB;
			try 
			{
				fhDB = ServerFamilyHistoryDB.getInstance();
				A4OFamilyHistory fh = fhDB.getHistory(year,fam.getHistoryID());
				
				if(addedMeal != null && fh != null)
					fam.setGiftCardOnly(isGiftCardFamily(year, fh, addedMeal));
			} 
			catch (FileNotFoundException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			fam.setMealID(addedMeal.getID());
			fam.setMealStatus(addedMeal.getStatus());
			fam.setChangedBy(addedMeal.getChangedBy());
			familyDB.get(year - BASE_YEAR).setChanged(true);
			
			Gson gson = new Gson();
	    	String changeJson = "UPDATED_FAMILY" + gson.toJson(fam, A4OFamily.class);
	    	clientMgr.notifyAllInYearClients(year, changeJson);	//null to notify all clients
		}
	}
	
//	boolean isGiftCardOnlyFamily(int year, int famid)
//	{
//		//set a return variable to true. If we find one instance, we'll set it to false
//		boolean bGiftCardOnlyFamily = true;
//		
//		//first, determine if gift cards are even in the catalog. If they aren't, return false as
//		//it can't be a gift card only family
//		int giftCardID = ServerWishCatalog.findWishIDByName(year, GIFT_CARD_WISH_NAME);
//		if(giftCardID == -1)
//			bGiftCardOnlyFamily = false;
//		else
//		{	
//			List<ONCChild> childList = ServerChildDB.getChildList(year, famid);	//get the children in the family
//		
//			//examine each child to see if their assigned gifts are gift cards. If gift is not
//			//assigned or not a gift card, then it's not a gift card only family
//			int childindex=0;
//			while(childindex < childList.size() && bGiftCardOnlyFamily)
//			{
//				ONCChild c = childList.get(childindex++);	//get the child
//				
//				//if all gifts aren't assigned, then not a gift card only family
//				int wn = 0;
//				while(wn < NUMBER_OF_WISHES_PER_CHILD && bGiftCardOnlyFamily)
//					if(c.getChildWishID(wn++) == -1)
//						bGiftCardOnlyFamily = false;
//				
//				//if all are assigned, examine each gift to see if it's a gift card
//				int giftindex = 0;
//				while(giftindex < NUMBER_OF_WISHES_PER_CHILD && bGiftCardOnlyFamily)
//				{
//					ONCChildWish cw = ServerChildWishDB.getWish(year, c.getChildWishID(giftindex++));
//					if(cw.getWishID() != giftCardID)	//gift card?
//						bGiftCardOnlyFamily = false;
//				}	
//			}
//		}
//		
//		return bGiftCardOnlyFamily;
//	}
	
	/*************************************************************************************************************
	* This method is called when a child's wish status changes due to a user change. The method
	* implements a set of rules that returns the family status when all children in the family
	* wishes/gifts attain a certain status. For example, when all children's gifts are selected,
	* the returned family status is FAMILY_STATUS_GIFTS_SELECTED. A matrix that correlates child
	* gift status to family status is used. There are 7 possible setting for child wish status.
	* The seven correspond to five family status choices. The method finds the lowest family
	* status setting based on the children's wish status and returns it. 
	**********************************************************************************************************/
//	FamilyGiftStatus getLowestGiftStatus(int year, int famid)
//	{
//		//This matrix correlates a child wish status to the family status.
//		FamilyGiftStatus[] wishstatusmatrix = {FamilyGiftStatus.Requested,	//WishStatus Index = 0;
//								FamilyGiftStatus.Requested,	//WishStatus Index = 1;
//								FamilyGiftStatus.Selected,	//WishStatus Index = 2;
//								FamilyGiftStatus.Selected,	//WishStatus Index = 3;
//								FamilyGiftStatus.Selected,	//WishStatus Index = 4;
//								FamilyGiftStatus.Selected,	//WishStatus Index = 5;
//								FamilyGiftStatus.Selected,	//WishStatus Index = 6;
//								FamilyGiftStatus.Received,	//WishStatus Index = 7;
//								FamilyGiftStatus.Received,	//WishStatus Index = 8;
//								FamilyGiftStatus.Selected,	//WishStatus Index = 9;
//								FamilyGiftStatus.Verified};	//WishStatus Index = 10;
//			
//		//Check for all gifts selected
//		FamilyGiftStatus lowestfamstatus = FamilyGiftStatus.Verified;
//		for(ONCChild c:ServerChildDB.getChildList(year, famid))
//		{
//			for(int wn=0; wn< NUMBER_OF_WISHES_PER_CHILD; wn++)
//			{
//				ONCChildWish cw = ServerChildWishDB.getWish(year, c.getChildWishID(wn));
//				
//				//if cw is null, it means that the wish doesn't exist yet. If that's the case, 
//				//set the status to the lowest status possible as if the wish existed
//				WishStatus childwishstatus = WishStatus.Not_Selected;	//Lowest possible child wish status
//				if(cw != null)
//					childwishstatus = ServerChildWishDB.getWish(year, c.getChildWishID(wn)).getChildWishStatus();
//					
//				if(wishstatusmatrix[childwishstatus.statusIndex()].compareTo(lowestfamstatus) < 0)
//					lowestfamstatus = wishstatusmatrix[childwishstatus.statusIndex()];
//			}
//		}
//			
//		return lowestfamstatus;
//	}
	
	void updateFamilyHistory(int year, A4OFamilyHistory addedHistObj)
	{
		//find the family
		FamilyDBYear famDBYear = familyDB.get(year - BASE_YEAR);
		A4OFamily fam = getFamily(year, addedHistObj.getFamID());
		
		//check to see if the change affected family gift card status
		ServerMealDB mealDB;
		try 
		{
			mealDB = ServerMealDB.getInstance();
			ONCMeal famMeal = mealDB.findCurrentMealForFamily(year, fam.getID());
			
			if(famMeal != null && addedHistObj != null)
				fam.setGiftCardOnly(isGiftCardFamily(year, addedHistObj, famMeal));
		} 
		catch (FileNotFoundException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//update the history ID and gift status
		fam.setHistoryID(addedHistObj.getID());
		fam.setGiftStatus(addedHistObj.getGiftStatus());
		fam.setChangedBy(addedHistObj.getChangedBy());
		famDBYear.setChanged(true);
		
		//notify in year clients of change
		Gson gson = new Gson();
    	String change = "UPDATED_FAMILY" + gson.toJson(fam, A4OFamily.class);
    	clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients	
	}
	
	boolean isGiftCardFamily(int year, A4OFamilyHistory fh, ONCMeal m)
	{
		int dPartnerID = DEFAULT_PARTNER_ID;
		try {
			ServerGlobalVariableDB sGVDB = ServerGlobalVariableDB.getInstance();
			dPartnerID = sGVDB.getDefaultPartnerID(year);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		System.out.println(String.format("ServerFamDB.isGiftCardFam: dPID=%d", dPartnerID));
		
		return fh.getPartnerID() == dPartnerID || m.getMealPartnerID() == dPartnerID;
	}
/*	
	void updateFamilyMeal(int year, ONCMeal addedMeal)
	{
		//try to find the family the meal belongs to. In order to add the meal, the family
		//must exist
		ONCFamily fam = getFamily(year, addedMeal.getFamilyID());				
		
		if(fam != null && addedMeal != null)
		{
			fam.setMealID(addedMeal.getID());
			
			if(addedMeal.getID() == -1)
				fam.setMealStatus(MealStatus.None);
			else if(addedMeal.getPartnerID() == -1)
				fam.setMealStatus(MealStatus.Requested);
			else if(fam.getMealStatus() == MealStatus.Requested && addedMeal.getPartnerID() > -1)
				fam.setMealStatus(MealStatus.Assigned);
			
			familyDB.get(year - BASE_YEAR).setChanged(true);
		
			//notify the in year clients of the family update
			Gson gson = new Gson();
			String change = "UPDATED_FAMILY" + gson.toJson(fam, ONCFamily.class);
			clientMgr.notifyAllInYearClients(year, change);	//null to notify all clients
		}
	}
*/	

	void addObject(int year, String[] nextLine)
	{
		FamilyDBYear famDBYear = familyDB.get(year - BASE_YEAR);

		Calendar date_changed = Calendar.getInstance();	//No date_changed in ONCFamily yet
		if(!nextLine[6].isEmpty())
			date_changed.setTimeInMillis(Long.parseLong(nextLine[6]));
			
		famDBYear.add(new A4OFamily(nextLine));
	}
	
	void save(int year)
	{
		String[] header = {"A4O ID", "A4O Num", "Region", "Ref #", "Batch #", "DNS Code", "Family Status", "Gift Status",
				"Speak English?","Language if No", "Changed By", "Notes", "Delivery Instructions",
				"Client Family", "First Name", "Last Name", "House #", "Street", "Unit #", "City", "Zip Code",
				"Substitute Delivery Address", "All Phone #'s", "Home Phone", "Other Phone", "Family Email", 
				"Details", "Children Names", "Schools", "WishList",
				"Adopted For", "Agent ID", "History ID", "Meal ID", "Meal Status", "# of Bags", "# of Large Items", 
				"Stoplight Pos", "Stoplight Mssg", "Stoplight C/B", "Transportation", "Gift Card Only"};
		
		FamilyDBYear fDBYear = familyDB.get(year - BASE_YEAR);
		if(fDBYear.isUnsaved())
			
		{
			String path = String.format("%s/A4O/%dDB/NewFamilyDB.csv", System.getProperty("user.dir"), year);
			exportDBToCSV(fDBYear.getList(), header, path);
			fDBYear.setChanged(false);
		}
	}
	
	static boolean didAgentReferInYear(int year, int agentID)
	{
		List<A4OFamily> famList = familyDB.get(year-BASE_YEAR).getList();
		
		int index = 0;
		while(index < famList.size() && famList.get(index).getAgentID() != agentID)
			index++;
		
		return index < famList.size();	//true if agentID referred family	
	}
	
	/****
	 * Checks to see if an address should have a unit number. Looks at all prior years stored
	 * in the database and tries to find a match for the address without unit. If it finds a
	 * match it stops the search. Then, using the matched address, determines if the stored 
	 * address had an apt/unit. If so, returns true. The address match search matches house
	 * number, street name without suffix and city. 
	 * @param priorYear
	 * @param housenum
	 * @param street
	 * @param zip
	 * @return
	 */
	static boolean shouldAddressHaveUnit(int priorYear, String housenum, String street, String zip)
	{
		boolean bAddressHadUnit = false;
		if(priorYear > BASE_YEAR)
		{
			List<A4OFamily> famList = familyDB.get(priorYear-BASE_YEAR).getList();
			int index = 0;
			while(index < famList.size() &&
				   !(famList.get(index).getHouseNum().equals(housenum) &&
//					 famList.get(index).getStreet().toLowerCase().equals(street.toLowerCase())))
					 removeStreetSuffix(famList.get(index).getStreet()).equals(removeStreetSuffix(street))))				
//					 && famList.get(index).getZipCode().equals(zip)))
				index++;
			
			if(index < famList.size())
			{
//				ONCFamily fam = famList.get(index);
//				System.out.println(String.format("FamilyDB.shouldAddressHaveUnit: Prior Year = %d, ONC#= %s, Unit=%s, Unit.length=%d", priorYear, fam.getONCNum(), fam.getUnitNum(), fam.getUnitNum().trim().length()));
				bAddressHadUnit = !famList.get(index).getUnitNum().trim().isEmpty();
			}
		}
		
		return bAddressHadUnit;
	}
	
	static String removeStreetSuffix(String streetName)
	{
		if(!streetName.isEmpty())
		{
			String[] nameParts = streetName.split(" ");
			StringBuffer buff = new StringBuffer(nameParts[0]);
			for(int i=0; i<nameParts.length-1; i++)
				buff.append(" " + nameParts[i]);
			
			return buff.toString().toLowerCase();
		}
		else
			return "";
	}
	
	//convert targetID to familyID
	static int getFamilyID(int year, String targetID)
	{
		List<A4OFamily> famList = familyDB.get(year-BASE_YEAR).getList();
		
		int index = 0;
		while(index < famList.size() && !famList.get(index).getReferenceNum().equals(targetID))
			index++;
		
		if(index < famList.size())
			return famList.get(index).getID();	//return famID
		else
			return -1;
	}
	
	List<A4OFamily> getList(int year)
	{
		return familyDB.get(year-BASE_YEAR).getList();
	}

	@Override
	void createNewYear(int newYear)
	{
		//test to see if prior year existed. If it did, need to add to the ApartmentDB the families who's
		//addresses had units.
		if(!familyDB.isEmpty())
		{
			FamilyDBYear fDBYear = familyDB.get(familyDB.size()-1);	//prior year familyDBYear
			ApartmentDB.addPriorYearApartments(fDBYear.getList());	//prior year list of ONCFamily
		}
		
		//create a new family data base year for the year provided in the newYear parameter
		//The family db year is initially empty prior to the import of families, so all we
		//do here is create a new FamilyDBYear for the newYear and save it.
		FamilyDBYear famDBYear = new FamilyDBYear(newYear);
		familyDB.add(famDBYear);
		famDBYear.setChanged(true);	//mark this db for persistent saving on the next save event
		
		//add a highest A4O number to the higest number map
		highestA4ONumMap.put(newYear, 0);
	}
	
	 /******************************************************************************************
     * This method automatically generates an A4O Number for family 
     ********************************************************************************************/
    String generateA4ONumber(int year)
    {
    	//get the current highest number, increment it and re-save it.
    	int highestNum = highestA4ONumMap.get(year);
    	highestNum++;
    	highestA4ONumMap.put(year, highestNum);
    	
    	//convert it to a 4 digit string and return it.
    	if(highestNum < 10)
    		return "000" + Integer.toString(highestNum);
    	else if(highestNum >= 10 && highestNum < 100)
    		return "00" + Integer.toString(highestNum);
    	else if(highestNum >= 100 && highestNum < 1000)
    		return "0" + Integer.toString(highestNum);
    	else if(highestNum >= 1000 && highestNum < 10000)
    		return Integer.toString(highestNum);
    	else
    		return "NNA";
/*    	
    	String oncNum = null;
    	//Verify region number is valid. If it's not return an error
    	RegionDB regionDB = null;
		try {
			regionDB = RegionDB.getInstance(null);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	//assume RegionDB already created
    	 
    	if(region == 0)		//Don't assign numbers without known valid region addresses
    		oncNum = "NNA";
    	else if(regionDB != null && regionDB.isRegionValid(region))
    	{
    		int start = oncnumRegionRanges[region];
    		int	end = oncnumRegionRanges[(region+1) % regionDB.getNumberOfRegions()];
    		
    		String searchstring = Integer.toString(start);
    		while(start < end && searchForONCNumber(year, searchstring) != -1)
    			searchstring = Integer.toString(++start);
    		
    		if(start==end)
    		{
    			oncNum = "OOR";		//Not enough size in range
//    			System.out.println(String.format("ERROR: Too many families in region %d," + 
//    					", can't automatically assign an ONC Nubmer", region));
    		}
    		else
    			oncNum = Integer.toString(start);	
    	}
    	else
    	{
//   		System.out.println(String.format("ERROR: ONC Region Invalid: Region %d is not in " +
//					"the vaild range", region));
    		oncNum = "RNV";
    	}	
    	
    	return oncNum;
*/    	
    }
    
    /***
     * Check to see if family is already in a family data base. 
     */
    A4OFamily getDuplicateFamily(int year, A4OFamily addedFamily, List<ONCChild> addedChildList)
    {
//    	System.out.println(String.format("FamilyDB.getDuplicateFamily: "
//				+ "year= %d, addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//				year, addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
    	
    	A4OFamily dupFamily = null;
    	boolean bFamilyDuplicate = false;
    	//check to see if family exists in year. 
    	List<A4OFamily> famList = familyDB.get(year-BASE_YEAR).getList();
    	
//    	System.out.println("getDuplicateFamiiy: got famList, size= " + famList.size());
    	
    	int dupFamilyIndex = 0;
    	
    	while(dupFamilyIndex < famList.size() && 
    		   addedFamily.getID() != famList.get(dupFamilyIndex).getID() &&
    			!bFamilyDuplicate)
    	{
    		dupFamily = famList.get(dupFamilyIndex++);
    		
//    		System.out.println(String.format("FamiyDB.getDuplicateFamily: Checking dupFamily id= %d "
//					+ "dupFamily HOHLastName= %s, dupRef#= %s, against addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//					dupFamily.getID(), dupFamily.getHOHLastName(), dupFamily.getODBFamilyNum(), 
//					addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
    		
    		List<ONCChild> dupChildList = ServerChildDB.getChildList(year, dupFamily.getID());
    		
//    		if(dupChildList == null)
//   			System.out.println("FamilyDB.getDuplicateFamily: dupChildList is null");
//    		else
//    			System.out.println(String.format("FamiyDB.getDuplicateFamily: #children in %s family = %d ",
//					dupFamily.getHOHLastName(), dupChildList.size()));
    	
    		bFamilyDuplicate = areFamiliesTheSame(dupFamily, dupChildList, addedFamily, addedChildList);
    	}
    	
//    	if(dupFamily != null)
//    		System.out.println(String.format("FamiyDB.getDuplicateFamily: "
//					+ "dupFamily HOHLastName= %s, dupRef#= %s, addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//					dupFamily.getHOHLastName(), dupFamily.getODBFamilyNum(), 
//					addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
//    	
//    	else
//    		System.out.println("FamiyDB.getDuplicateFamily: dupFamiy is null");
    	
    	return bFamilyDuplicate ? dupFamily : null;	
    }
    
    /****
     * Checks each prior year in data base to see if an addedFamily matches. Once an added 
     * family matches the search ends and the method returns the ODB# for the family. If
     * no match is found, null is returned
     * @param year
     * @param addedFamily
     * @param addedChildList
     * @return
     */
    A4OFamily isPriorYearFamily(int year, A4OFamily addedFamily, List<ONCChild> addedChildList)
    {
    	boolean bFamilyIsInPriorYear = false;
    	A4OFamily pyFamily = null;
    	int yearIndex = year-1;
    	
    	//check each prior year for a match
    	while(yearIndex >= BASE_YEAR && !bFamilyIsInPriorYear)
    	{
    		List<A4OFamily> pyFamilyList = familyDB.get(yearIndex-BASE_YEAR).getList();
    		
    		//check each family in year for a match
    		int pyFamilyIndex = 0;
    		while(pyFamilyIndex < pyFamilyList.size() && !bFamilyIsInPriorYear)
    		{
    			pyFamily = pyFamilyList.get(pyFamilyIndex++);
    			List<ONCChild> pyChildList = ServerChildDB.getChildList(yearIndex, pyFamily.getID());
    			
    			bFamilyIsInPriorYear = areFamiliesTheSame(pyFamily, pyChildList, addedFamily, addedChildList);	
    		}
    		
    		yearIndex--;
    	}
    	
    	return bFamilyIsInPriorYear ? pyFamily : null;
    	
    }
    
    boolean areFamiliesTheSame(A4OFamily checkFamily, List<ONCChild> checkChildList, 
    							A4OFamily addedFamily, List<ONCChild> addedChildList)
    {
    	
//    	System.out.println(String.format("FamiyDB.areFamiliesThe Same: Checking family "
//				+ "checkFamily HOHLastName= %s, checkFamilyRef#= %s, against addedFamily HOHLastName = %s, addedFamily Ref#= %s", 
//				checkFamily.getHOHLastName(), checkFamily.getODBFamilyNum(), 
//				addedFamily.getHOHLastName(), addedFamily.getODBFamilyNum()));
    	
    	return checkFamily.getHOHFirstName().equalsIgnoreCase(addedFamily.getHOHFirstName()) &&
    			checkFamily.getHOHLastName().equalsIgnoreCase(addedFamily.getHOHLastName()) &&
    			areChildrenTheSame(checkChildList, addedChildList);
    }
    
    boolean areChildrenTheSame(List<ONCChild> checkChildList, List<ONCChild> addedChildList)
    {
    	boolean bChildrenAreTheSame = true;
    	
    	int checkChildIndex = 0;
    	while(checkChildIndex < checkChildList.size() && bChildrenAreTheSame)
    	{
    		ONCChild checkChild = checkChildList.get(checkChildIndex);
    		if(!isChildInList(checkChild, addedChildList))
    			bChildrenAreTheSame = false;
    		else
    			checkChildIndex++;
    	}
    	
    	return bChildrenAreTheSame;
    }
    
    
    boolean isChildInList(ONCChild checkChild, List<ONCChild> addedChildList)
    {
    	boolean bChildIsInList = false;
    	int addedChildIndex = 0;
        	
    	while(addedChildIndex < addedChildList.size()  && !bChildIsInList)
    	{
    		ONCChild addedChild = addedChildList.get(addedChildIndex);
    		
    		if(checkChild.getChildLastName().equalsIgnoreCase(addedChild.getChildLastName()) &&
    				checkChild.getChildDOB().equals(addedChild.getChildDOB()) &&
    					checkChild.getChildGender().equalsIgnoreCase(addedChild.getChildGender()))
    			bChildIsInList = true;
    		else
    			addedChildIndex++;
    	}	
    			
    	return bChildIsInList;
    }
    
    
    
    
    /******************************************************************************************************
     * This method generates a family reference number prior to import of external data. Each family
     * has one reference number, which remains constant from year to year
     ******************************************************************************************************/
    String generateReferenceNumber()
    {
    	//increment the last reference number used and format it to a five digit string
    	//that starts with the letter 'A'
    	highestRefNum++;
    	
    	if(highestRefNum < 10)
    		return "A0000" + Integer.toString(highestRefNum);
    	else if(highestRefNum >= 10 && highestRefNum < 100)
    		return "A000" + Integer.toString(highestRefNum);
    	else if(highestRefNum >= 100 && highestRefNum < 1000)
    		return "A00" + Integer.toString(highestRefNum);
    	else if(highestRefNum >= 1000 && highestRefNum < 10000)
    		return "A0" + Integer.toString(highestRefNum);
    	else
    		return "A" + Integer.toString(highestRefNum);
    }
    
    void decrementReferenceNumber() { highestRefNum--; }
    
    /*******
     * This method looks at the specified year in the data base and determines the highest A4O
     * number used to date. A4O numbers are strings with the format cccc, where c's are 
     * character digits 0-9.
     ******************/
    int initializeHighestA4ONumber(FamilyDBYear dbYear)
    {
    	int highestA4ONum = 0;
    	List<A4OFamily> yearListOfFamilies = dbYear.getList();
    	for(A4OFamily f: yearListOfFamilies)
    	{
    		if(isNumeric(f.getONCNum()))
    		{
    			int number = Integer.parseInt(f.getONCNum());
    			if(number > highestA4ONum)
    				highestA4ONum = number;
    		}
    	}
    	
    	return highestA4ONum;
    }

    /*******
     * This method looks at every year in the data base and determines the highest A4O family
     * reference number used to date. A4O reference numbers start with the letter 'A'
     * and have the format Axxxxx, where x's are digits 0-9.
     ******************/
    int initializeHighestReferenceNumber()
    {
    	int highestRefNum = 0;
    	for(FamilyDBYear dbYear: familyDB)
    	{
    		List<A4OFamily> yearListOfFamilies = dbYear.getList();
    		for(A4OFamily f: yearListOfFamilies)
    		{
    			if(f.getReferenceNum().startsWith("A"))
    			{
    				int refNum = Integer.parseInt(f.getReferenceNum().substring(1));
    				if(refNum > highestRefNum)
    					highestRefNum = refNum;
    			}
    		}
    	}
    	
    	return highestRefNum;
    }

    int searchForONCNumber(int year, String oncnum)
    {
    	List<A4OFamily> oncFamAL = familyDB.get(year-BASE_YEAR).getList();
    	
    	int index = 0;
    	while(index < oncFamAL.size() && !oncnum.equals(oncFamAL.get(index).getONCNum()))
    		index++;
    	
    	return index == oncFamAL.size() ? -1 : index;   		
    }
    
    private class FamilyDBYear extends ServerDBYear
    {
    	private List<A4OFamily> fList;
    	
    	FamilyDBYear(int year)
    	{
    		super();
    		fList = new ArrayList<A4OFamily>();
    	}
    	
    	//getters
    	List<A4OFamily> getList() { return fList; }
    	
    	void add(A4OFamily addedFamily) { fList.add(addedFamily); }
    }
    
    void convertFamilyDBForStatusChanges(int year)
    {
    	String[] header, nextLine;
    	List<String[]> outputList = new ArrayList<String[]>();
    	
    	//open the current year file
    	String path = String.format("%s/%dDB/FamilyDB.csv", System.getProperty("user.dir"), year);
    	CSVReader reader;
		try 
		{
			reader = new CSVReader(new FileReader(path));
			if((header = reader.readNext()) != null)	//Does file have records? 
	    	{
	    		//Read the User File
	    		if(header.length == FAMILYDB_HEADER_LENGTH)	//Does the record have the right # of fields? 
	    		{
	    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
	    			{
	    				NewFamStatus nfs = getNewFamStatus(nextLine[6], nextLine[7]);
	    				nextLine[6] = nfs.getNewFamStatus();
	    				nextLine[7] = nfs.getNewGiftStatus();
	    				outputList.add(nextLine);
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
		
		String[] outHeader = {"ONC ID", "ONCNum", "Region", "ODB Family #", "Batch #", "DNS Code", "Family Status", "Delivery Status",
				"Speak English?","Language if No", "Caller", "Notes", "Delivery Instructions",
				"Client Family", "First Name", "Last Name", "House #", "Street", "Unit #", "City", "Zip Code",
				"Substitute Delivery Address", "All Phone #'s", "Home Phone", "Other Phone", "Family Email", 
				"ODB Details", "Children Names", "Schools", "ODB WishList",
				"Adopted For", "Agent ID", "Delivery ID", "Meal ID", "Meal Status", "# of Bags", "# of Large Items", 
				"Stoplight Pos", "Stoplight Mssg", "Stoplight C/B", "Transportation", "Gift Card Only"};
		
	//	System.out.println(String.format("FamilyDB saveDB - Saving %d New Family DB", year));
		String outPath = String.format("%s/%dDB/NewFamilyDB.csv", System.getProperty("user.dir"), year);
		
		 try 
		    {
		    	CSVWriter writer = new CSVWriter(new FileWriter(outPath));
		    	writer.writeNext(outHeader);
		    	 
		    	for(int index=0; index < outputList.size(); index++)
		    		writer.writeNext(outputList.get(index));
		    	
		    	writer.close();
		    	       	    
		    } 
		    catch (IOException x)
		    {
		    	System.err.format("IO Exception: %s%n", x);
		    }	
    }
    
    /***
     * Used to convert ServerFamilyDB from old family status & gift status to new
     * @param ofs
     * @param ogs
     * @return
     */
    NewFamStatus getNewFamStatus(String ofs, String ogs)
    {
    	int oldFamStatus = Integer.parseInt(ofs);
    	int oldGiftStatus = Integer.parseInt(ogs);
    	
    	if(oldFamStatus == 0 && oldGiftStatus == 0)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 1)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 2)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 3)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 4)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 5)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 6)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 7)
    		return new NewFamStatus(0,1);
    	else if(oldFamStatus == 0 && oldGiftStatus == 8)
    		return new NewFamStatus(0,0);
    	else if(oldFamStatus == 1 && oldGiftStatus == 0)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 1)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 2)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 3)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 4)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 5)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 6)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 7)
    		return new NewFamStatus(1,1);
    	else if(oldFamStatus == 1 && oldGiftStatus == 8)
    		return new NewFamStatus(1,0);
    	else if(oldFamStatus == 2 && oldGiftStatus == 0)
    		return new NewFamStatus(1,2);
    	else if(oldFamStatus == 2 && oldGiftStatus == 1)
    		return new NewFamStatus(2,2);
    	else if(oldFamStatus == 2 && oldGiftStatus == 2)
    		return new NewFamStatus(3,2);
    	else if(oldFamStatus == 2 && oldGiftStatus == 3)
    		return new NewFamStatus(3,6);
    	else if(oldFamStatus == 2 && oldGiftStatus == 4)
    		return new NewFamStatus(3,8);
    	else if(oldFamStatus == 2 && oldGiftStatus == 5)
    		return new NewFamStatus(3,9);
    	else if(oldFamStatus == 2 && oldGiftStatus == 6)
    		return new NewFamStatus(3,7);
    	else if(oldFamStatus == 2 && oldGiftStatus == 7)
    		return new NewFamStatus(3,10);
    	else if(oldFamStatus == 2 && oldGiftStatus == 8)
    		return new NewFamStatus(1,0);
    	else if(oldFamStatus == 3 && oldGiftStatus == 0)
    		return new NewFamStatus(1,3);
    	else if(oldFamStatus == 3 && oldGiftStatus == 1)
    		return new NewFamStatus(2,3);
    	else if(oldFamStatus == 3 && oldGiftStatus == 2)
    		return new NewFamStatus(3,3);
    	else if(oldFamStatus == 3 && oldGiftStatus == 3)
    		return new NewFamStatus(3,6);
    	else if(oldFamStatus == 3 && oldGiftStatus == 4)
    		return new NewFamStatus(3,8);
    	else if(oldFamStatus == 3 && oldGiftStatus == 5)
    		return new NewFamStatus(3,0);
    	else if(oldFamStatus == 3 && oldGiftStatus == 6)
    		return new NewFamStatus(3,7);
    	else if(oldFamStatus == 3 && oldGiftStatus == 7)
    		return new NewFamStatus(3,10);
    	else if(oldFamStatus == 3 && oldGiftStatus == 8)
    		return new NewFamStatus(1,0);
    	else if(oldFamStatus == 4 && oldGiftStatus == 0)
    		return new NewFamStatus(1,4);
    	else if(oldFamStatus == 4 && oldGiftStatus == 1)
    		return new NewFamStatus(2,4);
    	else if(oldFamStatus == 4 && oldGiftStatus == 2)
    		return new NewFamStatus(3,4);
    	else if(oldFamStatus == 4 && oldGiftStatus == 3)
    		return new NewFamStatus(3,6);
    	else if(oldFamStatus == 4 && oldGiftStatus == 4)
    		return new NewFamStatus(3,8);
    	else if(oldFamStatus == 4 && oldGiftStatus == 5)
    		return new NewFamStatus(3,9);
    	else if(oldFamStatus == 4 && oldGiftStatus == 6)
    		return new NewFamStatus(3,7);
    	else if(oldFamStatus == 4 && oldGiftStatus == 7)
    		return new NewFamStatus(3,10);
    	else if(oldFamStatus == 4 && oldGiftStatus == 8)
    		return new NewFamStatus(1,0);
    	else if(oldFamStatus == 5 && oldGiftStatus == 0)
    		return new NewFamStatus(1,5);
    	else if(oldFamStatus == 5 && oldGiftStatus == 1)
    		return new NewFamStatus(2,5);
    	else if(oldFamStatus == 5 && oldGiftStatus == 2)
    		return new NewFamStatus(3,5);
    	else if(oldFamStatus == 5 && oldGiftStatus == 3)
    		return new NewFamStatus(3,6);
    	else if(oldFamStatus == 5 && oldGiftStatus == 4)
    		return new NewFamStatus(3,8);
    	else if(oldFamStatus == 5 && oldGiftStatus == 5)
    		return new NewFamStatus(3,9);
    	else if(oldFamStatus == 5 && oldGiftStatus == 6)
    		return new NewFamStatus(3,7);
    	else if(oldFamStatus == 5 && oldGiftStatus == 7)
    		return new NewFamStatus(3,10);
    	else if(oldFamStatus == 5 && oldGiftStatus == 8)
    		return new NewFamStatus(1,0); 
    	else
    		return null;
    }
    
   
    
    private static class ONCFamilyONCNumComparator implements Comparator<A4OFamily>
	{
		@Override
		public int compare(A4OFamily o1, A4OFamily o2)
		{
			if(isNumeric(o1.getONCNum()) && isNumeric(o2.getONCNum()))
			{
				Integer onc1 = Integer.parseInt(o1.getONCNum());
				Integer onc2 = Integer.parseInt(o2.getONCNum());
				return onc1.compareTo(onc2);
			}
			else if(isNumeric(o1.getONCNum()) && !isNumeric(o2.getONCNum()))
				return -1;
			else if(!isNumeric(o1.getONCNum()) && isNumeric(o2.getONCNum()))
				return 1;
			else
				return o1.getONCNum().compareTo(o2.getONCNum());
		}
	}
    private static class ONCWebsiteFamilyLNComparator implements Comparator<ONCWebsiteFamily>
	{
		@Override
		public int compare(ONCWebsiteFamily o1, ONCWebsiteFamily o2)
		{
			return o1.getHOHLastName().toLowerCase().compareTo(o2.getHOHLastName().toLowerCase());
		}
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
    	
    	String getNewFamStatus() { return Integer.toString(famStatus); }
    	String getNewGiftStatus() { return Integer.toString(giftStatus); }
    }
}
