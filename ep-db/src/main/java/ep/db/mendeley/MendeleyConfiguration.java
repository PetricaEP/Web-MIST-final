package ep.db.mendeley;

public class MendeleyConfiguration {
	
	private static String MENDELEY_API_BASE_URL;
	
	
	public static String getApiBaseUrl(){
		return MENDELEY_API_BASE_URL;
	}
	
	public static void setApiBaseUrl(String url){
		MENDELEY_API_BASE_URL = url;
	}

}
