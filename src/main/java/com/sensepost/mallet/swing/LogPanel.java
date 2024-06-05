package com.sensepost.mallet.swing;

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Date;
import java.util.logging.ErrorManager;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.prefs.Preferences;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import io.netty.handler.logging.LogLevel;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class LogPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Level[] LOG_LEVELS = new Level[] { Level.OFF, Level.SEVERE, Level.WARNING, Level.INFO,
            Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST, Level.ALL };

    private ListModelHandler handler = new ListModelHandler(1024);

    private JTable logTable;
    private TableRowSorter<LogRecordTableModel> sorter;

    private Preferences prefs = Preferences.userNodeForPackage(LogPanel.class).node(LogPanel.class.getSimpleName());
    private JTextField filterText;

    public LogPanel() {
        setLayout(new BorderLayout(0, 0));

        LogRecordTableModel tableModel = new LogRecordTableModel(handler.getListModel()); 
        logTable = new JTable(tableModel) {
            private static final long serialVersionUID = 1L;

            @Override
            public int getScrollableUnitIncrement(Rectangle visibleRect,
                    int orientation,
                    int direction) {
                return 10;
            }
        };
        logTable.setFillsViewportHeight(true);
        logTable.setDefaultRenderer(String.class, new MultiLineCellRenderer());
        logTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        logTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logTable.setDefaultRenderer(Date.class, new DateRenderer(true));
        TableColumnModelPersistence tcmp = new TableColumnModelPersistence(prefs, "column_widths");
        tcmp.apply(logTable.getColumnModel(), 75, 75, 75, 200);
        logTable.getColumnModel().addColumnModelListener(tcmp);
        sorter = new TableRowSorter<LogRecordTableModel>(tableModel);
        logTable.setRowSorter(sorter);
        
        JPanel panel = new JPanel();
        add(panel, BorderLayout.NORTH);

        final JCheckBox enableCheckBox = new JCheckBox("Enable");
        panel.add(enableCheckBox);
        enableCheckBox.setSelected(true);

        enableCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateLogLevel(enableCheckBox.isSelected());
            }
        });
        updateLogLevel(enableCheckBox.isSelected());

        final JCheckBox scrollCheckBox = new JCheckBox("Scroll automatically");
        scrollCheckBox.setSelected(true);
        panel.add(scrollCheckBox);
        
        JLabel lblNewLabel = new JLabel("Regex:");
        panel.add(lblNewLabel);
        
        filterText = new JTextField();
        panel.add(filterText);
        filterText.setColumns(20);
        filterText.getDocument().addDocumentListener(new DocumentListener() {
            
            @Override
            public void removeUpdate(DocumentEvent e) {
                newFilter();
            }
            
            @Override
            public void insertUpdate(DocumentEvent e) {
                newFilter();
            }
            
            @Override
            public void changedUpdate(DocumentEvent e) {
                newFilter();
            }
        });

        DefaultComboBoxModel<Level> model = new DefaultComboBoxModel<>(LOG_LEVELS);

        scrollCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateScrollPolicy(scrollCheckBox.isSelected());
            }
        });
        updateScrollPolicy(scrollCheckBox.isSelected());

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setViewportView(logTable);
        add(scrollPane, BorderLayout.CENTER);

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                handler.clear();
            }
        });
        add(clearButton, BorderLayout.SOUTH);

    }
    
    private void newFilter() {
        RowFilter<? super TableModel, ? super Integer> rf = null;
        //If current expression doesn't parse, don't update.
        try {
            rf = RowFilter.regexFilter(filterText.getText(), 3);
        } catch (java.util.regex.PatternSyntaxException e) {
            return;
        }
        sorter.setRowFilter(rf);
    }
    
    public Handler getHandler() {
        return handler;
    }

    private void updateLogLevel(boolean enabled) {
        handler.setLevel(enabled ? Level.ALL : Level.OFF);
    }

    private void updateScrollPolicy(boolean scroll) {
    }

    public class ListModelHandler extends Handler {

        private int max;
        private DefaultListModel<LogRecord> model;

        public ListModelHandler() {
            this(Integer.MAX_VALUE);
        }

        public ListModelHandler(int limit) {
            max = limit;
            model = new DefaultListModel<LogRecord>();
        }

        public ListModel<LogRecord> getListModel() {
            return model;
        }

        public void close() {
        }

        public void flush() {
        }

        public void clear() {
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        model.removeAllElements();
                    } catch (Exception ex) {
                        // We don't want to throw an exception here, but we
                        // report the exception to any registered ErrorManager.
                        reportError(null, ex, ErrorManager.WRITE_FAILURE);
                    }
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeLater(r);
            }
        }

        private LogRecord prev = null;
        
        private boolean equals(LogRecord a, LogRecord b) {
            return (a != null && b != null) && a.getMessage().equals(b.getMessage()) && (a.getMillis() == b.getMillis())
                    && (a.getLongThreadID() == b.getLongThreadID());
        }
        
        public void publish(LogRecord record) {
            if (!isLoggable(record) || equals(prev, record)) {
                return;
            }
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        if (model.size() > max) {
                            int n = model.size() - max - 1;
                            model.removeRange(0, n);
                        }
                        int pos = model.size();
                        model.add(pos, record);
                    } catch (Exception ex) {
                        // We don't want to throw an exception here, but we
                        // report the exception to any registered ErrorManager.
                        reportError(null, ex, ErrorManager.WRITE_FAILURE);
                    }
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeLater(r);
            }
        }

    }

    private class LogRecordTableModel extends ListTableModelAdapter<LogRecord> {

        private static final long serialVersionUID = 1L;

        public LogRecordTableModel(ListModel<LogRecord> listModel) {
            super(new RowModel<LogRecord>() {
                private String[] columnNames = new String[] { "When", "Level", "LoggerName", "Message" };
                private Class<?>[] columnClasses = new Class[] { Date.class, LogLevel.class, String.class,
                        String.class };

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
                public Object getValueAt(LogRecord record, int columnIndex) {
                    switch (columnIndex) {
                    case 0:
                        return new Date(record.getMillis());
                    case 1:
                        return record.getLevel();
                    case 2:
                        return record.getLoggerName();
                    case 3:
                        return handler.getFormatter() != null ? handler.getFormatter().format(record)
                                : record.getMessage();
                    }
                    return null;
                }
            }, listModel);
        }

    }
}
