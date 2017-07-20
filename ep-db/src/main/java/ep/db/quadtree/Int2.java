package ep.db.quadtree;

public class Int2
{
  public int x;
  public int y;

  public Int2()
  {
    // do nothing
  }

  public Int2(int x, int y)
  {
    this.x = x;
    this.y = y;
  }

  public Int2(Int2 i)
  {
    this(i.x, i.y);
  }

	@Override
	public String toString()
	{
		return String.format("(%d,%d)", x, y);
	}

  public void print(String label)
  {
    System.out.println(label + toString());
  }

} // Int2
