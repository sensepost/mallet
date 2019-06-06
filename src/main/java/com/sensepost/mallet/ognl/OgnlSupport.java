package com.sensepost.mallet.ognl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.Map;

import io.netty.util.internal.ObjectUtil;
import ognl.ClassResolver;
import ognl.DefaultClassResolver;
import ognl.DefaultTypeConverter;
import ognl.MemberAccess;
import ognl.OgnlContext;
import ognl.TypeConverter;

public class OgnlSupport {

    private ClassResolver cr = new DefaultClassResolver();
    private TypeConverter tc = new DefaultTypeConverter();
    private MemberAccess ma = new DefaultMemberAccess();

    public static OgnlSupport INSTANCE = new OgnlSupport();

    private OgnlSupport() {
    }

    public OgnlContext context(Object root) {
        OgnlContext oc = new OgnlContext(cr, tc, ma);
        oc.setRoot(root);
        return oc;
    }

    public ClassResolver getClassResolver() {
        return cr;
    }

    private static class DefaultMemberAccess implements MemberAccess {

        @Override
        public Object setup(Map context, Object target, Member member, String propertyName) {
            return null;
        }

        @Override
        public void restore(Map context, Object target, Member member, String propertyName, Object state) {
        }

        @Override
        public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
            return Modifier.isPublic(member.getModifiers());
        }
    }
}
