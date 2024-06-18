package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import com.sensepost.mallet.model.ChannelEvent;
import com.sensepost.mallet.model.ChannelEvent.ChannelEventType;
import com.sensepost.mallet.model.ChannelEvent.ChannelMessageEvent;
import com.sensepost.mallet.model.ChannelEvent.ExceptionCaughtEvent;
import com.sensepost.mallet.model.ChannelEvent.UserEventTriggeredEvent;
import com.sensepost.mallet.swing.editors.EditorController;
import com.sensepost.mallet.swing.editors.ObjectEditor;

import io.netty.util.ReferenceCountUtil;

public class ConnectionDataPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private ListTableModelAdapter<ChannelEvent> tableModel = new ChannelEventListTableModelAdapter();
    private TableRowSorter<ListTableModelAdapter<ChannelEvent>> tableSorter = new TableRowSorter<ListTableModelAdapter<ChannelEvent>>(tableModel);

    private JButton dropButton, sendButton;

    private ConnectionData connectionData = null;
    private EditorController editorController = new EditorController();
    private ChannelEventRenderer channelEventRenderer = new ChannelEventRenderer();
    private DateRenderer dateRenderer = new DateRenderer(true);
    private DirectionRenderer directionRenderer = new DirectionRenderer();
    
    private ChannelMessageEvent editing = null;
    private JTable table;
    private Preferences prefs = Preferences.userNodeForPackage(ConnectionDataPanel.class)
            .node(ConnectionDataPanel.class.getSimpleName());

    public ConnectionDataPanel() {
        setLayout(new BorderLayout(0, 0));

        JSplitPane splitPane = new JSplitPane();
        splitPane.setOrientation(JSplitPane.VERTICAL_SPLIT);
        add(splitPane, BorderLayout.CENTER);

        JPanel pendingPanel = new JPanel();
        pendingPanel.setLayout(new BorderLayout(0, 0));
        splitPane.setBottomComponent(pendingPanel);
        SplitPanePersistence spp = new SplitPanePersistence(prefs);
        spp.apply(splitPane, 200);
        splitPane.addPropertyChangeListener(spp);

        ObjectEditor editor = new AutoEditor();
        editor.setEditorController(editorController);
        pendingPanel.add(editor.getEditorComponent(), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        pendingPanel.add(buttonPanel, BorderLayout.SOUTH);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));

        dropButton = new JButton("Drop");
        dropButton.addActionListener(new DropAction());
        dropButton.setEnabled(false);
        buttonPanel.add(dropButton);

        sendButton = new JButton("Send");
        sendButton.addActionListener(new SendAction());
        sendButton.setEnabled(false);
        buttonPanel.add(sendButton);
        
        ButtonGroup eventsButtons = new ButtonGroup();
        JRadioButton rdbtnAllEvents = new JRadioButton(new AllEventsAction());
        eventsButtons.add(rdbtnAllEvents);
        buttonPanel.add(rdbtnAllEvents);
                
        JRadioButton rdbtnImportantEvents = new JRadioButton(new ImportantEventsAction());
        rdbtnImportantEvents.setSelected(true);
        eventsButtons.add(rdbtnImportantEvents);
        buttonPanel.add(rdbtnImportantEvents);

        JPanel topPanel = new JPanel(new BorderLayout());
        splitPane.setTopComponent(topPanel);
        JScrollPane scrollPane = new JScrollPane();
        topPanel.add(scrollPane, BorderLayout.CENTER);
        
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        scrollPane.setViewportView(table);
        table.getSelectionModel().addListSelectionListener(new EventSelectionListener());
        table.setDefaultRenderer(Date.class, dateRenderer);
        table.setDefaultRenderer(ChannelEvent.class, channelEventRenderer);
        table.setDefaultRenderer(String.class, directionRenderer);
        table.setRowSorter(tableSorter);

        TableColumnModelPersistence tcmp = new TableColumnModelPersistence(prefs, "column_widths");
        tcmp.apply(table.getColumnModel(), 75, 75, 75, 200, 800);
        table.getColumnModel().addColumnModelListener(tcmp);
    }

    public void setConnectionData(ConnectionData connectionData) {
        if (this.connectionData == connectionData)
            return;

        this.connectionData = connectionData;

        if (connectionData != null) {
            tableModel.setListModel(connectionData.getEvents());
        } else {
            tableModel.setListModel(null);
        }
    }

    private class DirectionRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;
        private Icon clientToProxy = new ArrowIcon(ArrowIcon.EAST, 20, Color.blue);
        private Icon proxyToServer = new ArrowIcon(ArrowIcon.WEST, 20, Color.red);
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            Object base = table.getValueAt(0, column);
            Icon icon = null;
            if (base != null) {
                if (base.equals(value)) {
                    value = "Client to Proxy";
                    icon = clientToProxy;
                } else {
                    value = "Proxy to Server";
                    icon = proxyToServer;
                }
            } else
                value = "";
            setIcon(icon);
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        }

    }

    private class ChannelEventListTableModelAdapter extends ListTableModelAdapter<ChannelEvent> {

        private static final long serialVersionUID = 1L;

        public ChannelEventListTableModelAdapter() {
            super(new RowModel<ChannelEvent> () {
                private String[] columnNames = new String[] { "Received", "Sent", "Direction", "Event Type", "Value" };
                private Class<?>[] columnClasses = new Class<?>[] { Date.class, Date.class, String.class,
                        ChannelEventType.class, ChannelEvent.class };

                @Override
                public int getColumnCount() {
                    return columnNames.length;
                }

                @Override
                public Class<?> getColumnClass(int column) {
                    return columnClasses[column];
                }

                @Override
                public String getColumnName(int column) {
                    return columnNames[column];
                }

                @Override
                public Object getValueAt(ChannelEvent e, int columnIndex) {
                    switch (columnIndex) {
                    case 0:
                        return e.eventTime();
                    case 1:
                        return e.isExecuted() ? e.executionTime() : null;
                    case 2:
                        return e.channelId();
                    case 3:
                        return e.type();
                    case 4:
                        return e;
                    }
                    return null;
                }
                
            }, null);
        }

    }

    private class DropAction implements ActionListener {

        public void actionPerformed(ActionEvent e) {
            int n = table.getSelectedRow();
            if (n >= 0) {
                try {
                    connectionData.dropNextEvents(n);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                try {
                    connectionData.dropNextEvent();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            updateButtonState(null);
            if (n < table.getRowCount() - 1)
                table.getSelectionModel().setLeadSelectionIndex(n + 1);
        }
    }

    private class SendAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            int n = table.getSelectedRow();
            if (n >= 0) {
                if (editing != null && !editorController.isReadOnly()) {
                    Object o = editorController.getObject();
                    ReferenceCountUtil.retain(o);
                    editing.setMessage(o);
                    editing = null;
                    editorController.setObject(null);
                }
                try {
                    connectionData.executeNextEvents(n);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else {
                try {
                    connectionData.executeNextEvent();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            updateButtonState(null);
            if (n < table.getRowCount() - 1)
                table.getSelectionModel().setLeadSelectionIndex(n + 1);
        }
    }

    private class EventSelectionListener implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting())
                return;
            if (editing != null && !editorController.isReadOnly()) {
                Object o = editorController.getObject();
                ReferenceCountUtil.retain(o);
                editing.setMessage(o);
            }
            int selectedRow = table.getSelectedRow();
            if (selectedRow != -1)
                selectedRow = table.convertRowIndexToModel(selectedRow);
            ChannelEvent evt;
            if (selectedRow < 0)
                evt = null;
            else
                evt = connectionData.getEvents().getElementAt(selectedRow);
            System.out.println(evt);
            if (evt instanceof ChannelMessageEvent) {
                editing = (ChannelMessageEvent) evt;
                Object o = editing.getMessage();
                editorController.setObject(o);
                ReferenceCountUtil.release(o);
                editorController.setReadOnly(evt.isExecuted());
            } else if (evt instanceof ExceptionCaughtEvent) {
                editing = null;
                editorController.setObject(((ExceptionCaughtEvent) evt).cause());
                editorController.setReadOnly(true);
            } else if (evt instanceof UserEventTriggeredEvent) {
                editing = null;
                editorController.setObject(((UserEventTriggeredEvent) evt).userEvent());
                editorController.setReadOnly(true);
            } else {
                editing = null;
                editorController.setObject(null);
                editorController.setReadOnly(true);
            }
            updateButtonState(evt);
        }
    }

    private void updateButtonState(ChannelEvent evt) {
        dropButton.setEnabled(evt != null && !evt.isExecuted());
        sendButton.setEnabled(evt != null && !evt.isExecuted());
    }
    
    private class AllEventsAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        public AllEventsAction() {
            super("All Events");
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            tableSorter.setRowFilter(new EventRowFilter(EnumSet.allOf(ChannelEventType.class)));
        }
    }
    private class ImportantEventsAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        public ImportantEventsAction() {
            super("Importants Events");
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            tableSorter.setRowFilter(new EventRowFilter(ChannelEvent.IMPORTANT));
        }
    }
    
    private class EventRowFilter extends RowFilter<ListTableModelAdapter<ChannelEvent>, Integer> {

        private Set<ChannelEventType> events;
        public EventRowFilter(Set<ChannelEventType> events) {
            this.events = events;
        }
        @Override
        public boolean include(Entry<? extends ListTableModelAdapter<ChannelEvent>, ? extends Integer> entry) {
            ListTableModelAdapter<ChannelEvent> model = entry.getModel();
            ChannelEvent event = model.getElementAt(entry.getIdentifier());
            return events.contains(event.type());
        }
    }
}
