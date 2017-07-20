package controllers;

import javax.inject.Inject;
import javax.inject.Named;

import play.mvc.Controller;
import play.mvc.Result;
import services.search.DocumentSearcher;
import views.html.Graph;

public class GraphController extends Controller {
	
	private static final String DOCS_GRAPH = "docs";
	private static final String AUTS_GRAPH = "authors";
	
	/**
	 * IndexSearcher for documents in database
	 */
	private final DocumentSearcher docSearcher;

	@Inject
	public GraphController(@Named("docSearcher") DocumentSearcher docSearcher) {
		this.docSearcher = docSearcher;
	}

	public Result graph(String type){
		return ok(Graph.render(type));
	}
	
	public Result getGraph(String type){
		if ( DOCS_GRAPH.equals(type))
			return docsGraph();
		else if (AUTS_GRAPH.equals(type))
			return autGraph();
		return badRequest("Unkown type").as("application/json");
	}

	public Result docsGraph(){
		String json = docSearcher.getDocumentsGraph();
		return ok(json).as("application/json");
	}

	public Result autGraph(){
		String json = docSearcher.getAuthorsGraph();
		return ok(json).as("application/json");
	}
}
