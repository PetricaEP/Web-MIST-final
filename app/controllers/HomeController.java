package controllers;

import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import play.Logger;
import play.data.Form;
import play.data.FormFactory;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.routing.JavaScriptReverseRouter;
import services.search.DocumentSearcher;
import views.formdata.QueryData;
import views.formdata.SelectionData;
import views.html.Index;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {

	/**
	 * IndexSearcher for documents in database
	 */
	private final DocumentSearcher docSearcher;

	/**
	 * Form factory
	 */
	private final FormFactory formFactory;

	@Inject
	public HomeController(@Named("docSearcher") DocumentSearcher docSearcher, FormFactory formFactory) {
		this.docSearcher = docSearcher;
		this.formFactory = formFactory;
	}

	/**
	 * An action that renders an HTML page with a welcome message.
	 * The configuration in the <code>routes</code> file means that
	 * this method will be called when the application receives a
	 * <code>GET</code> request with a path of <code>/</code>.
	 */
	public Result index() {
		Form<QueryData> queryData = formFactory.form(QueryData.class);	
		return ok(Index.render(queryData));
	}

	/**
	 * Searches for user request term. 
	 * @param term the search term
	 * @return Play result as Json
	 */
	public Result search(){

		Form<QueryData> queryData = formFactory.form(QueryData.class).bindFromRequest();
		if ( queryData.hasErrors() ){
			return ok();
		}

		String jsonResult;
		try {
			jsonResult = docSearcher.search(queryData.get(), false, -1);
		} catch (Exception e) {
			Logger.error("Can't search for documents. Query: " + queryData.toString(), e);
			return internalServerError("Can't search for documents");
		}
		return ok(jsonResult).as("application/json");
	}

	/**
	 * Zoom to user selected area 
	 * @param term the search term
	 * @return Play result as Json
	 */
	public Result zoom(){

		Form<SelectionData> selectionData = formFactory.form(SelectionData.class).bindFromRequest();

		if ( selectionData.hasErrors() ){
			return ok();
		}

		String jsonResult;
		try {
			jsonResult = docSearcher.zoom(selectionData.get());
		} catch (Exception e) {
			Logger.error("Can't search for documents. Query: " + selectionData.toString(), e);
			return internalServerError("Can't search for documents");
		}
		return ok(jsonResult).as("application/json");
	}

	@BodyParser.Of(BodyParser.Json.class)
	public Result references(){
		JsonNode json = request().body().asJson();
		String jsonResult = "";
		
		if ( json.size() > 0){
			Iterator<JsonNode> iter = json.elements();
			long[] docIds = new long[json.size()];
			int i = 0;
			while(iter.hasNext()){
				docIds[i++] = iter.next().asLong();
			}
			
			try {
				jsonResult = docSearcher.getDocumentsReferences(docIds);
			} catch (Exception e) {
				Logger.error("Can't search for documents. Query: " + json.toString(), e);
				return internalServerError("Can't search for documents");
			}
		}
		return ok(jsonResult).as("application/json");

	}

	/**
	 * Create javascript routes for this controller
	 * @return javascrit route
	 */
	public Result javascriptRoutes(){
		return ok(
				JavaScriptReverseRouter.create("jsRoutes",
						routes.javascript.HomeController.search(),
						routes.javascript.HomeController.zoom(),
						routes.javascript.HomeController.references(),
						routes.javascript.GraphController.getGraph()
						)).as("text/javascript");
	}
}
