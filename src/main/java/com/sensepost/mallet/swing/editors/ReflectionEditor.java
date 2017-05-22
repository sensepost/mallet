package com.sensepost.mallet.swing.editors;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.AbstractAction;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

import org.jdesktop.swingx.JXTreeTable;
import org.jdesktop.swingx.treetable.AbstractTreeTableModel;

@SuppressWarnings("serial")
public class ReflectionEditor extends JPanel implements ObjectEditor {

	private static WeakHashMap<Class<?>, Field[]> fieldCache = new WeakHashMap<Class<?>, Field[]>();
	private ObjectTreeModel otm = new ObjectTreeModel();

	private RevertAction revertAction = new RevertAction();
	private ExecAction execAction;

	private ScriptEngineManager sem = new ScriptEngineManager();

	public ReflectionEditor() {
		setLayout(new BorderLayout());

		JSplitPane editorSplitPane = new JSplitPane();
		editorSplitPane.setPreferredSize(new Dimension(400, 200));
		editorSplitPane.setMinimumSize(new Dimension(400, 200));
		editorSplitPane.setOneTouchExpandable(true);
		editorSplitPane.setResizeWeight(0.5);
		editorSplitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
		add(editorSplitPane, BorderLayout.CENTER);

		JScrollPane objectScrollPane = new JScrollPane();
		editorSplitPane.setLeftComponent(objectScrollPane);

		JXTreeTable objectTree = new JXTreeTable();
		objectScrollPane.setViewportView(objectTree);
		objectTree.setRootVisible(true);
		objectTree.setTreeTableModel(otm);
		objectTree.setTreeCellRenderer(new NodeRenderer());
		objectTree.addTreeSelectionListener(new TreeListener());
		objectTree.setDefaultRenderer(Class.class, new ClassRenderer());
		objectTree.setDefaultRenderer(Object.class, new ValueRenderer());

		JPanel scriptPanel = new JPanel();
		editorSplitPane.setBottomComponent(scriptPanel);
		scriptPanel.setLayout(new BorderLayout(0, 0));

		JComboBox<String> engineBox = new JComboBox<String>();
		scriptPanel.add(engineBox, BorderLayout.NORTH);
		engineBox.setModel(new DefaultComboBoxModel<String>(getScriptLanguages()));

		JSplitPane scriptSplitPane = new JSplitPane();
		scriptPanel.add(scriptSplitPane, BorderLayout.CENTER);
		scriptSplitPane.setResizeWeight(0.5);

		JScrollPane scriptScrollPane = new JScrollPane();
		scriptSplitPane.setLeftComponent(scriptScrollPane);

		JTextArea scriptTextArea = new JTextArea();
		scriptTextArea.setText("object");
		scriptScrollPane.setViewportView(scriptTextArea);

		JScrollPane resultScrollPane = new JScrollPane();
		scriptSplitPane.setRightComponent(resultScrollPane);

		JTextArea scriptResultTextArea = new JTextArea();
		resultScrollPane.setViewportView(scriptResultTextArea);
		execAction = new ExecAction(scriptTextArea, scriptResultTextArea, engineBox);

		JPanel buttonPanel = new JPanel();
		scriptPanel.add(buttonPanel, BorderLayout.SOUTH);

		JButton execButton = new JButton("");
		buttonPanel.add(execButton);
		execButton.setAction(execAction);

		JButton revertButton = new JButton("");
		buttonPanel.add(revertButton);
		revertButton.setAction(revertAction);
	}

	private String[] getScriptLanguages() {
		List<ScriptEngineFactory> factories = sem.getEngineFactories();
		String[] languages = new String[factories.size()];
		int i = 0;
		for (ScriptEngineFactory f : factories)
			languages[i++] = f.getLanguageName();
		return languages;
	}

