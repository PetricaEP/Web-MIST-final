package services.search;

import static java.util.concurrent.CompletableFuture.supplyAsync;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.uci.ics.jung.graph.Graph;
import ep.db.database.DatabaseService;
import ep.db.grid.EpanechnikovKernel;
import ep.db.grid.Grid;
import ep.db.matrix.DenseMatrix;
import ep.db.matrix.DenseVector;
import ep.db.matrix.Matrix;
import ep.db.mdp.clustering.BKMeans;
import ep.db.mdp.dissimilarity.Dissimilarity;
import ep.db.mdp.dissimilarity.DissimilarityFactory;
import ep.db.mdp.dissimilarity.DissimilarityType;
import ep.db.model.Author;
import ep.db.model.Document;
import ep.db.model.IDocument;
import ep.db.pagerank.Edge;
import ep.db.quadtree.Bounds;
import ep.db.quadtree.QuadTree;
import ep.db.quadtree.QuadTreeNode;
import ep.db.quadtree.Vec2;
import ep.db.utils.Configuration;
import ep.db.utils.Utils;
import play.Logger;
import play.db.Database;
import play.libs.Json;
import services.database.DatabaseExecutionContext;
import services.database.PlayDatabaseWrapper;
import views.formdata.QueryData;

public class LocalDocumentSearcher implements DocumentSearcher {

	private final Logger.ALogger timeLogger = Logger.of("timing");

	private static final Pattern TERM_PATTERN = Pattern.compile(".*(\".*?\").*");

	private static final String TEMP_DIRECTORY = System.getProperty("java.io.tmpdir");

	private static Configuration configuration;

	private QuadTree quadTree;

	private DatabaseService dbService;

	private DatabaseExecutionContext executionContext;


	@Inject
	public LocalDocumentSearcher(Database db, DatabaseExecutionContext context) {
		this.dbService = new DatabaseService(new PlayDatabaseWrapper(db));
		this.executionContext = context;
		configuration = Configuration.getInstance();
		initQuadTree();
		initGrid();
	}
	
	@Override
	public CompletionStage<String> search(QueryData queryData) throws Exception {		
		return supplyAsync(() -> {
			try {				
				return searchData(queryData);
			} catch (Exception e) {
				Logger.error("Can't search for data", e);
				return null;
			}
		}, executionContext);
	}

	public String searchData(QueryData queryData) throws Exception {

		long start, elapsed;
		ObjectNode result = null;

		try {
			if ( queryData.getStart() == null || queryData.getEnd() == null){
				result = searchAll(queryData);
			}
			else{
				result = zoom(queryData);
			}
		} catch (Exception e) {
			Logger.error("Unkown error!", e);
			result = Json.newObject();
			result.set("documents", Json.newArray());
		}

		if ( result == null){
			result = Json.newObject();
			result.set("documents", Json.newArray());
		}

		// Envia parametros da consulta de volta para o cliente
		result.set("query", Json.toJson(queryData));

		start = System.nanoTime();
		ObjectMapper mapper = new ObjectMapper();
		try {
			String json = mapper.writeValueAsString(result);
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Converting to JSON: %d", elapsed));
			return json;
		} catch (JsonProcessingException e) {
			Logger.error("JSON processing error: " + result.toString(), e);
			return "";
		}
	}

