package oncserver;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import ourneighborschild.InventoryChange;
import ourneighborschild.InventoryItem;
import ourneighborschild.InventoryRequest;
import ourneighborschild.ONCObject;
import ourneighborschild.UPCFailure;

public class ServerInventoryDB extends ServerPermanentDB
{
	private static final String INVENTORYDB_FILENAME = "InventoryDB.csv";
//	private static final int INVENTORY_DB_RECORD_LENGTH = 6;
//	private static final String API_KEY = "c41c148aae17866d16c9e278968539b3";
	private static final String EANDATA_KEYCODE = "3236448D57742EAB";
//	private static final String UPC_LOOKUP_URL = "http://api.upcdatabase.org/json/%s/%s";
	private static final String EANDATA_URL = "http://eandata.com/feed/?v=3&keycode=%s&comp=no"
											+"&search=%s&mode=json&find=%s&get=product";
	private static final String EANDATA_SEARCH = "deep";
	private static final String EANDATA_SUCCESS_CODE = "200";
	
	private static ServerInventoryDB instance = null;
	private static List<InventoryItem> invList;
	private static List<Integer> hashTable;

	ClientManager clientMgr;

	public ServerInventoryDB() throws FileNotFoundException, IOException
	{
		clientMgr = ClientManager.getInstance();
		
		invList = new ArrayList<InventoryItem>();
		importDB(String.format("%s/PermanentDB/%s", System.getProperty("user.dir"), INVENTORYDB_FILENAME), "Inventory DB", getExportHeader().length);
		nextID = getNextID(invList);
		bSaveRequired = false;
		
		if(invList.size() == 0)
		{
//			createHashTable();	
		}
	}
	
	public static ServerInventoryDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ServerInventoryDB();
		
