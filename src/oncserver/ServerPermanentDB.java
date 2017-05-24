package oncserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import javax.swing.JOptionPane;

import ourneighborschild.ONCObject;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

public abstract class ServerPermanentDB extends ONCServerDB
{
	protected int nextID;
	protected boolean bSaveRequired;
	
	abstract String add(String userjson);
	
	void importDB(String path, String name, int length) throws FileNotFoundException, IOException
	{
    	CSVReader reader = new CSVReader(new FileReader(path));
    	String[] nextLine, header;
    	
    	if((header = reader.readNext()) != null)	//Does file have records? 
    	{
    		//Read the User File
    		if(header.length == length)	//Does the record have the right # of fields? 
    		{
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
    				addObject(nextLine);
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
	
	abstract void addObject(String[] nextLine);
	
	void save()
	{
		if(bSaveRequired)
		{
			String path = String.format("%s/PermanentDB/%s",System.getProperty("user.dir"), getFileName());
			File oncwritefile = new File(path);
		
			try 
			{
				CSVWriter writer = new CSVWriter(new FileWriter(oncwritefile.getAbsoluteFile()));
				writer.writeNext(getExportHeader());
	    	
				for(ONCObject oncObj : getONCObjectList())
					writer.writeNext(oncObj.getExportRow());
	    	
				writer.close();
				
				bSaveRequired = false;
			} 
			catch (IOException x)
			{
				System.err.format("IO Exception: %s%n", x.getMessage());
			}
		}
	}
	
	abstract String[] getExportHeader();
	abstract String getFileName();
	abstract List<? extends ONCObject> getONCObjectList();
}
