package com.sensepost.mallet.ognl;

import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import ognl.AbstractMemberAccess;
import ognl.ClassResolver;
import ognl.DefaultTypeConverter;
import ognl.MemberAccess;
import ognl.OgnlContext;
import ognl.TypeConverter;

public class OgnlSupport {

	private ClassResolver cr = new MalletClassResolver("io.netty.buffer", "io.netty.handler.codec", "io.netty.channel");
	private TypeConverter tc = new DefaultTypeConverter();

	public static MemberAccess MEMBER_ACCESS = new DefaultMemberAccess();
	public static OgnlSupport INSTANCE = new OgnlSupport();

	private OgnlSupport() {
	}

	public OgnlContext context(Object root) {
		OgnlContext oc = new OgnlContext(cr, tc, MEMBER_ACCESS);
		oc.setRoot(root);
		return oc;
	}

	public ClassResolver getClassResolver() {
		return cr;
	}

	private static class DefaultMemberAccess extends AbstractMemberAccess {
		@Override
		public boolean isAccessible(OgnlContext context, Object target, Member member, String propertyName) {
			int modifiers = member.getModifiers();
			return Modifier.isPublic(modifiers);
		}

	}

	private class MalletClassResolver implements ClassResolver {

		private final ConcurrentHashMap<String, Class<?>> classes = new ConcurrentHashMap<>(101);

		private final List<String> packages = new ArrayList<String>();

		public MalletClassResolver(String... packages) {
			this.packages.add("java.lang");
			if (packages != null && packages.length > 0) {
				this.packages.addAll(Arrays.asList(packages));
			}
		}

		public <T> Class<T> classForName(String className, OgnlContext context) throws ClassNotFoundException {
			Class<?> result = classes.get(className);
			if (result != null) {
				return (Class<T>) result;
			}
			try {
				result = toClassForName(className);
			} catch (ClassNotFoundException e) {
				if (className.indexOf('.') > -1) {
					throw e;
				}
				// The class was not in the default package.
				// Try prepending elements of this.packages.
				for (String p : packages) {
					try {
						result = toClassForName(p + "." + className);
						break;
					} catch (ClassNotFoundException e2) {
					}
				}
				if (result == null)
					// throw the original exception as-is
					throw e;
			}
			classes.putIfAbsent(className, result);
			return (Class<T>) result;
		}

		protected Class<?> toClassForName(String className) throws ClassNotFoundException {
			return Class.forName(className);
		}

	}

}
