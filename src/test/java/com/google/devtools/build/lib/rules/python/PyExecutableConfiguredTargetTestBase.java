// Copyright 2018 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.rules.python;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.devtools.build.lib.rules.python.PythonTestUtils.ensureDefaultIsPY2;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.devtools.build.lib.actions.Action;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.FailAction;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import org.junit.Test;

/** Tests that are common to {@code py_binary} and {@code py_test}. */
public abstract class PyExecutableConfiguredTargetTestBase extends PyBaseConfiguredTargetTestBase {

  private final String ruleName;

  protected PyExecutableConfiguredTargetTestBase(String ruleName) {
    super(ruleName);
    this.ruleName = ruleName;
  }

  /**
   * Returns the configured target with the given label while asserting that, if it is an executable
   * target, the executable is not produced by {@link FailAction}.
   *
   * <p>This serves as a drop-in replacement for {@link #getConfiguredTarget} that will also catch
   * unexpected deferred failures (e.g. {@code srcs_versions} validation failures) in {@code
   * py_binary} and {@code py_library} targets.
   */
  protected ConfiguredTarget getOkPyTarget(String label) throws Exception {
    ConfiguredTarget target = getConfiguredTarget(label);
    // It can be null without events due to b/26382502.
    Preconditions.checkNotNull(target, "target was null (is it misspelled or in error?)");
    Artifact executable = getExecutable(target);
    if (executable != null) {
      Action action = getGeneratingAction(executable);
      if (action instanceof FailAction) {
        throw new AssertionError(
            String.format(
                "execution of target would fail with error '%s'",
                ((FailAction) action).getErrorMessage()));
      }
    }
    return target;
  }

  /**
   * Gets the configured target for an executable Python rule (generally {@code py_binary} or {@code
   * py_library}) and asserts that it produces a deferred error via {@link FailAction}.
   *
   * @return the deferred error string
   */
  protected String getPyExecutableDeferredError(String label) throws Exception {
    ConfiguredTarget target = getConfiguredTarget(label);
    // It can be null without events due to b/26382502.
    Preconditions.checkNotNull(target, "target was null (is it misspelled or in error?)");
    Artifact executable = getExecutable(target);
    Preconditions.checkNotNull(
        executable, "executable was null (is this a py_binary/py_test target?)");
    Action action = getGeneratingAction(executable);
    assertThat(action).isInstanceOf(FailAction.class);
    return ((FailAction) action).getErrorMessage();
  }

  /**
   * Sets the configuration, then asserts that a configured target has the given Python version.
   *
   * <p>The configuration is given as a series of "--flag=value" strings.
   */
  protected void assertPythonVersionIs_UnderNewConfig(
      String targetName, PythonVersion version, String... flags) throws Exception {
    useConfiguration(flags);
    assertThat(getPythonVersion(getOkPyTarget(targetName))).isEqualTo(version);
  }

  /**
   * Asserts that a configured target has the given Python version under multiple configurations.
   *
   * <p>The configurations are given as a series of arrays of "--flag=value" strings.
   *
   * <p>This destructively changes the current configuration.
   */
  protected void assertPythonVersionIs_UnderNewConfigs(
      String targetName, PythonVersion version, String[]... configs) throws Exception {
    for (String[] config : configs) {
      useConfiguration(config);
      assertWithMessage(String.format("Under config '%s'", Joiner.on(" ").join(config)))
          .that(getPythonVersion(getOkPyTarget(targetName)))
          .isEqualTo(version);
    }
  }

  private static String join(String... lines) {
    return String.join("\n", lines);
  }

  private String ruleDeclWithDefaultPyVersionAttr(String name, String version) {
    return join(
        ruleName + "(",
        "    name = '" + name + "',",
        "    srcs = ['" + name + ".py'],",
        "    default_python_version = '" + version + "',",
        ")");
  }

  private String ruleDeclWithPyVersionAttr(String name, String version) {
    return join(
        ruleName + "(",
        "    name = '" + name + "',",
        "    srcs = ['" + name + ".py'],",
        "    python_version = '" + version + "'",
        ")");
  }

