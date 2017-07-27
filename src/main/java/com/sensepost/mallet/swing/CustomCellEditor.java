package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.util.EventObject;

import javax.swing.JPanel;

import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.view.mxICellEditor;
import com.sensepost.mallet.graph.GraphNode;

public class CustomCellEditor implements mxICellEditor {

	private mxGraphComponent graphComponent;
	
	private JPanel graphNodePanel = new JPanel(new BorderLayout());
	
	public CustomCellEditor(mxGraphComponent graphComponent) {
		this.graphComponent = graphComponent;
	}

	@Override
	public Object getEditingCell() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void startEditing(Object cell, EventObject trigger) {
		Object value = graphComponent.getGraph().getModel().getValue(cell);
		if (value instanceof GraphNode) {
			GraphNode node = (GraphNode) value;
			
		}
	}

	@Override
	public void stopEditing(boolean cancel) {
		// TODO Auto-generated method stub
		return;
	}
	
	

}
