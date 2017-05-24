package oncserver;

public class PriorYearPartnerPerformance
{
	private int childID;
	private int wishNumber;
	private int wishAssigneeID;
	private int wishDeliveredID;
//	private Calendar wishAssigneeTimeStamp;
	private int wishReceivedBeforeDeadlineID;
	private int wishReceivedAfterDeadlineID;
//	private Calendar wishReceivedTimeStamp;
	
	PriorYearPartnerPerformance(int id, int wn, int assignedID, int deliveredID,  int receivedBeforeID, int receivedAfterID)
	{
		this.childID = id;
		this.wishNumber = wn;
		this.wishAssigneeID = assignedID;
		this.wishDeliveredID = deliveredID;
		
//		this.wishAssigneeTimeStamp = Calendar.getInstance();
//		this.wishAssigneeTimeStamp.setTime(assigneeTS);
		
		this.wishReceivedBeforeDeadlineID = receivedBeforeID;
		this.wishReceivedAfterDeadlineID = receivedAfterID;
		
//		this.wishReceivedTimeStamp = Calendar.getInstance();
//		this.wishReceivedTimeStamp.setTime(receivedTS);
		
	}
	
	//getters
	int getPYChildID() { return childID; }
	int getPYPWishNumber() { return wishNumber;}
	
	int getPYPartnerWishAssigneeID() { return wishAssigneeID;}
	int getPYPartnerWishDeliveredID() { return wishDeliveredID;}
	int getPYPartnerWishReceivedBeforeDeadlineID() { return wishReceivedBeforeDeadlineID;}
	int getPYPartnerWishReceivedAfterDeadlineID() { return wishReceivedAfterDeadlineID;}
//	Calendar getPYPartnerAssigneeTimeStamp() { return wishAssigneeTimeStamp; }
//	Calendar getPYPartnerReceivedTimeStamp() { return wishReceivedTimeStamp; }
}
