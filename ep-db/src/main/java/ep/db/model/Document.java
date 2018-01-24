package ep.db.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import edu.uci.ics.jung.algorithms.scoring.PageRank;
import ep.db.quadtree.Vec2;

/**
 * Classe representando um documento armanzeado
 * no banco de dados
 * @version 1.0
 * @since 2017
 *
 */
public class Document implements IDocument, Serializable{

	/**
	 * Seria id
	 */
	private static final long serialVersionUID = -8040917169690062906L;

	/**
	 * Id do documento no banco de dados
	 */
	private long id;
	
	/**
	 * DOI
	 */
	private String doi;

	/**
	 * Título
	 */
	private String title;

	/**
	 * Palavras-chaves
	 */
	private String keywords;

	/**
	 * Lista de autores
	 */
	private List<Author> authors;

	/**
	 * Resumo
	 */
	private String _abstract;

	/**
	 * Data de publicação
	 */
	private String publicationDate;
	
	/**
	 * Volume
	 */
	private String volume;
	
	/**
	 * Página(s)
	 */
	private String pages;
	
	/**
	 * Issue
	 */
	private String issue;
	
	/**
	 * Nome do journal
	 */
	private String container;
	
	/**
	 * ISSN do jounal
	 */
	private String ISSN;
	
	/**
	 * Língua do documento.
	 */
	private String language;
	
	/**
	 * Coordenada x da projeção
	 */
	private float x;
	
	/**
	 * Coordenada y da projeção
	 */
	private float y;
	
	/**
	 * Relevância pelo {@link PageRank}
	 */
	private float relevance;
	
	private float documentRank;
	
	private float authorsRank;
	
	/**
	 * BibTEX
	 */
	private String bibTEX;
	
	/**
	 * Número do cluster
	 */
	private int cluster;

	/**
	 * Path para documento
	 */
	private String path;
	
	/**
	 * Número total de citações.
	 */
	private long numberOfCitations;
	
	/**
	 * Lista com id's dos documentos citados
	 */
	private List<Long> references;
	
	/**
	 * Raio do documento para
	 * visualização (interpolacão linear
	 * com a relevância).
	 */
	private double radius;

	/**
	 * Cria um novo documento
	 */
	public Document() {
		
	}
	
	public Document(long docId, float relevance, float x, float y) {
		this.id = docId;
		this.relevance = relevance;
		this.x = x;
		this.y = y;
	}

	/**
	 * Retorna id do documento.
	 * @return id do documento.
	 */
	public long getId() {
		return id;
	}

	/**
	 * Atribui id do documento.
	 * @param docId id do documento.
	 */
	public void setId(long docId) {
		this.id = docId;
	}

	/**
	 * Retorna DOI do documento.
	 * @return DOI.
	 */
	public String getDOI() {
		return doi;
	}

	/**
	 * Atribui DOI.
	 * @param doi DOI
	 */
	public void setDOI(String doi) {
		this.doi = doi;
	}

	/**
	 * Retorna título do documento.
	 * @return título.
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Atribui título do documento.
	 * @param title título.
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Retorna palavras-chaves.
	 * @return palavras-chaves.
	 */
	public String getKeywords() {
		return keywords;
	}

