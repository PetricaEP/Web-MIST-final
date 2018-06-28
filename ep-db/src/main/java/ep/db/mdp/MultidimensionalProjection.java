package ep.db.mdp;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
import ep.db.matrix.DocumentTermMatrix;
import ep.db.mdp.lamp.Lamp;
import ep.db.mdp.projection.ProjectionData;
import ep.db.tfidf.InverseDocumentFrequencyTFIDF;
import ep.db.tfidf.LogaritmicInverseDocumentFrequencyTFIDF;
import ep.db.tfidf.LogaritmicTFIDF;
import ep.db.tfidf.RawInverseDocumentFrequencyTFIDF;
import ep.db.tfidf.TFIDF;
import ep.db.utils.Configuration;

/**
 * Classe para realizar projeção multidimensional
 * utilizando {@link Lamp}.
 * @version 1.0
 * @since 2017
 *
 */
public class MultidimensionalProjection {

	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(MultidimensionalProjection.class);

	/**
	 * Serviço para manipulação do banco de dados.
	 */
	private DatabaseService dbService;

	/**
	 * Normalizar projeção para intervalo [-1,1]
	 */
	private  boolean normalize;

	/**
	 * Configuracao.
	 */
	private Configuration config;

	/**
	 * Cria novo objeto para projeção multidimensional
	 * com a configuração dada.
	 * @param config configuração.
	 */
	public MultidimensionalProjection( Configuration config ) {
		this(config,true);
	}

	/**
	 * Cria novo objeto para projeção multidimensional
	 * com a configuração dada.
	 * @param config configuração.
	 * @param normalize realiza normalizacao dos valores
	 */
	public MultidimensionalProjection( Configuration config, boolean normalize ) {
		this.dbService = new DatabaseService(new DefaultDatabase(config));
		this.normalize = normalize;
		this.config = config;
	}

	/**
	 * Realiza projeção multidimensional dos documentos
	 * no banco de dados.
	 * @throws Exception erro ao realizar projeção.
	 */
	public void project() throws Exception {

		List<Long> docIds = new ArrayList<>();		
		DocumentTermMatrix matrix = getBagOfWordsMatrix(docIds);		
		
		System.out.println(String.format("Bag of words size: %d x %d (rows x cols)", matrix.getRowCount(), matrix.getDimensions()));		
		System.out.println(matrix.toString());
		
		// Realiza projeção multidimensional utilizando LAMP
		System.out.println("Projecting...");

		final int numberControlPoints = (int) Math.sqrt(matrix.getRowCount());
		ProjectionData pdata = new ProjectionData();
		pdata.setControlPointsChoice(config.getControlPointsChoice());
		pdata.setDissimilarityType(config.getDissimilarityType());
		pdata.setProjectorType(config.getProjectorType());						
		pdata.setNumberIterations(config.getLampNumberOfIterations());
		pdata.setFractionDelta(config.getLampFractionDelta());		
		pdata.setPercentage(config.getLampPercentage());
		pdata.setNumberControlPoints(numberControlPoints);
		
		System.out.println("MDP settings: ");
		System.out.println(pdata.toString());

		final int nThreads = config.getLampNumberOfThreads();
		Lamp lamp = new Lamp(nThreads);
		long start = System.nanoTime();
		float[] proj = lamp.project(matrix, pdata);
		System.out.println("Elapsed: " + ((System.nanoTime() - start)/1e9));
		int nrows = proj.length / 2;	
		
		List<Integer> outliers = null;
		if ( Configuration.getInstance().isDisableOutliers()  ) {
			System.out.println("Outliers detection...");
			outliers = disableOutliers(proj);
			System.out.println("Number of documents disabled (outliers detected): " + outliers.size());
		}

		int[] rows = null;
		if ( outliers != null && outliers.size() > 0){
			rows = new int[nrows - outliers.size()];
			for(int i = 0, k = 0; i < nrows; i++)
				if ( !outliers.contains(i) )
					rows[k++] = i;
		}
		
		//		 Normaliza projeção para intervalo [-1,1]
		if ( normalize ){
			System.out.println("Normalizing to range [-1,1]...");
			normalizeProjections(proj, rows);
		}	

		// Atualiza projeções no banco de dados.
		System.out.println("Updating databse...");
		updateProjections(docIds, proj, outliers);
	}

