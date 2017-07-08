package ep.db.extractor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.naming.NoInitialContextException;

import org.apache.log4j.Logger;
import org.grobid.core.mock.MockContext;

import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
import ep.db.html_parser.DocumentHTMLParser;
import ep.db.model.Author;
import ep.db.model.Document;
import ep.db.utils.Configuration;
import ep.db.utils.Consolidation;

/**
 * Classe principal para processamento dos documentos
 * adicionando-os ao banco de dados.
 * @version 1.0
 * @since 2017
 *
 */
public class DocumentParserService {

	/**
	 * Diretório temporário
	 */
	private static final String TMP_DIR = System.getProperty("java.io.tmpdir");

	/**
	 * Logger
	 */
	private static Logger logger = Logger.getLogger(DocumentParserService.class);

	/**
	 * Document Parser
	 */
	private final DocumentParser documentParser;

	/**
	 * Serviço de manipulação do banco de dados.
	 */
	private final DatabaseService dbService;

	private final Consolidation consolidator;

	private final DocumentHTMLParser htmlParser;


	/**
	 * Cria um novo objecto {@link DocumentParserService} para 
	 * serviço de processamento de documentos.
	 * @param parser {@link DocumentParser} a ser utilizado para processamento
	 * do documentos (atualmente somente {@link GrobIDDocumentParser} disponível).
	 * @param config configuração
	 * @throws IOException erro ao inicializar {@link DocumentParser}.
	 */
	public DocumentParserService( DocumentParser parser, Configuration config ) throws IOException{
		this.documentParser = parser;
		this.dbService = new DatabaseService(new DefaultDatabase(config));
		this.consolidator = new Consolidation(config);
		this.htmlParser = new DocumentHTMLParser();
	}

	/**
	 * Adiciona documentos a partir de um arquivo ZIP.
	 * @param packageFile arquivo zip.
	 * @return lista com caminhos absolutos para os documentos 
	 * extraídos e adicinados ao banco de dados.
	 * @throws Exception erro ao adicionar arquivos.
	 */
	public List<String> addDocumentsFromPackage(File packageFile) throws Exception{

		byte[] buffer = new byte[1024];
		final String output = TMP_DIR + File.separator + packageFile.getName() + "_" + System.nanoTime();
		File outputDir = new File(output);
		// Cria diretório para extrair arquivo zip
		if ( !outputDir.mkdirs() )
			throw new IOException("Can't create output directory: "+outputDir.getAbsolutePath());

		List<String> documents = new ArrayList<>();
		// Extrai documentos PDF do arquivo ZIP e salva em diretório temporário
		try ( ZipInputStream zis = new ZipInputStream(new FileInputStream(packageFile));){
			ZipEntry entry = zis.getNextEntry();

			while ( entry != null ){
				if ( entry.isDirectory() || !entry.getName().endsWith(".pdf")){
					entry = zis.getNextEntry();
					continue;
				}

				File newFile = new File(outputDir + File.separator + entry.getName());
				try(FileOutputStream fos = new FileOutputStream(newFile)){
					int len;
					while ( (len = zis.read(buffer)) > 0){
						fos.write(buffer, 0, len);
					}
				}catch (Exception e) {
					logger.error("I/O error from ZIP file, writing to: " + newFile.getAbsolutePath(), e);
					continue;
				}

				documents.add(entry.getName());
				entry = zis.getNextEntry();
			}
		}catch (Exception e) {
			logger.error("Error reading from ZIP file: " + packageFile.getAbsolutePath(), e);
			throw e;
		}

		// Adiciona documentos PDF ao banco de dados
		addDocuments(outputDir.getAbsolutePath());
		return documents;
	}

	/**
	 * Adiciona todos os documentos com extensão .pdf presentes
	 * no diretório dado ao banco de dados.
	 * @param docsDir caminho completo para o diretório que contém documentos
	 * a serem adicionados.
	 * @throws IOException erro ao importar documentos.
	 */
	public void addDocuments(String docsDir) throws IOException
	{
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**.pdf");
		File dir = new File(docsDir);

		try{
			//Iterates over all documents in the directory
			Files.list(dir.toPath()).forEach( (path) -> 
			{
				if ( matcher.matches(path))
					try {
						// Adiciona documento
						addDocument(path.toFile().getAbsolutePath());
					} catch (Exception e) {
						logger.error("Error importing document: "+path.toFile().getAbsolutePath(), e);
					}
			});

		}catch(Exception e){
			throw e;	
		}
	}

