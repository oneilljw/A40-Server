package oncserver;

import java.util.List;

import ourneighborschild.ONCObject;

public abstract class ServerDBYear 
{
	private int nextID;
	private boolean bUnsavedChanges;
	
	public ServerDBYear()
	{
		nextID = 0;
		bUnsavedChanges = false;
	}
	
	int find(List<? extends ONCObject> list, int ID)
	{
		int index = 0;
		while(index < list.size() && list.get(index).getID() != ID)
			index++;
		
		if(index == list.size())
			return -1;
		else
			return index;
	}
	
	//getters
	int getNextID()
	{ 
		int nID = nextID++;
		return nID;
	}
	
	abstract List<? extends ONCObject> getList();
	
	//data base changed functions
	boolean isUnsaved() { return bUnsavedChanges; }
	synchronized void setChanged(boolean tf) { bUnsavedChanges = tf; }

	//setters
	void setNextID(int nid) { nextID = nid; }
}
