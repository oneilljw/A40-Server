package oncserver;

public enum HTTPCode 
{
	Ok (200),
	Redirect (301),
	Forbidden (403);
	
	private final int code;
	
	HTTPCode(int code)
	{
		this.code = code;
	}
	
	public int code() { return code; }
}
