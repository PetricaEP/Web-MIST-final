package ep.db.grid;

public class XYTransformer {
	
	private int width;
	
	private int height;
	
	private float minX;
	
	private float maxX;
	
	private float minY;
	
	private float maxY;

	public XYTransformer(int width, int height, float minX, float maxX, float minY, float maxY) {
		super();
		this.width = width;
		this.height = height;
		this.minX = minX;
		this.maxX = maxX;
		this.minY = minY;
		this.maxY = maxY;
	}
	
	public float x(float x){
		return width * ( x - this.minX) / (this.maxX - this.minX);
	}
	
	public float y(float y){
		return height * (y - this.minY) / (this.maxY - this.minY);
	}

}
