package ep.db.quadtree;

/**
 *
 * @author Márcio Peres
 */
//////////////////////////////////////////////////////////
//
// Vec2: vector 2D class
// ======================
public class Vec2 {

	public static float squaredDistance(Vec2 p1, Vec2 p2) {
        float dx = p2.x - p1.x;
        float dy = p2.y - p1.y;

        return dx * dx + dy * dy;
    }

    public static float distance(Vec2 p1, Vec2 p2) {
        return (float) Math.sqrt(squaredDistance(p1, p2));
    }

    public float x;
    public float y;

    public Vec2() {
        // do nothing
    }

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vec2(Vec2 v) {
        this(v.x, v.y);
    }

    public Vec2 scale(float s) {
        x *= s;
        y *= s;               
        return this;
    }

    public Vec2 add(Vec2 t) {
        x += t.x;
        y += t.y;
        return this;
    }

    public Vec2 sub(Vec2 t) {
        x -= t.x;
        y -= t.y;
        return this;
    }
    
    public Vec2 inverse() {
        x = 1/x;
        y = 1/y;
        return this;
    }
    
    public Vec2 multiply(float s) {
        return scale(s);
    }

    public Vec2 multiply(Vec2 v) {
        x *= v.x;
        y *= v.y;
        return this;
    }

    @Override
    public String toString() {
        return String.format("<%.2f,%.2f>", x, y);
    }

    public void print(String label) {
        System.out.println(label + toString());
    }

/*
    public Vec2 scale(float s) {
        return new Vec2(this.x * s, this.y * s);
    }

    public Vec2 add(Vec2 t) {
        return new Vec2(this.x + t.x, this.y + t.y);
    }

    public Vec2 sub(Vec2 t) {
        return new Vec2(this.x - t.x, this.y - t.y);
    }
    
    public Vec2 inverse() {
        return new Vec2(1 / this.x, 1 / this.y);
    }
    
    public Vec2 multiply(float s) {
        return new Vec2(this.x * s, this.y * s);
    }

    public Vec2 multiply(Vec2 v) {
        return new Vec2(this.x * v.x, this.y * v.y);
    }
*/

} // Vec2
