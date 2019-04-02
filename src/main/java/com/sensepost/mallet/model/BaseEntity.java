package com.sensepost.mallet.model;

public class BaseEntity {
    
    private Integer id = null;
    
    /** Creates a new instance of BaseEntity */
    protected BaseEntity() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
    
    public boolean isNew() {
        return getId() == null;
    }

}