	/**
	 * Atribui palavras-chaves.
	 * @param keywords palavras-chaves.
	 */
	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}

	/**
	 * Retorna autores do documento.
	 * @return lista de autores.
	 */
	public List<Author> getAuthors() {
		return authors;
	}

	/** 
	 * Atribui autores do documento.
	 * @param authors lista de autores.
	 */
	public void setAuthors(List<Author> authors) {
		this.authors = authors;
	}

	/**
	 * Retorna resumo.
	 * @return resumo.
	 */
	public String getAbstract() {
		return _abstract;
	}

	/**
	 * Atribui resumo.
	 * @param _abstract resumo.
	 */
	public void setAbstract(String _abstract) {
		this._abstract = _abstract;
	}

	/**
	 * Retorna data de publicação do documento.
	 * @return data de publicação (geralmente somente
	 * ano).
	 */
	public String getPublicationDate() {
		return publicationDate;
	}

	/**
	 * Atribui data de publicação.
	 * @param publicationDate data de publicação.
	 */
	public void setPublicationDate(String publicationDate) {
		this.publicationDate = publicationDate;
	}

	/**
	 * Retorna volume do material de divulgação (journal).
	 * @return volume.
	 */
	public String getVolume() {
		return volume;
	}

	/**
	 * Atribui volume do material de divulgação (journal).
	 * @param volume volume.
	 */
	public void setVolume(String volume) {
		this.volume = volume;
	}

	/**
	 * Retorna página(s) onde o documento foi
	 * divulgado. 
	 * @return página(s).
	 */
	public String getPages() {
		return pages;
	}

	/**
	 * Atribui página(s) onde o documento foi
	 * divulgado.
	 * @param pages página(s).
	 */
	public void setPages(String pages) {
		this.pages = pages;
	}

	/**
	 * Retorna issue.
	 * @return issue.
	 */
	public String getIssue() {
		return issue;
	}

	/**
	 * Atribui issue.
	 * @param issue issue.
	 */
	public void setIssue(String issue) {
		this.issue = issue;
	}

	/**
	 * Retorna nome do material de divulgação 
	 * (e.g nome do journal).
	 * @return nome do journal.
	 */
	public String getContainer() {
		return container;
	}

	/**
	 * Atribui nome do material de divulgação 
	 * (e.g nome do journal).
	 * @param container nome do journal.
	 */
	public void setContainer(String container) {
		this.container = container;
	}

	/**
	 * Retorna ISSN do journal.
	 * @return ISSN
	 */
	public String getISSN() {
		return ISSN;
	}

	/**
	 * Atribui ISSN do journal.
	 * @param iSSN ISSN.
	 */
	public void setISSN(String iSSN) {
		ISSN = iSSN;
	}

	/**
	 * Retorna língua do documento.
	 * @return código da línga em ISO-3166.
	 */
	public String getLanguage() {
		return language;
	}

	/**
	 * Atribui língua do documento.
	 * @param language código da língua.
	 */
	public void setLanguage(String language) {
		this.language = language;
	}

	/**
	 * Retorna coordenada x da projeção.
	 * @return coordenada x.
	 */
	public float getX() {
		return x;
	}

	/**
	 * Atribui coordenada x da projeção.
	 * @param x coordenda x.
	 */
	public void setX(float x) {
		this.x = x;
	}

	/**
	 * Retorna coordenada y da projeção.
	 * @return coordenada y.
	 */
	public float getY() {
		return y;
	}

	/**
	 * Atribui coordenada y da projeção.
	 * @param y coordenada y
	 */
	public void setY(float y) {
		this.y = y;
	}
	
	@Override
	public Vec2 getPos() {
		return new Vec2(x, y);
	}

	/**
	 * Retorna relavância do documento
	 * calculada a partir do algoritmo {@link PageRank}.
	 * @return relevância do documento em relação a toda a base 
	 * (no. de citações).
	 */
	public float getRank() {
		return relevance;
	}

	/**
	 * Atribui relevância do documento.
	 * @param relevance relevância.
	 */
	public void setRank(float relevance) {
		this.relevance = relevance;
	}
	
	public float getDocumentRank() {
		return documentRank;
	}
	
	public void setDocumentRank(float documentRank) {
		this.documentRank = documentRank;
	}
	
	public float getAuthorsRank() {
		return authorsRank;
	}
	
	public void setAuthorsRank(float authorsRank) {
		this.authorsRank = authorsRank;
	}
	
	/**
	 * Retorna o caminho no sistema de arquivos
	 * para o documento
	 * @return {@link String} com o URI do documento. 
	 */
	public String getPath() {
		return path;
	}
	
	/**
	 * Atribui o caminho para o documento.
	 * @param path {@link String} com o caminho 
	 * para o documento no sistema de arquivos.
	 */
	public void setPath(String path) {
		this.path = path;
	}
	
	/**
	 * Retorna lista de referências.
	 * @return lista com id's dos documentos citados.
	 */
	public List<Long> getReferences() {
		return references;
	}
	
	/**
	 * Atribui lista de referências.
	 * @param references lista com id's das citações.
	 */
	public void setReferences(List<Long> references) {
		this.references = references;
	}
	
	/**
	 * Adiciona um referência a lista de documentos
	 * citados.
	 * @param refId id do documento a ser adicionado 
	 * a lista de referências.
	 */
	public void addReference(long refId){
		if ( references == null )
			references = new ArrayList<>();
		references.add(refId);
	}
	
	/**
	 * Retorna número de cluster desde documento.
	 * @return número do cluster.
	 */
	public int getCluster() {
		return cluster;
	}
	
	/**
	 * Atribui número do cluster.
	 * @param cluster número do cluster.
	 */
	public void setCluster(int cluster) {
		this.cluster = cluster;
	}
	
	/**
	 * Get document's total number of citations.
	 * @return number of citations.
	 */
	public long getNumberOfCitations() {
		return numberOfCitations;
	}
	
	/**
	 * Set document's number of citation.
	 * @param numberOfCitations
	 */
	public void setNumberOfCitations(long numberOfCitations) {
		this.numberOfCitations = numberOfCitations;
	}

	/**
	 * Retorn o raio para visualzação
	 * @return raio
	 */
	public double getRadius() {
		return radius;
	}

	/**
	 * Atribui raio para visualização.
	 * @param radius
	 */
	public void setRadius(double radius) {
		this.radius = radius;
	}

	/**
	 * Retorna uma representação textual do documento.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if ( getAuthors() != null)
			sb.append(getAuthors() + " ");
		if ( getTitle() != null )
			sb.append(getTitle() + " ");
		if (getPublicationDate() != null)
			sb.append(getPublicationDate() + " ");
		return sb.toString().trim(); 
	}

	@Override
	public int compareTo(IDocument o) {
		return Float.compare(this.relevance, o.getRank());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (id ^ (id >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Document other = (Document) obj;
		if (id != other.id)
			return false;
		return true;
	}

	public void setBibTEX(String bibTEX) {
		this.bibTEX = bibTEX;
	}
	
	public String getBibTEX() {
		return bibTEX;
	}
}
