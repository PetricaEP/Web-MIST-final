package ep.db.utils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.grobid.core.data.BiblioItem;

import ep.db.mendeley.AuthTokenManager;
import ep.db.mendeley.ClientCredentials;
import ep.db.mendeley.InMemoryAuthTokenManager;
import ep.db.mendeley.MendeleyConfiguration;
import ep.db.mendeley.OAuthTokenEndpoint.AccessTokenWithClientCredentialsRequest;
import ep.db.model.Document;
import net.arnx.jsonic.JSON;

/**
 * Class for managing the extraction of bibliographical informations from pdf documents.
 * Adapted from GROBID projejct by JAS.
 * 
 * @author Patrice Lopez
 * 
 */
public class Consolidation {

	private static final String MENDELEY_DOI_BASE_QUERY = "catalog?doi=%s&view=bib";
	private static final String MENDELEY_TITLE_AUTHOR_BASE_QUERY = 
			"search/catalog?title=%s&author=%s&limit=1&view=bib";
	private static final String MENDELEY_JOURNAL_TITLE_YEAR_BASE_QUERY = 
			"search/catalog?title=%s&source=%s&min_year=%s&max_year=%s&limit=1&view=bib";
	
	private final AccessTokenWithClientCredentialsRequest oAuthTokeRequest;

	private final AuthTokenManager authTokenManager;
	
	public int counter = 0;
	
	public Consolidation( Configuration config ) {
		MendeleyConfiguration.setApiBaseUrl(config.getMendeleyHost());
		ClientCredentials credentials = new ClientCredentials(
				config.getMendeleyClientId(), config.getMendeleyClientSecret());
		
		authTokenManager = new InMemoryAuthTokenManager();
		oAuthTokeRequest = new AccessTokenWithClientCredentialsRequest(authTokenManager, credentials);
	}

	/**
	 * Try to consolidate some uncertain bibliographical data with mendeley web service based on
	 * core metadata
	 */
	public boolean consolidate(Document doc) throws Exception {
		boolean valid = false;

		String doi = doc.getDOI();

		BiblioItem result = null;

		if ( StringUtils.isNotBlank(doi))
			result = consolidateMendeleyGetByDOI(doi);
		
		if ( result != null ){
			++counter;
			if ( doc.getDOI() == null && result.getDOI() != null)
				doc.setDOI(result.getDOI());
			if (doc.getAbstract() == null && result.getAbstract() != null)
				doc.setAbstract(result.getAbstract());
			if (doc.getContainer() == null && result.getJournal() != null)
				doc.setContainer(result.getJournal());
			if (doc.getISSN() == null && result.getISSN() != null)
				doc.setISSN(result.getISSN());
			if (doc.getIssue() == null && result.getIssue() != null)
				doc.setIssue(result.getIssue());
			if (doc.getKeywords() == null && result.getKeywords() != null){
				String keywords = result.getKeywords().stream().map(s -> s.getKeyword().toString())
						.collect(Collectors.joining(", "));
				doc.setKeywords(keywords);
			}
			if (doc.getAuthors() == null && result.getAuthors() != null){
				 doc.setAuthors(Utils.getAuthors(result.getAuthors()));
			}
			if (doc.getPages() == null && result.getPageRange() != null)
				doc.setPages(result.getPageRange());
			if (doc.getPublicationDate() == null && result.getYear() != null)
				doc.setPublicationDate(result.getYear());
			if (doc.getTitle() == null && result.getTitle() != null)
				doc.setTitle(result.getTitle());
			if (doc.getVolume() == null && result.getVolume() != null)
				doc.setVolume(result.getVolume());
		}
		
		return valid;
	}

	private BiblioItem consolidateMendeleyGetByJournalTitleYear(String title, String journalTitle, String pubYear) throws Exception {
		String xml = null;
		BiblioItem bib = null;
				
		if (xml == null) {
			String token = null;
			if ( authTokenManager.tokenHasExpired() )
				oAuthTokeRequest.doRun();
				
			token = authTokenManager.getAccessToken();
			
			if (token.isEmpty())
				return null;

			
			String subpath = String.format(MENDELEY_JOURNAL_TITLE_YEAR_BASE_QUERY,  
					URLEncoder.encode(title, "UTF-8"), 
					URLEncoder.encode(journalTitle, "UTF-8"), pubYear, pubYear);
			
			URL url = new URL("https://" + MendeleyConfiguration.getApiBaseUrl() + "/" + subpath);

			System.out.println("Sending: " + url.toString());
			HttpURLConnection urlConn = null;
			try {
				urlConn = (HttpURLConnection) url.openConnection();
			} 
			catch (Exception e) {
				try {
					urlConn = (HttpURLConnection) url.openConnection();
				} catch (Exception e2) {
					urlConn = null;
					throw new Exception("An exception occured while updating bibliography information.", e2);
				}
			}

			if (urlConn != null) {
				try {
					urlConn.setDoOutput(true);
					urlConn.setDoInput(true);
					urlConn.setRequestMethod("GET");

					urlConn.setRequestProperty("Authorization", String.format("Bearer %s", token));
					urlConn.setRequestProperty("Accept", "application/vnd.mendeley-document.1+json");
					urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

					InputStream in = urlConn.getInputStream();
//					xml = TextUtilities.convertStreamToString(in);

					Object jsonObject = JSON.decode(in);
					MendeleyJsonParser parser = new MendeleyJsonParser();
					List<BiblioItem> results = parser.parse(jsonObject);

					if( results != null && !results.isEmpty())
						bib = results.get(0);

					in.close();
					urlConn.disconnect();

				} catch (Exception e) {
					System.err.println("Warning: Consolidation set true, " +
							"but the online connection to Crossref fails.");
				}
			}
		}
		return bib;
	}

	

