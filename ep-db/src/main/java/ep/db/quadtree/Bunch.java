/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.quadtree;

import java.util.ArrayList;
import java.util.List;

import ep.db.model.IDocument;

/**
 *
 * @author Márcio Peres
 */
public class Bunch {

    private List<IDocument> elements;
   
    public Bunch() {
        elements = new ArrayList<>();
    }

    public List<IDocument> getDocuments() {
        return elements;
    }

    public boolean addElements(List<IDocument> documents) {
        if (elements.size() + documents.size() <= QuadTree.MAX_ELEMENT_PER_BUNCH) {
            elements.addAll(documents);
            return true;
        }
        return false;
    }

    public boolean addElement(IDocument b) {
        if (elements.size() < QuadTree.MAX_ELEMENT_PER_BUNCH) {
            elements.add(b);
            return true;
        }
        return false;
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public IDocument getElement(int index) {
        if (index < elements.size()) {
            return elements.get(index);
        }
        return null;
    }
}
