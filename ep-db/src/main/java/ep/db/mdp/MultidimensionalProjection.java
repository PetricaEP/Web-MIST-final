package ep.db.mdp;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.jblas.FloatMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
import ep.db.mdp.lamp.Lamp;
import ep.db.mdp.projection.ProjectionData;
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

		FloatMatrix[] docIds = new FloatMatrix[1];		
		FloatMatrix matrix = getBagOfWordsMatrix(docIds);						

//		FloatMatrix matrix = Lamp.load("/Users/jose/Documents/freelancer/petricaep/ep-project/ep-db/documents100.data");
		save(matrix);

		// Realiza projeção multidimensional utilizando LAMP
		System.out.println("Projecting...");

		final int numberControlPoints = (int) Math.sqrt(matrix.rows);
		ProjectionData pdata = new ProjectionData();
		pdata.setControlPointsChoice(config.getControlPointsChoice());
		pdata.setDissimilarityType(config.getDissimilarityType());
		pdata.setProjectorType(config.getProjectorType());						
		pdata.setNumberIterations(config.getLampNumberOfIterations());
		pdata.setFractionDelta(config.getLampFractionDelta());		
		pdata.setPercentage(config.getLampPercentage());
		pdata.setNumberControlPoints(numberControlPoints);		

		final int nThreads = config.getLampNumberOfThreads();
		Lamp lamp = new Lamp(nThreads);
		float[] proj = lamp.project(matrix, pdata);
		FloatMatrix y = new FloatMatrix(proj);
		y = y.reshape(matrix.rows, 2);
		
		Lamp.save("documents200k.prj", y.toArray2());

		List<Integer> outliers = null;
		if ( Configuration.getInstance().isDisableOutliers()  ) {
			System.out.println("Outliers detection...");
			outliers = disableOutliers(y);
			System.out.println("Number of documents disabled (outliers detected): " + outliers.size());
		}

		int[] rows = null;
		if ( outliers != null && outliers.size() > 0){
			rows = new int[y.rows - outliers.size()];
			for(int i = 0, k = 0; i < y.rows; i++)
				if ( !outliers.contains(i) )
					rows[k++] = i;
		}

		//		 Normaliza projeção para intervalo [-1,1]
		if ( normalize ){
			System.out.println("Normalizing to range [-1,1]...");
			normalizeProjections(y, rows);
		} 

		Lamp.save("documents200k-norm.prj", y.toArray2());		

		// Atualiza projeções no banco de dados.
		System.out.println("Updating databse...");
		updateProjections(docIds[0], y, outliers);
	}

	private void save(FloatMatrix matrix) {
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(new File("documents200k.data")));)
		{

			bw.write("DY");
			bw.newLine();
			bw.write(Integer.toString(matrix.rows))	;
			bw.newLine();
			bw.write(Integer.toString(matrix.columns));
			bw.newLine();
			bw.newLine();

			for(int i = 0; i < matrix.rows; i++) {
				bw.write(Integer.toString(i));
				for(int j = 0; j < matrix.columns; j++) {
					bw.write(";");
					bw.write(Float.toString(matrix.get(i, j)));
				}
				bw.newLine();
			}						
		}catch (Exception e) {
			e.printStackTrace();
		}

	}

	private FloatMatrix getBagOfWordsMatrix(FloatMatrix[] docIds) throws Exception {
		// Constroi matriz de frequência de termos
		// ordenada decrescentemente pela relevancia.
		// Primeira coluna contém docIds.
		FloatMatrix matrix = null;	
		TFIDF tfidf = getTFIDFWeightingScheme();

		try {
			System.out.println("Building frequency matrix (bag of words)...");			
			matrix = dbService.buildFrequencyMatrix(null, tfidf, docIds);
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
		default:
			tfidf = new LogaritmicInverseDocumentFrequencyTFIDF();
			break;
		}
		return tfidf;
	}

	private List<Integer> disableOutliers(FloatMatrix proj) {		
		double[] x = new double[proj.rows], y = new double[proj.rows];
		for( int i = 0; i < proj.rows; i++) {
			x[i] = proj.get(i, 0);
			y[i] = proj.get(i, 1);
		}

		Percentile percentile = new Percentile();
		double Q1_x = percentile.evaluate(x, 0.25);
		double Q3_x = percentile.evaluate(x, 0.75);

		double Q1_y = percentile.evaluate(y, 0.25);
		double Q3_y = percentile.evaluate(y, 0.75);

		double IQR_x = 1.5*(Q3_x-Q1_x), 
				IQR_y = 1.5*(Q3_y-Q1_y);

		List<Integer> indices = new ArrayList<>();
		for(int i = 0; i < proj.rows; i++){
			float xp = proj.get(i, 0),
					yp = proj.get(i, 1);			
			if ( ( xp < Q1_x - IQR_x || xp > Q3_x + IQR_x) || 
					( yp < Q1_y - IQR_y || yp > Q3_y + IQR_y))
				indices.add(i);
		}

		return indices;
	}

	private void normalizeProjections(FloatMatrix proj, int[] rows) {
		final FloatMatrix maxs, mins;
		int size;
		if ( rows != null && rows.length > 0) {
			mins = proj.getRows(rows).columnMins();
			maxs = proj.getRows(rows).columnMaxs();
			size = rows.length;
		}
		else {
			mins = proj.columnMins();
			maxs = proj.columnMaxs();
			size = proj.rows;
		}
		
		for(int i = 0; i < size; i++) {
			int ind = rows != null ? rows[i] : i;
			float vx = proj.get(ind, 0), vy = proj.get(ind, 1);
			proj.put(ind, 0, 2 * (vx - mins.get(0) / (maxs.get(0) - mins.get(0) - 1 )));
			proj.put(ind, 1, 2 * (vy - mins.get(1) / (maxs.get(1) - mins.get(1) - 1 )));
		}
	}

	/**
	 * Atualiza projeções no banco de dados.
	 * @param y
	 * @param y 
	 * @param outliers 
	 * @throws Exception 
	 */
	private void updateProjections(FloatMatrix docIds, FloatMatrix y, List<Integer> outliers) throws Exception {
		try {
			dbService.updateXYProjections(docIds, y);
			if ( outliers != null && !outliers.isEmpty())
				dbService.disableDocuments(docIds.getRows(outliers.stream().mapToInt((i) -> i.intValue()).toArray()));
		} catch (Exception e) {
			logger.error("Error updating projections in database", e);
			throw e;
		}
	}

	/**
	 * Método main para calcúlo das projeções.
	 * @param args
	 */
	public static void main(String[] args) {
		try {

			Configuration config = Configuration.getInstance();
			config.loadConfiguration();

			System.out.println("Updating MDP...");
			MultidimensionalProjection mdp = new MultidimensionalProjection(config);
			mdp.project();
			System.out.println("MDP successful updated");
			System.out.println("\n");

		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
}
