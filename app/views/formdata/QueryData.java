package views.formdata;

import play.data.validation.Constraints.Required;

public class QueryData {
	
	protected String terms;
	
	@Required
	protected String operator;
	
	protected String author;
	
	protected String yearStart;
	
	protected String yearEnd;
	
	protected int numClusters = 10;
	
	@Required
	protected float width;
	
	@Required
	protected float height; 
	
	public QueryData() {
		
	}
	
	public QueryData(String terms, String operator, float width, float height) {
		this(terms, operator, null, null, null, 10, width, height);
	}

	public QueryData(String terms, String operator, String author, String yearStart, String yearEnd, int numClusters, 
			float width, float height) {
		super();
		this.terms = terms;
		this.operator = operator;
		this.author = author;
		this.yearStart = yearStart;
		this.yearEnd = yearEnd;
		this.numClusters = numClusters;
		this.width = width;
		this.height = height;
	}

	public String getTerms() {
		return terms;
	}

	public void setTerms(String terms) {
		this.terms = terms;
	}

	public String getOperator() {
		return operator;
	}

	public void setOperator(String operator) {
		this.operator = operator;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getYearStart() {
		return yearStart;
	}

	public void setYearStart(String yearStart) {
		this.yearStart = yearStart;
	}

	public String getYearEnd() {
		return yearEnd;
	}

	public void setYearEnd(String yearEnd) {
		this.yearEnd = yearEnd;
	}

	public int getNumClusters() {
		return numClusters;
	}
	
	public void setNumClusters(int numClusters) {
		this.numClusters = numClusters;
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
}
