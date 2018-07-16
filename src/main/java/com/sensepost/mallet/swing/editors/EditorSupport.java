package com.sensepost.mallet.swing.editors;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;

public abstract class EditorSupport<T extends JComponent> implements ObjectEditor {

	private EditorController controller = null;

	private boolean objectUpdating = false;

	private String name;

	private Class<?>[] supportedClasses;

	private T component;

	private PropertyChangeListener listener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (evt.getSource() != controller)
				return;
			if (objectUpdating)
				return;
			if (EditorController.OBJECT.equals(evt.getPropertyName())
					|| EditorController.READ_ONLY.equals(evt.getPropertyName())) {
				objectUpdating = true;
				updateEditObject(controller.getObject(), !controller.isReadOnly());
				objectUpdating = false;
			}
		}

	};

	protected EditorSupport(String name, Class<?>[] supportedClasses, T component) {
		this.name = name;
		this.supportedClasses = supportedClasses.clone();
		this.component = component;
	}
	
	private void updateEditObject(Object o, boolean editable) {
		if (o != null) {
			Class<?> c = o.getClass();
			boolean supported = false;
			for (int i=0; i<supportedClasses.length; i++) {
				if (supportedClasses[i].isAssignableFrom(c)) {
					supported = true;
					break;
				}
			}
			if (supported) {
				setEditObject(o, editable);
			} else {
				setEditObject(null, false);
			}
		} else {
			setEditObject(null, false);
		}
	}
	
	@Override
	public void setEditorController(EditorController controller) {
		if (this.controller != null)
			controller.removePropertyChangeListener(listener);
		this.controller = controller;
		objectUpdating = true;
		if (this.controller != null) {
			controller.addPropertyChangeListener(listener);
			updateEditObject(controller.getObject(), !controller.isReadOnly());
		} else {
			setEditObject(null, true);
		}
		objectUpdating = false;
	}

	public abstract void setEditObject(Object o, boolean editable);

	public boolean isUpdating() {
		return objectUpdating;
	}
	
	public void setUpdatedObject(Object o) {
		if (objectUpdating)
			return;
		objectUpdating = true;
		controller.setObject(o);
		objectUpdating = false;
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return supportedClasses.clone();
	}
	
	@Override
	public String getEditorName() {
		return name;
	}
	
	@Override
	public T getEditorComponent() {
		return component;
	}

}
