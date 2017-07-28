package com.sensepost.mallet.graph;

import java.io.Serializable;
import java.util.Arrays;

public class GraphNode implements Serializable {

	private String className;
	private String[] arguments;
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	public String[] getArguments() {
		return arguments;
	}
	public void setArguments(String[] arguments) {
		this.arguments = arguments;
	}
	
	@Override
	public String toString() {
		return className; // + (arguments == null ? "" : "\n" + Arrays.asList(arguments));
	}
}
