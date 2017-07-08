package ep.db.mdp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import cern.colt.matrix.tfloat.FloatMatrix1D;
import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.colt.matrix.tfloat.algo.DenseFloatAlgebra;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import cern.jet.math.tfloat.FloatFunctions;


public class ForceScheme {

	private final int maxIter;

	private final float tol;

	private final float fraction;

	private final float eps;

	private final Random rng = new Random();

	public ForceScheme() {
		this(50, 0f, 8.0f, 1e-5f);
	}

	public ForceScheme(int maxIter, float tol, float fraction, float eps) {
		this.maxIter = maxIter;
		this.tol = tol;
		this.fraction = fraction;
		this.eps = eps;
	}

	public FloatMatrix2D project(FloatMatrix2D xs) {
		// Gera matrix y aleatoriamente
		int n = xs.rows();
		FloatMatrix2D y = new DenseFloatMatrix2D(n, 2);
		for(int i = 0; i < n; i++)
			for(int j = 0; j < 2; j++)
				y.setQuick(i, j, rng.nextFloat());

		// Listas para iteração aleatoria nas linhas e colunas
		List<Integer> i = new ArrayList<>(n), j = new ArrayList<>(n);
		for (int k = 0; k < n; k++){
			i.add(k);
			j.add(k);
		}

		// Gradiente Descendente
		final DenseFloatAlgebra alg = new DenseFloatAlgebra();
		double prevDeltaSum = Double.POSITIVE_INFINITY;
		for (int iter = 0; iter < maxIter; iter++) {
			double deltaSum = 0;

			Collections.shuffle(i);
			for (int a : i) {
				Collections.shuffle(j);
				for (int b : j) {
					if (a == b)
						continue;

					FloatMatrix1D direction = y.viewRow(b).copy().assign(y.viewRow(a), FloatFunctions.minus);
					float d2 = (float) Math.max(Math.sqrt(direction.zDotProduct(direction)), eps);
					float delta = (xs.getQuick(a, b) - d2) / fraction;
					deltaSum += Math.abs(delta);
					direction.assign(FloatFunctions.mult(delta/d2));
					y.viewRow(b).assign(direction, FloatFunctions.plus);
				}
			}

			if (Math.abs(prevDeltaSum - deltaSum) < tol)
				break;
			prevDeltaSum = deltaSum;
		}

		return y;
	}

}