  @Test
  public void oldVersionAttr_UnknownValue() throws Exception {
    useConfiguration("--experimental_remove_old_python_version_api=false");
    checkError(
        "pkg",
        "foo",
        // error:
        "invalid value in 'default_python_version' attribute: "
            + "has to be one of 'PY2' or 'PY3' instead of 'doesnotexist'",
        // build file:
        ruleDeclWithDefaultPyVersionAttr("foo", "doesnotexist"));
  }

  @Test
  public void newVersionAttr_UnknownValue() throws Exception {
    checkError(
        "pkg",
        "foo",
        // error:
        "invalid value in 'python_version' attribute: "
            + "has to be one of 'PY2' or 'PY3' instead of 'doesnotexist'",
        // build file:
        ruleDeclWithPyVersionAttr("foo", "doesnotexist"));
  }

  @Test
  public void oldVersionAttr_BadValue() throws Exception {
    useConfiguration("--experimental_remove_old_python_version_api=false");
    checkError(
        "pkg",
        "foo",
        // error:
        "invalid value in 'default_python_version' attribute: "
            + "has to be one of 'PY2' or 'PY3' instead of 'PY2AND3'",
        // build file:
        ruleDeclWithDefaultPyVersionAttr("foo", "PY2AND3"));
  }

  @Test
  public void newVersionAttr_BadValue() throws Exception {
    checkError(
        "pkg",
        "foo",
        // error:
        "invalid value in 'python_version' attribute: "
            + "has to be one of 'PY2' or 'PY3' instead of 'PY2AND3'",
        // build file:
        ruleDeclWithPyVersionAttr("foo", "PY2AND3"));
  }

  @Test
  public void oldVersionAttr_GoodValue() throws Exception {
    useConfiguration("--experimental_remove_old_python_version_api=false");
    scratch.file("pkg/BUILD", ruleDeclWithDefaultPyVersionAttr("foo", "PY2"));
    getOkPyTarget("//pkg:foo");
    assertNoEvents();
  }

  @Test
  public void newVersionAttr_GoodValue() throws Exception {
    scratch.file("pkg/BUILD", ruleDeclWithPyVersionAttr("foo", "PY2"));
    getOkPyTarget("//pkg:foo");
    assertNoEvents();
  }

  @Test
  public void cannotUseOldVersionAttrWithRemovalFlag() throws Exception {
    useConfiguration("--experimental_remove_old_python_version_api=true");
    checkError(
        "pkg",
        "foo",
        // error:
        "the 'default_python_version' attribute is disabled by the "
            + "'--experimental_remove_old_python_version_api' flag",
        // build file:
        ruleDeclWithDefaultPyVersionAttr("foo", "PY2"));
  }

  /**
   * Regression test for #7071: Don't let prohibiting the old attribute get in the way of cloning a
   * target using {@code native.existing_rules()}.
   *
   * <p>The use case of cloning a target is pretty dubious and brittle. But as long as it's possible
   * and not proscribed, we won't let version attribute validation get in the way.
   */
  @Test
  public void canCopyTargetWhenOldAttrDisallowed() throws Exception {
    useConfiguration("--experimental_remove_old_python_version_api=true");
    scratch.file(
        "pkg/rules.bzl",
        "def copy_target(rulefunc, src, dest):",
        "    t = native.existing_rule(src)",
        "    t.pop('kind')",
        "    t.pop('name')",
        "    # Also remove these because they get in the way of creating the new target but aren't",
        "    # related to the attribute under test.",
        "    t.pop('restricted_to')",
        "    t.pop('shard_count', default=None)",
        "    rulefunc(name = dest, **t)");
    scratch.file(
        "pkg/BUILD",
        "load(':rules.bzl', 'copy_target')",
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'],",
        "    main = 'foo.py',",
        "    python_version = 'PY2',",
        ")",
        "copy_target(" + ruleName + ", 'foo', 'bar')");
    ConfiguredTarget target = getConfiguredTarget("//pkg:bar");
    assertThat(target).isNotNull();
  }