	private ObjectNode searchAll(QueryData queryData) throws Exception {

		ObjectNode result = null;
		
		try {
			final int page = Math.max(0, queryData.getPage());
			long start = System.nanoTime();
			String query = buildQuery(queryData);
			long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Query building: %d", elapsed));

			List<IDocument> docs = null;
			List<Vec2> points = new ArrayList<>();
			float[] densities = null;
			final int maxDocs = queryData.getMaxDocs();

			start = System.nanoTime();
			if ( ! queryData.getAuthor().isEmpty() || ! queryData.getYearStart().isEmpty() || ! queryData.getYearEnd().isEmpty() ){
				String op = getOperator(queryData.getOperator());
				String authors = queryData.getAuthor().replaceAll("\\s+", op);
				docs = dbService.getAdvancedSimpleDocuments(query, authors, queryData.getYearStart(), 
						queryData.getYearEnd(), points, maxDocs, page);
			}
			else if ( query != null ) {
				docs = dbService.getSimpleDocuments(query, points, maxDocs, page);
			}			
			else{
				docs = dbService.getAllSimpleDocuments(points, maxDocs, page);
			}

			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Quering DB: %d", elapsed));
			
			int numDocs = 0;
			if( docs != null && !docs.isEmpty()){

				result = Json.newObject();
				numDocs = docs.size() + points.size();				

				if ( points != null && !points.isEmpty()){
					int densityMap = configuration.getDensityMapCalculation();
					if ( densityMap == Configuration.DENSITY_MAP_SERVER ){

						start = System.nanoTime();
						float[] minmax = new float[] {-1,-1,1,1};
						int gridSize[] = new int[2];
						densities = gridify(points, minmax, gridSize);
						elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
						timeLogger.info(String.format("Creating Grid (KDE 2D): %d", elapsed));

						result.set("densities", Json.toJson(densities));												
						result.set("bounds", Json.toJson(minmax));
						result.set("gridSize", Json.toJson(gridSize));
						result.put("densityMap", Configuration.DENSITY_MAP_SERVER);
					}
					else {
						result.set("densities", Json.toJson(points));
						result.put("densityMap", Configuration.DENSITY_MAP_CLIENT);
					}
				}				
											
				result.put("tabId", queryData.getTabId());
				result.set("documents", Json.toJson(docs));	
				result.put("ndocs", numDocs);
				result.put("page", queryData.getPage());				
				result.put("op", "search");
				result.put("minRadiusPerc", configuration.getMinRadiusSizePercent());
				result.put("maxRadiusPerc", configuration.getMaxRadiusSizePercent());
			}
		}catch (Exception e) {
			throw e;
		}

		return result;
	}

	public ObjectNode zoom(QueryData queryData) {

		long start, elapsed;
		ObjectNode result = null;

		try {
			float x1, y1, x2, y2;
			final int page = queryData.getPage();
			final int maxDocs = queryData.getMaxDocs();

			x1 = queryData.getStart()[0];
			y1 = queryData.getStart()[1];
			x2 = queryData.getEnd()[0];
			y2 = queryData.getEnd()[1];

			float aux;
			if ( x1 > x2){
				aux = x1;
				x1 = x2;
				x2 = aux;
			}
			if ( y1 > y2 ){
				aux = y1;
				y1 = y2;
				y2 = aux;
			}

			Bounds rectangle = new Bounds(x1, y1, x2, y2);

			List<IDocument> documents = new ArrayList<>();
			List<QuadTreeNode> nodes = new ArrayList<>();

			start = System.nanoTime();
			String query = buildQuery(queryData);
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Query building: %d", elapsed));

			start = System.nanoTime();
			quadTree.findInRectangle(rectangle, query, documents, nodes);
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Find in quadtree: %d", elapsed));

			// Ordena por relevancia (descrescente)
			start = System.nanoTime();
			documents.sort(Comparator.comparing((IDocument d) -> d.getRank()).reversed());
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Sorting results: %d", elapsed));			

			float[] densities = null;
			final float[] minmax = new float[] {x1,y1,x2,y2};
			int numDocs = 0;
			if ( documents.size() > 0){
				result = Json.newObject();
				
				int numberOfPoints = Math.max(0, documents.size() - maxDocs);
				List<Vec2> points = new ArrayList<>(numberOfPoints);
				for(int i = 0; i < numberOfPoints && maxDocs + i < documents.size(); i++){
					IDocument doc = documents.get(maxDocs + i);
					points.add(doc.getPos());
				}
				documents = documents.subList(0, Math.min(documents.size(), maxDocs));

				numDocs = documents.size() + numberOfPoints;
				if ( numberOfPoints > 0 ){

					int densityMap = configuration.getDensityMapCalculation();
					if ( densityMap == Configuration.DENSITY_MAP_SERVER ){

						start = System.nanoTime();						
						int gridSize[] = new int[2];
						densities = gridify(points, minmax, gridSize);
						elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
						timeLogger.info(String.format("Creating Grid (KDE 2D): %d", elapsed));

						result.set("densities", Json.toJson(densities));
						result.set("gridSize",  Json.toJson(gridSize));						
						result.put("densityMap", Configuration.DENSITY_MAP_SERVER);
					}
					else {
						result.set("densities",  Json.toJson(points));
						result.put("densityMap", Configuration.DENSITY_MAP_CLIENT);
					}
				}
				
				// Somente documentos selecionados para exibição,
				// demais documentos irão compor o mapa de densidade.
				result.put("tabId", queryData.getTabId());
				result.set("documents",  Json.toJson(documents));
				result.put("ndocs", numDocs);
				result.put("page", page);
				result.set("bounds",  Json.toJson(minmax));				
				result.put("op", "zoom");				
				result.put("minRadiusPerc", configuration.getMinRadiusSizePercent());
				result.put("maxRadiusPerc", configuration.getMaxRadiusSizePercent());
			}

		} catch( Exception e){
			throw e;
		}

		return result;
	}

