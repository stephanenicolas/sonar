/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2011 SonarSource
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
package org.sonar.persistence;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.sonar.api.BatchComponent;
import org.sonar.api.ServerComponent;
import org.sonar.persistence.model.Duplication;
import org.sonar.persistence.model.DuplicationMapper;
import org.sonar.persistence.model.Rule;
import org.sonar.persistence.model.RuleMapper;

import java.io.IOException;
import java.io.InputStream;

public class MyBatis implements BatchComponent, ServerComponent {

  private Database database;
  private SqlSessionFactory sessionFactory;

  public MyBatis(Database database) {
    this.database = database;
  }

  public MyBatis start() throws IOException {
    Configuration conf = new Configuration();
    conf.setEnvironment(new Environment("production", createTransactionFactory(), database.getDataSource()));
    conf.setUseGeneratedKeys(true);
    conf.setLazyLoadingEnabled(false);

    loadMapper(conf, DuplicationMapper.class, "Duplication", Duplication.class);
    loadMapper(conf, RuleMapper.class, "Rule", Rule.class);

    sessionFactory = new SqlSessionFactoryBuilder().build(conf);
    return this;
  }

  public SqlSessionFactory getSessionFactory() {
    return sessionFactory;
  }

  public SqlSession openSession() {
    return sessionFactory.openSession();
  }

  public SqlSession openSession(ExecutorType type) {
    return sessionFactory.openSession(type);
  }

  private void loadMapper(Configuration conf, Class mapperClass, String alias, Class dtoClass) throws IOException {
    // trick to use database-specific XML files for a single Mapper Java interface
    InputStream input = getClass().getResourceAsStream("/" + StringUtils.replace(mapperClass.getName(), ".", "/") + ".xml");
    try {
      conf.getTypeAliasRegistry().registerAlias(alias, dtoClass);
      XMLMapperBuilder mapperParser = new XMLMapperBuilder(input, conf, mapperClass.getName(), conf.getSqlFragments());
      mapperParser.parse();
      conf.addLoadedResource(mapperClass.getName());

    } finally {
      IOUtils.closeQuietly(input);
    }
  }

  private static JdbcTransactionFactory createTransactionFactory() {
    return new JdbcTransactionFactory();
  }

}