	private DocumentTermMatrix getBagOfWordsMatrix(List<Long> docIds) throws Exception {
		// Constroi matriz de frequência de termos
		// ordenada decrescentemente pela relevancia.
		// Primeira coluna contém docIds.
		DocumentTermMatrix matrix = null;	
		TFIDF tfidf = getTFIDFWeightingScheme();

		try {
			System.out.println("Building frequency matrix (bag of words)...");			
			matrix = dbService.getDocumentTermMatrix(null, tfidf, docIds);
		} catch (Exception e) {
			logger.error("Error building frequency matrix", e);
			throw e;
		}

		return matrix;
	}

	private TFIDF getTFIDFWeightingScheme() {
		TFIDF tfidf;
		switch (config.getTfidfWeightingScheme()) {
		case 1:
			tfidf = new RawInverseDocumentFrequencyTFIDF();
			break;
		case 2:
			tfidf = new LogaritmicInverseDocumentFrequencyTFIDF();
			break;
		case 3: 
			tfidf = new LogaritmicTFIDF();
			break;
		case 4:
			tfidf = new InverseDocumentFrequencyTFIDF();
			break;
		default:
			tfidf = new LogaritmicInverseDocumentFrequencyTFIDF();
			break;
		}
		return tfidf;
	}

	private List<Integer> disableOutliers(float[] proj) {	
		int size = proj.length / 2;
		double[] x = new double[size], y = new double[size];
		for( int i = 0; i < size; i++) {
			x[i] = proj[i];
			y[i] = proj[i + size];
		}
				
		Percentile percentile = new Percentile();		
		double Q1_x = percentile.evaluate(x, 25);
		double Q3_x = percentile.evaluate(x, 75);

		double Q1_y = percentile.evaluate(y, 25);
		double Q3_y = percentile.evaluate(y, 75);

		double IQR_x = 1.5*(Q3_x-Q1_x), 
				IQR_y = 1.5*(Q3_y-Q1_y);

		List<Integer> indices = new ArrayList<>();
		for(int i = 0; i < size; i++){
			float xp = proj[i],
					yp = proj[i + size];

			if ( ( xp < Q1_x - IQR_x || xp > Q3_x + IQR_x) || 
					( yp < Q1_y - IQR_y || yp > Q3_y + IQR_y))
				indices.add(i);
		}

		return indices;
	}

	private void normalizeProjections(float[] proj, int[] rows) {
		final int size = proj.length / 2; 
		float maxX = Float.MIN_VALUE, 
				maxY = Float.MIN_VALUE,
				minX = Float.MAX_VALUE,
				minY = Float.MAX_VALUE;

		
		for(int i = 0; i < size; i++) {
			int k = rows != null ? Arrays.binarySearch(rows, i) : -1;
			if ( k < 0 ) {
				float x = proj[i], y = proj[i + size];

				//X
				if ( x > maxX) maxX = x;
				if ( x < minX) minX = x;

				//Y
				if ( y > maxY) maxY = y;
				if ( y < minY) minY = y;	
			}
		}

		//Normaliza em [-1,1]
		for(int i = 0; i < size; i++) {
			proj[i] = 2 * (proj[i] - minX) / (maxX - minX) -1;
			proj[i + size] = 2 * (proj[i + size] - minY) / (maxY - minY) -1;
		}		
	}

	/**
	 * Atualiza projeções no banco de dados.
	 * @param docIds
	 * @param proj 
	 * @param outliers 
	 * @throws Exception 
	 */
	private void updateProjections(List<Long> docIds, float[] proj, List<Integer> outliers) throws Exception {
		try {
			dbService.updateXYProjections(docIds, proj);
			if ( outliers != null && !outliers.isEmpty()) {		
				long[] outliersIds = new long[outliers.size()];
				for(int i = 0; i < outliers.size(); i++) 
					outliersIds[i] = docIds.get(outliers.get(i));				

				dbService.disableDocuments(outliersIds);
			}
		} catch (Exception e) {
			logger.error("Error updating projections in database", e);
			throw e;
		}
	}

	/**
	 * Método main para calcúlo das projeções.
	 * @param args argumento para MDP
	 */
	public static void main(String[] args) {
		try {

			Configuration config = Configuration.getInstance();			
			if (args.length > 0) {
				File configFile = new File(args[0]);
				config.loadConfiguration(configFile.getAbsolutePath());
			}
			else {			
				config.loadConfiguration();
			}
			
			System.out.println("Updating MDP...");
			MultidimensionalProjection mdp = new MultidimensionalProjection(config, true);
			mdp.project();
			System.out.println("MDP successful updated");
			System.out.println("\n");

		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
}
