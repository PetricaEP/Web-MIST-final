package ep.db.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.colt.matrix.tfloat.impl.SparseFloatMatrix2D;
import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import ep.db.model.Author;
import ep.db.model.Document;
import ep.db.model.IDocument;
import ep.db.pagerank.Edge;
import ep.db.quadtree.QuadTree;
import ep.db.quadtree.QuadTreeBranchNode;
import ep.db.quadtree.QuadTreeLeafNode;
import ep.db.quadtree.QuadTreeNode;
import ep.db.tfidf.LogaritmicTFIDF;
import ep.db.tfidf.TFIDF;
import ep.db.utils.Utils;

/*
import ep.db.utils.Utils;*
 * Provedor de serviços com o banco de dados.
 * Inclui métodos para manipulação do banco: 
 * inserções, deleções, atualizações e consultas.
 * Todo o CRUD está concentrado nessa classe. 
 * @version 1.0
 * @since 2017
 */
public class DatabaseService {
	
	public static final int DOCUMENTS_GRAPH = 0;
	
	public static final int AUTHORS_GRAPH = 1;

	/**
	 * SQL para inserção de um novo documento
	 */
	private static final String INSERT_DOC = "INSERT INTO documents AS d (title, doi, keywords, abstract, "
			+ "publication_date, volume, pages, issue, container, container_issn, language ) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::regconfig) ON CONFLICT (doi) DO UPDATE "
			+ "SET title = coalesce(d.title, excluded.title),"
			+ "keywords=coalesce(d.keywords, excluded.keywords), "
			+ "abstract = coalesce(d.abstract, excluded.abstract),"
			+ "publication_date = coalesce(d.publication_date, excluded.publication_date), "
			+ "volume = coalesce(d.volume, excluded.volume), "
			+ "pages = coalesce(d.pages, excluded.pages), "
			+ "issue = coalesce(d.issue, excluded.issue), "
			+ "container = coalesce(d.container, excluded.container), "
			+ "container_issn = coalesce(d.container_issn, excluded.container_issn), "
			+ "language = coalesce(d.language, excluded.language) ";

	/**
	 * SQL para inserção de novo autor
	 */
	private static final String INSERT_AUTHOR = "INSERT INTO authors as a (aut_name) "
			+ "VALUES (?) ON CONFLICT (aut_name) DO UPDATE "
			+ "SET aut_name = coalesce(a.aut_name,excluded.aut_name); ";

	/**
	 * SQL para inserção de relação entre documento-autor
	 */
	private static final String INSERT_DOC_AUTHOR = "INSERT INTO document_authors as a (doc_id,aut_id) VALUES(?,?) "
			+ "ON CONFLICT DO NOTHING";

	/**
	 * SQL para inserção de citações
	 */
	private static final String INSERT_REFERENCE = "INSERT INTO citations(doc_id, ref_id) VALUES (?, ?) "
			+ "ON CONFLICT DO NOTHING";

	/**
	 * SQL para remoção de documento
	 */
	private static final String DELETE_DOC = "DELETE FROM documents WHERE doc_id = ?";

	/**
	 * SQL para atualização das coordenadas X,Y resultantes de projeção multidimensional
	 */
	private static final String UPDATE_XY = "UPDATE documents_data SET x = ?, y = ? WHERE doc_id = ?";

	/**
	 * SQL para atualização da relevancia de um documento
	 */
	private static final String UPDATE_RELEVANCE_DOCUMENTS = "UPDATE documents_data SET relevance = ? WHERE doc_id = ?";

	/**
	 * SQL para atualização da relevancia de um autor.
	 */
	private static final String UPDATE_RELEVANCE_AUTHORS = "UPDATE authors SET relevance = ? WHERE aut_id = ?";
	
	private static final String SEARCH_SQL = "SELECT d.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
			+ "dd.x, dd.y, dd.relevance, ts_rank(tsv, query, 32) rank , authors_name FROM documents d "
			+ "INNER JOIN documents_data dd ON d.doc_id = dd.doc_id LEFT JOIN "
			+ "(SELECT doc_id, string_agg(a.aut_name,';') authors_name FROM document_authors da INNER JOIN authors a "
			+ "ON da.aut_id = a.aut_id GROUP BY da.doc_id) a ON d.doc_id = a.doc_id, "
			+ "to_tsquery(?) query WHERE query @@ tsv ORDER BY doc_id LIMIT ?";

	private static final String SEARCH_SQL_ALL = "SELECT d.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
			+ "dd.x, dd.y, dd.relevance , dd.relevance rank, authors_name FROM documents d "
			+ "INNER JOIN documents_data dd ON d.doc_id = dd.doc_id LEFT JOIN "
			+ "(SELECT doc_id, string_agg(a.aut_name,';') authors_name FROM document_authors da INNER JOIN authors a "
			+ "ON da.aut_id = a.aut_id GROUP BY da.doc_id) a ON d.doc_id = a.doc_id "
			+ "ORDER BY doc_id";

	private static final String ADVANCED_SEARCH_SQL = "SELECT d.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
			+ "dd.x, dd.y, dd.relevance %rank, authors_name FROM documents d "
			+ "INNER JOIN documents_data dd ON d.doc_id = dd.doc_id LEFT JOIN "
			+ "(SELECT doc_id, string_agg(a.aut_name,';') authors_name, array_to_tsvector2(array_agg(aut_name_tsv)) aut_name_tsv FROM document_authors da INNER JOIN authors a "
			+ "ON da.aut_id = a.aut_id GROUP BY da.doc_id) a ON d.doc_id = a.doc_id "
			+ "%advanced_query ORDER BY doc_id LIMIT ?";
	
