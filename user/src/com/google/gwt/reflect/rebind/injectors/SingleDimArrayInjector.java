package com.google.gwt.reflect.rebind.injectors;

import static com.google.gwt.reflect.rebind.ReflectionUtilAst.extractClassLiteral;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.dev.jjs.MagicMethodGenerator;
import com.google.gwt.dev.jjs.SourceInfo;
import com.google.gwt.dev.jjs.UnifyAstListener;
import com.google.gwt.dev.jjs.UnifyAstView;
import com.google.gwt.dev.jjs.ast.*;
import com.google.gwt.dev.jjs.impl.UnifyAst.UnifyVisitor;
import com.google.gwt.dev.util.collect.Lists;
import com.google.gwt.reflect.rebind.ReflectionUtilAst;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Queue;

public class SingleDimArrayInjector implements MagicMethodGenerator,
UnifyAstListener {

  private JMethod registerArray;
  private JMethod newArrayMethod;

  @Override
  public void destroy(final TreeLogger logger) {
    newArrayMethod = null;
    registerArray = null;
  }

  @Override
  public JExpression injectMagic(final TreeLogger logger,
    final JMethodCall methodCall, final JMethod enclosingMethod,
    final Context context, final UnifyAstView ast)
      throws UnableToCompleteException {
    final SourceInfo info = methodCall.getSourceInfo().makeChild();

    findMethods(ast);
    final JClassLiteral clazz = extractClassLiteral(logger, methodCall, 0, ast,
      false);
    if (clazz == null) {
      // The client did not provide a class literal. Defer to runtime support
      // for array reflection, which will only work for types that have
      // previously been registered via a successful call with a class literal
      final JExpression[] args = methodCall.getArgs()
        .toArray(new JExpression[2]);
      return new JMethodCall(info, null, newArrayMethod, args);
    }
    final JType type = ast.translate(clazz.getRefType());

    List<JExpression> dims = Lists.create(methodCall.getArgs().get(1));

    // Toss on an extra array dimension
    final JArrayType arrayType = ast.getProgram().getTypeArray(type);

    // Collect up the class literals
    JClassLiteral classLit;
    JType cur = arrayType;
    while (cur instanceof JArrayType) {
      cur = ((JArrayType) cur).getElementType();
    }
    classLit = new JClassLiteral(info.makeChild(), cur);

    // Define new array[n]...[]; statement
    final JNewArray newArr = new JNewArray(info, arrayType, dims, null,
      classLit);
    return new JMethodCall(info, null, registerArray, newArr, new JClassLiteral(info.makeChild(), arrayType));
  }

  @Override
  public boolean onUnifyAstPostProcess(final TreeLogger logger,
    final UnifyAstView ast,
    final UnifyVisitor visitor, final Queue<JMethod> todo) {
    return false;
  }

  @Override
  public void onUnifyAstStart(final TreeLogger logger, final UnifyAstView ast,
    final UnifyVisitor visitor, final Queue<JMethod> todo) {
  }

  private void findMethods(final UnifyAstView ast) {
    if (newArrayMethod == null) {
      JDeclaredType arrayType = ast
        .searchForTypeByBinary(Array.class.getName());
      arrayType = ast.translate(arrayType);
      for (final JMethod method : arrayType.getMethods()) {
        if ("register".equals(method.getName())) {
          registerArray = ast.translate(method);
          if (newArrayMethod != null) {
            return;
          }
        } else if ("newSingleDimArray".equals(method.getName())) {
          newArrayMethod = ast.translate(method);
          if (registerArray != null) {
            return;
          }
        }
      }
    }
  }

}
