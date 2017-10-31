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

package org.gradle.binarycompatibility.rules

import spock.lang.Specification
import spock.lang.Unroll
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiMethod
import japicmp.model.JApiImplementedInterface
import japicmp.model.JApiChangeStatus
import org.gradle.api.Incubating
import me.champeau.gradle.japicmp.report.Violation
import japicmp.model.JApiClass
import javassist.CtClass
import com.google.common.base.Optional
import me.champeau.gradle.japicmp.report.ViolationCheckContext

import java.lang.annotation.Annotation

class IncubatingInternalInterfaceAddedRuleTest extends Specification {
    IncubatingInternalInterfaceAddedRule rule = new IncubatingInternalInterfaceAddedRule([:])

    CtClass oldBase = Stub(CtClass)
    CtClass oldSuper = Stub(CtClass)

    List oldBaseInterfaces = []
    List oldSuperInterfaces = []

    CtClass newBase = Stub(CtClass)
    CtClass newSuper = Stub(CtClass)

    List newBaseInterfaces = []
    List newSuperInterfaces = []

    CtClass unusedInterface = Stub(CtClass)
    CtClass internalInterface = Stub(CtClass)
    CtClass incubatingInterface = Stub(CtClass)

    JApiClass apiClass = Stub(JApiClass)

    Map interfaces

    Object[] incubatingAnno = ([new Annotation() {
        @Override
        Class<? extends Annotation> annotationType() {
            return Incubating
        }
    }] as Object[])

    def setup() {
        rule.context = new ViolationCheckContext() {
            Map userData = [seenApiChanges: [] as Set]

            String getClassName() { return null }

            Map<String, ?> getUserData() { return userData }

            Object getUserData(String key) {
                return userData[key]
            }

            void putUserData(String key, Object value) {
                userData[key] = value
            }
        }

        oldSuper.superclass >> null
        oldBase.superclass >> oldSuper
        oldSuper.interfaces >> oldSuperInterfaces
        oldBase.interfaces >> oldBaseInterfaces

        newSuper.superclass >> null
        newBase.superclass >> newSuper
        newSuper.interfaces >> newSuperInterfaces
        newBase.interfaces >> newBaseInterfaces

        apiClass.oldClass >> Optional.of(oldBase)
        apiClass.newClass >> Optional.of(newBase)

        apiClass.changeStatus >> JApiChangeStatus.MODIFIED

        unusedInterface.name >> 'unused'
        internalInterface.name >> 'this.is.an.internal.type'
        incubatingInterface.name >> 'this.is.an.incubating.type'

        incubatingInterface.annotations >> incubatingAnno

        interfaces = [internal: internalInterface,
                      incubating: incubatingInterface,
                      unused: unusedInterface]
    }

    @Unroll
    def "#member change should not be reported"() {
        expect:
        rule.maybeViolation(apiClass) == null

        where:
        member << [Mock(JApiMethod), Mock(JApiField), Mock(JApiImplementedInterface), Mock(JApiConstructor)]
    }

    def "nothing be reported if no changes"() {
        expect:
        rule.maybeViolation(apiClass) == null
    }

    def "nothing be reported if new interface is neither internal nor incubating"() {
        when:
        newBaseInterfaces << unusedInterface

        then:
        rule.maybeViolation(apiClass) == null
    }

    def 'adding an #type interface can be reported'() {
        given:
        newBaseInterfaces << interfaces[type]

        when:
        Violation violation = rule.maybeViolation(apiClass)

        then:
        violation.humanExplanation.contains(result)

        where:
        type         | result
        'internal'   | '"this.is.an.internal.type"'
        'incubating' | '"this.is.an.incubating.type"'
    }

    def "do not report if they are both incubating"() {
        given:
        newBaseInterfaces << incubatingInterface
        newBase.annotations >> incubatingAnno

        expect:
        rule.maybeViolation(apiClass) == null
    }

    def "adding an #type interface to super class would not be reported"() {
        given:
        newSuperInterfaces << interfaces[type]

        expect:
        rule.maybeViolation(apiClass) == null

        where:
        type << ['internal', 'incubating']
    }
}
