/*
 * Sonar Scalastyle Plugin
 * Copyright (C) 2014 All contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.ncredinburgh.sonar.scalastyle

import java.io.File
import java.nio.charset.StandardCharsets

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalastyle._
import org.scalastyle.file.FileLengthChecker
import org.scalastyle.scalariform.{ForBraceChecker, IfBraceChecker}
import org.scalatest._
import org.scalatest.mock.MockitoSugar
import org.sonar.api.batch.SensorContext
import org.sonar.api.component.ResourcePerspectives
import org.sonar.api.issue.{Issuable, Issue}
import org.sonar.api.resources.Project
import org.sonar.api.rules.{Rule, RuleFinder, RuleQuery}
import org.sonar.api.scan.filesystem.{FileQuery, ModuleFileSystem}
import org.sonar.core.issue.DefaultIssueBuilder

import scala.collection.JavaConversions._


/**
 * Created by keir on 13/06/14.
 */
class ScalastyleSensorSpec extends FlatSpec with Matchers with MockitoSugar with PrivateMethodTester {

  trait Fixture {
    val fs = mock[ModuleFileSystem]
    val project = mock[Project]
    val runner = mock[ScalastyleRunner]
    val perspective = mock[ResourcePerspectives]
    val issuable = mock[Issuable]
    val issueBuilder = new DefaultIssueBuilder().componentKey("foo");
    val rf = mock[RuleFinder]
    val aRule = Rule.create("repo", "key")

    val testee = new ScalastyleSensor(perspective, runner, fs, rf)
    val testeeSpy = spy(testee)
    val context = mock[SensorContext]

    when(runner.run(anyString, anyListOf(classOf[File]))).thenReturn(List())
    when(fs.sourceCharset()).thenReturn(StandardCharsets.UTF_8)
    when(perspective.as(any(), any(classOf[org.sonar.api.resources.Resource]))).thenReturn(issuable)
    when(issuable.newIssueBuilder()).thenReturn(issueBuilder)
    when(rf.find(any[RuleQuery])).thenReturn(aRule)
  }

  "A Scalastyle Sensor" should "execute when the project have Scala files" in new Fixture {
    val scalaFiles = List(new File("foo"), new File("bar"))
    val query = mock[FileQuery]
    doReturn(query).when(testeeSpy).ScalaFileQuery
    when(fs.files(query)).thenReturn(scalaFiles)

    val shouldExecuteOnProject = testeeSpy.shouldExecuteOnProject(project)

    shouldExecuteOnProject shouldBe true
  }

  it should "not execute when there isn't any Scala files" in new Fixture {
    val query = mock[FileQuery]
    doReturn(query).when(testeeSpy).ScalaFileQuery
    when(fs.files(any[FileQuery])).thenReturn(List())

    val shouldExecuteOnProject = testeeSpy.shouldExecuteOnProject(project)

    shouldExecuteOnProject shouldBe false
  }

  it should "analyse all scala source files in project" in new Fixture {
    val scalaFiles = List(new File("foo"), new File("bar"))
    val query = mock[FileQuery]
    doReturn(query).when(testeeSpy).ScalaFileQuery
    when(fs.files(any[FileQuery])).thenReturn(scalaFiles)

    testeeSpy.analyse(project, context)

    verify(runner).run(StandardCharsets.UTF_8.name(), scalaFiles)
  }

  it should "not create SonarQube issues when there isn't any scalastyle errors" in new Fixture {
    when(runner.run(anyString, anyListOf(classOf[File]))).thenReturn(List())

    testee.analyse(project, context)

    verify(issuable, never).addIssue(any[Issue])
  }

  it should "report a scalastyle error as a SonarQube issue" in new Fixture {
    val anError = new StyleError[FileSpec](new RealFileSpec("foo", None), classOf[ForBraceChecker], "org.scalastyle.scalariform.ForBraceChecker", WarningLevel, List(), None)
    when(runner.run(anyString, anyListOf(classOf[File]))).thenReturn(List(anError))

    testee.analyse(project, context)

    verify(issuable, times(1)).addIssue(any[Issue])
  }

  it should "report scalastyle errors as SonarQube issues" in new Fixture {
    val error1 = new StyleError[FileSpec](new RealFileSpec("foo", None), classOf[FileLengthChecker], "org.scalastyle.file.FileLengthChecker", WarningLevel, List(), None)
    val error2 = new StyleError[FileSpec](new RealFileSpec("bar", None), classOf[IfBraceChecker], "org.scalastyle.scalariform.IfBraceChecker", WarningLevel, List(), None)
    when(runner.run(anyString, anyListOf(classOf[File]))).thenReturn(List(error1, error2))

    testee.analyse(project, context)

    verify(issuable, times(2)).addIssue(any[Issue])
  }

  it should "find sonar rule for error" in new Fixture {
    val findSonarRuleForError = PrivateMethod[Rule]('findSonarRuleForError)
    val error = new StyleError[FileSpec](new RealFileSpec("foo", None), classOf[FileLengthChecker], "org.scalastyle.file.FileLengthChecker", WarningLevel, List(), None)

    val rule = testee invokePrivate findSonarRuleForError(error)

    rule.getKey shouldEqual "key"
  }
}