	private static final String DOCUMENTS_DATA_SQL = "SELECT d.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
			+ "dd.x, dd.y, dd.relevance, a.authors_name, dd.node_id FROM (SELECT dd.doc_id, dd.x, dd.y, "
			+ "dd.relevance, dd.node_id, rank() over (partition by dd.node_id order by dd.relevance desc) as r "
			+ "FROM documents_data dd) dd INNER JOIN documents d ON dd.doc_id = d.doc_id LEFT JOIN "
			+ "(SELECT doc_id, string_agg(a.aut_name,';') authors_name FROM document_authors da INNER JOIN authors a "
			+ "ON da.aut_id = a.aut_id GROUP BY da.doc_id) a ON d.doc_id = a.doc_id WHERE r <= ? "
			+ "ORDER BY node_id,relevance DESC";
	
	private static final String DOCUMENTS_NODE_SQL = "SELECT d.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
			+ "d.x, d.y, d.relevance, authors_name FROM (SELECT * FROM documents d "
			+ "INNER JOIN documents_data dd ON d.doc_id = dd.doc_id WHERE dd.node_id = ? ) d LEFT JOIN "
			+ "(SELECT doc_id, string_agg(a.aut_name,';') authors_name FROM document_authors da INNER JOIN authors a "
			+ "ON da.aut_id = a.aut_id GROUP BY da.doc_id) a ON d.doc_id = a.doc_id ORDER BY d.relevance "
			+ "DESC LIMIT ? OFFSET ?";
	
	private static final String NODE_DATA_SQL = "SELECT *, (select count(*) as nDocuments FROM documents_data dn "
			+ "where dn.node_id=n.node_id)  FROM nodes n ORDER BY node_id";
	
	private static final String DELETE_NODE_DATA_SQL = "DELETE FROM nodes;";
	
	private static final String UPDATE_NODEID_NULL_DOC_DATA_SQL = "UPDATE documents_data SET node_id = NULL;";
	
	private static final String INSERT_NODE_SQL = "INSERT INTO nodes( node_id, isleaf, rankmax, rankmin, "
			+ "parent_id, depth, index) VALUES (?, ?, ?, ?, ?, ?, ?);";
	
	private static final String INSERT_DOC_DATA_SQL = "INSERT INTO documents_data( doc_id, node_id, x, y, relevance ) "
			+ "VALUES (?, ?, ?, ?, ?);";
	
	private static final String UPDATE_NODEID_DOC_DATA_SQL = "UPDATE documents_data SET node_id=? WHERE doc_id=?;";

	private static final String AUTHORS_GRAPH_SQL = "SELECT a.aut_id source,a.aut_name source_name, a.relevance source_rank,"
			+ "at.aut_id target,at.aut_name target_name, at.relevance target_relevance "
			+ "FROM ( SELECT c.doc_id,c.ref_id,da.aut_id,at.aut_name, at.relevance FROM citations c INNER JOIN document_authors da ON "
			+ "c.doc_id = da.doc_id INNER JOIN authors at ON da.aut_id = at.aut_id ) a INNER JOIN document_authors rda ON "
			+ "a.ref_id = rda.doc_id INNER JOIN authors at ON rda.aut_id = at.aut_id";
	
	private static final String DOCS_GRAPH_SQL = "SELECT d.doc_id, r.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
			+ "dd.x, dd.y, dd.relevance, "
			+ "r.doi, r.title, r.keywords, r.publication_date, rr.x, rr.y, rr.relevance "
			+ "FROM citations c INNER JOIN documents d "
			+ "ON c.doc_id = d.doc_id INNER JOIN documents r ON c.ref_id = r.doc_id "
			+ "INNER JOIN documents_data dd on d.doc_id = dd.doc_id INNER JOIN documents_data rr "
			+ "ON r.doc_id = rr.doc_id ORDER BY c.doc_id, c.doc_id";

	private static final String DOCUMENTS_GRAPH_SQL = "SELECT doc_id, ref_id FROM citations ORDER BY doc_id, ref_id";

	private static final String AUTHROS_GRAPH_SQL = "SELECT a.aut_id source, ra.aut_id target FROM citations c "
			+ "INNER JOIN document_authors a ON c.doc_id = a.doc_id INNER JOIN document_authors ra ON c.ref_id = ra.doc_id";

	public static float minimumPercentOfTerms = 0;

	/**
	 * Data source
	 */
	private Database db;

	/**
	 * Tamanho do batch (para inserções)
	 */
	private final int batchSize;

	/**
	 * Cria um novo serviço para manipulação do banco de dados
	 * @param db banco de dados
	 * @param batchSize processamento em lote
	 */
	public DatabaseService(Database db, int bacthSize) {
		this.db = db;
		this.batchSize = bacthSize;
	}

	/**
	 * Cria um novo serviço para manipulação do banco de dados, 
	 * com processamento em lote padrão (batchSize = 50).
	 * @param db banco de dados
	 */
	public DatabaseService(Database db) {
		this(db, 50);
	}

	/**
	 * Retorna o número total de documentos na base.
	 * @return inteiro com número total de documentos.
	 * @throws Exception erro ao executar consulta.
	 */
	public int getNumberOfDocuments() throws Exception {
		try ( Connection conn = db.getConnection();){
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT count(*) FROM documents");
			if ( rs.next() ){
				return rs.getInt(1);
			}
			return 0;
		}catch( Exception e){
			throw e;
		}
	}

