package ep.db.matrix;

public interface SVD {
	
	
	public void svd(Matrix matrix);
	
	public float[][] getV();
	
	public float[] getS();
	
	public float[][] getU();	
}
