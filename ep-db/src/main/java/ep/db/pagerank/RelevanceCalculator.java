package ep.db.pagerank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.DirectedGraph;
import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
import ep.db.utils.Configuration;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;

/**
 * Classe para cálculo das relevâncias de cada
 * documento no banco de dados.
 * 
 * @version 1.0
 * @since 2017
 *
 */
public class RelevanceCalculator {

	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(RelevanceCalculator.class);

	/**
	 * Serviço de manipulação do banco de dados.
	 */
	private final DatabaseService dbService;

	/**
	 * Fator-C
	 */
	private final double c;

	/**
	 * Cria novo objeto para cálculo de relevância utilizando
	 * fator-C padrão ({@value #C} e configuração especificada.
	 * @param config configuração.
	 */
	public RelevanceCalculator( Configuration config ) {
		this(config, config.getPageRankAlpha());
	}

	/**
	 * Cria novo objeto para cálculo de relevância utilizando
	 * fator-C e configuração especificada.
	 * @param config configuração.
	 * @param c fator-c.
	 */
	public RelevanceCalculator( Configuration config, double c ) {
		this.dbService = new DatabaseService(new DefaultDatabase(config));
		this.c = c;
	}

	/**
	 * Atualiza PageRank.
	 */
	public void update(){
		try {
			System.out.println("Documents Page Rank...");
//			updateRelevance(DatabaseService.DOCUMENTS_GRAPH);
			calcPageRank(DatabaseService.DOCUMENTS_GRAPH);
		} catch (Exception e) {
			logger.error("Error calculating relevance for documents.",e);
		}

		try {
			System.out.println("Authors Page Rank...");
//			updateRelevance(DatabaseService.AUTHORS_GRAPH);
			calcPageRank(DatabaseService.AUTHORS_GRAPH);
		} catch (Exception e) {
			logger.error("Error calculating relevance for authors.",e);
		}
		
		try{
			System.out.println("Updating documents rank...");
			dbService.updateDocumentsRank();
		}catch (Exception e) {
			logger.error("Error updating relevance for documents.",e);
		}
	}

	private void calcPageRank(int type) throws Exception {
		try{
			dbService.calPageRank(type, c);
		}catch (Exception e) {
			logger.error("Error while getting citation graph from database",e);
			throw e;
		}
		
	}

	/**
	 * Atualiza relevâncias no banco de dados.
	 * @throws Exception erro ao recuperar ou atualizar relevâncias.
	 */
	public void updateRelevance(int type) throws Exception {

		DirectedGraph<Long,Long> graph = null;
		try {
			graph = dbService.getCitationGraph(type);
		} catch (Exception e) {
			logger.error("Error while getting citation graph from database",e);
			throw e;
		}

		PageRank<Long, Long> pageRank = new PageRank<>(graph, c);
		ProgressBar pb = new ProgressBar("Page Rank", pageRank.getMaxIterations(), 
				1000, System.out, ProgressBarStyle.UNICODE_BLOCK).start();

		do{
			pageRank.step();
			pb.step();
		} while (!pageRank.done());
		//		pageRank.evaluate(); 

		pb.stepTo(pb.getMax());
		pb.stop();

		try {
			dbService.updatePageRank(graph, pageRank, type);
		} catch (Exception e) {
			logger.error("Error updating page rank in database", e);
			throw e;
		}
	}

	/**
	 * Método main para cálculo/atualização das relevâncias.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Configuration config = Configuration.getInstance();
			config.loadConfiguration();

			System.out.println("Updating ranking...");
			RelevanceCalculator ranking = new RelevanceCalculator(config);
			ranking.update();
			System.out.println("Ranking successful updated");

		} catch (Exception e) {
			System.err.println("Some error has occured. See log file for more details");
			System.exit(-1);
		}
	}

}
