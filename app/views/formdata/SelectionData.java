package views.formdata;

import play.data.validation.Constraints.Required;

public class SelectionData {
	
	@Required
	protected float[] start;
	
	@Required
	protected float[] end;
	
	private int numClusters = 10; 
	
	@Required
	protected int maxDocs;
	
	public SelectionData() {
	
	}

	public SelectionData(float[] start, float[] end, 
			int numClusters, int maxDocs) {
		super();
		this.start = start;
		this.end = end;
		this.numClusters = numClusters;
		this.maxDocs = maxDocs;
	}
	
	public SelectionData(float[] start, float[] end, int maxDocs){
		this(start, end, 10, maxDocs);
	}

	public int getMaxDocs() {
		return maxDocs;
	}

	public void setMaxDocs(int maxDocs) {
		this.maxDocs = maxDocs;
	}

	public float[] getStart() {
		return start;
	}

	public void setStart(float[] start) {
		this.start = start;
	}

	public float[] getEnd() {
		return end;
	}
	
	public void setEnd(float[] end) {
		this.end = end;
	}
	
	public int getNumClusters() {
		return numClusters;
	}
	
	public void setNumClusters(int numClusters) {
		this.numClusters = numClusters;
	}
}
