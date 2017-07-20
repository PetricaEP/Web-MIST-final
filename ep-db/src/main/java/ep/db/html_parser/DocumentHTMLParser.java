package ep.db.html_parser;

import java.io.IOException;
import java.net.URL;
import java.util.stream.Collectors;

import org.grobid.core.data.BiblioItem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import ep.db.model.IDocument;
import ep.db.utils.Utils;

public class DocumentHTMLParser {


	private static final String DOI_BASE_URL = "doi.org/";


	public DocumentHTMLParser() {

	}

	public void process(IDocument idoc) throws IOException {
		
		ep.db.model.Document doc;
		if ( idoc instanceof Document )
			doc = (ep.db.model.Document) idoc;
		else
			return;
		
		String doi = doc.getDOI();
		if ( doi == null )
			return;

		// some cleaning of the doi
		if (doi.startsWith("doi:") || doi.startsWith("DOI:")) {
			doi.substring(4, doi.length());
			doi = doi.trim();
		}

		doi = doi.replace(" ", "");

		BiblioItem result;
		try {
			result = parse(doi);
		} catch (IOException e) {
			throw e;
		}

		if ( result != null ){
			if ( doc.getDOI() == null && result.getDOI() != null)
				doc.setDOI(result.getDOI());
			if (doc.getAbstract() == null && result.getAbstract() != null)
				doc.setAbstract(result.getAbstract());
			if (doc.getContainer() == null && result.getJournal() != null)
				doc.setContainer(result.getJournal());
			if (doc.getISSN() == null && result.getISSN() != null)
				doc.setISSN(result.getISSN());
			if (doc.getIssue() == null && result.getIssue() != null)
				doc.setIssue(result.getIssue());
			if (doc.getKeywords() == null && result.getKeywords() != null){
				String keywords = result.getKeywords().stream().map(s -> s.getKeyword().toString())
						.collect(Collectors.joining(", "));
				doc.setKeywords(keywords);
			}
			if (doc.getAuthors() == null && result.getAuthors() != null){
				doc.setAuthors(Utils.getAuthors(result.getAuthors()));
			}
			if (doc.getPages() == null && result.getPageRange() != null)
				doc.setPages(result.getPageRange());
			if (doc.getPublicationDate() == null && result.getYear() != null)
				doc.setPublicationDate(""+Utils.extractYear(result.getYear()));
			if (doc.getTitle() == null && result.getTitle() != null)
				doc.setTitle(result.getTitle());
			if (doc.getVolume() == null && result.getVolume() != null)
				doc.setVolume(result.getVolume());
		}
	}

	public BiblioItem parse(String doi) throws IOException{


		doi = doi.replace(" ", "");

		URL url = new URL("https://" + DOI_BASE_URL + "/" + doi);
		Document doc = Jsoup.parse(url, 60000);
		Elements metaTags = doc.getElementsByTag("meta");

		BiblioItem result = new BiblioItem();

		for(Element e :  metaTags){
			String name = e.attr("name");
			String content = e.attr("content");

			if ( name != null ){
				switch (name.toLowerCase()) {
				case "citation_journal_title":
					result.setJournal(content);
					break;
				case "citation_publication_date":
				case "dc.date":
					result.setPublicationDate(content);
					break;
				case "citation_volume":
					result.setVolume(content);
					break;
				case "citation_issue":
					result.setIssue(content);
					break;
				case "citation_author":
				case "citation_authors":
				case "dc.creator":
				case "dc.contributor":
				case "author":
					String[] authors = content.split(";");
					for(String a : authors)
						result.addAuthor(a.trim());
					break;
				case "citation_abstract":
				case "dc.description":
				case "description":
				case "meta:description":
				case "og:description":
					result.setAbstract(content);
					break;
				case "citation_title":
				case "dc.title":
					result.setTitle(content);
					break;
				case "citation_keyword":
				case "citation_keywords":
				case "dc.subject":
				case "keywords":
					String[] keywords = content.split(";");
					for(String k : keywords)
						result.addKeyword(k);
					break;
				default:
					break;
				}
			}
		}
		
		if ( result.getAbstract() == null || result.getAbstract().trim().isEmpty()){
			Elements elements = doc.select(".abstract, .Abstract");
			StringBuilder sb = new StringBuilder();
			extractAbstract(elements, sb);
			if (sb.length() > 0)
				result.setAbstract(sb.toString());
		}

		return result;
	}

	private void extractAbstract(Elements elements, StringBuilder sb) {
		for(Element e : elements){
			sb.append(e.text().trim());
			extractAbstract(e.children(), sb);
		}
	}
}
