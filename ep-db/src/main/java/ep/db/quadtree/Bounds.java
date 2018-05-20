/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.quadtree;

/**
 *
 * @author Márcio Peres
 */
public class Bounds {

	public Bounds() {
		p1 = new Vec2(+Float.MAX_VALUE, +Float.MAX_VALUE);
		p2 = new Vec2(-Float.MAX_VALUE, -Float.MAX_VALUE);
	}

	public Bounds(float x1, float y1, float x2, float y2){
		p1 = new Vec2(x1,y1);
		p2 = new Vec2(x2,y2);
	}
	
	public Bounds(Vec2 p, Vec2 size) {
		p1 = new Vec2(p);
		p2 = new Vec2(p.x + size.x, p.y + size.y);
	}

	public Bounds(Bounds b) {
		p1 = new Vec2(b.p1);
		p2 = new Vec2(b.p2);
	}

	public Vec2 getP1() {
		return new Vec2(p1);
	}

	public Vec2 getP2() {
		return new Vec2(p2);
	}

	public void inflate(float x, float y) {
		if (x < p1.x) {
			p1.x = x;
		}
		if (y < p1.y) {
			p1.y = y;
		}
		if (x > p2.x) {
			p2.x = x;
		}
		if (y > p2.y) {
			p2.y = y;
		}
	}

	public void inflate(float x) {
		inflate(x, x);
	}

	public void inflate(Vec2 p) {
		inflate(p.x, p.y);
	}

	public Vec2 center() {
		return new Vec2((p1.x + p2.x) * 0.5f, (p1.y + p2.y) * 0.5f);
	}

	public Bounds scale(float s) {
		Vec2 t = center().scale(1 - s);

		p1.x = p1.x * s + t.x;
		p1.y = p1.y * s + t.y;
		p2.x = p2.x * s + t.x;
		p2.y = p2.y * s + t.y;
		return this;
	}

	public Vec2 size() {
		return new Vec2(p2.x - p1.x, p2.y - p1.y);
	}

	public boolean contains(Vec2 p) {
		return p1.x <= p.x && p2.x >= p.x && p1.y <= p.y && p2.y >= p.y;
	}

	//0 - Don't Intersects
	//1 - Intersects
	//2 - Intersects and the node is INSIDE of the rectangle
	int intersect(Bounds node) {

		//Testing if the rectangle don't intersect the node
		//rectangle is on the LEFT of the node
		if (node.getP1().x > getP2().x) {
			return 0;
		}
		//rectangle is on the RIGHT of the node
		if (node.getP2().x <= getP1().x) {
			return 0;
		}
		//rectangle is on the TOP of the node
		if (node.getP1().y > getP2().y) {
			return 0;
		}
		//rectangle is on the BOTTON of the node
		if (node.getP2().y <= getP1().y) {
			return 0;
		}

		//Testing if the node is INSIDE of the rectangle
		boolean inside = node.getP1().x >= getP1().x;
		inside &= node.getP2().x < getP2().x;
		inside &= node.getP1().y >= getP1().y;
		inside &= node.getP2().y < getP2().y;

		//The node is INSIDE of the rectangle
		//The rectangle intecsects the node
		return inside ? 2 : 1;		
	}

	@Override
	public String toString() {
		return String.format("p1%s p2%s", p1.toString(), p2.toString());
	}

	public void print(String label) {
		System.out.println(label + toString());
	}

	private final Vec2 p1;
	private final Vec2 p2;
}
