package ep.db.tfidf;

import java.util.Map;

public class LogaritmicTFIDF implements TFIDF{

	public LogaritmicTFIDF() {
		
	}

	public void setTermsCount(Map<String, Integer> termsCount){
		
	}
	
	@Override
	public double calculate(double freq, int n, String term){
		if ( freq > 0)
			return 1.0 + Math.log(freq);
		return 0;
		
	}
}
