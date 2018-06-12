package views.formdata;

import play.data.validation.Constraints.Required;

public class SelectionData {
	
	@Required
	protected float[] start;
	
	@Required
	protected float[] end;	
	
	@Required
	protected int maxDocs;
	
	public SelectionData() {
	
	}

	public SelectionData(float[] start, float[] end, int maxDocs) {
		super();
		this.start = start;
		this.end = end;		
		this.maxDocs = maxDocs;
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
}
