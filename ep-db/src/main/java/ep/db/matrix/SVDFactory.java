package ep.db.matrix;

import ep.db.utils.Configuration;

public class SVDFactory {
	
		
	public static final int FULL_SVD = 0;
	
	public static final int RANDOM_SVD = 1;

	public static SVD createSVD(int svdType) throws IllegalArgumentException{
		if (svdType == RANDOM_SVD) {
			return new RandomizedSVD(2, Configuration.getInstance().getRandomSVDInterations());
		}
		else if (svdType == FULL_SVD) {
			return new SingularValueDecomposition();
		}
		throw new IllegalArgumentException("SVD type not supported!");
	}

}
