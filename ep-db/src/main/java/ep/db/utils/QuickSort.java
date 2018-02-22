package ep.db.utils;

public class QuickSort {

	public static void sort(double[] p, int[] indices, int from, int to)
	{
		while (from < to)
		{
			int pi = partition(p, indices, from, to);

			if (pi - from < to - pi)
			{
				sort(p, indices, from, pi - 1);
				from = pi + 1;
			}
			else
			{
				sort(p, indices, pi + 1, to);
				to = pi - 1;
			}
		}
	}

	private static int partition (double p[], int[] indices, int from, int to)
	{
		double pivot = p[to];
		int i = from - 1;

		for (int j = from; j <= to- 1; j++)
			if (p[j] <= pivot)
				swap(p, indices, ++i, j);
		swap(p, indices, i + 1, to);
		return i + 1;
	}

	// Quicksort algorithm
	private static void swap(double[] p, int[] indices, int i, int j)
	{
		{
			double temp = p[i];

			p[i] = p[j];
			p[j] = temp;
		}
		{
			int temp = indices[i];

			indices[i] = indices[j];
			indices[j] = temp;
		}
	}
}
