package com.sensepost.mallet.swing;

import java.util.prefs.Preferences;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableColumnModel;

public class TableColumnModelPersistence implements TableColumnModelListener {

	private Preferences prefs;
	private String key;

	public TableColumnModelPersistence(Preferences prefs, String key) {
		this.prefs = prefs;
		this.key = key;
	}

	@Override
	public void columnAdded(TableColumnModelEvent e) {
	}

	@Override
	public void columnRemoved(TableColumnModelEvent e) {
	}

	@Override
	public void columnMoved(TableColumnModelEvent e) {
	}

	@Override
	public void columnMarginChanged(ChangeEvent e) {
		StringBuilder buff = new StringBuilder();
		TableColumnModel tcm = (TableColumnModel) e.getSource();
		for (int i = 0; i < tcm.getColumnCount(); i++) {
			buff.append(tcm.getColumn(i).getPreferredWidth()).append(", ");
		}
		int length = buff.length();
		if (length>2)
			buff.delete(length-2, length);
		prefs.put(key, buff.toString());
	}

	@Override
	public void columnSelectionChanged(ListSelectionEvent e) {
	}

	public void apply(TableColumnModel model, int... widths) {
		String pref = prefs.get(key, "");
		if (pref != null) {
			try {
				String[] split = pref.split(", ");
				int[] tmp_widths = new int[split.length];
				for (int i=0; i<split.length; i++) {
					tmp_widths[i] = Integer.parseInt(split[i]);
				}
				widths = tmp_widths;
			} catch (NumberFormatException nfe) {
				// ignore
			}
		}
		for (int i=0; i<Math.min(widths.length, model.getColumnCount()); i++)
			model.getColumn(i).setPreferredWidth(widths[i]);
	}
}
