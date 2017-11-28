package services.search;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import cern.colt.matrix.tint.IntMatrix1D;
import edu.uci.ics.jung.graph.Graph;
import ep.db.database.DatabaseService;
import ep.db.grid.GaussianKernel;
import ep.db.grid.Grid;
import ep.db.model.Author;
import ep.db.model.Document;
import ep.db.model.IDocument;
import ep.db.pagerank.Edge;
import ep.db.quadtree.Bounds;
import ep.db.quadtree.QuadTree;
import ep.db.quadtree.QuadTreeNode;
import ep.db.quadtree.Vec2;
import ep.db.utils.Configuration;
import play.Logger;
import play.db.Database;
import services.clustering.KMeans;
import services.database.PlayDatabaseWrapper;
import views.formdata.QueryData;
import views.formdata.SelectionData;

public class LocalDocumentSearcher implements DocumentSearcher {

	private final Logger.ALogger timeLogger = Logger.of("timing");

	private static final Pattern TERM_PATTERN = Pattern.compile(".*(\".*?\").*");

	private static final String TEMP_DIRECTORY = System.getProperty("java.io.tmpdir");

	private static Configuration configuration;

	private static QuadTree quadTree;

	private DatabaseService dbService;


	@Inject
	public LocalDocumentSearcher(Database db) {
		this.dbService = new DatabaseService(new PlayDatabaseWrapper(db));
		configuration = Configuration.getInstance();
		initQuadTree();
	}

	@Override
	public String search(QueryData queryData) throws Exception {
		return search(queryData, false, 0);
	}

	@Override
	public String search(QueryData queryData, int count) throws Exception {
		return search(queryData, false, count);
	}

	@Override
	public String search(QueryData queryData, boolean fetchNumberOfCitations) throws Exception {
		return search(queryData, fetchNumberOfCitations, 0);
	}

