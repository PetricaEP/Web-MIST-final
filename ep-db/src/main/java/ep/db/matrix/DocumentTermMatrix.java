package ep.db.matrix;

import java.util.Map;

public class DocumentTermMatrix extends SparseMatrix {

	protected String[] terms;
		
	public DocumentTermMatrix(Map<String, Integer> termsToColumnMap) {
		super();
		this.terms = new String[ termsToColumnMap.size() ];
		for(String t : termsToColumnMap.keySet())
			terms[ termsToColumnMap.get(t) ] = t;		
	}
	
	public DocumentTermMatrix() {
		super();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("#Terms: %d\n", this.getDimensions()));
		sb.append(String.format("#Documents: %d\n", this.getRowCount()));

		float sparsity = (float) getSparsity() / (rows.size() * dimensions ) * 100.0f;
		int maxTermLength = getMaxTermLength();
		sb.append(String.format("Sparsity: %.2f\n", sparsity));
		sb.append(String.format("Max. term length: %d\n", maxTermLength));
		return sb.toString();
		
	}

	private int getMaxTermLength() {
		int max = 0;
		for(int i = 0; i < terms.length; i++) {
			if ( terms[i].length() > max ) {
				max = terms[i].length();
			}
		}
		return max;
	}

	private int getSparsity() {
		int zero = 0;
		for(int i = 0; i < dimensions; i++) {
			zero += getSparsity(i);
		}
		return zero;
	}

	private int getSparsity(int column) {
		int zero = 0;
		for(int i = 0; i < rows.size(); i++) {
			if( rows.get(i).getValue(column) == 0) ++zero;			
		}
		return zero;
	}
}
