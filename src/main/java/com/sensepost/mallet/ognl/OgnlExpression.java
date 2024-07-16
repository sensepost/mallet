package com.sensepost.mallet.ognl;

import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;

public class OgnlExpression {
    private String expression;
    private Object parsedExpression = null;

    public OgnlExpression(String expression) {
        this.expression = expression;
    }

    public String getExpression() {
        return expression;
    }

    private Object parsedExpression() throws OgnlException {
        if (parsedExpression != null)
            return parsedExpression;
        return parsedExpression = Ognl.parseExpression(expression);
    }

    public Object getValue(OgnlContext context, Object root) throws OgnlException {
        return Ognl.getValue(parsedExpression(), context, root);
    }

    public void setValue(OgnlContext context, Object root, Object value) throws OgnlException {
        Ognl.setValue(parsedExpression(), context, root, value);
    }
}