	@Override
	public String search(QueryData queryData, boolean fetchNumberOfCitations, int count) throws Exception {

		long start;
		double elapsed;
		Map<String, Object> result = new HashMap<>();

		try {
			start = System.nanoTime();
			String query = buildQuery(queryData);
			elapsed = (System.nanoTime() - start) / 10e9;
			timeLogger.info(String.format("Query building: %.3f", elapsed));

			List<Document> docs = new ArrayList<>();
			List<Vec2> points = new ArrayList<>();
			float[] densities = null;
			final float rankMax = quadTree.getRoot().getRankMax(),
					rankMin = quadTree.getRoot().getRankMin(),
					radiiMax = configuration.getMaxRadiusSizePercent() * queryData.getWidth(),
					radiiMin = configuration.getMinRadiusSizePercent() * queryData.getWidth(),
					maxArea = queryData.getHeight() * queryData.getWidth();

			start = System.nanoTime();
			if ( ! queryData.getAuthor().isEmpty() || ! queryData.getYearStart().isEmpty() || ! queryData.getYearEnd().isEmpty() ){
				String op = getOperator(queryData.getOperator());
				String authors = queryData.getAuthor().replaceAll("\\s+", op);
				dbService.getAdvancedSimpleDocuments(query, authors, queryData.getYearStart(), 
						queryData.getYearEnd(), count, rankMax, rankMin, radiiMax, radiiMin, maxArea, docs, points);
			}
			else if ( query != null )
				dbService.getSimpleDocuments(query, count, rankMax, rankMin, radiiMax, radiiMin, maxArea, docs, points);
			else{
				dbService.getAllSimpleDocuments(rankMax, rankMin, radiiMax, radiiMin, maxArea, docs, points);
			}
			elapsed = (System.nanoTime() - start)/10e9;
			timeLogger.info(String.format("Quering DB: %.3f", elapsed));

			if( docs.size() > 0){

				//Clustering
				final int numClusters = queryData.getNumClusters();
				start = System.nanoTime();
				IntMatrix1D clusters = clustering(docs, numClusters);
				elapsed = (System.nanoTime() - start)/10e9;
				timeLogger.info(String.format("Clustering: %.3f", elapsed));

				//Atribui id cluster e referencias
				start = System.nanoTime();
				IntStream.range(0, docs.size()).parallel().forEach( (i) -> {
					docs.get(i).setCluster(clusters.get(i));
				});
				elapsed = (System.nanoTime() - start)/10e9;
				timeLogger.info(String.format("Fecthing references: %.3f", elapsed));

				if ( points != null && !points.isEmpty()){
					float minX = points.get(0).x,
							maxX = minX, 
							minY = points.get(0).y, 
							maxY = minY;
					for(int i = 0; i < points.size(); i++){
						Vec2 p = points.get(i);

						//Atualiza min/max
						if ( p.x > maxX) maxX = p.x;
						if ( p.x < minX) minX = p.x;
						if ( p.y > maxY) maxY = p.y;
						if ( p.y < minY) minY = p.y;

					}

					//					EpanechnikovKernel k = new EpanechnikovKernel(10.0f);
					GaussianKernel k = new GaussianKernel();
					final int  width = (int) queryData.getWidth(), 
							height = (int) queryData.getHeight();
					Bounds bounds = new Bounds(-1, -1, 1, 1);
					//					XYTransformer transformer = new XYTransformer(width, height, minX, maxX, minY, maxY);
					//					for(Vec2 p : points){
					//						p.x = transformer.x(p.x);
					//						p.y = transformer.y(p.y);
					//					}

					//					densities = Grid2.readDensities();

					Grid grid = new Grid(1024, 1024);
					grid.evaluate(points, bounds, k);
					float[][] values = grid.getData();
					densities = new float[1024*1024];
					int kk = 0;
					for(int i = 0; i < values.length; i++)
						for(int j =0; j < values[i].length; j++){
							densities[kk] = values[i][j];
							++kk;
						}
					
					grid.printData();
				}

				result.put("documents", docs);
				result.put("densities", densities);
				result.put("nclusters", numClusters);
				result.put("op", "search");
				result.put("minRadiusPerc", configuration.getMinRadiusSizePercent());
				result.put("maxRadiusPerc", configuration.getMaxRadiusSizePercent());
			}
			else{
				result.put("documents", new ArrayList<Document>(0));
				result.put("densities", null);
				result.put("nclusters", 0);
				result.put("op", "search");
			}
		}catch (Exception e) {
			Logger.error("Unkown error!", e);
			result.put("documents", new ArrayList<Document>(0));
			result.put("densities", null);
			result.put("nclusters", 0);
			result.put("op", "search");
		}

		start = System.nanoTime();
		ObjectMapper mapper = new ObjectMapper();
		try {
			String json = mapper.writeValueAsString(result);
			elapsed = ( System.nanoTime() - start) / 10e9;
			timeLogger.info(String.format("Converting to JSON: %.3f", elapsed));
			return json;
		} catch (JsonProcessingException e) {
			Logger.error("JSON processing error: " + result.toString(), e);
			return "";
		}
	}

