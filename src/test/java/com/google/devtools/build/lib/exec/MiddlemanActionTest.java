// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.exec;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.devtools.build.lib.actions.util.ActionsTestUtil.NULL_ACTION_OWNER;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.MiddlemanAction;
import com.google.devtools.build.lib.actions.MiddlemanFactory;
import com.google.devtools.build.lib.analysis.util.AnalysisTestUtil;
import com.google.devtools.build.lib.analysis.util.BuildViewTestCase;
import com.google.devtools.build.lib.cmdline.RepositoryName;
import com.google.devtools.build.lib.skyframe.serialization.testutils.SerializationTester;
import com.google.devtools.build.lib.testutil.Suite;
import com.google.devtools.build.lib.testutil.TestSpec;
import com.google.devtools.build.lib.vfs.FileSystem;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A test for {@link MiddlemanAction}.
 */
@TestSpec(size = Suite.SMALL_TESTS)
@RunWith(JUnit4.class)
public class MiddlemanActionTest extends BuildViewTestCase {

  private AnalysisTestUtil.CollectingAnalysisEnvironment analysisEnvironment;
  private MiddlemanFactory middlemanFactory;
  private Artifact a, b, middle;

  @Before
  public final void initializeMiddleman() throws Exception  {
    scratch.file("a/BUILD",
                "testing_dummy_rule(name='a', outs=['a.out'])");
    scratch.file("b/BUILD",
                "testing_dummy_rule(name='b', outs=['b.out'])");
    a = getFilesToBuild(getConfiguredTarget("//a")).iterator().next();
    b = getFilesToBuild(getConfiguredTarget("//b")).iterator().next();
    analysisEnvironment =
        new AnalysisTestUtil.CollectingAnalysisEnvironment(
            AnalysisTestUtil.STUB_ANALYSIS_ENVIRONMENT);
    middlemanFactory = new MiddlemanFactory(view.getArtifactFactory(), analysisEnvironment);
    middle = middlemanFactory.createAggregatingMiddleman(
        NULL_ACTION_OWNER, "middleman_test",
        Arrays.asList(a, b),
        targetConfig.getMiddlemanDirectory(RepositoryName.MAIN));
    analysisEnvironment.registerWith(getMutableActionGraph());
  }

  @Test
  public void testActionIsAMiddleman() {
    Action middleman = getGeneratingAction(middle);
    assertWithMessage("Encountered instance of " + middleman.getClass())
        .that(middleman.getActionType().isMiddleman())
        .isTrue();
  }

  @Test
  public void testAAndBAreInputsToMiddleman() {
    MiddlemanAction middleman = (MiddlemanAction) getGeneratingAction(middle);
    assertThat(middleman.getInputs()).containsExactly(a, b);
  }

  @Test
  public void testMiddleIsOutputOfMiddleman() {
    MiddlemanAction middleman = (MiddlemanAction) getGeneratingAction(middle);
    assertThat(middleman.getOutputs()).containsExactly(middle);
  }

  @Test
  public void testMiddlemanIsNullForEmptyInputs() throws Exception {
    assertThat(
            middlemanFactory.createAggregatingMiddleman(
                NULL_ACTION_OWNER,
                "middleman_test",
                new ArrayList<Artifact>(),
                targetConfig.getMiddlemanDirectory(RepositoryName.MAIN)))
        .isNull();
  }

  @Test
  public void testMiddlemanIsIdentityForLonelyInput() throws Exception {
    assertThat(
            middlemanFactory.createAggregatingMiddleman(
                NULL_ACTION_OWNER,
                "middleman_test",
                Lists.newArrayList(a),
                targetConfig.getMiddlemanDirectory(RepositoryName.MAIN)))
        .isEqualTo(a);
  }

  @Test
  public void testDifferentExecutablesForRunfilesMiddleman() throws Exception {
    scratch.file("c/BUILD",
                "testing_dummy_rule(name='c', outs=['c.out', 'd.out', 'common.out'])");

    Artifact c = getFilesToBuild(getConfiguredTarget("//c:c.out")).iterator().next();
    Artifact d = getFilesToBuild(getConfiguredTarget("//c:d.out")).iterator().next();
    Artifact common = getFilesToBuild(getConfiguredTarget("//c:common.out")).iterator().next();

    analysisEnvironment.clear();
    Artifact middlemanForC = middlemanFactory.createRunfilesMiddleman(
        NULL_ACTION_OWNER, c, Arrays.asList(c, common),
        targetConfig.getMiddlemanDirectory(RepositoryName.MAIN),
        "runfiles");
    Artifact middlemanForD = middlemanFactory.createRunfilesMiddleman(
        NULL_ACTION_OWNER, d, Arrays.asList(d, common),
        targetConfig.getMiddlemanDirectory(RepositoryName.MAIN),
        "runfiles");
    analysisEnvironment.registerWith(getMutableActionGraph());

    MiddlemanAction middlemanActionForC = (MiddlemanAction) getGeneratingAction(middlemanForC);
    MiddlemanAction middlemanActionForD = (MiddlemanAction) getGeneratingAction(middlemanForD);

    assertThat(Sets.newHashSet(middlemanActionForD.getInputs()))
        .isNotEqualTo(Sets.newHashSet(middlemanActionForC.getInputs()));
    assertThat(Sets.newHashSet(middlemanActionForD.getOutputs()))
        .isNotEqualTo(Sets.newHashSet(middlemanActionForC.getOutputs()));
  }

  @Test
  public void testCodec() throws Exception {
    new SerializationTester(getGeneratingAction(middle))
        .addDependency(FileSystem.class, scratch.getFileSystem())
        .setVerificationFunction(MiddlemanActionTest::verifyEquivalent)
        .runTests();
  }

  private static void verifyEquivalent(MiddlemanAction first, MiddlemanAction second) {
    assertThat(first.getActionType()).isEqualTo(second.getActionType());
    assertThat(first.getInputs()).isEqualTo(second.getInputs());
    assertThat(first.getOutputs()).isEqualTo(second.getOutputs());
    assertThat(first.getOwner()).isEqualTo(second.getOwner());
  }
}
