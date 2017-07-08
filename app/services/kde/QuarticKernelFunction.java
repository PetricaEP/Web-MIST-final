package services.kde;

public class QuarticKernelFunction implements KernelFunction {

	public QuarticKernelFunction() {

	}

	@Override
	public double apply(double ux, double uy) {
		if ( Math.abs(ux) < 1.0 && Math.abs(uy) < 1.0){
			double a = 1.0 - ux*ux;
			double b = 1.0 - uy*uy;
			return 15.0/16.0 * a*a * 15.0/16.0 * b*b;
		}
		return 0.0;
	}
}
