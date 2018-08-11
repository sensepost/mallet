package com.sensepost.mallet.swing;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.prefs.Preferences;

import javax.swing.JSplitPane;

public class SplitPanePersistence implements PropertyChangeListener {

	private Preferences prefs;
	private String item;
	
	public SplitPanePersistence(Preferences prefs) {
		this(prefs, "");
	}
	
	public SplitPanePersistence(Preferences prefs, String item) {
		this.prefs = prefs;
		this.item = item;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (!"dividerLocation".equals(evt.getPropertyName()))
			return;
		prefs.putInt(item+"dividerLocation", (int) evt.getNewValue());
	}

	public void apply(JSplitPane splitPane, int defaultLocation) {
		int location = prefs.getInt(item+"dividerLocation", defaultLocation);
		splitPane.setDividerLocation(location);
	}
}
