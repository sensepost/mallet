package com.sensepost.mallet.ognl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import io.netty.util.internal.ObjectUtil;

public class OgnlExpressions {

    private final static String DEFAULTS = "/ognl.txt";
    private final static String PREFERENCES = "com/sensepost/Mallet/OgnlExpressions";

    private Map<String, PerClassExpressions> defaultPerClassExpressions = new HashMap<>();
    private Map<String, PerClassExpressions> customPerClassExpressions = new HashMap<>();

    public enum OgnlFunction {
        DECODE, ENCODE, TOSTRING
    }

    private static class PerClassExpressions {
        private EnumMap<OgnlFunction, OgnlExpression> expressions = new EnumMap<>(OgnlFunction.class);

        public void setExpression(OgnlFunction function, OgnlExpression expression) {
            expressions.put(function, expression);
        }

        public OgnlExpression getExpression(OgnlFunction function) {
            return expressions.get(function);
        }
    }

    public static final OgnlExpressions INSTANCE = new OgnlExpressions();

    private OgnlExpressions() {
        loadDefaultExpressions(getClass().getResourceAsStream(DEFAULTS));
        Preferences prefs = Preferences.userRoot().node(PREFERENCES);
        try {
            loadCustomExpressions(prefs);
        } catch (BackingStoreException e) {
            e.printStackTrace();
        }
    }

    private void loadDefaultExpressions(InputStream in) {
        ObjectUtil.checkNotNull(in, "in");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    int dot = parts[0].lastIndexOf('.');
                    if (dot > -1) {
                        String className = parts[0].substring(0, dot);
                        String function = parts[0].substring(dot + 1);
                        String ognl = parts[1];
                        updatePerClassOperation(className, OgnlFunction.valueOf(function.toUpperCase()), ognl, false);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadCustomExpressions(Preferences prefs) throws BackingStoreException {
        String[] keys = prefs.keys();
        for (String key : keys) {
            int dot = key.lastIndexOf('.');
            if (dot > -1) {
                String className = key.substring(0, dot);
                String function = key.substring(dot + 1);
                String ognl = prefs.get(key, null);
                updatePerClassOperation(className, OgnlFunction.valueOf(function.toUpperCase()), ognl, true);
            }
        }
    }

    public void saveCustomExpressions(Preferences prefs) throws BackingStoreException {
        Iterator<Entry<String, PerClassExpressions>> it = customPerClassExpressions.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, PerClassExpressions> e = it.next();
            String c = e.getKey();
            PerClassExpressions pce = e.getValue();
            for (OgnlFunction f : OgnlFunction.values()) {
                OgnlExpression oe = pce.getExpression(f);
                if (oe != null) {
                    prefs.put(c + "." + f, oe.getExpression());
                } else {
                    prefs.remove(c + "." + f);
                }
            }
        }
        prefs.flush();
    }

    public Set<String> getClasses() {
        Set<String> keys = new HashSet<>(defaultPerClassExpressions.keySet());
        keys.addAll(customPerClassExpressions.keySet());
        return keys;
    }

    public void updatePerClassOperation(String className, OgnlFunction function, String ognl) {
        updatePerClassOperation(className, function, ognl, true);
    }

    private void updatePerClassOperation(String className, OgnlFunction function, String ognl, boolean custom) {
        Map<String, PerClassExpressions> perClassExpressions = custom ? customPerClassExpressions
                : defaultPerClassExpressions;
        PerClassExpressions pce = perClassExpressions.get(className);
        if (pce == null) {
            pce = new PerClassExpressions();
            perClassExpressions.put(className, pce);
        }
        OgnlExpression expression = new OgnlExpression(ognl);
        pce.setExpression(function, expression);
    }

    public OgnlExpression getOgnlExpression(String className, OgnlFunction function) {
        PerClassExpressions pco = customPerClassExpressions.get(className);
        if (pco == null) {
            pco = defaultPerClassExpressions.get(className);
            if (pco == null) {
                return null;
            }
        }
        return pco.getExpression(function);
    }

    public OgnlExpression getOgnlExpression(Class<?> c, OgnlFunction function) {
        // Find by class hierarchy
        while (c != null && c != Object.class) {
            OgnlExpression exp = getOgnlExpression(c.getName(), function);
            if (exp != null) {
                return exp;
            }
            // or by interfaces
            for (Class<?> i : c.getInterfaces()) {
                exp = getOgnlExpression(i.getName(), function);
                if (exp != null) {
                    return exp;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

}
