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

package org.gradle.nativeplatform.test.xctest

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftXCTestAddDiscoveryBundle
import org.gradle.nativeplatform.fixtures.app.IncrementalSwiftXCTestRemoveDiscoveryBundle
import org.gradle.nativeplatform.fixtures.app.SwiftFailingXCTestBundle
import org.gradle.nativeplatform.fixtures.app.SwiftLibWithXCTestWithInfoPlist
import org.gradle.nativeplatform.fixtures.app.SwiftXCTestBundle
import org.gradle.nativeplatform.fixtures.app.SwiftXCTestBundleWithInfoPlist
import org.gradle.nativeplatform.fixtures.app.XCTestCaseElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceFileElement
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Unroll

@Requires([TestPrecondition.SWIFT_SUPPORT, TestPrecondition.MAC_OS_X])
class XCTestIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    @Rule
    TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        settingsFile << "rootProject.name = 'app'"
        buildFile << """
apply plugin: 'xctest'
"""
    }

    def "skips test tasks when no source is available"() {
        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        result.assertTasksSkipped(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "fails when test cases fail"() {
        def testBundle = new SwiftFailingXCTestBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        fails("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest")
        testBundle.assertTestCasesRan(testExecutionResult)
    }

    def "succeeds when test cases pass"() {
        def testBundle = new SwiftXCTestBundleWithInfoPlist()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.assertTestCasesRan(testExecutionResult)
    }

    def "can build xctest bundle when Info.plist is missing"() {
        def testBundle = new SwiftXCTestBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.assertTestCasesRan(testExecutionResult)
    }

    def 'can build xctest bundle which depends multiple swift modules'() {
        when:
        succeeds 'test'

        then:
        result.assertTasksExecuted(':greeter:compileDebugSwift', ':greeter:compileTestSwift', ':greeter:linkDebug',
            ':greeter:linkTest', ':greeter:bundleSwiftTest', ':greeter:xcTest', ':greeter:test', ':compileDebugSwift',
            ':compileTestSwift', ':linkTest', ':bundleSwiftTest', ':xcTest', ':test')
    }

    def 'can run xctest in swift package manager layout'() {
        when:
        succeeds 'test'

        then:
        result.assertTasksExecuted(':greeter:compileDebugSwift', ':greeter:compileTestSwift', ':greeter:linkDebug',
            ':greeter:linkTest', ':greeter:bundleSwiftTest', ':greeter:xcTest', ':greeter:test', ':app:compileDebugSwift',
            ':app:compileTestSwift', ':app:linkTest', ':app:bundleSwiftTest', ':app:xcTest', ':app:test', ':compileTestSwift',
            ':linkTest', ':bundleSwiftTest', ':xcTest', ':test')
    }

    @Unroll
    def "runs tests when #task lifecycle task executes"() {
        def testBundle = new SwiftXCTestBundleWithInfoPlist()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds(task)

        then:
        executed(":xcTest")
        testBundle.assertTestCasesRan(testExecutionResult)

        where:
        task << ["test", "check", "build"]
    }

    def "doesn't execute removed test suite and case"() {
        def testBundle = new IncrementalSwiftXCTestRemoveDiscoveryBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.assertTestCasesRan(testExecutionResult)

        when:
        testBundle.applyChangesToProject(testDirectory)
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.assertAlternateTestCasesRan(testExecutionResult)
    }

    def "executes added test suite and case"() {
        def testBundle = new IncrementalSwiftXCTestAddDiscoveryBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.assertTestCasesRan(testExecutionResult)

        when:
        testBundle.applyChangesToProject(testDirectory)
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.assertAlternateTestCasesRan(testExecutionResult)
    }

    def "skips test tasks as up-to-date when nothing changes between invocation"() {
        def testBundle = new SwiftXCTestBundleWithInfoPlist()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        result.assertTasksSkipped(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "can specify a test dependency on another library"() {
        def lib = new SwiftLibWithXCTestWithInfoPlist()

        given:
        settingsFile << """
include 'greeter'
"""
        buildFile << """
project(':greeter') {
    apply plugin: 'swift-library'
}

dependencies {
    testImplementation project(':greeter')
}
"""
        lib.main.writeToProject(file('greeter'))
        lib.test.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":greeter:compileDebugSwift", ":greeter:linkDebug", ":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
    }

    def "does not build or run any of the tests when assemble task executes"() {
        def testBundle = new SwiftFailingXCTestBundle()

        given:
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("assemble")

        then:
        result.assertTasksExecuted(":assemble")
        result.assertTasksSkipped(":assemble")
    }

    def "build logic can change source layout convention"() {
        def testBundle = new SwiftXCTestBundleWithInfoPlist()

        given:
        testBundle.writeToSourceDir(file("Tests"))
        file("src/test/swift/broken.swift") << "ignore me!"

        and:
        buildFile << """
            xctest {
                source.from 'Tests'
                resourceDir.set(file('Tests'))
            }
         """

        expect:
        succeeds "test"
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")

        file("build/obj/test").assertIsDir()
        executable("build/exe/test/AppTest").assertExists()
        testBundle.assertTestCasesRan(testExecutionResult)
    }

    def "build passes when tests have unicode characters"() {
        given:
        def testBundle = new SwiftXCTestBundleWithInfoPlist()
        testBundle.testSuites += new XCTestSourceFileElement() {
            String testSuiteName = "SpecialCharsTestSuite"
            List<XCTestCaseElement> testCases = [
                testCase("test_name_with_leading_underscore", "XCTAssert(true)"),
                testCase("testname_with_a_number_1234", "XCTAssert(true)"),
                testCase("test·middle_dot", "XCTAssert(true)"),
                testCase("test1234", "XCTAssert(true)"),
                testCase("testᏀᎡᎪᎠᏞᎬ", "XCTAssert(true)")
            ]
            String moduleName = "AppTest"
        }
        testBundle.testSuites += new XCTestSourceFileElement() {
            String testSuiteName = "UnicodeᏀᎡᎪᎠᏞᎬSuite"
            List<XCTestCaseElement> testCases = [
                testCase("testSomething", "XCTAssert(true)"),
            ]
            String moduleName = "AppTest"
        }
        testBundle.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest", ":test")
        testBundle.assertTestCasesRan(testExecutionResult)
    }

    def "build still fails when tests have unicode characters"() {
        given:
        def testBundle = new SwiftFailingXCTestBundle()
        testBundle.testSuites += new XCTestSourceFileElement() {
            String testSuiteName = "FailingSpecialCharsTestSuite"
            List<XCTestCaseElement> testCases = [
                testCase("test·middle_dot", "XCTAssert(false)", true),
            ]
            String moduleName = "AppTest"
        }
        testBundle.testSuites += new XCTestSourceFileElement() {
            String testSuiteName = "UnicodeᏀᎡᎪᎠᏞᎬSuite"
            List<XCTestCaseElement> testCases = [
                testCase("testSomething", "XCTAssert(false)", true),
            ]
            String moduleName = "AppTest"
        }
        testBundle.writeToProject(testDirectory)

        when:
        fails("test")

        then:
        result.assertTasksExecuted(":compileTestSwift", ":linkTest", ":bundleSwiftTest", ":xcTest")
        testBundle.assertTestCasesRan(testExecutionResult)
    }

    TestExecutionResult getTestExecutionResult() {
        return new DefaultTestExecutionResult(testDirectory, 'build', '', '', 'xcTest')
    }
}