	public FloatMatrix2D buildFrequencyMatrix(long[] docIds) throws Exception {
		return buildFrequencyMatrix(docIds, new LogaritmicTFIDF());
	}

	/**
	 * Retorna matrix de frequência de todos os termos presentes
	 * nos documentos especificados.
	 * @param docIds id's dos documentos considerados para obtenção dos termos ou 
	 * <code>null</code> para recuperar termos de todos os documentos. 
	 * @return matrix N x M onde N é o número de documentos e M o número de
	 * termos.
	 * @throws Exception erro ao executar consulta.
	 */
	public FloatMatrix2D buildFrequencyMatrix(long[] docIds, TFIDF tfidfCalc) throws Exception {

		// Retorna numero de documentos e ocorrencia total dos termos
		int numberOfDocuments;
		if ( docIds == null )
			numberOfDocuments = getNumberOfDocuments();
		else
			numberOfDocuments = docIds.length;

		// Constroi consulta caso docIds != null
		StringBuilder sql = new StringBuilder();
		if ( docIds != null ){
			sql.append(" WHERE doc_id IN (");
			sql.append(docIds[0]);
			for(int i = 1; i < docIds.length; i++){
				sql.append(",");
				sql.append(docIds[i]);
			}
			sql.append(")");
		}

		String where = sql.toString();

		float minNumberOfTerms = DatabaseService.minimumPercentOfTerms;
		int numOfTerms = (int) Math.ceil(numberOfDocuments * minNumberOfTerms);

		// Recupera frequencia indiviual de cada termo na base de dados (todos os documentos)
		final Map<String, Integer> termsCount = getTermsCounts(where, numOfTerms);

		// Mapeamento termo -> coluna na matriz (bag of words)
		final Map<String, Integer> termsToColumnMap = new HashMap<>();
		int c = 0;
		for(String key : termsCount.keySet()){
			termsToColumnMap.put(key, c);
			++c;
		}

		FloatMatrix2D matrix = new SparseFloatMatrix2D(numberOfDocuments, termsCount.size()); 

		tfidfCalc.setTermsCount(termsCount);

		// Popula matriz com frequencia dos termos em cada documento
		buildFrequencyMatrix(matrix, termsToColumnMap, where, true, tfidfCalc );

		return matrix;
	}

