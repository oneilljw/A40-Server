package oncserver;

import ourneighborschild.ONCObject;

/*******
 * Class is used when importing families from Britepaths. Provides for determining whether
 * an agent already exists, exists but info is updated, or the referral was from a new Agent
 * that was added to the data base.
 * @author John O'Neill
 */
public class ImportONCObjectResponse 
{
	private int result;
	private ONCObject obj;
	private String jsonResponse;
	
	ImportONCObjectResponse(int result, ONCObject obj, String jsonResponse)
	{
		this.result = result;
		this.obj = obj;
		this.jsonResponse = jsonResponse;
	}
	
	//getters
	int getImportResult() { return result; }
	int getONCObjectID(){ return obj.getID(); }
	String getJsonResponse() { return jsonResponse; }	
}