		return instance;
	}
	
	static void createHashTable()
	{
		//build hash index array. Used to quickly search the gift inventory. Hash is based on
		//first two characters in inventory item number (bar code). The range is 10 to 99		
		hashTable = new ArrayList<Integer>();
		
		//initialize the hash table, which has a size of 90 elements since the first two digits
		//of a bar code range from 10 to 99
		for(int i=0; i<90; i++)
			hashTable.add(0);
		
		//populate the hash table
		int hashCode = 10;	//used to iterate the inventory list, 10 is first legal hash code
		int currIndex = 0;
		while(hashCode < 100)		
		{
			//find the first code that starts with the hash code
			int index = currIndex;
			while(index < invList.size() && Integer.parseInt(invList.get(index).getNumber().substring(0,2)) != hashCode)
				index++;
			
			if(index < invList.size())	//first item that starts with hash code found
				hashTable.add(index++);
			else if(hashCode > 10)	//no item found that starts with the hash code, set the hashTable entry to
				hashTable.add(hashTable.get(hashCode-1));
			else
				hashTable.add(invList.size());
			
			hashCode++;
			currIndex = 0;
		}
	}
	
	InventoryItem find(String barcode)
	{
		int hashIndex = Integer.parseInt(barcode.substring(0,2))-10;
		int startIndex = hashTable.get(hashIndex);
		int endIndex = startIndex == hashTable.size() ? hashTable.size() -1 : hashTable.get(hashIndex+1);
		
		while(startIndex < endIndex && !invList.get(startIndex).getNumber().equals(barcode))
			startIndex++;
		
		return startIndex < endIndex ? invList.get(startIndex) : null;
	}
	
	//Get a json of the list of inventory in the data base 
	String getInventory()
	{
		Gson gson = new Gson();
		Type listtype = new TypeToken<List<InventoryItem>>(){}.getType();
				
		String response = gson.toJson(invList, listtype);
		return response;	
	}
	
	@Override
	void addObject(String[] nextLine) 
	{
		invList.add(new InventoryItem(nextLine));
	}
	
	String addFromBarcodeScan(String json)
	{
		//its a bar code add request using InventoryRequest object
		Gson gson = new Gson();
		InventoryRequest addReq = gson.fromJson(json,  InventoryRequest.class);
		
		//determine if the bar code is present in the data base. If it is, 
		//increment the count. Eliminate leading zeros from the bar code since we
		//don't store leading zeros and the UPC Database web site doesn't use leading
		//zeros in the submitted URL
		String searchCode = addReq.getBarcode().replaceFirst("^0+(?!$)", "");
		int index = 0;
		while(index < invList.size() && !invList.get(index).getNumber().equals(searchCode))
			index++;
		
		if(index < invList.size())	//bar code is already in inventory, update the count
		{
			//ensure the item isn't already at a zero count. If it is, return a failure
			if(addReq.getCount() + invList.get(index).getCount() < 0)
			{
				UPCFailure upcFailure = new UPCFailure("false", "Count cannot be less then 0");
				return "UPC_LOOKUP_FAILED" + gson.toJson(upcFailure, UPCFailure.class);
			}
			else
			{
				int updatedCount = invList.get(index).incrementCount(addReq.getCount());
				int updatedCommits = invList.get(index).incrementCommits(addReq.getCommits());
				InventoryChange incItem = new InventoryChange(invList.get(index).getID(), updatedCount, updatedCommits);
				bSaveRequired = true;
				return "INCREMENTED_INVENTORY_ITEM" + gson.toJson(incItem, InventoryChange.class);
			}
		}
		else	//bar code is not in inventory, attempt to get it from an external data base
		{
			String upcDBItemJson = getUPCDataJson(addReq.getBarcode());
			if(upcDBItemJson == null)
			{
				UPCFailure upcFailure = new UPCFailure("false", "UPC Website did not respond");
				return "UPC_LOOKUP_FAILED" + gson.toJson(upcFailure, UPCFailure.class);
			}
			else
			{
				//check the return code from the external database
				EANData upcDBItem = gson.fromJson(upcDBItemJson, EANData.class);
				
				if(upcDBItem.getStatus().getCode().equals(EANDATA_SUCCESS_CODE))
				{
					//found the item, create a new InventoryItem, set it's ID, add it to the
					//inventory data base, mark the DB as changed and return the added item
					//and return it. Eliminate any leading zeros in the bar code number prior
					//to storing it in the data base
					InventoryItem addedItem = new InventoryItem(nextID, 
							upcDBItem.getStatus().getFind().replaceFirst("^0+(?!$)", ""),
							upcDBItem.getProduct().getAttributes().getProduct());
					
					invList.add(addedItem);
					nextID++;
					bSaveRequired = true;
					
					return "ADDED_INVENTORY_ITEM" + gson.toJson(addedItem, InventoryItem.class);
				}
				else
				{
					//return the message in a UPCFailre object
					EANStatus status = upcDBItem.getStatus();
					String failureMssg = String.format("%s, code %s", status.getMessage(), status.getCode());
					UPCFailure upcFailure = new UPCFailure("false", failureMssg);
					return "UPC_LOOKUP_FAILED" + gson.toJson(upcFailure, UPCFailure.class);
				}	
			}
		}
	}
	
	@Override
	String add(String json)
	{
		//it's a manual add request using InventoryItem object. Add it to the db and
		//return it.
		Gson gson = new Gson();
		InventoryItem addItemReq = gson.fromJson(json,  InventoryItem.class);
		
		addItemReq.setID(nextID++);
		invList.add(addItemReq);
		bSaveRequired = true;
			
		return "ADDED_INVENTORY_ITEM" + gson.toJson(addItemReq, InventoryItem.class);
	}
	
	String update(String json)
	{
		//go to the external UPC database and attempt to get the item.
		Gson gson = new Gson();
		InventoryItem updateItemReq = gson.fromJson(json, InventoryItem.class);
		
		//find the item
		int index = 0;
		while(index < invList.size() && invList.get(index).getID() != updateItemReq.getID())
			index++;
		
		if(index < invList.size())
		{
			invList.set(index, updateItemReq);
			bSaveRequired = true;
			return "UPDATED_INVENTORY_ITEM" + gson.toJson(updateItemReq, InventoryItem.class);
		}
		else
			return "UPDATE_INVENTORY_FAILED";	
	}
	
	String delete(String json)
	{
		//delete the item from the database. Check to insure the current inventory count is zero
		Gson gson = new Gson();
		InventoryItem delItemReq = gson.fromJson(json, InventoryItem.class);
		delItemReq.setNumber(delItemReq.getNumber().replaceFirst("^0+(?!$)", ""));
		
		//find the item and if found, ensure quantity is zero
		int index = 0;
		while(index < invList.size() && !invList.get(index).getNumber().equals(delItemReq.getNumber()))
			index++;
		
		if(index < invList.size() && invList.get(index).getCount() == 0)
		{
			invList.remove(index);
			bSaveRequired = true;
			return "DELETED_INVENTORY_ITEM" + gson.toJson(delItemReq, InventoryItem.class);
		}
		else
			return "DELETE_INVENTORY_FAILED";
	}
