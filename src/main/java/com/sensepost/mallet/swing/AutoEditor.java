package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import com.sensepost.mallet.swing.editors.ByteArrayEditor;
import com.sensepost.mallet.swing.editors.ByteBufEditor;
import com.sensepost.mallet.swing.editors.EditorController;
import com.sensepost.mallet.swing.editors.HttpMessageEditor;
import com.sensepost.mallet.swing.editors.ObjectEditor;
import com.sensepost.mallet.swing.editors.ReflectionEditor;
import com.sensepost.mallet.swing.editors.StringEditor;
import com.sensepost.mallet.swing.editors.ToStringEditor;

public class AutoEditor extends JPanel implements ObjectEditor {

	private List<ObjectEditor> editors = new ArrayList<>();
	private JTabbedPane tabbedPane;
	private EditorController controller = null;

	public AutoEditor() {
		setLayout(new BorderLayout(0, 0));

		tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		add(tabbedPane);
		addEditors();
	}

	private void addEditor(ObjectEditor e) {
		editors.add(e);
		tabbedPane.addTab(e.getEditorName(), e.getEditorComponent());
	}

	private void addEditors() {
		addEditor(new StringEditor());
		addEditor(new HttpMessageEditor());
		addEditor(new ByteArrayEditor());
		addEditor(new ByteBufEditor());
		addEditor(new ToStringEditor());
		addEditor(new ReflectionEditor());
	}

	public void setEditorController(EditorController controller) {
		if (this.controller != null)
			this.controller.removePropertyChangeListener(listener);
		this.controller = controller;
		if (this.controller != null) {
			this.controller.addPropertyChangeListener(listener);
			Object o = this.controller.getObject();
			if (o == null)
				updateVisibleEditors(null);
			else
				updateVisibleEditors(o.getClass());
		}
		for (ObjectEditor e : editors) {
			e.setEditorController(controller);
		}
	}

	@Override
	public JComponent getEditorComponent() {
		return this;
	}

	@Override
	public String getEditorName() {
		return "Automatic";
	}

	private void updateVisibleEditors(Class<?> clazz) {
		for (int i = tabbedPane.getTabCount(); i > 0; i--) {
			tabbedPane.removeTabAt(i - 1);
		}
		if (clazz != null) {
			for (ObjectEditor e : editors) {
				Class<?>[] classes = e.getSupportedClasses();
				for (Class<?> c : classes) {
					if (c.isAssignableFrom(clazz)) {
						tabbedPane.addTab(e.getEditorName(), e.getEditorComponent());
						break;
					}
				}
			}
		}
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		List<Class<?>> classList = new ArrayList<>();
		for (ObjectEditor e : editors) {
			Class<?>[] classes = e.getSupportedClasses();
			for (int i = 0; i < classes.length; i++)
				classList.add(classes[i]);
		}
		return classList.toArray(new Class<?>[0]);
	}

	private PropertyChangeListener listener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (EditorController.OBJECT.equals(evt.getPropertyName())) {
				Class<?> n = evt.getNewValue() == null ? null : evt.getNewValue().getClass();
				Class<?> o = evt.getOldValue() == null ? null : evt.getOldValue().getClass();
				if (o != n) {
					updateVisibleEditors(n);
				}
			}
		}

	};

}
