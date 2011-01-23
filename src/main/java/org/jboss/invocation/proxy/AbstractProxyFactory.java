/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.invocation.proxy;

import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import org.jboss.classfilewriter.AccessFlag;
import org.jboss.classfilewriter.ClassMethod;
import org.jboss.classfilewriter.code.BranchEnd;
import org.jboss.classfilewriter.code.CodeAttribute;
import org.jboss.classfilewriter.code.CodeLocation;

public abstract class AbstractProxyFactory<T> extends AbstractSubclassFactory<T> {

    private static final String METHOD_FIELD_PREFIX = "METHOD$$IDENTIFIER";

    private static final String METHOD_FIELD_DESCRIPTOR = "Ljava/lang/reflect/Method;";

    private final Map<Method, String> methodIdentifiers = new HashMap<Method, String>();

    private int identifierCount = 0;

    private ClassMethod staticConstructor;

    public AbstractProxyFactory(String className, Class<T> superClass, ClassLoader classLoader) {
        super(className, superClass, classLoader);
        staticConstructor = classFile.addMethod(AccessFlag.of(AccessFlag.PUBLIC, AccessFlag.STATIC), "<clinit>", "V");
    }

    public AbstractProxyFactory(String className, Class<T> superClass, ClassLoader classLoader,
            ProtectionDomain protectionDomain) {
        super(className, superClass, classLoader, protectionDomain);
        staticConstructor = classFile.addMethod(AccessFlag.of(AccessFlag.PUBLIC, AccessFlag.STATIC), "<clinit>", "V");
    }

    public AbstractProxyFactory(String className, Class<T> superClass) {
        super(className, superClass);
        staticConstructor = classFile.addMethod(AccessFlag.of(AccessFlag.PUBLIC, AccessFlag.STATIC), "<clinit>", "V");
    }

    /**
     * This method must be called by subclasses after they have finished generating the class
     */
    protected void finalizeStaticConstructor() {
        staticConstructor.getCodeAttribute().returnInstruction();
    }

    @Override
    protected void cleanup() {
        staticConstructor = null;
        super.cleanup();
    }

    /**
     * Writes the bytecode to load an instance of Method for the given method onto the stack and set it to accessible
     * <p>
     * If loadMethod has not already been called for the given method then a static field to hold the method is added to the
     * class, and code is added to the static constructor to initalize the field to the correct Method
     *
     */
    protected void loadMethodIdentifier(Method methodToLoad, ClassMethod method) {
        if (!methodIdentifiers.containsKey(methodToLoad)) {
            int identifierNo = identifierCount++;
            String fieldName = METHOD_FIELD_PREFIX + identifierNo;
            classFile.addField(AccessFlag.PRIVATE | AccessFlag.STATIC, fieldName, Method.class);
            methodIdentifiers.put(methodToLoad, fieldName);
            // we need to create the method in the static constructor
            CodeAttribute ca = staticConstructor.getCodeAttribute();
            // we need to call getDeclaredMethods and then iterate
            ca.loadClass(methodToLoad.getDeclaringClass().getName());
            ca.invokevirtual("java.lang.Class", "getDeclaredMethods", "()[Ljava/lang/reflect/Method;");
            ca.dup();
            ca.arraylength();
            ca.dup();
            ca.istore(0);
            CodeLocation loopBegin = ca.mark();
            ca.aconstNull();
            ca.astore(1);
            ca.aconstNull();
            ca.astore(2);
            BranchEnd loopEnd = ca.ifeq();
            ca.dup();
            ca.iinc(0, -1);
            ca.iload(0);
            ca.dupX1();
            ca.aaload();
            ca.dup();
            ca.astore(2);
            ca.checkcast("java.lang.reflect.Method");
            ca.invokevirtual("java.lang.reflect.Method", "getName", "()Ljava/lang/String;");
            ca.ldc(methodToLoad.getName());
            ca.invokevirtual("java.lang.Object", "equals", "(Ljava/lang/Object;)Z");
            ca.ifEq(loopBegin);
            // just match method name for now
            ca.aload(2);
            ca.checkcast("java.lang.reflect.Method");
            ca.putstatic(getClassName(), fieldName, METHOD_FIELD_DESCRIPTOR);
            // BranchEnd gotoEnd = ca.gotoInstruction();
            ca.branchEnd(loopEnd);
            // ca.newInstruction("java.lang.RuntimeException");
            // ca.ldc("Could not find method " + methodToLoad);
            // ca.invokespecial("java.lang.RuntimeException", "<init>", "(Ljava/lang/String;)V");
            // ca.athrow();
            // ca.branchEnd(gotoEnd);
        }
        String fieldName = methodIdentifiers.get(methodToLoad);
        method.getCodeAttribute().getstatic(getClassName(), fieldName, METHOD_FIELD_DESCRIPTOR);
    }

}
