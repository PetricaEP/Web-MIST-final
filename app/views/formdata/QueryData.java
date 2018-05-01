package views.formdata;

import play.data.validation.Constraints.Required;

public class QueryData {
	
	protected String terms;
	
	@Required
	protected String operator;
	
	protected String author;
	
	protected String yearStart;
	
	protected String yearEnd;
	
	protected float[] start;
	
	protected float[] end;
	
	private int numClusters = 10; 
	
	private int page;
	
	private String tabId;
	
	@Required
	protected int maxDocs;
	
	public QueryData() {
		
	}
	
	public QueryData(String tabId, String terms, String operator, float width, float height, int maxDocs, int page) {
		this(tabId, terms, operator, null, null, null, null, null, 10, maxDocs, page);
	}

	public QueryData(String tabId, String terms, String operator, String author, String yearStart, String yearEnd, 
			float[] start,  float[] end, int numClusters, int maxDocs, int page) {
		super();
		this.tabId = tabId;
		this.terms = terms;
		this.operator = operator;
		this.author = author;
		this.yearStart = yearStart;
		this.yearEnd = yearEnd;
		this.start = start;
		this.end = end;
		this.numClusters = numClusters;
		this.maxDocs = maxDocs;
		this.page = page;
	}

	public String getTabId() {
		return tabId;
	}

	public void setTabId(String tabId) {
		this.tabId = tabId;
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

	public int getMaxDocs() {
		return maxDocs;
	}

	public void setMaxDocs(int maxDocs) {
		this.maxDocs = maxDocs;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		this.page = page;
	}
}
