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
public class QuadTreeKey extends Int2{

    QuadTreeKey() {
        // do nothing
    }

    QuadTreeKey(int i) {
        x = y = i;
    }

    QuadTreeKey(int i, int j) {
        x = i;
        y = j;
    }

    QuadTreeKey(Vec2 v) {
        x = (int) v.x;
        y = (int) v.y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    QuadTreeKey pushChild(int index) {
        int x = (this.x << 1) | ((index & (1 << 1))==0 ? 0 : 1);
        int y = (this.y << 1) | ((index & (1 << 0))==0 ? 0 : 1);
        return new QuadTreeKey(x, y);
    }

    QuadTreeKey popChild() {
        int x = this.x >> 1;
        int y = this.y >> 1;
        return new QuadTreeKey(x, y);
    }

    int childIndex(long mask) {
        return (((x & mask)==0 ? 0 : 1) << 1) | ((y & mask)==0 ? 0 : 1);
    }

    QuadTreeKey childKey(int i) {
        return this.pushChild(i);
    }
}
