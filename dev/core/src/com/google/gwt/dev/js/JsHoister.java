/*
 * Copyright 2007 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.dev.js;

import com.google.gwt.dev.jjs.InternalCompilerException;
import com.google.gwt.dev.js.ast.JsArrayAccess;
import com.google.gwt.dev.js.ast.JsArrayLiteral;
import com.google.gwt.dev.js.ast.JsBinaryOperation;
import com.google.gwt.dev.js.ast.JsBooleanLiteral;
import com.google.gwt.dev.js.ast.JsConditional;
import com.google.gwt.dev.js.ast.JsContext;
import com.google.gwt.dev.js.ast.JsDecimalLiteral;
import com.google.gwt.dev.js.ast.JsExpression;
import com.google.gwt.dev.js.ast.JsFunction;
import com.google.gwt.dev.js.ast.JsIntegralLiteral;
import com.google.gwt.dev.js.ast.JsInvocation;
import com.google.gwt.dev.js.ast.JsNameRef;
import com.google.gwt.dev.js.ast.JsNew;
import com.google.gwt.dev.js.ast.JsNullLiteral;
import com.google.gwt.dev.js.ast.JsObjectLiteral;
import com.google.gwt.dev.js.ast.JsPostfixOperation;
import com.google.gwt.dev.js.ast.JsPrefixOperation;
import com.google.gwt.dev.js.ast.JsPropertyInitializer;
import com.google.gwt.dev.js.ast.JsRegExp;
import com.google.gwt.dev.js.ast.JsStringLiteral;
import com.google.gwt.dev.js.ast.JsThisRef;
import com.google.gwt.dev.js.ast.JsVisitor;

import java.util.List;
import java.util.Stack;

/**
 * A utility class to clone JsExpression AST members for use by
 * {@link JsInliner}. <b>Not all expressions are necessarily
 * implemented</b>, only those that are safe to hoist into outer call sites.
 */
final class JsHoister {
  /**
   * Implements actual cloning logic. We rely on the JsExpressions to provide
   * traversal logic. The {@link #stack} field is used to accumulate
   * already-cloned JsExpression instances. One gotcha that falls out of this is
   * that argument lists are on the stack in reverse order, so lists should be
   * constructed via inserts, rather than appends.
   */
  private static class Cloner extends JsVisitor {
    private final Stack<JsExpression> stack = new Stack<JsExpression>();
    private boolean successful = true;

    @Override
    public void endVisit(JsArrayAccess x, JsContext<JsExpression> ctx) {
      JsArrayAccess newExpression = new JsArrayAccess();
      newExpression.setIndexExpr(stack.pop());
      newExpression.setArrayExpr(stack.pop());
      stack.push(newExpression);
    }

    @Override
    public void endVisit(JsArrayLiteral x, JsContext<JsExpression> ctx) {
      JsArrayLiteral toReturn = new JsArrayLiteral();
      List<JsExpression> expressions = toReturn.getExpressions();
      for (JsExpression e : x.getExpressions()) {
        expressions.add(0, stack.pop());
      }
      stack.push(toReturn);
    }

    @Override
    public void endVisit(JsBinaryOperation x, JsContext<JsExpression> ctx) {
      JsBinaryOperation toReturn = new JsBinaryOperation(x.getOperator());
      toReturn.setArg2(stack.pop());
      toReturn.setArg1(stack.pop());
      stack.push(toReturn);
    }

    @Override
    public void endVisit(JsBooleanLiteral x, JsContext<JsExpression> ctx) {
      stack.push(x);
    }

    @Override
    public void endVisit(JsConditional x, JsContext<JsExpression> ctx) {
      JsConditional toReturn = new JsConditional();
      toReturn.setElseExpression(stack.pop());
      toReturn.setThenExpression(stack.pop());
      toReturn.setTestExpression(stack.pop());
      stack.push(toReturn);
    }

    @Override
    public void endVisit(JsDecimalLiteral x, JsContext<JsExpression> ctx) {
      stack.push(x);
    }

    /**
     * The only functions that would get be visited are those being used as
     * first-class objects.
     */
    @Override
    public void endVisit(JsFunction x, JsContext<JsExpression> ctx) {
      // Set a flag to indicate that we cannot continue, and push a null so
      // we don't run out of elements on the stack.
      successful = false;
      stack.push(null);
    }

    @Override
    public void endVisit(JsIntegralLiteral x, JsContext<JsExpression> ctx) {
      stack.push(x);
    }

