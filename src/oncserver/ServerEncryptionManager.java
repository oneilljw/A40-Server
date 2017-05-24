package oncserver;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.JOptionPane;

import org.apache.commons.codec.binary.Base64;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import au.com.bytecode.opencsv.CSVReader;


public class ServerEncryptionManager
{
	private static final int KEY_LIST_NUM_OF_FIELDS = 1;
	
	private static ServerEncryptionManager instance;
	private static Map<String, String> keyMap;
	
	private static String secretKey = "XMzDdG4D03CKm2IxIWQw7g==";
	
	public static ServerEncryptionManager getInstance()
	{
		if(instance == null)
			instance = new ServerEncryptionManager();
		
		return instance;
	}
	
	private ServerEncryptionManager()
	{
		keyMap = new HashMap<String, String>();
		
		try 
		{
			importKeyMap(String.format("%s/Keys.csv", System.getProperty("user.dir")), "Key List");
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
		
//		Set<String> keyset = keyMap.keySet();
//		for(String key:keyset)
//			System.out.println(String.format("/keyMap key=%s, value=%s", key, keyMap.get(key)));
	}
	
	void importKeyMap(String path, String name) throws FileNotFoundException, IOException
	{
    	CSVReader reader = new CSVReader(new FileReader(path));
    	String[] nextLine, header;  		
    		
    	if((header = reader.readNext()) != null)	//Does file have records? 
    	{
    		//Read the data base years file
    		if(header.length == KEY_LIST_NUM_OF_FIELDS)	//Does the header have the right # of fields? 
    		{
    			int index = 0;
    			while ((nextLine = reader.readNext()) != null)	// nextLine[] is an array of fields from the record
    				keyMap.put(String.format("key%d", index++), nextLine[0]);
    		}
    		else
    		{
    			String error = String.format("%s file corrupted, header lentgth = %d", name, header.length);
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
	
	//create a json of the key map
	static String getKeyMapJson()
	{		
		Gson gson = new Gson();
		Type mapOfKeys = new TypeToken<Map<String, String>>(){}.getType();
					
		return gson.toJson(keyMap, mapOfKeys);
	}
		
	
	//getters
	static String getKey(String key)
	{
		return keyMap.containsKey(key) ? keyMap.get(key) : null;
	}

	public static String encrypt(String text)
	{
        byte[] raw;
        String encryptedString;
        SecretKeySpec skeySpec;
        byte[] encryptText = text.getBytes();
        Cipher cipher;
        try {
        	raw = Base64.decodeBase64(secretKey);
//          raw = Base64.decodeBase64(keyMap.get("key0"));
            skeySpec = new SecretKeySpec(raw, "AES");
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            encryptedString = Base64.encodeBase64String(cipher.doFinal(encryptText));
        } 
        catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
        return encryptedString;
    }

    public static String decrypt(String text)
    {
        Cipher cipher;
        String encryptedString;
        byte[] encryptText = null;
        byte[] raw;
        SecretKeySpec skeySpec;
        try {
        	raw = Base64.decodeBase64(secretKey);
//          raw = Base64.decodeBase64(keyMap.get("key0"));
            skeySpec = new SecretKeySpec(raw, "AES");
            encryptText = Base64.decodeBase64(text);
            cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            encryptedString = new String(cipher.doFinal(encryptText));
        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
        return encryptedString;
    }
}