	private float[] gridify(List<Vec2> points, float[] minmax, int[] gridSize){
		// Calcula coordenadas max/min dos pontos
		// para calcular camada do grid
//		minmax[0] = points.get(0).x; //minX
//		minmax[1] = points.get(0).y; //minY
//		minmax[2] = points.get(0).x; //maxX
//		minmax[3] = points.get(0).y; //maxY
//		for(int i = 1; i < points.size(); i++){
//			Vec2 p = points.get(i);
//			if ( p.x > minmax[2]) minmax[2] = p.x;
//			if ( p.x  < minmax[0]) minmax[0] = p.x;
//			if ( p.y > minmax[3]) minmax[3] = p.y;
//			if ( p.y < minmax[1]) minmax[1] = p.y;
//		}

		EpanechnikovKernel k = new EpanechnikovKernel(1.0f);
		float bandwidth = Grid.calcBandWidth(points);
		Grid grid = new Grid(new Bounds(minmax[0],minmax[1],minmax[2],minmax[3]), bandwidth);
		grid.evaluate(points, k);
		gridSize[0] = grid.getWidth();
		gridSize[1] = grid.getHeight();
		return grid.getData();
	}	

	public String getDocumentsReferences(List<Long> docIds){
		// References
		Map<Long, List<Long>> references;		
		try {
			references = dbService.getReferences(docIds);
		} catch (Exception e) {
			Logger.error("Can't get references for documents", e);
			references = new HashMap<>(0);
		}		

		Map<String, Object> result = new HashMap<>();
		result.put("references", references);
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.writeValueAsString(result);
		} catch (JsonProcessingException e) {
			Logger.error("JSON processing error: " + result.toString(), e);
			return "";
		}
	}

	public File downloadDocuments(List<Long> docIds){

		List<Document> docs;
		try {
			docs = dbService.getSimpleDocuments(docIds.toArray(new Long[docIds.size()]));
		} catch (Exception e) {
			Logger.error("Can't get selected documents: " + docIds.toString(),e);
			return null;
		}

		File outputFile;
		try {
			outputFile = exportToFile(docs);
		} catch (Exception e) {
			Logger.error("Can't export documents to file: " + docIds.toString(),e);
			return null;
		}
		return outputFile;
	}

	private File exportToFile(List<Document> docs) throws Exception {
		File file = new File(TEMP_DIRECTORY +  File.separator + "download_" + System.nanoTime() + ".csv");
		try( BufferedWriter bw = new BufferedWriter(new FileWriter(file))){
			bw.write("DOI,Title,Authors,Year,BibTEX");
			bw.newLine();
			for(Document doc : docs){
				String line = String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"", 
						doc.getDOI() != null ? followCVSformat(doc.getDOI()) : "", 
								doc.getTitle() != null ? followCVSformat(doc.getTitle()) : "",
										followCVSformat(Utils.authorsToString(doc.getAuthors())),
										doc.getPublicationDate() != null ? followCVSformat(doc.getPublicationDate()) : "",
												doc.getBibTEX() != null ? followCVSformat(doc.getBibTEX()) : ""
						);
				bw.write(line);
				bw.newLine();
			}
		}catch (Exception e) {
			throw e;
		}
		return file;
	}
	
	private static String followCVSformat(String value) {

        String result = value;
        if (result.contains("\"")) {
            result = result.replace("\"", "\"\"");
        }
        return result;

    }

	private void clustering(final List<IDocument> documents, int numClusters, DissimilarityType type) {
		// Matrix para K-means
		Matrix matrix = new DenseMatrix();
		float[] xy = new float[2];
		for(int i = 0; i < documents.size(); i++){
			xy[0] = documents.get(i).getX();
			xy[1] = documents.get(i).getY();
			matrix.addRow(new DenseVector(xy));			
		}

		BKMeans kmeans = new BKMeans(numClusters);		
		ArrayList<ArrayList<Integer>> clusters;
		Dissimilarity diss = DissimilarityFactory.getInstance(type);
		try {
			clusters = kmeans.execute(diss, matrix);
		} catch (IOException e) {
			Logger.error("Can't clustering documents", e);
			return;			
		}		

		//Atribui id cluster
		IntStream.range(0, clusters.size()).forEach( (cl) -> {
			for(int i : clusters.get(cl)) 
				documents.get(i).setCluster(cl);				
		});
	}

	private void initQuadTree() {
		Vec2 p1 = new Vec2(-1,-1);
		Vec2 p2 = new Vec2(2,2);
		Bounds bounds = new Bounds(p1,p2);
		quadTree = new QuadTree(bounds, configuration.getQuadTreeMaxDepth(), 
				configuration.getQuadTreeMaxElementsPerBunch(), 
				configuration.getQuadTreeMaxElementsPerLeaf(), dbService);
		try {
			quadTree.loadQuadTree();
		} catch (Exception e) {
			Logger.error("Can't load QuadTree from Database", e);
			return;
		}
	}

	private void initGrid(){
		//		kernel = new EpanechnikovKernel(1.0f);
		//		Bounds bounds = new Bounds(-1, -1, 1, 1);
		//		grid = new Grid(GRID_SIZE_X, GRID_SIZE_Y, bounds);
		//		try {
		//			List<Vec2> points = dbService.loadXY();
		//			grid.evaluate(points, kernel);
		//		} catch (Exception e) {
		//			Logger.error("Can't load XY (Grid) from Database", e);
		//			return;
		//		}
	}

	@Override
	public String getDocumentsGraph() {
		Graph<Document, Edge<Long>> graph;
		try {
			graph = dbService.getDocumentsGraph();
		} catch (Exception e) {
			Logger.error("Can't get citation graph",e);
			return null;
		}

		try{
			return graphToJson(graph);
		} catch (JsonProcessingException e) {
			Logger.error("Could not write authors graph as JSON",e);
			return null;
		}
	}

	@Override
	public String getAuthorsGraph() {
		Graph<Author, Edge<Long>> graph;
		try {
			graph = dbService.getAuthorsGraph();
		} catch (Exception e) {
			Logger.error("Can't get authors graph",e);
			return null;
		}

		try{
			return graphToJson(graph);
		} catch (JsonProcessingException e) {
			Logger.error("Could not write authors graph as JSON",e);
			return null;
		}
	}

	private <V,E> String graphToJson(Graph<V, E> graph) throws JsonProcessingException {
		final JsonGraph<V,E> jsonGraph = new JsonGraph<>(graph.getVertices(), graph.getEdges());
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writeValueAsString(jsonGraph);	
	}

	private String buildQuery(QueryData queryData) {
		String terms = queryData.getTerms();
		if ( terms.trim().isEmpty() )
			return null;

		String op = getOperator(queryData.getOperator());

		StringBuilder query = new StringBuilder();
		// Checa consulta usando padrão para expressões entre aspas:
		// busca por frases
		Matcher m = TERM_PATTERN.matcher(terms);
		while( m.matches() ){
			// Extrai grupo entre aspas;
			String term = m.group(1);
			// Atualiza String de busca removendo grupo
			// extraido
			String f = terms.substring(0, m.start(1));
			String l = terms.substring(m.end(1)); 
			terms =  f + l;

			// Remove aspas do termo
			term = term.substring(1, term.length()-1);

			// Divide o term em tokens e adiciona na query (ts_query)
			// utilizando operador FOLLOWED BY (<->)
			String[] split = term.split("\\s+");

			// Caso não seja o primeiro termo
			// adiciona operador OR (|)
			if ( query.length() > 0)
				query.append(op);

			query.append("(");
			query.append(split[0]);
			for(int i = 1; i < split.length; i++){
				query.append(" <-> ");
				query.append(split[i]);
			}
			query.append(")");

			// Atualize Matcher
			m = TERM_PATTERN.matcher(terms);
		}

		// Ainda tem termos a serem processados?
		if ( terms.length() > 0){

			// Caso não seja o primeiro termo
			// adiciona operador OR (|)
			if ( query.length() > 0)
				query.append(op);

			String[] split = terms.split("\\s+");

			// O sinal negativo indica exclusão na busca, 
			// operador negação ! em SQL

			if ( split[0].charAt(0) == '-')
				query.append("!");
			query.append(split[0]);

			for(int i = 1; i < split.length; i++){
				query.append(op);
				if ( split[i].charAt(0) == '-')
					query.append("!");
				query.append(split[i]);
			}
		}

		return query.toString();
	}

	private String getOperator(String operator) {
		switch (operator) {
		case "or":		
			return "|";
		case "and":
			return "&";
		default:
			return "|";
		}
	}	
}
