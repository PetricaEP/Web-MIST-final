package ep.db.extractor;

public class DocumentExtractionTask implements Runnable{
	
	
	private final String path;
	
	private final int depth;

	public DocumentExtractionTask(String path, int depth) {
		super();
		this.path = path;
		this.depth = depth;
	}

	public String getPath() {
		return path;
	}

	public int getDepth() {
		return depth;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + depth;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DocumentExtractionTask other = (DocumentExtractionTask) obj;
		if (depth != other.depth)
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}
}
