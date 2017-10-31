/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.binarycompatibility.rules;

import me.champeau.gradle.japicmp.report.Violation;
import japicmp.model.JApiClass;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;

import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;

public class MethodsRemovedInInternalSuperClassRule extends AbstractSuperClassChangesRule {

    public MethodsRemovedInInternalSuperClassRule(Map<String, String> acceptedApiChanges) {
        super(acceptedApiChanges);
    }

    protected Violation checkSuperClassChanges(JApiClass c, CtClass oldClass, CtClass newClass) throws Exception {
        Set<CtMethod> oldMethods = collectAllPublicApiMethods(oldClass);
        Set<CtMethod> newMethods = collectAllPublicApiMethods(newClass);

        oldMethods.removeAll(newMethods);

        if (oldMethods.isEmpty()) {
            return null;
        }

        List<String> changes = filterChangesToReport(oldClass, oldMethods);
        if (changes.isEmpty()) {
            return null;
        }
        return acceptOrReject(c, changes, Violation.error(c, " methods removed in internal super class"));
    }

    private Set<CtMethod> collectAllPublicApiMethods(CtClass c) throws NotFoundException {
        Set<CtMethod> result = new HashSet<CtMethod>();
        collect(result, c.getSuperclass());
        return result;
    }

    private void collect(Set<CtMethod> result, CtClass c) throws NotFoundException {
        if (c == null) {
            return;
        }
        for (CtMethod m : c.getDeclaredMethods()) {
            if (isPublicApi(m)) {
                result.add(m);
            }
        }

        collect(result, c.getSuperclass());
    }

    private boolean isPublicApi(CtMethod method) {
        return Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers());
    }

    private List<String> filterChangesToReport(CtClass c, Set<CtMethod> methods) throws Exception {
        List<String> result = new ArrayList<>();
        for (CtMethod method : methods) {
            if (declaredInInternalClass(method) && isTop(method, c)) {
                result.add(method.getLongName());
            }
        }

        Collections.sort(result);
        return result;
    }

    private boolean declaredInInternalClass(CtMethod method) {
        return isInternal(method.getDeclaringClass());
    }

    private boolean isTop(CtMethod method, CtClass c) throws NotFoundException {
        c = c.getSuperclass();
        while (c != null) {
            if (methodDeclared(method, c)) {
                return false;
            }
            c = c.getSuperclass();
        }

        return true;
    }

    private boolean methodDeclared(CtMethod method, CtClass c) {
        for (CtMethod m : c.getDeclaredMethods()) {
            // TODO signature contains return type
            // but return type can be overriden
            if (m.getSignature().equals(method.getSignature())) {
                return true;
            }
        }

        return false;
    }
}
