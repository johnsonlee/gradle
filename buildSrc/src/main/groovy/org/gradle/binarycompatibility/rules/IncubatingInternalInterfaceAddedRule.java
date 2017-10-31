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
import javassist.NotFoundException;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.lang.annotation.Annotation;

public class IncubatingInternalInterfaceAddedRule extends AbstractSuperClassChangesRule {

    public IncubatingInternalInterfaceAddedRule(Map<String, String> acceptedApiChanges) {
        super(acceptedApiChanges);
    }

    protected Violation checkSuperClassChanges(JApiClass c, CtClass oldClass, CtClass newClass) throws Exception {
        Map<String, CtClass> oldInterfaces = collectImplementedInterfaces(oldClass);
        Map<String, CtClass> newInterfaces = collectImplementedInterfaces(newClass);

        newInterfaces.keySet().removeAll(oldInterfaces.keySet());

        if (newInterfaces.isEmpty()) {
            return null;
        }

        List<String> changes = filterChangesToReport(newClass, newInterfaces);
        if (changes.isEmpty()) {
            return null;
        }
        return acceptOrReject(c, changes, Violation.error(c, " introduces internal or incubating interfaces"));
    }

    private Map<String, CtClass> collectImplementedInterfaces(CtClass c) throws NotFoundException {
        Map<String, CtClass> result = new HashMap<String, CtClass>();
        collect(result, c);
        return result;
    }

    private void collect(Map<String, CtClass> result, CtClass c) throws NotFoundException {
        for (CtClass i : c.getInterfaces()) {
            result.put(i.getName(), i);
        }

        if (c.getSuperclass() != null) {
            collect(result, c.getSuperclass());
        }
    }

    private List<String> filterChangesToReport(CtClass c, Map<String, CtClass> interfaces) throws Exception {
        List<String> result = new ArrayList<>();
        for (CtClass interf : interfaces.values()) {
            if (implementedDirectly(interf, c) && addedInterfaceIsIncubatingOrInternal(interf, c)) {
                result.add(interf.getName());
            }
        }

        Collections.sort(result);
        return result;
    }

    private boolean implementedDirectly(CtClass interf, CtClass c) throws Exception {
        for (CtClass i : c.getInterfaces()) {
            if (interf.getName().equals(i.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean addedInterfaceIsIncubatingOrInternal(CtClass interf, CtClass c) throws Exception {
        return (isIncubating(interf) && !isIncubating(c)) || isInternal(interf);
    }

    private boolean isIncubating(CtClass c) throws ClassNotFoundException {
        for (Object anno : c.getAnnotations()) {
            if (((Annotation) anno).annotationType().getName().equals("org.gradle.api.Incubating")) {
                return true;
            }
        }
        return false;
    }
}
