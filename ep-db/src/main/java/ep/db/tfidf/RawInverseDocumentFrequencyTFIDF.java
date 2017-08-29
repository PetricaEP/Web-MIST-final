package ep.db.tfidf;

import java.util.Map;

public class RawInverseDocumentFrequencyTFIDF implements TFIDF {

	private Map<String, Integer> termsCount;

	@Override
	public double calculate(double freq, int n, String term) {
		int nt = 0;
		if ( termsCount.containsKey(term))
			nt = termsCount.get(term);
		if ( n > 0)
			return freq * Math.log( n / (1.0 + nt));
		return 0;
	}

	@Override
	public void setTermsCount(Map<String, Integer> termsCount) {
		this.termsCount = termsCount;
	}

}
