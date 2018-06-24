package ep.db.tfidf;

import java.util.Map;

public class InverseDocumentFrequencyTFIDF implements TFIDF {
	
	private Map<String, Integer> termsCount;
	
	private static final double LOG2 = Math.log10(2.0);

	@Override
	public double calculate(double freq, int n, String term) {
		double tf = 0, idf = 1;
		
		if ( freq > 0)
			tf = freq;
		
		if ( termsCount.containsKey(term) )
			idf = ((double) termsCount.get(term) / n);

		// f(t,d)  * ( log N / |{d E D: t E d} )
		return tf * Math.log10(idf) / LOG2;

	}

	@Override
	public void setTermsCount(Map<String, Integer> termsCount) {
		this.termsCount = termsCount;
	}	

}
