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
package org.sonar.server.plugins;

import com.google.common.base.*;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.CharEncoding;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @since 3.0
 */
public final class ClassLoaderUtils {

  private ClassLoaderUtils() {
  }

  public static File copyResources(ClassLoader classLoader, String rootPath, File toDir) {
    return copyResources(classLoader, rootPath, toDir, Functions.<String>identity());
  }

  public static File copyResources(ClassLoader classLoader, String rootPath, File toDir, Function<String, String> relocationFunction) {
    Collection<String> relativePaths = listFiles(classLoader, rootPath);
    for (String relativePath : relativePaths) {
      URL resource = classLoader.getResource(relativePath);
      String filename = relocationFunction.apply(relativePath);
      File toFile = new File(toDir, filename);
      try {
        FileUtils.copyURLToFile(resource, toFile);
      } catch (IOException e) {
        throw new IllegalStateException("Fail to extract " + relativePath + " to " + toFile.getAbsolutePath());
      }
    }

    return toDir;
  }

  /**
   * Finds files within a given directory and its subdirectories
   *
   * @param classLoader
   * @param rootPath    the root directory, for example org/sonar/sqale
   * @return a list of relative paths, for example {"org/sonar/sqale/foo/bar.txt}. Never null.
   */
  public static Collection<String> listFiles(ClassLoader classLoader, String rootPath) {
    return listResources(classLoader, rootPath, new Predicate<String>() {
      public boolean apply(@Nullable String path) {
        return !StringUtils.endsWith(path, "/");
      }
    });
  }


  public static Collection<String> listResources(ClassLoader classLoader, String rootPath) {
    return listResources(classLoader, rootPath, Predicates.<String>alwaysTrue());
  }

  /**
   * Finds directories and files within a given directory and its subdirectories.
   *
   * @param classLoader
   * @param rootPath    the root directory, for example org/sonar/sqale, or a file in this root directory, for example org/sonar/sqale/index.txt
   * @param
   * @return a list of relative paths, for example {"org/sonar/sqale", "org/sonar/sqale/foo", "org/sonar/sqale/foo/bar.txt}. Never null.
   */
  public static Collection<String> listResources(ClassLoader classLoader, String rootPath, Predicate<String> predicate) {
    String jarPath = null;
    JarFile jar = null;
    try {
      Collection<String> paths = Lists.newArrayList();
      rootPath = StringUtils.removeStart(rootPath, "/");

      URL root = classLoader.getResource(rootPath);
      if (root != null) {
        checkJarFile(root);

        // Path of the root directory
        // Examples :
        // org/sonar/sqale/index.txt  -> rootDirectory is org/sonar/sqale
        // org/sonar/sqale/  -> rootDirectory is org/sonar/sqale
        // org/sonar/sqale  -> rootDirectory is org/sonar/sqale
        String rootDirectory = rootPath;
        if (StringUtils.substringAfterLast(rootPath, "/").indexOf('.') >= 0) {
          rootDirectory = StringUtils.substringBeforeLast(rootPath, "/");
        }
        jarPath = root.getPath().substring(5, root.getPath().indexOf("!")); //strip out only the JAR file
        jar = new JarFile(URLDecoder.decode(jarPath, CharEncoding.UTF_8));
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
          String name = entries.nextElement().getName();
          if (name.startsWith(rootDirectory) && predicate.apply(name)) {
            paths.add(name);
          }
        }
      }
      return paths;
    } catch (Exception e) {
      throw Throwables.propagate(e);
    } finally {
      closeJar(jar, jarPath);
    }
  }

  private static void closeJar(JarFile jar, String jarPath) {
    if (jar != null) {
      try {
        jar.close();
      } catch (Exception e) {
        LoggerFactory.getLogger(ClassLoaderUtils.class).error("Fail to close JAR file: " + jarPath, e);
      }
    }
  }

  private static void checkJarFile(URL root) {
    if (!"jar".equals(root.getProtocol())) {
      throw new IllegalStateException("Unsupported protocol: " + root.getProtocol());
    }
  }
}
