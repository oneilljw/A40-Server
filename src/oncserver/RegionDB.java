package oncserver;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

import ourneighborschild.Address;
import ourneighborschild.Region;

import com.google.gson.Gson;

import au.com.bytecode.opencsv.CSVReader;

public class RegionDB 
{
	/**
	 * 
	 */
	private static final int ONC_REGION_HEADER_LENGTH = 14;
	
	private static RegionDB instance = null;
	private static ArrayList<Region> regAL = new ArrayList<Region>();
	private static String[] regions = {"?", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", 
										"N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
	private ImageIcon oncIcon;
	
	private static List<Integer> hashIndex;
	
	protected RegionDB(ImageIcon appicon) throws FileNotFoundException, IOException
	{
		oncIcon = appicon;
		if(regAL.size() == 0)
			getONCRegions(System.getProperty("user.dir") +"/regions_2015.csv");
		
		//build hash index array. Used to quickly search the regions list. Hash is based on first character
		//in street name. Prior to building, sort the region list alphabetically by street name , with street
		//names that start with a digit at the top of the list
		Collections.sort(regAL, new RegionStreetNameComparator());	//sort region list by street name
		
		hashIndex = new ArrayList<Integer>();
		int index = 0;	//used to iterate the region list
		int regionIndex = 1;	//used to iterate the regions array
		
		hashIndex.add(0);	//add the first row to the hash: it's the streets that start with a digit
		while(index < regAL.size() && regionIndex < regions.length)		
		{
			if(regAL.get(index).getStreetName().charAt(0) == regions[regionIndex].charAt(0))
			{
				hashIndex.add(index++);
				regionIndex++;
			}
			else
				index++;
		}
		hashIndex.add(regAL.size());	//add the last index into the hash table
	}
	
	public static RegionDB getInstance(ImageIcon appicon) throws FileNotFoundException, IOException
	{
		if(instance == null)
			instance = new RegionDB(appicon);
		
		return instance;
	}
	
	String getRegions()
	{
		if(regions != null)
			return "REGIONS" + "?,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z";
		else
			return "NO_REGIONS";
	}
	
	/********************************************************************************************
	 * An address for region match requires four parts: Street Number, Street Direction, Street Name 
	 * and Street Suffix. This method takes two parameters, street number and name and determines
	 * the four parts. It is possible the street number is embedded in the streetname parameter and
	 * or is present in the streetnum parameter
	 *********************************************************************************************/
	String getRegionMatch(String addressjson)
	{
		Gson gson = new Gson();
		Address searchAddress = gson.fromJson(addressjson, Address.class);
		
//		String[] searchAddress = createSearchAddress(matchAddress.getStreetNum(),
//													  matchAddress.getStreetName(),
//													   matchAddress.getZipCode());
		
		//Must have a valid street number and street name to search
		if(searchAddress.getStreetNum().isEmpty() || searchAddress.getStreetName().isEmpty())
			return "NO_MATCH" + Integer.toString(0);
		else	
			return "MATCH" + Integer.toString(searchForRegionMatch(searchAddress));		
	}
/*	
	int getRegionMatch(Address matchAddress)
	{
		return searchForRegionMatch(matchAddress);
	}
	
	static String[] createSearchAddress(String streetnum, String streetname, String zipcode)
	{
		String[] searchAddress = new String[5];
		String[] step1 = new String[2];
		
		//Break the street name into its five parts using a three step process. If only the street name
		//or street number are empty, take different paths. 
		if(streetname.length() < 2)
			step1 = separateLeadingDigits(streetnum);
		else
			step1 = separateLeadingDigits(streetname);
		
		String[] step2 = separateStreetDirection(step1[1]);
		String[] step3 = separateStreetSuffix(step2[1]);
		
		searchAddress[0] = step1[0];	//Street Number
		searchAddress[1] = step2[0];	//Street Direction
		searchAddress[2] = step3[0];	//Street Name
		searchAddress[3] = step3[1];	//Street Type
		searchAddress[4] = zipcode;		//zip code
		
		if(searchAddress[0].isEmpty())	//Street number may not be contained in street name
		{
			String[] stnum = separateLeadingDigits(streetnum);
			searchAddress[0] = stnum[0];
		}
		
		return searchAddress;
	}
	
	/*********************************************************************************
	 * This method takes a string and separates the leading digits. If there are characters
	 * after the leading digits without a blank space, it will throw those away. It will
	 * return a two element string array with the leading digits as element 1 and the remainder
	 * of the string past the blank space as element 2. If there are no leading digits, element 1
	 ***********************************************************************************/
/*	
	static String[] separateLeadingDigits(String src)
	{
		String[] output = {"",""};
		
		if(!src.isEmpty())
		{	
			StringBuffer buff = new StringBuffer("");
		
			//IF there are leading digits, separate them and if the first character is a digit
			//and a non digit character is found before a black space, throw the non digit characters away.
			int ci = 0;
			if(Character.isDigit(src.charAt(ci)))
			{	
				while(ci < src.length() && Character.isDigit(src.charAt(ci)))	//Get leading digits
					buff.append(src.charAt(ci++));
		
				while(ci < src.length() && src.charAt(ci++) != ' '); //Throw away end of digit string if necessary	
				
			}
	
			output[0] = buff.toString();
			output[1] = src.substring(ci).trim();
		}

		return output;		
	}
*/	
	/*********************************************************************************
	 * This method takes a string and separates the street direction. If the first character
	 * is a N or a S and and the second character is a period or a blank space, a direction
	 * is present. The method will return a two element string array with the direction
	 * character as element 0 and the remainder of the string as element 1. If a street
	 * direction is not present, element 0 will be empty and element 1 will contain the 
	 * original street parameter

	 ***********************************************************************************/
/*	
	static String[] separateStreetDirection(String street)
	{
		String[] output = new String[2];
		
		if(!street.isEmpty() && (street.charAt(0) == 'N' || street.charAt(0) == 'S') &&
			 (street.charAt(1) == '.' || street.charAt(1) == ' '))
		{
			output[0] = Character.toString(street.charAt(0));
			output[1] = street.substring(2);
		}
		else
		{
			output[0] ="";
			output[1] = street;
		}
	
		return output;
	}
*/	
/*	
	static String[] separateStreetSuffix(String streetname)
	{
		StringBuffer buf = new StringBuffer("");
		
		String[] stnameparts = streetname.split(" ");
		
		int index = 0;
		while(index < stnameparts.length-1)
			buf.append(stnameparts[index++] + " ");
		
		String[] output = {buf.toString().trim(), stnameparts[index].trim()};
		return output;
	}
*/	
	static int searchForRegionMatch(Address searchAddress)
	{	
		//determine which part of the region list to search based on street name. Street names that start
		//with a digit are first in the region list. searchAddres[2] is the street name
		int searchIndex, endIndex;
		
		if(searchAddress.getStreetName().isEmpty())
			return 0;	//if no street name, can't find a region.
		else
		{
			if(Character.isDigit(searchAddress.getStreetName().charAt(0)))
			{	
				searchIndex = 0;
				endIndex = hashIndex.get(1);
			}
			else
			{
				int index = Character.toUpperCase(searchAddress.getStreetName().charAt(0)) - 'A' + 1;	//'A'
				searchIndex = hashIndex.get(index);
				endIndex = hashIndex.get(index+1);
			}

			while(searchIndex < endIndex && !regAL.get(searchIndex).isRegionMatch(searchAddress))
				searchIndex++;
		
			//If match not found return region = 0, else return region
			return searchIndex == endIndex ? 0 : getRegionNumber(regAL.get(searchIndex).getRegion());
		}
	}
	
	static boolean isAddressValid(Address chkAddress)
	{
		return searchForRegionMatch(chkAddress) > 0;		
	}
	
	static int getRegionNumber(String r) //Returns 0 if r is null or empty, number corresponding to letter otherwise
	{
		int index = 0;
		if(r != null && !r.isEmpty())
			while (index < regions.length && !r.equals(regions[index]))
				index++;
		
		return index;
	}
	
	static String getRegion(int regnum)
	{
		String region = "?";
		
		if(regnum > 0 && regnum < regions.length)
			region = regions[regnum];
		
		return region;
	}
	
	void getONCRegions(String path) throws FileNotFoundException, IOException
	{		
    	CSVReader reader = new CSVReader(new FileReader(path));
    	String[] nextLine, header;
    		
    	if((header = reader.readNext()) != null)
    	{
    		//Read the ONC CSV File
    		if(header.length == ONC_REGION_HEADER_LENGTH)
    		{
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of values from the line
    				regAL.add(new Region(nextLine));
    		}
    		else
    			JOptionPane.showMessageDialog(null, "Regions file corrupted, header length = " + Integer.toString(header.length), 
    						"Invalid Region File", JOptionPane.ERROR_MESSAGE, oncIcon);   			
    	}
    	else
			JOptionPane.showMessageDialog(null, "Couldn't read file, is it empty?: " + path, 
					"Invalid Region File", JOptionPane.ERROR_MESSAGE, oncIcon);
    	
    	reader.close();
	}
	
	int getNumberOfRegions() { return regions.length; }
	boolean isRegionValid(int region) { return region >=0 && region < regions.length; }
	
	private class RegionStreetNameComparator implements Comparator<Region>
	{
		@Override
		public int compare(Region region1, Region region2)
		{
			String streetname1 = region1.getStreetName();
			String streetname2 = region2.getStreetName();
			
			Integer si1 = Character.getNumericValue(streetname1.charAt(0));	//non digit returns -1
			Integer si2 = Character.getNumericValue(streetname2.charAt(0));
			
			if(si1 != -1  && si2 != -1)
				return si1.compareTo(si2);
			
			else if(si1 != -1 && si2 == -1)
				return -1;
			else if(si1 == -1 && si2 != -1)
				return 1;
			else
				return streetname1.compareTo(streetname2);
		}
	}
}