	private BiblioItem consolidateMendeleyGetByAuthorTitle(String aut, String title) throws Exception {
		String xml = null;
		BiblioItem bib = null;
				
		if (xml == null) {
			String token = null;
			if ( authTokenManager.tokenHasExpired() )
				oAuthTokeRequest.doRun();
				
			token = authTokenManager.getAccessToken();
			if (token.isEmpty())
				return null;

			String subpath = String.format(MENDELEY_TITLE_AUTHOR_BASE_QUERY,  
					URLEncoder.encode(title, "UTF-8"), 
					URLEncoder.encode(aut, "UTF-8"));
			
			URL url = new URL("https://" + MendeleyConfiguration.getApiBaseUrl() + "/" + subpath);

			System.out.println("Sending: " + url.toString());
			HttpURLConnection urlConn = null;
			try {
				urlConn = (HttpURLConnection) url.openConnection();
			} 
			catch (Exception e) {
				try {
					urlConn = (HttpURLConnection) url.openConnection();
				} catch (Exception e2) {
					urlConn = null;
					throw new Exception("An exception occured while updating bibliography information.", e2);
				}
			}

			if (urlConn != null) {
				try {
					urlConn.setDoOutput(true);
					urlConn.setDoInput(true);
					urlConn.setRequestMethod("GET");

					urlConn.setRequestProperty("Authorization", String.format("Bearer %s", token));
					urlConn.setRequestProperty("Accept", "application/vnd.mendeley-document.1+json");
					urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

					InputStream in = urlConn.getInputStream();
//					xml = TextUtilities.convertStreamToString(in);

					Object jsonObject = JSON.decode(in);
					MendeleyJsonParser parser = new MendeleyJsonParser();
					List<BiblioItem> results = parser.parse(jsonObject);

					if( results != null && !results.isEmpty())
						bib = results.get(0);
					
					in.close();
					urlConn.disconnect();

				} catch (Exception e) {
					System.err.println("Warning: Consolidation set true, " +
							"but the online connection to Crossref fails.");
				}
			}
		}
		return bib;
	}
	

	private BiblioItem consolidateMendeleyGetByDOI(String doi) throws Exception {
		
		BiblioItem bib = null;
		
		// some cleaning of the doi
		if (doi.startsWith("doi:") | doi.startsWith("DOI:")) {
			doi.substring(4, doi.length());
			doi = doi.trim();
		}

		doi = doi.replace(" ", "");
		String xml = null;

		if (xml == null) {
			String token = null;
			if ( authTokenManager.tokenHasExpired() )
				oAuthTokeRequest.doRun();
				
			token = authTokenManager.getAccessToken();
			if (token.isEmpty())
				return null;

			String subpath = String.format(MENDELEY_DOI_BASE_QUERY, doi);
			
			URL url = new URL("https://" + MendeleyConfiguration.getApiBaseUrl() + "/" + subpath);

			System.out.println("Sending: " + url.toString());
			HttpURLConnection urlConn = null;
			try {
				urlConn = (HttpURLConnection) url.openConnection();
			} 
			catch (Exception e) {
				try {
					urlConn = (HttpURLConnection) url.openConnection();
				} catch (Exception e2) {
					urlConn = null;
					throw new Exception("An exception occured while updating bibliography information.", e2);
				}
			}

			if (urlConn != null) {
				try {
					urlConn.setDoOutput(true);
					urlConn.setDoInput(true);
					urlConn.setRequestMethod("GET");

					urlConn.setRequestProperty("Authorization", String.format("Bearer %s", token));
					urlConn.setRequestProperty("Accept", "application/vnd.mendeley-document.1+json");
					urlConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

					InputStream in = urlConn.getInputStream();
										
//					xml = TextUtilities.convertStreamToString(in);
					
					Object jsonObject = JSON.decode(in);
					MendeleyJsonParser parser = new MendeleyJsonParser();
					List<BiblioItem> result = parser.parse(jsonObject);

					if ( result != null && !result.isEmpty())
						bib = result.get(0);
					
					in.close();
					urlConn.disconnect();

					
				} catch (Exception e) {
					System.err.println("Warning: Consolidation set true, " +
							"but the online connection to Mendeley fails.");
				}
			}
		}
		return bib;
	}
}
