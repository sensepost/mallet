package com.sensepost.mallet.ognl;

import ognl.Ognl;
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

    public Object getValue(Object root) throws OgnlException {
        return Ognl.getValue(parsedExpression(), OgnlSupport.INSTANCE.context(root), root);
    }
}