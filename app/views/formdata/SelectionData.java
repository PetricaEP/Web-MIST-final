package views.formdata;

import play.data.validation.Constraints.Required;

public class SelectionData {
	
	@Required
	protected float[] start;
	
	@Required
	protected float[] end;
	
	@Required
	protected float width;
	
	@Required
	protected float height;

	protected int zoomLevel;
	
	private int numClusters = 10; 
	
	public SelectionData() {
	
	}

	public SelectionData(float[] start, float[] end, float width, float height, int numClusters, int zoomLevel) {
		super();
		this.start = start;
		this.end = end;
		this.width = width;
		this.height = height;
		this.numClusters = numClusters;
		this.zoomLevel = zoomLevel;
	}
	
	public SelectionData(float[] start, float[] end, float width, float height){
		this(start, end, width, height, 10, 1);
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
	
	public float getWidth() {
		return width;
	}

	public void setWidth(float width) {
		this.width = width;
	}

	public float getHeight() {
		return height;
	}

	public void setHeight(float height) {
		this.height = height;
	}

	public int getNumClusters() {
		return numClusters;
	}
	
	public void setNumClusters(int numClusters) {
		this.numClusters = numClusters;
	}
	
	public int getZoomLevel() {
		return zoomLevel;
	}
	
	public void setZoomLevel(int zoomLevel) {
		this.zoomLevel = zoomLevel;
	}
}
