/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2012 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.batch;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.hamcrest.core.Is;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.test.TestUtils;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Properties;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class MavenProjectConverterTest {

  /**
   * See SONAR-2681
   */
  public void shouldThrowExceptionWhenUnableToDetermineProjectStructure() {
    MavenProject root = new MavenProject();
    root.setFile(new File("/foo/pom.xml"));
    root.getBuild().setDirectory("target");
    root.getModules().add("module/pom.xml");

    try {
      MavenProjectConverter.convert(Arrays.asList(root), root);
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), containsString("Advanced Reactor Options"));
    }
  }

  @Test
  public void shouldConvertModules() {
    MavenProject root = new MavenProject();
    root.setFile(new File("/foo/pom.xml"));
    root.getBuild().setDirectory("target");
    root.getModules().add("module/pom.xml");
    MavenProject module = new MavenProject();
    module.setFile(new File("/foo/module/pom.xml"));
    module.getBuild().setDirectory("target");
    ProjectDefinition project = MavenProjectConverter.convert(Arrays.asList(root, module), root);

    assertThat(project.getSubProjects().size(), is(1));
  }

  @Test
  public void shouldConvertProperties() {
    MavenProject pom = new MavenProject();
    pom.setGroupId("foo");
    pom.setArtifactId("bar");
    pom.setVersion("1.0.1");
    pom.setName("Test");
    pom.setDescription("just test");
    pom.setFile(new File("/foo/pom.xml"));
    pom.getBuild().setDirectory("target");
    ProjectDefinition project = MavenProjectConverter.convert(pom);

    Properties properties = project.getProperties();
    assertThat(properties.getProperty(CoreProperties.PROJECT_KEY_PROPERTY), is("foo:bar"));
    assertThat(properties.getProperty(CoreProperties.PROJECT_VERSION_PROPERTY), is("1.0.1"));
    assertThat(properties.getProperty(CoreProperties.PROJECT_NAME_PROPERTY), is("Test"));
    assertThat(properties.getProperty(CoreProperties.PROJECT_DESCRIPTION_PROPERTY), is("just test"));
  }

  @Test
  public void moduleNameShouldEqualArtifactId() throws Exception {
    File rootDir = TestUtils.getResource("/org/sonar/batch/MavenProjectConverterTest/moduleNameShouldEqualArtifactId/");
    MavenProject parent = loadPom("/org/sonar/batch/MavenProjectConverterTest/moduleNameShouldEqualArtifactId/pom.xml", true);
    MavenProject module1 = loadPom("/org/sonar/batch/MavenProjectConverterTest/moduleNameShouldEqualArtifactId/module1/pom.xml", false);
    MavenProject module2 = loadPom("/org/sonar/batch/MavenProjectConverterTest/moduleNameShouldEqualArtifactId/module2/pom.xml", false);

    ProjectDefinition rootDef = MavenProjectConverter.convert(Arrays.asList(parent, module1, module2), parent);

    assertThat(rootDef.getSubProjects().size(), Is.is(2));
    assertThat(rootDef.getKey(), Is.is("org.test:parent"));
    assertNull(rootDef.getParent());
    assertThat(rootDef.getBaseDir(), is(rootDir));

    ProjectDefinition module1Def = rootDef.getSubProjects().get(0);
    assertThat(module1Def.getKey(), Is.is("org.test:module1"));
    assertThat(module1Def.getParent(), Is.is(rootDef));
    assertThat(module1Def.getBaseDir(), Is.is(new File(rootDir, "module1")));
    assertThat(module1Def.getSubProjects().size(), Is.is(0));
  }

  @Test
  public void moduleNameDifferentThanArtifactId() throws Exception {
    File rootDir = TestUtils.getResource("/org/sonar/batch/MavenProjectConverterTest/moduleNameDifferentThanArtifactId/");
    MavenProject parent = loadPom("/org/sonar/batch/MavenProjectConverterTest/moduleNameDifferentThanArtifactId/pom.xml", true);
    MavenProject module1 = loadPom("/org/sonar/batch/MavenProjectConverterTest/moduleNameDifferentThanArtifactId/path1/pom.xml", false);
    MavenProject module2 = loadPom("/org/sonar/batch/MavenProjectConverterTest/moduleNameDifferentThanArtifactId/path2/pom.xml", false);

    ProjectDefinition rootDef = MavenProjectConverter.convert(Arrays.asList(parent, module1, module2), parent);

    assertThat(rootDef.getSubProjects().size(), is(2));
    assertThat(rootDef.getKey(), is("org.test:parent"));
    assertNull(rootDef.getParent());
    assertThat(rootDef.getBaseDir(), is(rootDir));

    ProjectDefinition module1Def = rootDef.getSubProjects().get(0);
    assertThat(module1Def.getKey(), Is.is("org.test:module1"));
    assertThat(module1Def.getParent(), Is.is(rootDef));
    assertThat(module1Def.getBaseDir(), Is.is(new File(rootDir, "path1")));
    assertThat(module1Def.getSubProjects().size(), Is.is(0));
  }

  @Test
  public void testSingleProjectWithoutModules() throws Exception {
    File rootDir = TestUtils.getResource("/org/sonar/batch/MavenProjectConverterTest/singleProjectWithoutModules/");
    MavenProject pom = loadPom("/org/sonar/batch/MavenProjectConverterTest/singleProjectWithoutModules/pom.xml", true);

    ProjectDefinition rootDef = MavenProjectConverter.convert(Arrays.asList(pom), pom);

    assertThat(rootDef.getKey(), is("org.test:parent"));
    assertThat(rootDef.getSubProjects().size(), is(0));
    assertNull(rootDef.getParent());
    assertThat(rootDef.getBaseDir(), is(rootDir));
  }

  private MavenProject loadPom(String pomPath, boolean isRoot) throws URISyntaxException, IOException, XmlPullParserException {
    File pomFile = new File(getClass().getResource(pomPath).toURI());
    Model model = new MavenXpp3Reader().read(new StringReader(FileUtils.readFileToString(pomFile)));
    MavenProject pom = new MavenProject(model);
    pom.setFile(pomFile);
    pom.getBuild().setDirectory("target");
    pom.setExecutionRoot(isRoot);
    return pom;
  }
}
