package oncserver;

import ourneighborschild.ONCServerUser;

public class ONCWebAgent 
{
	/**
	 * This class provides the blueprint for web agent objects in the ONC web site. 
	 */
	private int id;
	private String firstname;
	private String lastname;
	private String name;
	private String org;
	private String title;
	private String email;
	private String phone;

	//Agent from ONCServerUser
	public ONCWebAgent(ONCServerUser su)
	{
		id = su.getID(); 
		firstname = su.getFirstname();
		lastname = su.getLastname();
		name = firstname + " " + lastname;
		org = su.getOrg();
		title = su.getTitle();
		email = su.getEmail();
		phone = su.getPhone();
	}
	
	//getters
	public String getLastname() { return lastname; }
}
