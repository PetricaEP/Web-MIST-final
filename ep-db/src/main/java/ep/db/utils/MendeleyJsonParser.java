package ep.db.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.grobid.core.data.BiblioItem;

public class MendeleyJsonParser {

	private BiblioItem biblio = null;

	public MendeleyJsonParser() {

	}

	public List<BiblioItem> parse(Object jsonResponse) {
		
		List<BiblioItem> biblios = null;
		if ( jsonResponse instanceof List ){

			List<Object> items = (List<Object>) jsonResponse;

			biblios = new ArrayList<>(items.size());

			for( Object item : items ){

				biblio = new BiblioItem();
				Map<String, Object> itemMap = (Map<String, Object>) item;

				if ( itemMap.containsKey("type") ){
					setType(itemMap.get("type"));
				}

				for( String key : itemMap.keySet() ){
					try {
						switch (key) {

						case "title":
							String title = (String) itemMap.get(key);
							biblio.setArticleTitle(title);
							biblio.setTitle(title);
							break;
						case "source":
							biblio.setPublisher( (String) itemMap.get(key)); 
							break;
						case "identifiers":
							Map<String, Object> identifiers = (Map<String, Object>) itemMap.get(key);
							for ( String identKey : identifiers.keySet() ){
								switch (identKey) {
								case "issn":
									biblio.setISSN((String) identifiers.get(identKey));
									break;
								case "doi":
									biblio.setDOI((String) identifiers.get(identKey));
									break;
								case "isbn":
									biblio.setISBN10((String) identifiers.get(identKey));
									biblio.setISBN13((String) identifiers.get(identKey));
									break;
								}
							}
							biblio.setError(false);
							break;
						case "abstract":
							biblio.setAbstract((String) itemMap.get(key));
							break;
						case "keywords":
							List<String> keywords = (List<String>) itemMap.get(key);
							for(String keyword : keywords)
								biblio.addKeyword(keyword);
							break;
						case "issue":
							String issue = itemMap.get(key).toString();
							biblio.setIssue(issue);
							biblio.setNumber(issue);
							break;
						case "volume":
							String volume = itemMap.get(key).toString();
							biblio.setVolume(volume);
							biblio.setVolumeBlock(volume, true);
							break;
						case "pages":
							String pageRange = itemMap.get(key).toString();
							biblio.setPageRange(pageRange);
							String[] pages = pageRange.split("-");
							try {
								biblio.setBeginPage(Integer.parseInt(pages[0]));
								biblio.setEndPage(Integer.parseInt(pages[1]));
							}catch( Exception e){
								// warning message to be logged here
							}
							break;
						case "month":
							biblio.setMonth(itemMap.get(key).toString());
							break;
						case "year":
							biblio.setYear(itemMap.get(key).toString());
							break;
						case "day":
							biblio.setDay(itemMap.get(key).toString());
							break;
						case "publisher":
							biblio.setPublisher(itemMap.get(key).toString());
							break;
						case "edition":
							biblio.setEdition(itemMap.get(key).toString());
							break;
						case "institution":
							biblio.setInstitution((String) itemMap.get(key));
							break;
						case "series":
							biblio.setSerie(itemMap.get(key).toString());
							break;
						case "authors":
							List authors = (List) itemMap.get(key);
							for( Object au : authors ){
								biblio.addAuthor(
										((Map) au).get("first_name") + " " + ((Map) au).get("last_name"));								
							}
							break;
						default:
							break;
						}
					}catch( Exception e){
						//warning to log
						e.printStackTrace();
					}
				}
				biblios.add(biblio);
			}
		}
		return biblios;
	}

	private void setType(Object type){
		switch( type.toString() ){
		case "book":
		case "book-section":
		case "book-track":
		case "book-part":
		case "book-set":
		case "book-chapter":
		case "book-series":
			biblio.setItem(BiblioItem.Book);
			break;
		case "journal-article":
			biblio.setItem(BiblioItem.Article);
			break;
		case "journal-volume":
		case "journal-issue":
		case "journal":
			biblio.setItem(BiblioItem.Periodical);
			break;
		case "proceedings-article":
			biblio.setItem(BiblioItem.InProceedings);
			break;
		case "report":
			biblio.setItem(BiblioItem.TechReport);
			break;
		case "monograph":
			biblio.setItem(BiblioItem.MasterThesis);
			break;
		case "dissertation":
			biblio.setItem(BiblioItem.PhdThesis);
			break;
		}
	}

}
