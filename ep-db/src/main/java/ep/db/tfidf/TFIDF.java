package ep.db.tfidf;

import java.util.Map;

public interface TFIDF {
	
	public double calculate(double freq, int n, String term);

	public void setTermsCount(Map<String, Integer> termsCount);

}
