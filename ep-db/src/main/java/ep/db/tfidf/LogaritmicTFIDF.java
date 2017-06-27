package ep.db.tfidf;

import java.util.Map;

public class LogaritmicTFIDF implements TFIDF{

	
	private Map<String, Integer> termsCount;

	public LogaritmicTFIDF() {
		
	}

	public void setTermsCount(Map<String, Integer> termsCount){
		this.termsCount = termsCount;
	}
	
	@Override
	public double calculate(double count, int n, String term){
		// 1 + log(f(t,d))
		double tf = 0, idf;
		if ( count > 0)
			tf = 1.0 + Math.log(count);
		if ( termsCount.containsKey(term) )
			idf = n / (1.0 + termsCount.get(term));
		else 
			idf = n;
		
		// ( 1 + log f(t,d) ) * ( log N / |{d E D: t E d} )
		return tf * Math.log(idf);
	}
}
