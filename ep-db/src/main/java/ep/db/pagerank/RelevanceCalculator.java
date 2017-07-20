package ep.db.pagerank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.DirectedGraph;
import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
import ep.db.utils.Configuration;

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
	 * Fator-C padrão
	 */
	private static final double C = 0.85;
	
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
		this(config, C);
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
			updateRelevance(DatabaseService.DOCUMENTS_GRAPH);
		} catch (Exception e) {
			logger.error("Error updating relevance for documents.",e);
		}
		
		try {
			updateRelevance(DatabaseService.AUTHORS_GRAPH);
		} catch (Exception e) {
			logger.error("Error updating relevance for authors.",e);
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
		pageRank.evaluate(); 
		
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
			Configuration config = new Configuration();
			
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
