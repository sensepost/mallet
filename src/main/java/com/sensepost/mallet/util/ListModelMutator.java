package com.sensepost.mallet.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

/**
 * Mutates one list into another
 * 
 * Lists will be sorted according to the natural order of the elements. Lists
 * may not contain nulls
 * 
 * @author rogan
 * 
 * @param <T>
 */
public class ListModelMutator<T extends Comparable<T>> {

	private DefaultListModel<T> model;

	public ListModelMutator(DefaultListModel<T> model) {
		this.model = model;
	}

	/**
	 * Mutates the model to contain the same elements as the target list
	 * 
	 * @param target
	 */
	public void mutate(List<T> target) {
		Collections.sort(target);
		if (model.size() == 0) {
			for (int i=0; i<target.size(); i++) {
				model.add(i, target.get(i));
			}
			return;
		}
		if (target.size() == 0) {
			model.clear();
			return;
		}
		int i = 0;
		do {
			T current = model.get(i);
			T future = target.get(i);
			int comp = current.compareTo(future);
			if (comp == 0) {
				// elements are the same
				i++;
			} else if (comp < 0) {
				model.remove(i);
			} else {
				model.add(i, future);
			}
		} while (i < target.size());
		while (model.size() > target.size())
			model.remove(i);
	}

	public static void main(String[] args) {
		final DefaultListModel<String> model = new DefaultListModel<>();
		final List<String> target = new ArrayList<>(Arrays.asList(new String[] { "a", "f", "l"}));
		
		model.addListDataListener(new ListDataListener() {

			@Override
			public void intervalAdded(ListDataEvent e) {
				System.out.println("Model : " + Collections.list(model.elements()));
				System.out.println("Target: " + target);
			}

			@Override
			public void intervalRemoved(ListDataEvent e) {
				System.out.println("Model : " + Collections.list(model.elements()));
				System.out.println("Target: " + target);
			}

			@Override
			public void contentsChanged(ListDataEvent e) {
				System.out.println("Model : " + Collections.list(model.elements()));
				System.out.println("Target: " + target);
			}
			
		});
		ListModelMutator<String> diff = new ListModelMutator<>(model);
		diff.mutate(target);
		target.add("b");
		diff.mutate(target);
		target.remove("l");
		diff.mutate(target);
		target.remove("b");
		diff.mutate(target);
	}
}