  @Test
  public void newVersionAttrTakesPrecedenceOverOld() throws Exception {
    scratch.file(
        "pkg/BUILD",
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = ['foo.py'],",
        "    default_python_version = 'PY2',",
        "    python_version = 'PY3',",
        ")");
    assertPythonVersionIs_UnderNewConfig(
        "//pkg:foo", PythonVersion.PY3, "--experimental_remove_old_python_version_api=false");
  }

  @Test
  public void versionAttrWorksUnderOldAndNewSemantics_WhenNotDefaultValue() throws Exception {
    ensureDefaultIsPY2();
    scratch.file("pkg/BUILD", ruleDeclWithDefaultPyVersionAttr("foo", "PY3"));

    assertPythonVersionIs_UnderNewConfigs(
        "//pkg:foo",
        PythonVersion.PY3,
        new String[] {"--experimental_allow_python_version_transitions=false"},
        new String[] {"--experimental_allow_python_version_transitions=true"});
  }

  @Test
  public void versionAttrWorksUnderOldAndNewSemantics_WhenSameAsDefaultValue() throws Exception {
    ensureDefaultIsPY2();
    scratch.file("pkg/BUILD", ruleDeclWithDefaultPyVersionAttr("foo", "PY2"));

    assertPythonVersionIs_UnderNewConfigs(
        "//pkg:foo",
        PythonVersion.PY2,
        new String[] {"--experimental_allow_python_version_transitions=false"},
        new String[] {"--experimental_allow_python_version_transitions=true"});
  }

  @Test
  public void flagTakesPrecedenceUnderOldSemantics_NonDefaultValue() throws Exception {
    ensureDefaultIsPY2();
    scratch.file("pkg/BUILD", ruleDeclWithDefaultPyVersionAttr("foo", "PY2"));
    assertPythonVersionIs_UnderNewConfig(
        "//pkg:foo",
        PythonVersion.PY3,
        "--experimental_allow_python_version_transitions=false",
        "--force_python=PY3");
  }

  @Test
  public void flagTakesPrecedenceUnderOldSemantics_DefaultValue() throws Exception {
    ensureDefaultIsPY2();
    scratch.file("pkg/BUILD", ruleDeclWithDefaultPyVersionAttr("foo", "PY3"));
    assertPythonVersionIs_UnderNewConfig(
        "//pkg:foo",
        PythonVersion.PY2,
        "--experimental_allow_python_version_transitions=false",
        "--force_python=PY2");
  }

  @Test
  public void versionAttrTakesPrecedenceUnderNewSemantics_NonDefaultValue() throws Exception {
    ensureDefaultIsPY2();
    scratch.file("pkg/BUILD", ruleDeclWithDefaultPyVersionAttr("foo", "PY3"));

    // Test against both flags.
    assertPythonVersionIs_UnderNewConfigs(
        "//pkg:foo",
        PythonVersion.PY3,
        new String[] {
          "--experimental_allow_python_version_transitions=true",
          "--experimental_remove_old_python_version_api=false",
          "--force_python=PY2"
        },
        new String[] {
          "--experimental_allow_python_version_transitions=true", "--python_version=PY2"
        });
  }

  @Test
  public void versionAttrTakesPrecedenceUnderNewSemantics_DefaultValue() throws Exception {
    ensureDefaultIsPY2();
    scratch.file("pkg/BUILD", ruleDeclWithDefaultPyVersionAttr("foo", "PY2"));

    // Test against both flags.
    assertPythonVersionIs_UnderNewConfigs(
        "//pkg:foo",
        PythonVersion.PY2,
        new String[] {
          "--experimental_allow_python_version_transitions=true",
          "--experimental_remove_old_python_version_api=false",
          "--force_python=PY3"
        },
        new String[] {
          "--experimental_allow_python_version_transitions=true", "--python_version=PY3"
        });
  }

