/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.model;

import java.util.List;

import ep.db.quadtree.Vec2;

/**
 *
 * @author Márcio Peres
 */
public interface IDocument extends Comparable<IDocument>{
    

    public long getId();
    
    public void setId(long id);
    
    public String getDOI();
    
    public String getTitle();
    
    public String getKeywords();
    
    public List<Author> getAuthors();
    
    public String getAbstract();
    
    public String getPublicationDate();
    
    public String getVolume();
    
    public String getPages();
    
    public String getIssue();
    
    public String getContainer();
    
    public String getISSN();
    
    public String getLanguage();
    
    public int getCluster();
    
    public void setCluster(int cluster);
    
    public String getPath();
    
    public List<Long> getReferences();
    
    public void setReferences(List<Long> references);
    
    public double getRadius();
    
    public void setRadius(double r);

    public float getRank();
    
    public float getX();

    public float getY();

    public Vec2 getPos();
    
    @Override
    public int compareTo(IDocument o);
}