    /**
     * Cloning the invocation allows us to modify it without damaging other call
     * sites.
     */
    @Override
    public void endVisit(JsInvocation x, JsContext<JsExpression> ctx) {
      JsInvocation toReturn = new JsInvocation();
      List<JsExpression> params = toReturn.getArguments();
      for (JsExpression e : x.getArguments()) {
        params.add(0, stack.pop());
      }
      toReturn.setQualifier(stack.pop());
      stack.push(toReturn);
    }

    /**
     * Do a deep clone of a JsNameRef. Because JsNameRef chains are shared
     * throughout the AST, you can't just go and change their qualifiers when
     * re-writing an invocation.
     */
    @Override
    public void endVisit(JsNameRef x, JsContext<JsExpression> ctx) {
      JsNameRef toReturn = new JsNameRef(x.getName());

      if (x.getQualifier() != null) {
        toReturn.setQualifier(stack.pop());
      }
      stack.push(toReturn);
    }

    @Override
    public void endVisit(JsNew x, JsContext<JsExpression> ctx) {
      JsNew toReturn = new JsNew();

      List<JsExpression> arguments = toReturn.getArguments();
      for (JsExpression a : x.getArguments()) {
        arguments.add(0, stack.pop());
      }
      toReturn.setConstructorExpression(stack.pop());
      stack.push(toReturn);
    }

    @Override
    public void endVisit(JsNullLiteral x, JsContext<JsExpression> ctx) {
      stack.push(x);
    }

    @Override
    public void endVisit(JsObjectLiteral x, JsContext<JsExpression> ctx) {
      JsObjectLiteral toReturn = new JsObjectLiteral();
      List<JsPropertyInitializer> inits = toReturn.getPropertyInitializers();

      for (JsPropertyInitializer init : x.getPropertyInitializers()) {
        /*
         * JsPropertyInitializers are the only non-JsExpression objects that we
         * care about, so we just go ahead and create the objects in the loop,
         * rather than expecting it to be on the stack and having to perform
         * narrowing casts at all stack.pop() invocations.
         */
        JsPropertyInitializer newInit = new JsPropertyInitializer();
        newInit.setValueExpr(stack.pop());
        newInit.setLabelExpr(stack.pop());

        inits.add(0, newInit);
      }
      stack.push(toReturn);
    }

    @Override
    public void endVisit(JsPostfixOperation x, JsContext<JsExpression> ctx) {
      JsPostfixOperation toReturn = new JsPostfixOperation(x.getOperator());
      toReturn.setArg(stack.pop());
      stack.push(toReturn);
    }

    @Override
    public void endVisit(JsPrefixOperation x, JsContext<JsExpression> ctx) {
      JsPrefixOperation toReturn = new JsPrefixOperation(x.getOperator());
      toReturn.setArg(stack.pop());
      stack.push(toReturn);
    }

    @Override
    public void endVisit(JsRegExp x, JsContext<JsExpression> ctx) {
      stack.push(x);
    }

    @Override
    public void endVisit(JsStringLiteral x, JsContext<JsExpression> ctx) {
      stack.push(x);
    }

    /**
     * A "this" reference can only effectively be hoisted if the call site is
     * qualified by a JsNameRef, so we'll ignore this for now.
     */
    @Override
    public void endVisit(JsThisRef x, JsContext<JsExpression> ctx) {
      // Set a flag to indicate that we cannot continue, and push a null so
      // we don't run out of elements on the stack.
      successful = false;
      stack.push(null);
    }

    public JsExpression getExpression() {
      return (successful && checkStack()) ? stack.peek() : null;
    }

    private boolean checkStack() {
      if (stack.size() > 1) {
        throw new InternalCompilerException("Too many expressions on stack");
      }

      return stack.size() == 1;
    }
  }

  /**
   * Given a JsStatement, construct an expression to hoist into the outer
   * caller. This does not perform any name replacement, nor does it verify the
   * scope of referenced elements, but simply constructs a mutable copy of the
   * expression that can be manipulated at-will.
   * 
   * @return A copy of the original expression, or <code>null</code> if the
   *         expression cannot be hoisted.
   */
  public static JsExpression hoist(JsExpression expression) {
    if (expression == null) {
      return null;
    }

    Cloner c = new Cloner();
    c.accept(expression);
    return c.getExpression();
  }

  private JsHoister() {
  }
}
