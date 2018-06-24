package ep.db.tfidf;

import java.util.Map;

public class LogaritmicInverseDocumentFrequencyTFIDF implements TFIDF {
	
	private Map<String, Integer> termsCount;

	@Override
	public double calculate(double freq, int n, String term) {
		double tf = 0, idf = 1;
		
		if ( freq > 0)
			tf = 1.0 + Math.log10(freq);
		
		if ( termsCount.containsKey(term) )
			idf = (1.0 + (double) termsCount.get(term) / n);

		// ( 1 + log f(t,d) ) * ( log N / |{d E D: t E d} )
		return tf * Math.log10(idf);

	}

	@Override
	public void setTermsCount(Map<String, Integer> termsCount) {
		this.termsCount = termsCount;
	}	

}