	/**
	 * Insere novo documento ao banco de dados ou atualiza campos
	 * caso já exista na base documento com mesmo DOI.
	 * @param doc documento a ser inserido.
	 * @return id do novo documento.
	 * @throws Exception erro ao executar inserção.
	 */
	public long addDocument(Document doc) throws Exception {
		long docId = -1;
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(INSERT_DOC, Statement.RETURN_GENERATED_KEYS);
			stmt.setString(1, doc.getTitle());
			stmt.setString(2, doc.getDOI());
			stmt.setString(3, doc.getKeywords());
			stmt.setString(4, doc.getAbstract());
			stmt.setInt(5, Utils.extractYear(doc.getPublicationDate()));
			stmt.setString(6, doc.getVolume());
			stmt.setString(7, doc.getPages());
			stmt.setString(8, doc.getIssue());
			stmt.setString(9, doc.getContainer());
			stmt.setString(10, doc.getISSN());
			stmt.setString(11, doc.getLanguage());
			stmt.executeUpdate();
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()){
				docId = rs.getLong(1);
				doc.setId(docId);
			}
		}catch( Exception e){
			throw e;
		}

		// Em caso de succeso, insere autores na
		// tabela authors e também atribui ao documento
		// seus autores na tabela document_authors
		if ( docId > 0 ){
			addAuthors(Arrays.asList(doc));
			addDocumetAuthors(Arrays.asList(doc));
		}

		return docId;
	}

	/**
	 * Insere todos os documentos da lista dada no bando de dados, 
	 * atualizando campos caso já exista algum documento com mesmo DOI.
	 * <p>Para inserção de multiplos documentos esse método é mais eficiente do que
	 * multiplas chamadas do método {@link #addDocument(Document)} uma vez que realiza
	 * inserção em batch.</p>
	 * @param documents documentos a serem inseridos.
	 * @return vetor com id dos documentos inseridos (excluidos os id's dos documentos
	 * atualizados).
	 * @throws Exception
	 */
	public long[] addDocuments(List<Document> documents) throws Exception {
		List<Long> docIds = new ArrayList<>();

		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(INSERT_DOC, Statement.RETURN_GENERATED_KEYS);

			int count = 0;

			for( Document doc: documents ){
				stmt.setString(1, doc.getTitle());
				stmt.setString(2, doc.getDOI());
				stmt.setString(3, doc.getKeywords());
				stmt.setString(4, doc.getAbstract());
				stmt.setInt(5, Utils.extractYear(doc.getPublicationDate()));
				stmt.setString(6, doc.getVolume());
				stmt.setString(7, doc.getPages());
				stmt.setString(8, doc.getIssue());
				stmt.setString(9, doc.getContainer());
				stmt.setString(10, doc.getISSN());
				stmt.setString(11, doc.getLanguage());
				stmt.addBatch();

				if (++count % batchSize == 0){
					stmt.executeBatch();
					getGeneratedKeys(docIds, stmt.getGeneratedKeys());
				}
			}

			stmt.executeBatch();
			getGeneratedKeys(docIds, stmt.getGeneratedKeys());

			for(int i = 0; i < docIds.size(); i++)
				documents.get(i).setId(docIds.get(i));

		}catch( Exception e){
			throw e;
		}

		if ( docIds.size() > 0 ){
			addAuthors(documents);
			addDocumetAuthors(documents);
		}

		return docIds.stream().mapToLong(l->l).toArray();
	}

	/**
	 * Adiciona autores dos documentos dados no banco de dados
	 * @param documents documentos para quais os autores devem ser inseridos
	 * ou atualizados no banco de dados.
	 * @return id's do autores corretamente inseridos (excluídos id's dos registros
	 * atualizados).
	 * @throws Exception erro ao executar inserção.
	 */
	private long[] addAuthors(List<Document> documents) throws Exception {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(INSERT_AUTHOR, Statement.RETURN_GENERATED_KEYS);
			int count = 0;

			List<Long> ids = new ArrayList<>();

			for( Document doc : documents){
				for( Author author : doc.getAuthors() ){
					stmt.setString(1, author.getName());
					stmt.addBatch();

					if(++count % batchSize == 0){
						stmt.executeBatch();
						getGeneratedKeys(ids, stmt.getGeneratedKeys());
					}
				}
			}

			stmt.executeBatch();
			getGeneratedKeys(ids, stmt.getGeneratedKeys());

			int i = 0;
			for( Document doc : documents){
				for( Author author : doc.getAuthors() ){
					author.setId(ids.get(i));
					++i;
				}
			}

			return ids.stream().mapToLong(l->l).toArray();

		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Adiciona ligação entre documento e autor no banco de dados.
	 * @param docs documentos para quais as ligações documento-autor serão
	 * inseridas.
	 * @throws Exception erro ao executar inserção.
	 */
	private void addDocumetAuthors(List<Document> docs) throws Exception {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(INSERT_DOC_AUTHOR, Statement.RETURN_GENERATED_KEYS);

			int count = 0;
			for( Document doc : docs ){
				for( Author aut : doc.getAuthors() ){
					stmt.setLong(1, doc.getId());
					stmt.setLong(2,aut.getId());
					stmt.addBatch();

					if (++count % batchSize == 0){
						stmt.executeBatch();
					}
				}
			}
			stmt.executeBatch();
		}catch (Exception e) {
			throw e;
		}
	}

	/**
	 * Insere na lista dada (ids) os id's gerados automaticamente
	 * pelo banco de dados.
	 * @param ids lista com id's gerados automaticamente.
	 * @param generatedKeys {@link ResultSet} resultante da inserção.
	 * @throws SQLException erro ao recuperar id's do {@link ResultSet}.
	 */
	private void getGeneratedKeys(List<Long> ids, ResultSet generatedKeys) throws SQLException {
		while (generatedKeys.next()){
			ids.add(generatedKeys.getLong(1));
		}
	}

	/**
	 * Remove documento da base de dados.
	 * @param id id do documento a ser removido.
	 * @throws Exception erro ao executar remoção.
	 */
	public void deleteDocument(long id) throws Exception {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(DELETE_DOC);
			stmt.setLong(1, id);
			stmt.executeUpdate();
		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Adiciona uma referência ao banco de dados.
	 * <p>Este método irá adicionar o documento de referência
	 * caso não exista no bando de dados e adicionar citação
	 * docId -> ref</p>
	 * @param docId id do documento que faz a citação.
	 * @param ref documento citado.
	 * @throws Exception erro ao executar inserção.
	 */
	public void addReference(long docId, Document ref) throws Exception {
		//Insere referencia do banco de dados
		long refId = addDocument(ref);
		if ( refId > 0){
			// Em caso de sucesso, adiciona citação
			try ( Connection conn = db.getConnection();){
				PreparedStatement stmt = conn.prepareStatement(INSERT_REFERENCE);
				stmt.setLong(1, docId);
				stmt.setLong(2, refId);
				stmt.executeUpdate();
			}catch( Exception e){
				throw e;
			}
		}
	}

	/**
	 * Adiciona todos as referência de um documento ao banco de dados. 
	 * @param docId id do documento que faz a citação
	 * @param refs documentos citados. 
	 * @throws Exception erro ao executar inserção.
	 */
	public void addReferences(long docId, List<Document> refs) throws Exception {
		long[] refIds = addDocuments(refs);
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(INSERT_REFERENCE);
			for(int i = 0; i < refIds.length; i++){
				if ( refIds[i] > 0){
					stmt.setLong(1, docId);
					stmt.setLong(2, refIds[i]);
					stmt.addBatch();
				}
			}
			stmt.executeBatch();
		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Retorna mapa com contagem do numero de documentos que um termo ocorre, 
	 * ou seja, numero de documentos que um termo apareceu.
	 * @param where parte da consultam em SQL indicando id's do documentos a serem
	 * considerados ou <code>null</code> para todos os documentos.
	 * @return mapa de termos ordenados pela contagem absoluta.
	 * @throws Exception
	 */
	private Map<String, Integer> getTermsCounts(String where, int minNumberOfTerms) throws Exception {
		try ( Connection conn = db.getConnection();){

			String sql = "SELECT word,ndoc FROM ts_stat('SELECT tsv FROM documents";
			if ( where != null && !where.isEmpty() )
				sql += where;
			sql += String.format("') WHERE nentry > 1 AND ndoc > %d", minNumberOfTerms);

			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			Map<String,Integer> termsCount = new HashMap<>();
			while( rs.next() ){
				String term = rs.getString("word");
				int ndoc = rs.getInt("ndoc");
				termsCount.put(term, ndoc);
			}
			return termsCount;

		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Constroi matrix de frequência de termos (bag of words). 
	 * @param matrix matrix de frequência de termos já inicializada
	 * @param termsCount mapa com os termos ordenados por frequência. 
	 * @param termsToColumnMap mapa de termos para indice da coluna na matrix de frequência.
	 * @param where clause WHERE em SQL para filtragem de documentos por id's.
	 * @param normalize se <code>true</code> a frequência de cada termo será normalizada,
	 * caso contrário a frequência absoluta é considerada.
	 * @throws Exception erro ao executar consulta.
	 */
	private void buildFrequencyMatrix(FloatMatrix2D matrix,Map<String, Integer> termsToColumnMap,
			String where, boolean normalize, TFIDF tfidfCalc) throws Exception {
		try ( Connection conn = db.getConnection();){

			String sql = "SELECT freqs FROM documents";
			if ( where != null)
				sql += where;
			sql += " ORDER BY doc_id";

			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			int doc = 0;

			// Numero de documentos
			long n = matrix.rows();
			ObjectMapper mapper = new ObjectMapper();

			while( rs.next() ){
				String terms = rs.getString("freqs");
				if ( terms != null && !terms.isEmpty() ){

					List<Map<String,Object>> t = mapper.readValue(terms, 
							new TypeReference<List<Map<String,Object>>>(){});

					for(Map<String,Object> o : t){
						String term = (String) o.get("word");
						if ( termsToColumnMap.containsKey(term)){
							double freq = ((Number) o.get("nentry")).doubleValue();

							double tfidf = tfidfCalc.calculate(freq, (int) n, term);
							if ( freq != 0 )
								tfidf += Math.log(freq);

							if ( tfidf != 0){
								int col = termsToColumnMap.get(term);
								matrix.set(doc, col, (float) tfidf);
							}
						}
					}
				}
				++doc;
			}

		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Atualiza projeção dos documentos
	 * @param y matrix de projeção N x 2, onde N é o 
	 * número de documentos.
	 * @throws Exception erro ao executar atualização.
	 */
	public void updateXYProjections(FloatMatrix2D y) throws Exception {
		Connection conn = null;
		try { 
			conn = db.getConnection();
			conn.setAutoCommit(false);

			PreparedStatement pstmt = conn.prepareStatement(UPDATE_XY);
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT doc_id FROM documents ORDER BY doc_id");
			int doc = 0;
			while( rs.next() ){
				long id = rs.getLong("doc_id");
				pstmt.setDouble(1, y.get(doc, 0));
				pstmt.setDouble(2, y.get(doc, 1));
				pstmt.setLong(3, id);
				pstmt.addBatch();
				++doc;

				if ( doc % 50 == 0)
					pstmt.executeBatch();
			}

			pstmt.executeBatch();
			conn.commit();

		}catch( Exception e){
			if ( conn != null )
				conn.rollback();
			throw e;
		}finally {
			if ( conn != null )
				conn.close();
		}
	}

	/**
	 * Retorna grafo de citação
	 * @return grafo direcionado com citações.
	 * @throws Exception erro ao executar consulta.
	 */
	public DirectedGraph<Long,Long> getCitationGraph(int type) throws Exception {

		try ( Connection conn = db.getConnection();){
			Statement stmt = conn.createStatement();
			String sql;
			if (type == DOCUMENTS_GRAPH)
				sql = DOCUMENTS_GRAPH_SQL;
			else if ( type == AUTHORS_GRAPH)
				sql = AUTHROS_GRAPH_SQL;
			else
				throw new IllegalArgumentException("Unkown graph type: " + type);
			
			ResultSet rs = stmt.executeQuery(sql);

			DirectedGraph<Long, Long> graph = new DirectedSparseGraph<>();
			long e = 0;
			while( rs.next() ){
				long source = rs.getLong(1);
				long target = rs.getLong(2);

				if ( !graph.containsVertex(source))
					graph.addVertex(source);
				if ( !graph.containsVertex(target))
					graph.addVertex(target);

				graph.addEdge(e, source, target);
				++e;
			}
			return graph;

		}catch( Exception e){
			throw e;
		}
	}
	
	/**
	 * Retorna grafo de citação
	 * @return grafo direcionado com citações.
	 * @throws Exception erro ao executar consulta.
	 */
	public DirectedGraph<Document,Edge<Long>> getDocumentsGraph() throws Exception {

		try ( Connection conn = db.getConnection();){
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(DOCS_GRAPH_SQL);

			DirectedGraph<Document,Edge<Long>> graph = new DirectedSparseGraph<>();
			while( rs.next() ){
				long docId = rs.getLong(1);
				long refId = rs.getLong(2);
				
				Document doc = createDocument(docId, rs, 3);
				Document ref = createDocument(refId, rs, 10);
				
				if ( !graph.containsVertex(doc))
					graph.addVertex(doc);
				if ( !graph.containsVertex(ref))
					graph.addVertex(ref);

				graph.addEdge(new Edge<Long>(docId, refId), doc, ref);
			}
			return graph;

		}catch( Exception e){
			throw e;
		}
	}
	
	//d.doc_id, r.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
	//	+ "dd.x, dd.y, dd.relevance, "
	//	+ "r.doi, r.title, r.keywords, r.publication_date, rr.x, rr.y, rr.relevance "
	private Document createDocument(long docId, ResultSet rs, int i) throws SQLException {
		Document doc = new Document();
		doc.setId( docId );
		doc.setDOI( rs.getString(i++) );
		doc.setTitle( rs.getString(i++) );
		doc.setKeywords(rs.getString(i++));
		doc.setPublicationDate(rs.getString(i++));
		doc.setX(rs.getFloat(i++));
		doc.setY(rs.getFloat(i++));
		doc.setRank(rs.getFloat(i));
		return doc;
	}

	/**
	 * Retorna grafo de citação
	 * @return grafo direcionado com citações.
	 * @throws Exception erro ao executar consulta.
	 */
	public DirectedGraph<Author, Edge<Long>> getAuthorsGraph() throws Exception {

		try ( Connection conn = db.getConnection();){
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(AUTHORS_GRAPH_SQL);

			DirectedGraph<Author, Edge<Long>> graph = new DirectedSparseGraph<>();
			while( rs.next() ){
				long source = rs.getLong(1);
				String sName = rs.getString(2);
				float sRank = rs.getFloat(3);
				long target = rs.getLong(4);
				String tName = rs.getString(5);
				float tRank = rs.getFloat(3);

				Author sAuthor = new Author(sName);
				sAuthor.setId(source);
				sAuthor.setRank(sRank);
				
				if ( !graph.containsVertex(sAuthor))
					graph.addVertex(sAuthor);
				
				Author tAuthor = new Author(tName);
				tAuthor.setId(target);
				tAuthor.setRank(tRank);
				
				if ( !graph.containsVertex(tAuthor))
					graph.addVertex(tAuthor);

				Edge<Long> e = graph.findEdge(sAuthor, tAuthor); 
				if ( e == null )
					graph.addEdge(new Edge<Long>(source, target), sAuthor, tAuthor);
				else
					e.weight++;
			}
			return graph;

		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Atualiza relevância dos documentos.
	 * @param graph grafo de citações.
	 * @param pageRank relevância dos documentos (pagerank).
	 * @param type 
	 * @throws Exception erro ao executar atualização.
	 */
	public void updatePageRank(DirectedGraph<Long, Long> graph, PageRank<Long,Long> pageRank, int type) throws Exception {
		Connection conn = null;
		try { 
			conn = db.getConnection(); 
			conn.setAutoCommit(false);

			String sql;
			if ( type == DOCUMENTS_GRAPH )
				sql = UPDATE_RELEVANCE_DOCUMENTS;
			else if (type == AUTHORS_GRAPH)
				sql = UPDATE_RELEVANCE_AUTHORS;
			else
				return;
			
			PreparedStatement pstmt = conn.prepareStatement(sql);
			int i = 0;
			for(Long docId : graph.getVertices()){
				pstmt.setDouble(1, pageRank.getVertexScore(docId));
				pstmt.setLong(2, docId);
				pstmt.addBatch();
				++i;

				if (i % 50 == 0)
					pstmt.executeBatch();
			}

			pstmt.executeBatch();
			conn.commit();

		}catch( Exception e){
			if ( conn != null )
				conn.rollback();
			throw e;
		}finally {
			if ( conn != null )
				conn.close();
		}
	}

	public Map<Long, List<Long>> getReferences(long[] docIds) throws Exception {
		try ( Connection conn = db.getConnection();){
			String sql = "SELECT doc_id, ref_id FROM citations";
			if ( docIds != null ){
				StringBuilder sb = new StringBuilder();
				sb.append(docIds[0]);
				for(int i = 1; i < docIds.length; i++){
					sb.append(",");
					sb.append(docIds[i]);
				}
				sql = sql + " WHERE doc_id IN(" + sb.toString() + ") AND ref_id IN(" + sb.toString() + ")";
			}
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql.toString());

			Map<Long, List<Long>> map = new HashMap<>();
			while( rs.next() ){
				long docId = rs.getInt(1);
				long refId = rs.getLong(2);
				List<Long> refs = map.get(docId);
				if ( refs == null){
					refs = new ArrayList<>();
					map.put(docId, refs);
				}
				refs.add(refId);
			}
			return map;
		}catch( Exception e){
			throw e;
		}
	}

	public List<Document> getSimpleDocuments(String querySearch, int limit) throws Exception {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(SEARCH_SQL);
			stmt.setString(1, querySearch);
			if ( limit > 0)
				stmt.setInt(2, limit);
			else
				stmt.setNull(2, java.sql.Types.INTEGER);

			try (ResultSet rs = stmt.executeQuery()){
				List<Document> docs = new ArrayList<>();
				while ( rs.next() ){
					Document doc = newSimpleDocument( rs );
					docs.add(doc);
				}
				return docs;
			}catch (SQLException e) {
				throw e;
			}
		}catch( Exception e){
			throw e;
		}
	}

	public List<Document> getAllSimpleDocuments() throws Exception {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(SEARCH_SQL_ALL);

			try (ResultSet rs = stmt.executeQuery()){
				List<Document> docs = new ArrayList<>();
				while ( rs.next() ){
					Document doc = newSimpleDocument( rs );
					docs.add(doc);
				}
				return docs;
			}catch (SQLException e) {
				throw e;
			}
		}catch( Exception e){
			throw e;
		}
	}

	public List<Document> getAdvancedSimpleDocuments(String querySearch, String authors, String yearStart, 
			String yearEnd, int limit) throws Exception {
		try ( Connection conn = db.getConnection();){

			StringBuilder sql = new StringBuilder();
			StringBuilder rankSql = new StringBuilder();

			if ( querySearch != null ){
				sql.append(", to_tsquery(?) query");
				rankSql.append(", ts_rank(tsv, query, 32) ");
			}
			if ( !authors.isEmpty() ){
				sql.append( ", to_tsquery(?) aut_query");
				if ( querySearch != null)
					rankSql.append(" + ts_rank(aut_name_tsv, aut_query, 32)");
				else
					rankSql.append(", ts_rank(aut_name_tsv, aut_query, 32)");
			}

			if ( querySearch != null || !authors.isEmpty())
				rankSql.append(" rank ");
			else
				rankSql.append(", dd.relevance rank");

			sql.append(" WHERE ");
			if ( querySearch != null )
				sql.append("query @@ tsv AND ");
			if ( !authors.isEmpty() )
				sql.append( "aut_query @@ aut_name_tsv AND ");
			if ( !yearStart.isEmpty() )
				sql.append("publication_date >= ? AND ");
			if ( !yearEnd.isEmpty() )
				sql.append("publication_date <= ? AND ");
			sql.append("TRUE");

			PreparedStatement stmt = conn.prepareStatement(
					ADVANCED_SEARCH_SQL
					.replaceAll("%rank", rankSql.toString())
					.replaceAll("%advanced_query", sql.toString())
					);

			int index = 1;
			if (querySearch != null)
				stmt.setString(index++, querySearch);
			if ( !authors.isEmpty() )
				stmt.setString(index++, authors);
			if ( !yearStart.isEmpty() )
				stmt.setInt(index++, Integer.parseInt(yearStart));
			if ( !yearEnd.isEmpty() )
				stmt.setInt(index++, Integer.parseInt(yearEnd));

			if ( limit > 0 )
				stmt.setInt(index, limit);
			else
				stmt.setNull(index, java.sql.Types.INTEGER);

			try (ResultSet rs = stmt.executeQuery()){
				List<Document> docs = new ArrayList<>();
				while ( rs.next() ){
					Document doc = newSimpleDocument( rs );
					docs.add(doc);
				}
				return docs;
			}catch (SQLException e) {
				throw e;
			}
		}catch( Exception e){
			throw e;
		}
	}

	private Document newSimpleDocument(ResultSet rs) throws SQLException {
		//d.doc_id, d.doi, d.title, d.keywords, d.publication_date,
		//dd.x, dd.y, dd.relevance , dd.relevance rank, authors_name
		Document doc = new Document();
		doc.setId( rs.getLong(1) );
		doc.setDOI( rs.getString(2) );
		doc.setTitle( rs.getString(3) );
		doc.setKeywords(rs.getString(4));
		doc.setPublicationDate(rs.getString(5));
		doc.setX(rs.getFloat(6));
		doc.setY(rs.getFloat(7));
		doc.setRank(rs.getFloat(8));
		doc.setScore(rs.getDouble(9));
		doc.setAuthors(Utils.getAuthors(rs.getString(10)));

		return doc;
	}
	
	public List<Document> getFullDocuments(int maxDocumentsPerNode){
		List<Document> docs = new ArrayList<>();
		try (Connection conn = db.getConnection();) {
			PreparedStatement stmt = conn.prepareStatement(DOCUMENTS_DATA_SQL);
			stmt.setInt(1, maxDocumentsPerNode);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					//d.doc_id, d.doi, d.title, d.keywords, d.publication_date, d.x, d.y, d.relevance, authors_name
					Document doc = new Document();
					doc.setId( rs.getLong(1));
					doc.setDOI( rs.getString(2));
					doc.setTitle( rs.getString(3));
					doc.setKeywords(rs.getString(4));
					doc.setPublicationDate(rs.getString(5));
					doc.setX(rs.getFloat(6));
					doc.setY(rs.getFloat(7));
					doc.setRank(rs.getFloat(8));
					doc.setAuthors(Utils.getAuthors(rs.getString(9)));                                        
					docs.add(doc);
				}
				return docs;
			} catch (SQLException e) {
				System.out.println(e.getMessage());
				return docs;
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
			return docs;
		}
	}

	public List<IDocument> getDocumentsFromNode(long node_id, int offset, int limit){
		List<IDocument> docs = new ArrayList<>();
		try (Connection conn = db.getConnection();) {
			String sql = DOCUMENTS_NODE_SQL;
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setLong(1, node_id);
			stmt.setInt(2, limit);
			stmt.setInt(3, offset);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					//dd.doc_id, dd.x, dd.y, dd.relevance
					long doc_id = rs.getLong(1);
					float x = rs.getFloat(2);
					float y = rs.getFloat(3);
					float relevance = rs.getFloat(4);                                        
					Document doc = new Document(doc_id, relevance, x, y);
					docs.add(doc);
				}
				return docs;
			} catch (SQLException e) {
				System.out.println(e.getMessage());
				return docs;
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
			return docs;
		}
	}

	public QuadTree loadQuadTree(QuadTree qTree) {

		try (Connection conn = db.getConnection();) {
			String sql = NODE_DATA_SQL;
			PreparedStatement stmt = conn.prepareStatement(sql);
			HashMap<Integer, QuadTreeNode> nodes = new HashMap<>();

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					int node_id = rs.getInt(1);
					boolean isLeaf = rs.getBoolean(2);
					float rankMax = rs.getFloat(3);
					float rankMin = rs.getFloat(4);
					int parent_id = rs.getInt(5);
					if ( rs.wasNull() )
						parent_id = -1;
					int depth = rs.getByte(6);
					int index = rs.getByte(7);
					int nDocuments = rs.getInt(8);
					QuadTreeBranchNode parent = parent_id < 0 ? null : (QuadTreeBranchNode) nodes.get(parent_id);

					if (isLeaf) {
						QuadTreeLeafNode leaf = new QuadTreeLeafNode(depth, index, node_id, rankMax, rankMin, parent);
						leaf.setnTotalDocuments(nDocuments);
						nodes.put(node_id, leaf);
					} else {
						nodes.put(node_id, new QuadTreeBranchNode(depth, index, node_id, rankMax, rankMin, parent));
					}
					if (parent != null) {
						parent.setChild(index, nodes.get(node_id));
					}
				}
				qTree.setRoot((QuadTreeBranchNode) nodes.get(0));
			}

			sql = DOCUMENTS_DATA_SQL;
			stmt = conn.prepareStatement(sql);
			stmt.setInt(1, QuadTree.MAX_ELEMENT_PER_BUNCH);

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					//d.doc_id, d.doi, d.title, d.keywords, d.publication_date, 
					//dd.x, dd.y, dd.relevance, a.authors_name, dd.node_id
					Document doc = new Document();
					doc.setId( rs.getLong(1));
					doc.setDOI( rs.getString(2));
					doc.setTitle( rs.getString(3));
					doc.setKeywords(rs.getString(4));
					doc.setPublicationDate(rs.getString(5));
					doc.setX(rs.getFloat(6));
					doc.setY(rs.getFloat(7));
					doc.setRank(rs.getFloat(8));
					doc.setAuthors(Utils.getAuthors(rs.getString(9)));  
					int node_id = rs.getInt(10);
					
					((QuadTreeLeafNode) nodes.get(node_id)).addElement(doc);
				}
			}
			
			conn.close();
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		return qTree;
	}
	/*
    private QuadTreeNode rsToNode(ResultSet rs) throws SQLException {
        int node_id = rs.getInt(1);
        boolean isLeaf = rs.getBoolean(2);
        float rankMax = rs.getFloat(3);
        float rankMin = rs.getFloat(4);
        int parent_id = rs.getInt(5);
        int depth = rs.getByte(6);
        int index = rs.getByte(7);
        QuadTreeBranchNode parent = QuadTreeBranchNode) nodes.get(parent_id);

        if (isLeaf) {
            nodes.put(node_id, new QuadTreeLeafNode(depth, index, node_id, rankMax, rankMin, parent));
        } else {
            nodes.put(node_id, new QuadTreeBranchNode(depth, index, node_id, rankMax, rankMin, parent));
        }
        if (parent != null) {
            parent.setChild(index, nodes.get(node_id));
        }

    }
	 */
	public boolean persistQuadTree(QuadTree quadTree) {
		boolean result = true;
		try (Connection conn = db.getConnection();) {
			//Deleting Table Nodes
			String sql = UPDATE_NODEID_NULL_DOC_DATA_SQL;
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.execute();

			sql = DELETE_NODE_DATA_SQL;
			stmt = conn.prepareStatement(sql);
			stmt.execute();

			List<QuadTreeNode> nodes = new ArrayList<>();
			nodes.add(quadTree.getRoot());
			while (!nodes.isEmpty()) {
				QuadTreeNode node = nodes.remove(0);

				//INSERT INTO nodes( node_id, isleaf, rankmax, rankmin, parent_id, depth, index)
				sql = INSERT_NODE_SQL;
				stmt = conn.prepareStatement(sql);
				stmt.setLong(1, node.getNodeId());
				stmt.setBoolean(2, node.isLeaf());
				stmt.setFloat(3, node.getRankMax());
				stmt.setFloat(4, node.getRankMin());
				long pId = node.getParentNodeId();
				if ( pId != -1 )
					stmt.setLong(5, pId);
				else
					stmt.setNull(5, Types.BIGINT);
				stmt.setInt(6, node.getDepth());
				stmt.setInt(7, node.getIndex());

				stmt.execute();

				if (!node.isLeaf()) {
					QuadTreeBranchNode branch = (QuadTreeBranchNode) node;
					for (int i = 0; i < 4; i++) {
						if (branch.getChild(i) != null) {
							nodes.add(branch.getChild(i));
						}
					}
				} else { //Leaf
					QuadTreeLeafNode leaf = (QuadTreeLeafNode) node;

					for (int i = 0; i < leaf.size(); i++) {
						sql = UPDATE_NODEID_DOC_DATA_SQL;
						stmt = conn.prepareStatement(sql);

						IDocument d = leaf.getDocument(i);
						stmt.setLong(1, node.getNodeId());
						stmt.setLong(2, d.getId());
						if (stmt.executeUpdate() == 0) {
							sql = INSERT_DOC_DATA_SQL;
							stmt = conn.prepareStatement(sql);

							stmt.setLong(1, d.getId());
							stmt.setLong(2, node.getNodeId());
							stmt.setFloat(3, d.getPos().x);
							stmt.setFloat(4, d.getPos().y);
							stmt.setFloat(5, d.getRank());
							stmt.execute();
						}
					}
				}
			}
			conn.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.getMessage());
		}
		return result;
	}
}
