package ep.db.tfidf;

import java.util.Map;

public class LogaritmicInverseDocumentFrequencyTFIDF implements TFIDF {

	private Map<String, Integer> termsCount;

	@Override
	public double calculate(double freq, int n, String term) {
		double tf = 0, idf = n;
		
		if ( freq > 0)
			tf = 1.0 + Math.log(freq);
		
		if ( termsCount.containsKey(term) )
			idf /= (1.0 + termsCount.get(term));

		// ( 1 + log f(t,d) ) * ( log N / |{d E D: t E d} )
		return tf * Math.log(idf);

	}

	@Override
	public void setTermsCount(Map<String, Integer> termsCount) {
		this.termsCount = termsCount;
	}	

}
