package com.sensepost.mallet.graph;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Comparator;

import javax.script.Bindings;

public class InstanceFactory {

	private Bindings scriptContext;

	/**
	 * Sorter intended to reduce the chance that a parameter string is used as is
	 * when it could be resolved to something more specific that would match
	 * the constructor. i.e. prefer constructors with non-String parameters
	 */
	Comparator<Constructor<?>> stringLastSorter = new Comparator<Constructor<?>>() {

        @Override
        public int compare(Constructor<?> o1, Constructor<?> o2) {
            Parameter[] p1 = o1.getParameters();
            Parameter[] p2 = o2.getParameters();
            int s1 = countStringParameters(p1);
            int s2 = countStringParameters(p2);
            if (s1 < s2)
                return -1;
            else if (s2 < s1)
                return 1;
            return 0;
        }
    };

    private int countStringParameters(Parameter[] p) {
        int c = 0;
        for (int i=0; i<p.length; i++) {
            if (p[i].getType().isAssignableFrom(String.class))
                c++;
        }
        return c;
    }

	public InstanceFactory(Bindings scriptContext) {
		this.scriptContext = scriptContext;
	}

	/** 
	 * Attempts to instantiate an object of the specified type, based on
	 * the classname provided in the description, using the String
	 * of arguments. An attempt is made to coerce the string arguments
	 * to the required type to match those accepted by the type Constructors.
	 * 
	 * @param description The desired class name, or else a static field
	 * in a class
	 * @param type the desired object should be derived from this class.
	 * @param arguments Any arguments required by the constructor.
	 * @return an instance of the described object
	 * @throws ClassNotFoundException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	public Object getClassInstance(String description, Class<?> type,
			String[] arguments) throws ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		StringBuffer log = new StringBuffer();
		if (arguments == null)
			arguments = new String[0];

		// See if it is a defined internal object
		if (description.startsWith("{")) {
			return getInternalInstance(description, type, arguments);
		}
		if (arguments.length == 0) { // try to return a static field of a class
    		try {
    			int dot = description.lastIndexOf('.');
    			if (dot > 0) {
    				String clsname = description.substring(0, dot);
    				String fieldName = description.substring(dot + 1);
    				Class<?> clz = Class.forName(clsname);
    				Field f = clz.getField(fieldName);
    				if (Modifier.isStatic(f.getModifiers())) {
    					Object instance = f.get(null);
    					if (instance != null
    							&& type.isAssignableFrom(instance.getClass()))
    						return instance;
    				}
    			}
    		} catch (ClassNotFoundException | NoSuchFieldException e) {
    			// that didn't work, try something else
    		}
		}
		// See if it is a basic type that can easily be converted from a String
		if (type.equals(String.class)) {
			return description;
		} else if (type.equals(Integer.class) || type.equals(Integer.TYPE)) {
			return Integer.parseInt(description);
		} else if (type.equals(Boolean.class) || type.equals(Boolean.TYPE)) {
			return Boolean.parseBoolean(description);
		} else if (type.equals(InetSocketAddress.class) || type.equals(SocketAddress.class)) {
			int c = description.indexOf(':');
			if (c > 0) {
				String host = description.substring(0, c);
				try {
					int port = Integer.parseInt(description.substring(c + 1));
					if (port > 0 && port < 65536) {
						return new InetSocketAddress(host, port);
					}
				} catch (NumberFormatException e) {
				}
			}
		}
		// Try to do a naive instantiation
		try {
			Class<?> clz = Class.forName(description);
			if (type.isAssignableFrom(clz)) {
				Constructor<?>[] constructors = clz.getConstructors();
				Arrays.sort(constructors, stringLastSorter);
				for (Constructor<?> c : constructors) {
					log.append("Considering " + c + "\n");
					Object[] args = null;
					try {
						if (c.getParameterCount() == arguments.length) {
							args = getArgumentInstances(arguments,
									c.getParameters());
							return c.newInstance(args);
						}
					} catch (Exception e) {
						System.out.println("Can't instantiate " + description
								+ "(" + Arrays.toString(args) + ") using " + c
								+ ": " + e.getMessage());
						e.printStackTrace();
					}
				}
			} else
				throw new RuntimeException(description
						+ " exists, but does not implement " + type.getName());
		} catch (ClassNotFoundException cnfe) {
			System.out.println(description
					+ " could not be instantiated as a class");
		}
		throw new ClassNotFoundException("'" + description + "' not found\n" + log);
	}

	private Object getInternalInstance(String description, Class<?> type,
			String[] arguments) throws ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		int b = description.indexOf('}');
		String key = description.substring(1,b);
		if (! scriptContext.containsKey(key))
			throw new InstantiationException("'{" + key + "}' was not defined in Script Context!");
		Object internal = scriptContext.get(key);
		if (internal == null && (type.isPrimitive() || b != description.length()-1))
			throw new InstantiationException("Cannot assign null for " + type + ": '" + description + "'");

		if (description.endsWith("}")) {
			description = description
					.substring(1, description.length() - 2);
			Class<?> c = internal == null ? null : internal.getClass();
			if (type.isPrimitive() && c == null)
				throw new InstantiationException("Cannot assign " + type + " from null for '{" + description + "}'");
			if (c != null && !type.isAssignableFrom(c))
				throw new InstantiationException("Wanted a " + type
						+ ", but cannot assign from " + c);

			return internal;
		} else if (description.indexOf('}') > 1) {
			if (internal == null)
				throw new InstantiationException("Could not execute method for '" + description + "', " + key + " was null");
		}
		throw new InstantiationException("Could not parse '" + description + "' as a Context variable");
	}
	
	private Object[] getArgumentInstances(String[] arguments,
			Parameter[] parameters) throws ClassNotFoundException,
			IllegalAccessException, InstantiationException {
		Object[] args = new Object[arguments.length];
		for (int i = 0; i < parameters.length; i++) {
			args[i] = getClassInstance(arguments[i], parameters[i].getType(),
					null);
		}
		return args;
	}

}
