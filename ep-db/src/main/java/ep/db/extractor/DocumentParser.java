package ep.db.extractor;

import java.util.List;

import ep.db.model.Author;
import ep.db.model.Document;
import ep.db.utils.Utils;
import ep.db.utils.Utils;

/**
 * Interface de processadores de documentos (parsers)
 * @version 1.0
 * @since 2017
 *
 */
public interface DocumentParser {

	/**
	 * Processa cabeçalho do documento especificado
	 * @param filename nome do arquivo (caminho) para o documento.
	 */
	public void parseHeader(String filename) throws Exception;

	/**
	 * Processa referências do documento.
	 * @param filename nome do arquivo (caminho) para o documento.
	 */
	public void parseReferences(String filename) throws Exception;

	/**
	 * Retorna lista de autores do último
	 * documento processado.
	 * @return lista de autores.
	 */
	public List<Author> getAuthors();

	/**
	 * Retorna título do último documento
	 * processado.
	 * @return título do documento.
	 */
	public String getTitle();

	/**
	 * Retorna DOI do último documento
	 * processado.
	 * @return DOI
	 */
	public String getDOI();

	/**
	 * Retorna data de publicação do último documento
	 * processado.
	 * @return data de publicação (na maioria das vezes
	 * somente o ano é retornado). 
	 * @see {@link Utils#extractYear(String)}.).
	 */
	public String getPublicationDate();

	/**
	 * Retorna resumo do último documento
	 * processado.
	 * @return abstract
	 */
	public String getAbstract();

	/**
	 * Retorna palavras-chaves do último documento
	 * processado.
	 * @return keywords.
	 */
	public String getKeywords();

	/**
	 * Retorna língua do último documento
	 * processado. Línguas são codificadas segundo
	 * ISO-3166.
	 * @return código da língua.
	 */
	public String getLanguage();

	/**
	 * Retorna lista de referências do último documento
	 * processado.
	 * @return lista de documentos citados pelo do último documento
	 * processado..
	 */
	public List<Document> getReferences();

	/**
	 * Retorna nome do journal ou 
	 * veículo de divulgação do último documento
	 * processado.
	 * @return nome da revista.
	 */
	public String getContainer();

	/**
	 * Retorna número de série (issue) do 
	 * journal.
	 * @return issue
	 */
	public String getIssue();

	/**
	 * Retorna ISSN do journal.
	 * @return ISSN
	 */
	public String getISSN();

	/**
	 * Retorna página(s) onde o 
	 * documento foi publicado.
	 * @return
	 */
	public String getPages();

	/**
	 * Retorna volume/edição do
	 * container onde o último documento
	 * processado foi publicado. 
	 * @return
	 */
	public String getVolume();
}
