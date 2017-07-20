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
// QuadtreeBranchNodeBase: quadtree branch node base class
// ======================
public class QuadTreeBranchNode extends QuadTreeNode {

    protected QuadTreeNode[] children;

    public QuadTreeBranchNode() {
        nodeId = numberOfNodes++;
        depth = 0;
        children = new QuadTreeNode[4];
    }

    public QuadTreeBranchNode(int depth, int index, int nodeId, float rankMax, float rankMin, QuadTreeBranchNode parent) {
        this.depth = depth;
        this.index = index;
        this.nodeId = nodeId;
        this.rankMax = rankMax;
        this.rankMin = rankMin;
        this.parent = parent;
        children = new QuadTreeNode[4];
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    public void setChild(int index, QuadTreeNode child) {
        children[index] = child;
        child.parent = this;
        child.index = index;
        child.depth = depth + 1;
    }

    public QuadTreeNode getChild(int index) {
        return children[index];
    }

    public QuadTreeLeafNode createLeafChild(int index) {
        QuadTreeLeafNode child = new QuadTreeLeafNode();

        setChild(index, child);
        return child;
    }

    public QuadTreeBranchNode createBranchChild(int index) {
        QuadTreeBranchNode child = new QuadTreeBranchNode();

        setChild(index, child);
        return child;
    }

    public boolean hasChild(int index) {
        //se há index != 0 então o child existe.
        return getChild(index) != null;
    }

    @Override
    public void getDocuments(List<IDocument> elements) {
        for (QuadTreeNode child : children) {
            if (child != null) {
                child.getDocuments(elements);
            }
        }
    }

    public void updateRankPathRoot(float rank) {
        if (rank < rankMin) {
            rankMin = rank;
            //Is not the Root
            if (parent != null) {
                parent.updateRankPathRoot(rank);
            }
        }
        if (rank > rankMax) {
            rankMax = rank;
            //Is not the Root
            if (parent != null) {
                parent.updateRankPathRoot(rank);
            }
        }
    }

    @Override
    public List<IDocument> getElementsByRank(List<IDocument> documents, float totalRankArea, Bounds rectangle, Boolean filledArea) {
        //Se a lista está preenchida e o RankMáximo do nó é menor que o Rank menor da lista, não há necessidade de percorrer o nó.
        if(filledArea && rankMax < documents.get(documents.size()-1).getRank()){
            return documents;
        }
        for (QuadTreeNode child : children) {
            if (child != null) {
                documents = child.getElementsByRank(documents, totalRankArea, rectangle, filledArea);
            }
        }
        return documents;
    }
}
