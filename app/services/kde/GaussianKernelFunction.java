package services.kde;

public class GaussianKernelFunction implements KernelFunction {

	@Override
	public double apply(double ux, double uy) {
		double a = 1.0 / (2.0 * Math.PI);
		double b = -(ux*ux + uy*uy) / (2.0);
		return a * Math.exp(b);
	}
}
