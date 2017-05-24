package oncserver;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import ourneighborschild.ONCObject;
import au.com.bytecode.opencsv.CSVWriter;

public abstract class ONCServerDB
{
	protected static final int USER_UNCHANGED = 0;
	protected static final int USER_UPDATED = 1;
	protected static final int USER_ADDED = 2;
//	protected static final int BASE_YEAR = 2012;
	
//	abstract String add(int year, String userjson);
	
//	abstract void createNewYear(int year);	//used to create a new year from the authorized client
/*	
	public <T extends ONCObject> T find(ArrayList<? extends ONCObject> list, int id, Class<T> type)
	{
		int index = 0;
		while(index < list.size() && list.get(index).getID() != id);
			index++;
		
		if(index < list.size())
			return type.cast(list.get(index));
		else 
			return null;		
	}
*/	
	ONCObject find(List<? extends ONCObject> list, int id)
	{
		int index = 0;
		while(index < list.size() && id != list.get(index).getID())
			index++;
		
		if(index == list.size())
			return null;
		else
			return list.get(index);		
	}
/*	
	void importDB(int year, String path, String name, int length) throws FileNotFoundException, IOException
	{
		CSVReader reader = new CSVReader(new FileReader(path));
    	String[] nextLine, header;  		
    		
    	if((header = reader.readNext()) != null)	//Does file have records? 
    	{
    		//Read the User File
    		if(header.length == length)	//Does the record have the right # of fields? 
    		{
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
    				addObject(year, nextLine);
    		}
    		else
    		{
    			String error = String.format("%s file corrupted, header length = %d", name, header.length);
    	       	JOptionPane.showMessageDialog(null, error,  name + "Corrupted", JOptionPane.ERROR_MESSAGE);
    		}		   			
    	}
    	else
    	{
    		String error = String.format("%s file is empty", name);
    		JOptionPane.showMessageDialog(null, error,  name + " Empty", JOptionPane.ERROR_MESSAGE);
    	}
    	
    	reader.close();
	}
*/	
//	abstract void addObject(int year, String[] nextLine);
	
	boolean exportDBToCSV(List<? extends ONCObject> list, String[] header, String path)
    {	
	    try 
	    {
	    	CSVWriter writer = new CSVWriter(new FileWriter(path));
	    	writer.writeNext(header);
	    	 
	    	for(int index=0; index < list.size(); index++)
	    	{
	    		ONCObject oncObj = (ONCObject)list.get(index);
	    		writer.writeNext(oncObj.getExportRow());	//Get family object row
	    	}
	    	
	    	writer.close();
	    	
	    	return true;
	    	       	    
	    } 
	    catch (IOException x)
	    {
	    	System.err.format("IO Exception: %s%n", x);
	    	return false;
	    }
    }
	
//	abstract void save(int year);
	
	/**********************************************************************************
	 * This method searches the list for the highest ID and returns the next highest
	 * integer. It is called when initializing the server and reading the data base
	 * from disk.
	 * @param list
	 * @return
	 *********************************************************************************/
	public int getNextID(List<? extends ONCObject> list)
	{
		int hID = 0;
		for(int i=0; i< list.size(); i++)
			if(list.get(i).getID() > hID)
				hID = list.get(i).getID();
		
		return hID+1;
	}
	
	protected static boolean isNumeric(String str)
	{
		if(str == null || str.isEmpty())
			return false;
		else
			return str.matches("-?\\d+(\\.\\d+)?");  //match a number with optional '-' and decimal.
	}
}