/*
	@Override
	void save()
	{
		if(bSaveRequired)
		{
			String[] header = {"ID", "Count", "Commits", "Number", "Item", "Wish ID", "Alias", "Description",
							"Avg. Price", "Rate Up", "Rate Down"};
		
			String path = System.getProperty("user.dir") + "/InventoryDB.csv";
			File oncwritefile = new File(path);
			
			try 
			{
				CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
				writer.writeNext(header);
	    	
				for(InventoryItem i: invList)
					writer.writeNext(i.getExportRow());	//Write server Apartment row
	    	
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
		return new String[] {"ID", "Count", "Commits", "Number", "Item", "Wish ID"};
	}
		
	@Override
	String getFileName() { return INVENTORYDB_FILENAME; }
	
	@Override
	List<? extends ONCObject> getONCObjectList() { return invList; }
	
	/*****
	 * caller
	 * @param barcode - String representing the bar code number
	 * @return - String that contains the JSON response from UPC Database
	 */
	String getUPCDataJson(String barcode)
	{
		StringBuffer responseJson = new StringBuffer(1024);
		 
		//Turn the string into a valid URL
	    URL modAPIUrl = null;
		try
		{
//			String stringURL = String.format(UPC_LOOKUP_URL, API_KEY, barcode);
			String stringURL = String.format(EANDATA_URL, EANDATA_KEYCODE, EANDATA_SEARCH, barcode);
			modAPIUrl = new URL(stringURL);
		} 
		catch (MalformedURLException e2)
		{
			JOptionPane.showMessageDialog(null, "Can't form UPC Database API URL",
					"UPC Database API Issue", JOptionPane.ERROR_MESSAGE);
			
			return null;
		}
		
		//Attempt to open the URL via a network connection to the Internet
	    HttpURLConnection httpconn = null;
		try
		{
			httpconn = (HttpURLConnection)modAPIUrl.openConnection();
		} 
		catch (IOException e1)
		{
			JOptionPane.showMessageDialog(null, "Can't access UPC Database API",
					"UPC Database Access Issue", JOptionPane.ERROR_MESSAGE);
			
			return null;
		}
		
		//It opened successfully, get the data via HTTP GET
	    try
	    {
	    	if (httpconn.getResponseCode() == HttpURLConnection.HTTP_OK)
	    	{
	    		BufferedReader input = new BufferedReader(new InputStreamReader(httpconn.getInputStream()), 1024);
	    		String strLine = null;
	    		while ((strLine = input.readLine()) != null)
				    responseJson.append(strLine);
	    			
	    		input.close();
	    		httpconn.disconnect();
			}
	    }
		catch (IOException e1)
		{
			 JOptionPane.showMessageDialog(null, "Can't get UPC Database Item",
						"UPC Database Issue", JOptionPane.ERROR_MESSAGE);
		}
	    
	    //return the UPCDatabaseItem Json
	    return responseJson.toString();
	}
	
	private class EANData
	{
		private EANStatus status;
		private EANProduct product;
		
		//getters
		EANStatus getStatus() { return status; }
		EANProduct getProduct() { return product; }
		
		@Override
		public String toString() { return status.toString() + " " + product.toString();}
	}
	
	private class EANStatus
	{
		private String version;
		private String code;
		private String message;
		private String find;
		private String run;
		
		//getters
//		String getVersion() { return version; }
		String getCode() { return code; }
		String getMessage() { return message; }
		String getFind() { return find; }
//		String getRun() { return run; }
		
		public String toString() {return "version:" + version + ", code:" + code
									+ ", message:" + message + ", find:" + find
									+", run: " + run;}
	}
	
	private class EANProduct
	{
		EANAttributes attributes;
		String locked;
		String modified;
		
		//getters
		EANAttributes getAttributes() { return attributes; }
//		String getLocked() { return locked; }
//		String getModified() { return modified; }
		
		public String toString() {return attributes.toString() + ", locked:" + locked + ", modified:" + modified;}
	}
	
	private class EANAttributes
	{
		private String product;
		
		//getters
		String getProduct() { return product; }
		
		@Override
		public String toString() {return "product:" + product;}
	}
}