	private PropertyChangeListener listener = new PropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (EditorController.OBJECT.equals(evt.getPropertyName())) {
				otm.setObject(evt.getNewValue());
			}
		}

	};

	private EditorController controller = null;

	@Override
	public JComponent getComponent() {
		return this;
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return new Class<?>[] { Object.class };
	}

	@Override
	public void setEditorController(EditorController controller) {
		if (this.controller != null)
			controller.removePropertyChangeListener(listener);
		this.controller = controller;
		if (this.controller != null) {
			controller.addPropertyChangeListener(listener);
			otm.setObject(controller.getObject());
		} else {
			otm.setObject(null);
		}
	}

	private void uiError(Exception e) {
		JOptionPane.showMessageDialog(this, e.getLocalizedMessage(), "Error", JOptionPane.ERROR_MESSAGE);
	}

	private Class<?>[] getGenericTypes(Node node) {
		Class<?>[] type = null;
		if (node instanceof FieldNode) {
			FieldNode fn = (FieldNode) node;
			Field f = fn.getField();
			Type t = f.getGenericType();
			if (t instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) t;
				Type[] ta = pt.getActualTypeArguments();
				if (ta != null) {
					type = new Class<?>[ta.length];
					for (int i = 0; i < ta.length; i++) {
						type[i] = (Class<?>) ta[i];
					}
				}
			}
		}
		return type;
	}

	private class ObjectTreeModel extends AbstractTreeTableModel {

		private Object object = null;
		private Node root = new RootNode();

		public void setObject(Object object) {
			this.object = object;
			modelSupport.fireNewRoot();
		}

		public Object getObject() {
			return object;
		}

		@Override
		public Object getRoot() {
			return root;
		}

		private Field[] getFields(Class<?> type) {
			Field[] fields = fieldCache.get(type);
			if (fields != null)
				return fields;
			List<Field> list = new ArrayList<Field>();
			getFields(type, list);
			fields = list.toArray(new Field[list.size()]);
			fieldCache.put(type, fields);
			return fields;
		}

		private void getFields(Class<?> type, List<Field> list) {
			Field[] fields = type.getDeclaredFields();
			for (Field f : fields) {
				int mod = f.getModifiers();
				if (!Modifier.isStatic(mod)) {
					f.setAccessible(true);
					list.add(f);
				}
			}
			if (type != Object.class)
				getFields(type.getSuperclass(), list);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Object getChild(Object parent, int index) {
			Node node = (Node) parent;
			Object value = node.getValue();
			if (value.getClass().isArray()) {
				return new ArrayNode(value, index);
			} else if (List.class.isAssignableFrom(value.getClass())) {
				List list = (List) value;
				Class<?>[] types = getGenericTypes(node);
				return new ListNode(list, types, index);
			} else if (Map.class.isAssignableFrom(value.getClass())) {
				Map map = (Map) value;
				Object key = map.keySet().toArray()[index];
				Class<?>[] types = getGenericTypes(node);
				return new MapNode(map, types, key);
			} else {
				Field field = getFields(value.getClass())[index];
				return new FieldNode(value, field);
			}
		}

		@SuppressWarnings("rawtypes")
		private int getChildCountForObject(Object value) {
			if (value == null)
				return 0;
			if (value.getClass().isArray())
				return Array.getLength(value);
			else if (List.class.isAssignableFrom(value.getClass()))
				return ((List) value).size();
			else if (Map.class.isAssignableFrom(value.getClass()))
				return ((Map) value).size();
			else
				return getFields(value.getClass()).length;
		}

		@Override
		public int getChildCount(Object parent) {
			if (isLeaf(parent))
				return 0;
			return getChildCountForObject(((Node) parent).getValue());
		}

		private boolean isEditableType(Class<?> type) {
			return type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type);
		}

		@Override
		public boolean isLeaf(Object parent) {
			Node node = (Node) parent;
			Object value = node.getValue();
			return value == null || isEditableType(value.getClass());
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) {
			Node node = (Node) path.getLastPathComponent();
			Object oldValue = node.getValue();
			revertAction.setRevert(path, oldValue);
			try {
				node.setValue(newValue);
				modelSupport.fireTreeStructureChanged(path);
			} catch (Exception e) {
				uiError(e);
			}

		}

		@Override
		public Class<?> getColumnClass(int column) {
			switch (column) {
			case 0:
				return super.getColumnClass(column);
			case 1:
				return Class.class;
			case 2:
				return Class.class;
			case 3:
				return Object.class;
			}
			throw new IndexOutOfBoundsException("Column out of range: " + column);
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
			case 0:
				return "";
			case 1:
				return "Declared Type";
			case 2:
				return "Value Type";
			case 3:
				return "Value";
			}
			throw new IndexOutOfBoundsException("Column out of range: " + column);
		}

		@Override
		public boolean isCellEditable(Object node, int column) {
			return column == 3 || super.isCellEditable(node, column);
		}

		@Override
		public void setValueAt(Object value, Object node, int column) {
			// TODO
			Node n = (Node) node;
			try {
				Class<?> type = n.getType();
				if (value != null && value.getClass() != type) {
					if (value.getClass() == String.class) {
						String s = (String) value;
						if (type == char.class && s.length() == 1) {
							value = s.charAt(0);
						} else if (type == char[].class) {
							value = s.toCharArray();
						} else if (type == byte[].class) {
							value = s.getBytes();
						} else if (type == byte.class || type == Byte.class) {
							value = Byte.parseByte(s);
						} else if (type == double.class || type == Double.class) {
							value = Double.parseDouble(s);
						} else if (type == int.class || type == Integer.class) {
							value = Integer.parseInt(s);
						} else if (type == long.class || type == Long.class) {
							value = Long.parseLong(s);
						} else if (type == short.class || type == Short.class) {
							value = Short.parseShort(s);
						}
					}
				}
				n.setValue(value);
			} catch (RuntimeException e) {
				uiError(e);
			}
		}

		public class RootNode implements Node {

			@Override
			public Object getValue() {
				return object;
			}

			@Override
			public void setValue(Object value) {
				object = value;
			}

			public String toString() {
				return ReflectionEditor.toString(object == null ? Object.class : object.getClass(), object);
			}

			public Class<?> getType() {
				return object == null ? Object.class : object.getClass();
			}
		}

		@Override
		public int getColumnCount() {
			return 4;
		}

		@Override
		public Object getValueAt(Object node, int column) {
			switch (column) {
			case 0:
				return node;
			case 1:
				return ((Node) node).getType();
			case 2:
				return ((Node) node).getValue() == null ? "" : ((Node) node).getValue().getClass();
			case 3:
				return ((Node) node).getValue();
			}
			return null;
		}

		@Override
		public int getIndexOfChild(Object parent, Object child) {
			if (child instanceof ArrayNode) {
				return ((ArrayNode) child).index;
			} else if (child instanceof ListNode) {
				return ((ListNode) child).index;
			} else if (child instanceof MapNode) {
				MapNode node = (MapNode) child;
				Iterator<Object> it = node.map.keySet().iterator();
				int i = 0;
				for (; it.hasNext(); i++)
					if (it.next() == node.key)
						return i;
				throw new RuntimeException("Didn't find " + node.key + " in " + node.map.keySet());
			} else if (child instanceof FieldNode) {
				FieldNode node = (FieldNode) child;
				Field[] fields = getFields(node.object.getClass());
				for (int i = 0; i < fields.length; i++) {
					if (fields[i] == node.field)
						return i;
				}
			}
			throw new RuntimeException("Didn't find child " + child + " in " + parent);
		}
	}

	private static String toString(Class<?> type, Object value) {
		StringBuilder b = new StringBuilder();

		if (type == null) {
			b.append("null");
		} else if (type.isArray()) {
			b.append(type.getComponentType().getSimpleName());
			b.append("[").append(value == null ? "" : Array.getLength(value)).append("]");
		} else {
			b.append(type.getSimpleName());
		}
		b.append(" = ");
		if (value == null)
			b.append("null");
		else if (value.getClass().isArray()) {
			type = value.getClass().getComponentType();

			if (type == boolean.class)
				b.append(Arrays.toString((boolean[]) value));
			else if (type == byte.class)
				b.append(Arrays.toString((byte[]) value));
			else if (type == char.class)
				b.append(Arrays.toString((char[]) value));
			else if (type == double.class)
				b.append(Arrays.toString((double[]) value));
			else if (type == float.class)
				b.append(Arrays.toString((float[]) value));
			else if (type == int.class)
				b.append(Arrays.toString((int[]) value));
			else if (type == long.class)
				b.append(Arrays.toString((long[]) value));
			else if (type == short.class)
				b.append(Arrays.toString((short[]) value));
			else
				b.append(Arrays.toString((Object[]) value));
		} else {
			b.append(value);
		}
		return b.toString();
	}

	private interface Node {
		Object getValue();

		void setValue(Object value);

		Class<?> getType();
	}

	private static class FieldNode implements Node {
		private Field field;
		private Object object;

		public FieldNode(Object object, Field field) {
			this.object = object;
			this.field = field;
		}

		public Field getField() {
			return field;
		}

		public Object getObject() {
			return object;
		}

		public Object getValue() {
			try {
				return field.get(object);
			} catch (IllegalAccessException e) {
				// should never happen
				return null;
			}
		}

		public void setValue(Object value) throws ClassCastException {
			try {
				field.set(object, value);
			} catch (IllegalAccessException e) {
				// should never happen
			}
		}

		public Class<?> getType() {
			return field.getType();
		}

		public String toString() {
			StringBuffer b = new StringBuffer();
			b.append(field.getName()).append(" : ");
			b.append(ReflectionEditor.toString(field.getType(), getValue()));
			return b.toString();
		}
	}

	private static class ListNode implements Node {
		private List<Object> list;
		private Class<?> type;
		private int index;

		public ListNode(List<Object> list, Class<?>[] types, int index) {
			this.list = list;
			this.type = types != null && types.length == 1 ? types[0] : Object.class;
			this.index = index;
		}

		public Object getValue() {
			return list.get(index);
		}

		public void setValue(Object newValue) {
			list.set(index, newValue);
		}

		public Class<?> getType() {
			return type;
		}

		public String toString() {
			StringBuffer b = new StringBuffer();
			b.append("(").append(index).append(") : ");
			b.append(ReflectionEditor.toString(type, getValue()));
			return b.toString();
		}
	}

	private static class MapNode implements Node {
		private Map<Object, Object> map;
		private Class<?>[] types;
		private Object key;

		public MapNode(Map<Object, Object> map, Class<?>[] types, Object key) {
			this.map = map;
			this.types = types != null && types.length == 2 ? types : new Class<?>[] { Object.class, Object.class };
			this.key = key;
		}

		@Override
		public Object getValue() {
			return map.get(key);
		}

		@Override
		public void setValue(Object value) {
			map.put(key, value);
		}

		public Class<?> getType() {
			return types[1];
		}

		public String toString() {
			StringBuffer b = new StringBuffer();
			b.append(ReflectionEditor.toString(types[0], key)).append(" : ");
			Object value = getValue();
			b.append(ReflectionEditor.toString(types[1], value));
			return b.toString();
		}

	}

	private static class ArrayNode implements Node {

		private Object array;
		private int index;

		public ArrayNode(Object array, int index) {
			this.array = array;
			this.index = index;
		}

		public Object getValue() {
			return Array.get(array, index);
		}

		public void setValue(Object value) throws ClassCastException {
			Array.set(array, index, value);
		}

		public Class<?> getType() {
			return array.getClass().getComponentType();
		}

		public String toString() {
			StringBuffer b = new StringBuffer();
			b.append("[").append(index).append("] : ");
			b.append(ReflectionEditor.toString(array.getClass().getComponentType(), getValue()));
			return b.toString();
		}

	}

	private class RevertAction extends AbstractAction {
		private TreePath path = null;
		private Object oldValue = null;

		public RevertAction() {
			super("Undo");
			putValue(SHORT_DESCRIPTION, "Undoes the previous execution");
			setToolTipText("Undoes the previous operation");
			setEnabled(false);
		}

		public void setRevert(TreePath path, Object oldValue) {
			this.path = path;
			this.oldValue = oldValue;
			setEnabled(path != null);
		}

		@Override
		public void actionPerformed(ActionEvent ae) {
			Object newValue = null;
			if (path.getPathCount() == 1) {
				newValue = otm.getObject();
				otm.setObject(oldValue);
				this.oldValue = newValue;
			} else {
				FieldNode node = (FieldNode) path.getLastPathComponent();
				try {
					newValue = node.getField().get(node.getObject());
					node.getField().set(node.getObject(), oldValue);
					this.oldValue = newValue;
				} catch (IllegalAccessException e) {
					setRevert(null, null);
				}
			}
		}

	}

	private class ExecAction extends AbstractAction {

		private JComboBox<String> engineBox;

		private JTextComponent src, dst;
		private TreePath path = null;

		public ExecAction(JTextComponent src, JTextComponent dst, JComboBox<String> engineBox) {
			super("Execute");
			putValue(SHORT_DESCRIPTION, "Execute the script");
			this.src = src;
			this.dst = dst;
			this.engineBox = engineBox;
			setToolTipText("Execute the script");
			setEnabled(false);
		}

		public void setPath(TreePath path) {
			this.path = path;
			setEnabled(path != null);
		}

		@Override
		public void actionPerformed(ActionEvent ae) {
			String script = src.getSelectedText();
			if (script == null)
				script = src.getText();
			String language = (String) engineBox.getSelectedItem();
			ScriptEngine engine = sem.getEngineByName(language);
			try {
				Object object = ((Node) path.getLastPathComponent()).getValue();
				Bindings bindings = engine.createBindings();
				bindings.put("object", object);
				Object result = engine.eval(script, bindings);
				dst.setText(String.valueOf(result));
				object = bindings.get("object");
				otm.valueForPathChanged(path, object);
			} catch (ScriptException e) {
				JOptionPane.showMessageDialog(src, e.getLocalizedMessage(), "Script error", JOptionPane.ERROR_MESSAGE);
			}
		}

	}

	private class NullAction extends AbstractAction {
		private TreePath path = null;

		public NullAction() {
			super("Null");
			setToolTipText("Set the field to null");
			setEnabled(false);
		}

		public void setPath(TreePath path) {
			this.path = path;
			setEnabled(path != null);
		}

		@Override
		public void actionPerformed(ActionEvent ae) {
			otm.valueForPathChanged(path, null);
		}
	}

	private static class NodeRenderer extends DefaultTreeCellRenderer {

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
				boolean leaf, int row, boolean hasFocus) {
			if (value == null) {
				value = "null";
			} else if (value instanceof FieldNode) {
				value = ((FieldNode) value).getField().getName();
			} else if (value instanceof ArrayNode) {
				value = "[" + ((ArrayNode) value).index + "]";
			} else if (value instanceof ListNode) {
				value = "(" + ((ListNode) value).index + ")";
			} else if (value instanceof MapNode) {
				value = ((MapNode) value).key;
			} else if (value instanceof com.sensepost.mallet.swing.editors.ReflectionEditor.ObjectTreeModel.RootNode) {
				value = "";
			}
			return super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
		}

	}

	private static class ClassRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			if (value instanceof Class) {
				Class<?> type = (Class<?>) value;
				if (type.isArray()) {
					value = type.getComponentType().getSimpleName() + "[]";
				} else {
					value = type.getSimpleName();
				}
			}
			return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		}

	}

	private static class ValueRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			if (value == null) {
				value = "null";
			} else {
				Class<?> type = value.getClass();
				if (type.isArray()) {
					value = ReflectionEditor.toString(type, value);
				}
			}
			return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		}

	}

	private class TreeListener implements TreeSelectionListener {

		@Override
		public void valueChanged(TreeSelectionEvent e) {
			execAction.setPath(e.getPath());
		}

	}

}