	@Override
	public String zoom(SelectionData selectionData) {

		long start;
		double elapsed;
		Map<String, Object> result = new HashMap<>();

		try {
			float x1, y1, x2, y2;

			x1 = selectionData.getStart()[0];
			y1 = selectionData.getStart()[1];
			x2 = selectionData.getEnd()[0];
			y2 = selectionData.getEnd()[1];

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
			quadTree.findInRectangle(rectangle, documents, nodes, null);
			elapsed = (System.nanoTime() - start)/10e9;
			timeLogger.info(String.format("Find in quadtree: %.3f", elapsed));

			if ( documents.size() > 0){
				// Ordena por relevancia (descrescente)
				start = System.nanoTime();
				documents.sort(Comparator.comparing((IDocument d) -> d.getRank()).reversed());
				elapsed = (System.nanoTime() - start)/10e9;
				timeLogger.info(String.format("Sorting results: %.3f", elapsed));

				start = System.nanoTime();
				final int numClusters = selectionData.getNumClusters();
				IntMatrix1D clusters = clustering(documents, numClusters);
				elapsed = (System.nanoTime() - start)/10e9;
				timeLogger.info(String.format("Clustering results: %.3f", elapsed));

				//Atribui id cluster e referencias
				start = System.nanoTime();
				IntStream.range(0, documents.size()).parallel().forEach( (i) -> {
					documents.get(i).setCluster(clusters.get(i));
				});
				elapsed = (System.nanoTime() - start)/10e9;
				timeLogger.info(String.format("Fecthing references: %.3f", elapsed));

				// Valores min/max das coordenadas
				// x,y para interpolação com as dimensões
				// da tela
				float minX = documents.get(0).getX(), 
						maxX = minX, 
						minY = documents.get(0).getY(), 
						maxY = minY;

				start = System.nanoTime();
				for(int i = 0; i < documents.size(); i++){
					IDocument doc = documents.get(i);
					float x = doc.getX();
					float y = doc.getY();

					//Atualiza min/max
					if ( x > maxX) maxX = x;
					if ( x < minX) minX = x;
					if ( y > maxY) maxY = y;
					if ( y < minY) minY = y;
				}
				elapsed = (System.nanoTime() - start)/10e9;
				timeLogger.info(String.format("Calculation min/max (x,y) coordinates: %.3f", elapsed));


				// Somente documentos selecionados para exibição,
				// demais documentos irão compor o mapa de densidade.
				result.put("documents", documents);
				result.put("nclusters", numClusters);
				result.put("op", "zoom");
				result.put("min_max", new float[]{minX, maxX, minY, maxY});
				result.put("minRadiusPerc", configuration.getMinRadiusSizePercent());
				result.put("maxRadiusPerc", configuration.getMaxRadiusSizePercent());
			}
			else{
				result.put("documents", new ArrayList<Document>(0));
				result.put("nclusters", 0);
				result.put("op", "zoom");
			}

		} catch( Exception e){
			Logger.error("Unkown error!", e);
			result.put("documents", new ArrayList<Document>(0));
			result.put("nclusters", 0);
			result.put("op", "zoom");
		}

		ObjectMapper mapper = new ObjectMapper();
		try {
			start = System.nanoTime();
			String json = mapper.writeValueAsString(result);
			elapsed = (System.nanoTime() - start)/10e9;
			timeLogger.info(String.format("Converting to JSON: %.3f", elapsed));
			return json;
		} catch (JsonProcessingException e) {
			Logger.error("JSON processing error: " + result.toString(), e);
			return "";
		}
	}

	public String getDocumentsReferences(long[] docIds){
		// References
		final Map<Long, List<Long>> references;
		Map<Long, List<Long>> ref;
		try {
			ref = dbService.getReferences(docIds);
		} catch (Exception e) {
			Logger.error("Can't get references for documents", e);
			ref = new HashMap<>(0);
		}
		references = ref;


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
			for(Document doc : docs){
				String line = String.format("%s,%s,%s,%s,%s", 
						doc.getDOI(), 
						doc.getTitle(), 
						doc.getAuthors().toString(),
						doc.getPublicationDate(),
						doc.getBibTEX()
						);
				bw.write(line);
				bw.newLine();
			}
		}catch (Exception e) {
			// TODO: handle exception
			throw e;
		}
		return file;
	}

	private IntMatrix1D clustering(List<? extends IDocument> documents, int numClusters) {
		// Matrix para K-means
		FloatMatrix2D matrix = new DenseFloatMatrix2D(documents.size(),2);
		for(int i = 0; i < documents.size(); i++){
			matrix.setQuick(i, 0, documents.get(i).getX());
			matrix.setQuick(i, 1, documents.get(i).getY());
		}

		KMeans kmeans = new KMeans();
		kmeans.cluster(matrix, numClusters);
		IntMatrix1D clusters = kmeans.getClusterAssignments();

		return clusters;
	}

	private void initQuadTree() {
		Vec2 p1 = new Vec2(-1,-1);
		Vec2 p2 = new Vec2(2,2);
		Bounds bounds = new Bounds(p1,p2);
		quadTree = new QuadTree(bounds, configuration.getQuadTreeMaxDepth(), 
				configuration.getQuadTreeMaxElementsPerBunch(), 
				configuration.getQuadTreeMaxElementsPerLeaf(), dbService);
		quadTree.loadQuadTree();
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
