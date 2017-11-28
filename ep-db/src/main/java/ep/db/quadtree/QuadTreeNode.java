/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.quadtree;

import java.util.List;

import ep.db.model.IDocument;

/**
 *
 * @author Márcio Peres
 */
//////////////////////////////////////////////////////////
//
// QuadtreeNode: generic quadtree node class
// ============
public abstract class QuadTreeNode {

    static long numberOfNodes = 0L;
    protected QuadTreeBranchNode parent;
    protected long nodeId;
    protected int index;
    protected int depth;
    protected float rankMax = Float.MIN_VALUE;
    protected float rankMin = Float.MAX_VALUE;

    public boolean isLeaf() {
        return true;
    }

    public abstract void getDocuments(List<IDocument> documents);

    public abstract List<IDocument> getElementsByRank(List<IDocument> documents, float totalRankArea, Bounds rectangle, Boolean filledArea);

    public QuadTreeBranchNode getParent() {
        return parent;
    }

    public long getNodeId() {
        return nodeId;
    }

    public int getIndex() {
        return index;
    }

    public int getDepth() {
        return depth;
    }

    public float getRankMax() {
        return rankMax;
    }

    public float getRankMin() {
        return rankMin;
    }

    public long getParentNodeId() {
        if (parent != null) {
            return parent.getNodeId();
        }
        return -1;
    }

}; // QuadtreeNode

