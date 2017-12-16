/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.quadtree;

import java.util.ArrayList;
import java.util.List;

import ep.db.model.Document;
import ep.db.model.IDocument;

/**
 *
 * @author Márcio Peres
 */
//////////////////////////////////////////////////////////
//
// QuadtreeLeafNode: quadtree leaf node class
// ================
public class QuadTreeLeafNode extends QuadTreeNode {
    
	private Bunch[] data;
    private int nTotalDocuments;
    private int nDocuments;

    public void setnTotalDocuments(int nTotalDocuments) {
        this.nTotalDocuments = nTotalDocuments;
    }
    
    public QuadTreeLeafNode() {
        nodeId = numberOfNodes++;
        data = new Bunch[QuadTree.maxElementsPerLeaf / QuadTree.maxElementsPerBunch]; //25 por Bunch X 4 = 100 por Leaf
        nDocuments = 0;
    }

    public QuadTreeLeafNode(int depth, int index, int nodeId, float rankMax, float rankMin, QuadTreeBranchNode parent) {
        this.depth = depth;
        this.index = index;
        this.nodeId = nodeId;
        this.rankMax = rankMax;
        this.rankMin = rankMin;
        this.parent = parent;
        data = new Bunch[QuadTree.maxElementsPerLeaf / QuadTree.maxElementsPerBunch]; //25 por Bunch X 4 = 100 por Leaf
        nDocuments = 0;
    }

    public boolean addElement(int id, float rank, float x, float y) {
        return addElement(new Document(id, rank, x, y));
    }

    public boolean addElement(IDocument b) {
        if (nDocuments >= QuadTree.maxElementsPerLeaf) {
            return false;
        }
        int bunchIndex = nDocuments / QuadTree.maxElementsPerBunch;

        if (data[bunchIndex] == null) {
            data[bunchIndex] = new Bunch();
        }
        
        if(++nDocuments > nTotalDocuments){
            nTotalDocuments = nDocuments;
        }
        data[bunchIndex].addElement(b);

        updateRankPathRoot(b.getRank());
        return true;
    }

    private void updateRankPathRoot(float rank) {
        if (rank < rankMin) {
            rankMin = rank;
            parent.updateRankPathRoot(rank);
        }
        if (rank > rankMax) {
            rankMax = rank;
            parent.updateRankPathRoot(rank);
        }
    }

    public int size() {
        return nTotalDocuments;
    }

    public IDocument getDocument(int index, String query) {
        if (index >= nTotalDocuments) {
            return null;
        }
        int bunchIndex = index / QuadTree.maxElementsPerBunch;
        if (data[bunchIndex] == null || data[bunchIndex].isEmpty()) {
            data[bunchIndex] = new Bunch();
            loadBunch(bunchIndex, query);
        }
        int elementIndex = index % QuadTree.maxElementsPerBunch;
        if(elementIndex < data[bunchIndex].size()){
            return data[bunchIndex].getElement(elementIndex);
        } 
        return null;
    }

    public void loadBunch(int indexBunch, String query) {
        //Load the Documents from the Bunch from Data Base
        List<IDocument> documents;
		try {
			documents = QuadTree.dbService.getDocumentsFromNode(nodeId, query, indexBunch*QuadTree.maxElementsPerBunch, QuadTree.maxElementsPerBunch);
		} catch (Exception e) {
			e.printStackTrace();
			documents = null;
			return;
		}
		
        if(data[indexBunch].addElements(documents)){
            nDocuments += documents.size();
        }
    }

    @Override
    public void getDocuments(List<IDocument> documents) {
        getDocuments(documents, null);
    }

    public void getDocuments(List<IDocument> documents, Bounds rectangle) {
        if (rectangle == null) {
            for (Bunch bunch : data) {
                if (bunch != null) {
                    documents.addAll(bunch.getDocuments());
                }
            }
        } else {
            for (Bunch bunch : data) {
                if (bunch != null) {
                    for (IDocument document : bunch.getDocuments()) {
                        if (rectangle.contains(document.getPos())) {
                            documents.add(document);
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<IDocument> getElementsByRank(List<IDocument> documents, float totalRankArea, Bounds rectangle, Boolean filledArea) {
        //Se a lista está preenchida e o RankMáximo do nó é menor que o Rank menor da lista, não há necessidade de percorrer o nó.
        if(filledArea && rankMax < documents.get(documents.size()-1).getRank()){
            return documents;
        }
        
        List<IDocument> documentsNew = new ArrayList<>();
        float totalRankAreaTemp = 0;
        int iList = 0, iLeaf = 0;
        while (totalRankArea > totalRankAreaTemp) {
            IDocument docList = null;
            IDocument docLeaf = null;
            if (iList < documents.size()) {
                docList = documents.get(iList);
            }
            if (iLeaf < nDocuments) {
                docLeaf = getDocument(iLeaf, null);
            }

            if (docList == null && docLeaf == null) {
                break;
            }

            if (docLeaf == null || 
                    (docList != null && docList.getRank() > docLeaf.getRank())) {
                totalRankAreaTemp += docList.getRank();
                documentsNew.add(docList);
                iList++;
            } else if (docLeaf != null) {
                if (rectangle == null || rectangle.contains(docLeaf.getPos())) {
                    totalRankAreaTemp += docLeaf.getRank();
                    documentsNew.add(docLeaf);
                }
                iLeaf++;
            }
        }
        return documentsNew;
    }
}
