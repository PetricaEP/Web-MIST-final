package services.search;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletionStage;

import views.formdata.QueryData;
import views.formdata.SelectionData;

public interface DocumentSearcher {

	public CompletionStage<String> search(QueryData queryData) throws Exception;
	
	public CompletionStage<String> search(QueryData queryData, int count) throws Exception;
	
	public CompletionStage<String> search(QueryData queryData, boolean fetchNumberOfCitations) throws Exception;
	
	public CompletionStage<String> search(QueryData queryData, boolean fetchNumberOfCitations, int count) throws Exception;
	
	public String zoom(SelectionData selectionData);
	
	public String getDocumentsReferences(List<Long> docIds);

	public String getAuthorsGraph();

	public String getDocumentsGraph();

	public File downloadDocuments(List<Long> docIds);
	
}
