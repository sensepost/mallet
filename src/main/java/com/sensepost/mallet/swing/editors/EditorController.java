package com.sensepost.mallet.swing.editors;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class EditorController {
	
	public static final String OBJECT = "object";
	public static final String READ_ONLY = "readOnly";
	
	private PropertyChangeSupport pcs = new PropertyChangeSupport(this);
	private Object object;
	private boolean readOnly;

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(listener);
	}
	
	public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		pcs.addPropertyChangeListener(propertyName, listener);
	}
	
	public void removePropertyChangeListener(PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
		pcs.removePropertyChangeListener(propertyName, listener);
	}
	
	public Object getObject() {
		return object;
	}
	
	public void setObject(Object object) {
		Object old = this.getObject();
		this.object = object;
		pcs.firePropertyChange(OBJECT, old, this.object);
	}
	
	public boolean isReadOnly() {
		return readOnly;
	}
	
	public void setReadOnly(boolean readOnly) {
		boolean old = this.readOnly;
		this.readOnly = readOnly;
		pcs.firePropertyChange(READ_ONLY, old, this.readOnly);
	}
	
	protected void dispose() {
		PropertyChangeListener[] listeners = pcs.getPropertyChangeListeners();
		for (PropertyChangeListener l : listeners) {
			pcs.removePropertyChangeListener(l);
		}
	}
}
