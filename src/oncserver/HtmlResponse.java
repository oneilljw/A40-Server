package oncserver;

public class HtmlResponse 
{
	private String htmlResponse;
	private HTTPCode httpCode;
	
	HtmlResponse(String response, HTTPCode code)
	{
		this.htmlResponse = response;
		this.httpCode = code;
	}
	
	String getResponse() { return htmlResponse; }
	HTTPCode getResponseCode() { return httpCode; }
	int getCode() { return httpCode.code(); }
	
	void append(String html_add) { htmlResponse += html_add; }
}