	/**
	 * Processa e adiciona um documento ao banco de dados.
	 * @param docPath caminho completo para o documento a ser
	 * adicionado.
	 * @throws Exception erro ao processar/adicionar documento.
	 */
	private void addDocument(String docPath) throws Exception
	{
		File file = new File(docPath);
		try {
			// Processa documento utilizando DocumentParser
			Document doc = parseDocument(file);
			if (doc != null){
				// Em caso de sucesso, adiciona ao banco de dados
				long docId = dbService.addDocument(doc);
				if ( docId > 0){
					// Processa referências do documento recém adicionado
					List<Document> references = parseReferences(file, doc);
					if ( references != null && ! references.isEmpty() ){
						references.parallelStream().forEach((ref) -> {
							try {
								consolidator.consolidate(ref);
							} catch (Exception e) {
								logger.error("Error consolidating document: " + ref.getDOI(), e);
							}

							if ( ref.getAbstract() == null || ref.getAbstract().trim().isEmpty()){
								try {
									htmlParser.process(ref);
								} catch (IOException e) {
									logger.error("Error parsing HTML document: " + ref.getDOI(), e);
								}
							}
						});
						//Adiciona referências ao banco de dados.
						dbService.addReferences(docId, references);

					}
				}
			}
		}catch(Exception e){
			throw e;
		}
	}

	/**
	 * Deleta documento do banco de dados.
	 * @param id id do documento a ser removido. 
	 * @throws Exception erro ao remover documento.
	 */
	public void removeDocument(long id) throws Exception{
		try{
			// Remove from Index
			dbService.deleteDocument(id);
		}catch(Exception e){
			throw e;
		}
	}

	/**
	 * Processa documento data utilizando {@link DocumentParser}
	 * @param filename arquivo a ser processado
	 * @return documento extraído a partir do arquivo dado.
	 * @throws Exception erro ao processar documento.
	 */
	private Document parseDocument(File filename) throws Exception 
	{
		try {
			// Processa documento utilizando os
			// parsers registrados: extrai dados
			// do cabeçalho e referências.
			this.documentParser.parseHeader(filename.getAbsolutePath());

			String title = documentParser.getTitle();
			List<Author> authors = documentParser.getAuthors();
			String doi = documentParser.getDOI();

			if ( (title == null || authors == null) && doi == null )
				throw new Exception("Document has no title, authors or DOI");

			Document doc = new Document();
			doc.setTitle(title);
			doc.setAuthors(authors);
			doc.setDOI(doi);
			doc.setKeywords(documentParser.getKeywords());
			doc.setAbstract(documentParser.getAbstract());
			doc.setContainer(documentParser.getContainer());
			doc.setISSN(documentParser.getISSN());
			doc.setIssue(documentParser.getIssue());
			doc.setPages(documentParser.getPages());
			doc.setVolume(documentParser.getVolume());
			doc.setPublicationDate(documentParser.getPublicationDate());
			doc.setLanguage(documentParser.getLanguage());

			consolidator.consolidate(doc);

			return doc;
		} catch (Exception e) {
			logger.error("Error extracting document's information: " + filename, e);
			return null;
		}

	}

	/**
	 * Processa referência do documento dado.
	 * @param docFile arquivo a ser processado.
	 * @param doc documento extraído a partir do arquivo dado.
	 * @return lista de documento citados.
	 */
	private List<Document> parseReferences(File docFile, Document doc) {
		try {
			documentParser.parseReferences(docFile.getAbsolutePath());
		} catch (Exception e) {
			logger.error("Can't parse references for: " + docFile.getAbsolutePath(), e);
			return null;
		}

		return documentParser.getReferences(); 
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		//Encerre GROBID se registrado como DocumentParser
		if ( documentParser instanceof GrobIDDocumentParser){
			MockContext.destroyInitialContext();
		}
	}

	/**
	 * Método principal para adição de novo documentos
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		if ( args.length != 1){
			System.out.println("Provide the directory path where articles are located");
			System.out.println("Usage: DocumentParserService <directory_with_pdf_documents>");
			return;
		}

		try {
			Configuration config = new Configuration();

			String grobidHome = config.getGrobidHome();
			String grobidProperties = config.getGrobidConfig();
			DocumentParser parser = new GrobIDDocumentParser(grobidHome, grobidProperties, true);
			DocumentParserService parserService = new DocumentParserService(parser, config);

			long start = System.nanoTime();
			parserService.addDocuments(args[0]);
			System.out.println("Elapsed time: " + ((System.nanoTime() - start)/1e9));

			System.out.println("Consolidated: " + parserService.consolidator.counter);

		} catch (Exception e) {
			logger.error("Error adding documents", e);
			System.err.println("Error adding documents. See log for more details.");
			System.exit(-1);
		}finally {
			try {
				MockContext.destroyInitialContext();
				GrobIDDocumentParser.closeEngine();
			}catch (NoInitialContextException e) {}
		}
	}
}
