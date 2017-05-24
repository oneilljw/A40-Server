package oncserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JOptionPane;

import ourneighborschild.Address;
import ourneighborschild.ONCFamily;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public class ApartmentDB 
{
	private static final int APARTMENTDB_HEADER_LENGTH = 9;
	
	private static ApartmentDB instance = null;
	private static List<Address> aptList;
	private static List<Integer> hashTable;
	private static char[] streetLetter = {'?','A','B','C','D','E','F','G','H','I','J','K','L','M',
										 'N','O','P','Q','R','S','T','U','V','W','X','Y','Z'};

	public ApartmentDB() throws FileNotFoundException, IOException
	{
		aptList = new ArrayList<Address>();
		if(aptList.size() == 0)
		{
			readApartmentDBFromFile(System.getProperty("user.dir") +"/PermanentDB/ApartmentDB.csv");
//			Collections.sort(aptList, new AddressStreetNameComparator());	//sort region list by street name
			createHashTable();
		}
	}
	
	public static ApartmentDB getInstance() throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new ApartmentDB();
		
		return instance;
	}
	
	static void createHashTable()
	{
		//build hash index array. Used to quickly search the apartment list. Hash is based on 
		//first character in street name.			
		hashTable = new ArrayList<Integer>();
		int letterIndex = 1;	//used to iterate the apartment list
						
		hashTable.add(0);	//add the first row to the hash, it's the streets that begin with a number
		while(letterIndex < streetLetter.length)		
		{
			//find street that starts with the current street letter
			int index = 0;
			while(index < aptList.size() && aptList.get(index).getStreetName().charAt(0) != streetLetter[letterIndex])
				index++;
			
			if(index < aptList.size())	//first street found
			hashTable.add(index++);
			else	//no street found that starts with that letter, set the hashIndex = to it's size
				hashTable.add(aptList.size());
			
			letterIndex++;
		}
		
		hashTable.add(aptList.size());	//add the last index into the hash table
	}
	
	static boolean isAddressAnApartment(Address checkAddress)
	{
		//determine which part of the apartment list to search based on street name. Street names that start
		//with a digit are first in the apartment list.
		
//		System.out.println(String.format("AptDB.isAddAnApt: #: %s, name: %s", checkAddress.getStreetNum(), checkAddress.getStreetName()));
		
		int searchIndex, endIndex;
//		if(Character.isDigit(checkAddress.getStreetName().charAt(0)))
//		{	
//			searchIndex = 0;
//			endIndex = hashIndex.get(1);
//		}
//		else
//		{
//			int index = Character.toUpperCase(checkAddress.getStreetName().charAt(0)) - 'A' + 1;	//'A'
//			searchIndex = hashIndex.get(index);
//			endIndex = hashIndex.get(index+1);
//		}
		
		//temporary fix until hash issue figured out
		searchIndex = 0;
		endIndex = aptList.size();
		
		while(searchIndex < endIndex && !(aptList.get(searchIndex).getStreetName().equalsIgnoreCase(checkAddress.getStreetName()) &&
										  aptList.get(searchIndex).getStreetNum().equals(checkAddress.getStreetNum())))
			searchIndex++;
				
		//If match not found return false, else return true
//		System.out.println(String.format("AptDB.isAddApt: searchIndex = %d, endIndex = %d", searchIndex, endIndex));
		return searchIndex != endIndex;	
	}
	
	void readApartmentDBFromFile(String path) throws FileNotFoundException, IOException
	{		
    	CSVReader reader = new CSVReader(new FileReader(path));
    	String[] nextLine, header;
    		
    	if((header = reader.readNext()) != null)
    	{
    		//Read the ONC CSV File
    		if(header.length == APARTMENTDB_HEADER_LENGTH)
    		{
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of values from the line
    			{
    				Address address = new Address(nextLine);
    				aptList.add(address);
    			}
    		}
    		else
    			JOptionPane.showMessageDialog(null, "ApartmentDB file corrupted, header length = " + Integer.toString(header.length), 
    						"Invalid ApartmentDB File", JOptionPane.ERROR_MESSAGE);   			
    	}
    	else
			JOptionPane.showMessageDialog(null, "Couldn't read ApartmentDB file, is it empty?: " + path, 
					"Invalid ApartmentDB", JOptionPane.ERROR_MESSAGE);
    	
    	reader.close();
	}
	
	static void addPriorYearApartments(List<ONCFamily> famList)
	{
		//add the prior year addresses that had units that weren't already in the db
		for(ONCFamily f:famList)
		{
			//see if the family was served and address had a unit and  already in the data base
			//if they aren't in the database add them.
			if(f.getDNSCode().isEmpty() && f.getUnitNum().trim().length() > 0)
			{
				//family was served and had a unit number in prior year, so need to check to see if it's in the
				//apartment list already. Convert family address into an Address Object
				Address famAddress = new Address(f.getHouseNum(), f.getStreet(), f.getUnitNum(), f.getCity(), f.getZipCode());
				
				if(!isAddressAnApartment(famAddress))
					aptList.add(famAddress); //its not in the list, we need to add it
			}
			
			Collections.sort(aptList, new AddressStreetNameComparator());	//sort region list by street name
			createHashTable();
		}
		
		save();
	}
	
	static void save()
	{
		String[] header = {"House #", "Suffix", "Direction", "Street Name", "Street Type", 
							"Post Dir","Unit #", "City", "Zip Code"};
		
		String path = System.getProperty("user.dir") + "/PermanentDB/ApartmentDB.csv";
		File oncwritefile = new File(path);
			
		try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
	    	writer.writeNext(header);
	    	
	    	for(Address a: aptList)
	    		writer.writeNext(a.getExportRow());	//Write server Apartment row
	    	
	    	writer.close();
	    } 
	    catch (IOException x)
	    {
	    	System.err.format("IO Exception: %s%n", x);
	    }
	}
	private static class AddressStreetNameComparator implements Comparator<Address>
	{
		@Override
		public int compare(Address a1, Address a2)
		{
			return a1.getStreetName().compareTo(a2.getStreetName());
		}
	}
}
