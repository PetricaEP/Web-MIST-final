package services.search;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletionStage;

import views.formdata.QueryData;

public interface DocumentSearcher {

	public CompletionStage<String> search(QueryData queryData) throws Exception;	
	
	public String getDocumentsReferences(List<Long> docIds);

	public String getAuthorsGraph();

	public String getDocumentsGraph();

	public File downloadDocuments(List<Long> docIds);
	
}
