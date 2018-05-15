package ep.db.extractor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.grobid.core.data.BibDataSet;
import org.grobid.core.data.BiblioItem;
import org.grobid.core.engines.Engine;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.mock.MockContext;
import org.grobid.core.utilities.GrobidProperties;

import ep.db.model.Author;
import ep.db.model.Document;
import ep.db.utils.Utils;

/**
 * Classe para processar documentos (artigos cientificos)
 * utilizando GROBID.
 * @version 1.0
 * @since 2017
 *
 */
public final class GrobIDDocumentParser implements DocumentParser{

	/**
	 * GROBID engine (singleton)
	 */
	private static Engine engine;

	private BiblioItem metadata;

	private List<BibDataSet> references;

	private final boolean consolidate;

	private final String grobidHome;

	private final String grobidProperties;

	public GrobIDDocumentParser(String grobidHome, String grobidProperties) throws Exception {
		this(grobidHome, grobidProperties, false);
	}

	/**
	 * Cria um novo processador {@link GrobIDDocumentParser} (singleton)
	 * <p>Somente uma instância da ENGINE do GrobID deve existir durante a
	 * execução do programa. Por isto, diferentes objetos desta classe irão
	 * compartilhar mesma ENGINE.</p>
	 * @param grobidHome caminho para o diretorio GROBID_HOME
	 * @param grobidProperties caminho para o arquivo de configuração do GLOBIID.
	 * @param consolidate: opção de consolidação permite ao GROBID consultar
	 * CrossRef para melhorar informação extraída.
	 * @throws Exception se um erro ocorrer ao inicializar GROBID
	 */
	public GrobIDDocumentParser(String grobidHome, String grobidProperties, boolean consolidate) throws Exception {
		this.consolidate = consolidate;
		this.grobidHome = grobidHome;
		this.grobidProperties = grobidProperties;
		initialize();
	}

	/**
	 * Inicializa GROBID
	 * @throws Exception
	 */
	private void initialize() throws Exception {			
		try {
			System.out.println("Initializing GROBID...");
			MockContext.setInitialContext(grobidHome, grobidProperties);
			GrobidProperties.getInstance();
			engine = GrobidFactory.getInstance().getEngine();
			if ( engine == null )
				engine = GrobidFactory.getInstance().createEngine();
		} catch (Exception e1) {
			MockContext.destroyInitialContext();
			throw e1;
		}
	}

	/**
	 * Returna a engine do GROBID
	 * @return grobid engine
	 */
	public Engine getEngine() {
		return engine;
	}

	@Override
	public void parseHeader(String filename) {
		metadata = new BiblioItem();
		getEngine().processHeader(filename, consolidate , metadata);
	}

	@Override
	public void parseReferences(String filename) {
		references = getEngine().processReferences(new File(filename), consolidate);
	}

	@Override
	public List<Author> getAuthors() {
		if (metadata.getAuthors() != null)
			return Utils.getAuthors(metadata.getAuthors());
		return null;
	}

	@Override
	public String getLanguage() {
		return Utils.languageToISO3166(metadata.getLanguage());
	}
	
	@Override
	public String toBibTEX() {
		return metadata.toBibTeX();
	}

	@Override
	public String getTitle() {
		return metadata.getTitle();
	}

	@Override
	public String getDOI() {
		return metadata.getDOI();
	}

	@Override
	public String getPublicationDate() {
		return metadata.getPublicationDate() == null ? metadata.getYear() : 
			metadata.getPublicationDate();
	}

	@Override
	public String getAbstract() {
		return metadata.getAbstract();
	}

	@Override
	public String getKeywords() {
		if (metadata.getKeywords() != null )
			//Concatena palavras-chaves separando as com ','
			return metadata.getKeywords().stream().map(s -> s.getKeyword().toString()).collect(Collectors.joining(", "));
		return null;
	}

	@Override
	public String getContainer() {
		String container = null;
		if ( metadata.getJournal() != null )
			container = metadata.getJournal();
		else if (metadata.getBookTitle() != null)
			container = metadata.getBookTitle();
		else if ( metadata.getEvent() != null )
			container = metadata.getEvent();
		return container;
	}

	@Override
	public String getIssue() {
		return metadata.getIssue();
	}

	@Override
	public String getISSN() {
		return metadata.getISSN() != null ? metadata.getISSN() : metadata.getISSNe();
	}

	@Override
	public String getPages() {
		return metadata.getPageRange() != null ? metadata.getPageRange() : (
				metadata.getBeginPage() + "-" + metadata.getEndPage());
	}

	@Override
	public String getVolume() {
		return metadata.getVolume();
	}

	@Override
	public List<Document> getReferences() {
		return references.parallelStream()
				.map((ref) -> processReference(ref))
				.filter((ref) -> ref != null)
				.collect(Collectors.toCollection(ArrayList::new));
	}

	private Document processReference(BibDataSet bds) {
		BiblioItem bib = bds.getResBib();
		Document ref = null;
		if (!metadata.getDOI().equals(bib.getDOI())){
			ref = new Document();

			ref.setDOI(bib.getDOI());
			ref.setAuthors(Utils.getAuthors(bib.getAuthors()));
			ref.setTitle(bib.getTitle());
			ref.setAbstract(bib.getAbstract());
			ref.setIssue(bib.getIssue());
			if (bib.getKeywords() != null && !bib.getKeywords().isEmpty() )
				ref.setKeywords(bib.getKeywords().stream().map(s -> s.getKeyword().toString()).collect(Collectors.joining(", ")));
			ref.setLanguage(Utils.languageToISO3166(bib.getLanguage()));
			ref.setPages(bib.getPageRange());
			ref.setVolume(bib.getVolume());

			// Set Container ISSN
			if ( bib.getISSN() != null )
				ref.setISSN(bib.getISSN());
			else if (bib.getISSNe() != null )
				ref.setISSN(bib.getISSNe());

			// Set Container Name
			if ( bib.getJournal() != null )
				ref.setContainer(bib.getJournal());
			else if ( bib.getEvent() != null )
				ref.setContainer(bib.getEvent());
			else if ( bib.getBookTitle() != null )
				ref.setContainer(bib.getBookTitle());

			//Set publication date
			if ( bib.getPublicationDate() != null )
				ref.setPublicationDate( bib.getPublicationDate() );
			else if ( bib.getYear() != null ) 
				ref.setPublicationDate( bib.getYear() );

			// Set URL
			ref.setPath(bib.getURL());
		}
		return ref;
	}

	public static void closeEngine() {
		try {
			if (engine != null )
				engine.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