  @Test
  public void canBuildWithDifferentVersionAttrs_UnderOldAndNewSemantics() throws Exception {
    scratch.file(
        "pkg/BUILD",
        ruleDeclWithDefaultPyVersionAttr("foo_v2", "PY2"),
        ruleDeclWithDefaultPyVersionAttr("foo_v3", "PY3"));

    assertPythonVersionIs_UnderNewConfigs(
        "//pkg:foo_v2",
        PythonVersion.PY2,
        new String[] {"--experimental_allow_python_version_transitions=false"},
        new String[] {"--experimental_allow_python_version_transitions=true"});
    assertPythonVersionIs_UnderNewConfigs(
        "//pkg:foo_v3",
        PythonVersion.PY3,
        new String[] {"--experimental_allow_python_version_transitions=false"},
        new String[] {"--experimental_allow_python_version_transitions=true"});
  }

  @Test
  public void canBuildWithDifferentVersionAttrs_UnderOldSemantics_FlagSetToDefault()
      throws Exception {
    ensureDefaultIsPY2();
    scratch.file(
        "pkg/BUILD",
        ruleDeclWithDefaultPyVersionAttr("foo_v2", "PY2"),
        ruleDeclWithDefaultPyVersionAttr("foo_v3", "PY3"));

    assertPythonVersionIs_UnderNewConfig("//pkg:foo_v2", PythonVersion.PY2, "--force_python=PY2");
    assertPythonVersionIs_UnderNewConfig("//pkg:foo_v3", PythonVersion.PY2, "--force_python=PY2");
  }

  @Test
  public void canBuildWithDifferentVersionAttrs_UnderOldSemantics_FlagSetToNonDefault()
      throws Exception {
    ensureDefaultIsPY2();
    scratch.file(
        "pkg/BUILD",
        ruleDeclWithDefaultPyVersionAttr("foo_v2", "PY2"),
        ruleDeclWithDefaultPyVersionAttr("foo_v3", "PY3"));

    assertPythonVersionIs_UnderNewConfig("//pkg:foo_v2", PythonVersion.PY3, "--force_python=PY3");
    assertPythonVersionIs_UnderNewConfig("//pkg:foo_v3", PythonVersion.PY3, "--force_python=PY3");
  }

  @Test
  public void incompatibleSrcsVersion_OldSemantics() throws Exception {
    useConfiguration("--experimental_allow_python_version_transitions=false");
    checkError(
        "pkg",
        "foo",
        // error:
        "'//pkg:foo' can only be used with Python 2",
        // build file:
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = [':foo.py'],",
        "    srcs_version = 'PY2ONLY',",
        "    default_python_version = 'PY3')");
  }

  @Test
  public void incompatibleSrcsVersion_NewSemantics() throws Exception {
    reporter.removeHandler(failFastHandler); // We assert below that we don't fail at analysis.
    useConfiguration("--experimental_allow_python_version_transitions=true");
    scratch.file(
        "pkg/BUILD",
        // build file:
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = [':foo.py'],",
        "    srcs_version = 'PY2ONLY',",
        "    default_python_version = 'PY3')");
    // Under the new semantics, this is an execution-time error, not an analysis-time one. We fail
    // by setting the generating action to FailAction.
    assertNoEvents();
    assertThat(getPyExecutableDeferredError("//pkg:foo"))
        .contains("being built for Python 3 but (transitively) includes Python 2-only sources");
  }

  @Test
  public void incompatibleSrcsVersion_DueToVersionAttrDefault() throws Exception {
    ensureDefaultIsPY2(); // When changed to PY3, flip srcs_version below to be PY2ONLY.

    // This test doesn't care whether we use old and new semantics, but it affects how we assert.
    useConfiguration("--experimental_allow_python_version_transitions=false");

    // Fails because default_python_version is PY2 by default, so the config is set to PY2
    // regardless of srcs_version.
    checkError("pkg", "foo",
        // error:
        "'//pkg:foo' can only be used with Python 3",
        // build file:
        ruleName + "(",
        "    name = 'foo',",
        "    srcs = [':foo.py'],",
        "    srcs_version = 'PY3ONLY')");
  }
}
