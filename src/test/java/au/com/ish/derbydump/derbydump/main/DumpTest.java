/*
 * Copyright 2013 ish group pty ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.com.ish.derbydump.derbydump.main;

import au.com.ish.derbydump.derbydump.config.Configuration;
import au.com.ish.derbydump.derbydump.config.DBConnectionManager;
import au.com.ish.derbydump.derbydump.metadata.Column;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.sql.rowset.serial.SerialBlob;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;

import static junit.framework.TestCase.*;

/**
 * comprehensive test for the whole process
 */
@RunWith(Parameterized.class)
public class DumpTest {

  private static final Logger LOGGER = LogManager.getLogger(DumpTest.class);

  public static final String RESOURCE_DATABASE_PATH = "memory:testdb";
  public static final String RESOURCE_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";
  public static final String RESOURCE_SCHEMA_NAME = "app";
  public static final int RESOURCE_MAX_BUFFER_SIZE = 200;

  private DBConnectionManager db;

  private Configuration config;

  private final String tableName;
  private final String outputTableName;
  private final boolean skipped;
  private final boolean truncate;
  private final String[] columns;
  private final Object[] valuesToInsert;
  private final String[] validOutputs;

  @Before
  public void setUp() throws Exception {
    config = Configuration.getConfiguration();
    config.setDerbyDbPath(RESOURCE_DATABASE_PATH);
    config.setDriverClassName(RESOURCE_DRIVER_NAME);
    config.setSchemaName(RESOURCE_SCHEMA_NAME);
    config.setBufferMaxSize(RESOURCE_MAX_BUFFER_SIZE);

    db = new DBConnectionManager(config.getDerbyUrl().replace("create=false", "create=true"));
  }

  @After
  public void tearDown() throws Exception {
    try {
      new DBConnectionManager("jdbc:derby:" + config.getDerbyDbPath() + ";drop=true");
    } catch (SQLNonTransientConnectionException e) {
      //the db was dropped
    }
  }

  public DumpTest(String tableName, String outputTableName, String[] columns, Object[] valuesToInsert, String[] validOutputs, boolean skipped, boolean truncate) {
    this.tableName = tableName;
    if (outputTableName == null) {
      this.outputTableName = tableName.toUpperCase();
    } else {
      this.outputTableName = outputTableName;
    }
    this.columns = columns;
    this.valuesToInsert = valuesToInsert;
    this.validOutputs = validOutputs;
    this.skipped = skipped;
    this.truncate = truncate;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> setupTestMatrix() throws Exception {
    List<Object[]> result = new ArrayList<>();

    //testing numbers (BIGINT, DECIMAL, REAL, SMALLINT, INTEGER, DOUBLE)
    {
      //standard set of numbers
      String[] columns = new String[]{"c1 BIGINT", "c2 DECIMAL(10,2)", "c3 REAL", "c4 SMALLINT", "c5 INTEGER", "c6 DOUBLE"};
      Object[] row1 = new Object[]{new BigInteger("12"), new BigDecimal("12.12"), new Float("12.1"), 12, 24, 12.12};
      String validOutput1 = "(12,12.12,12.1,12,24,12.12),";
      Object[] row2 = new Object[]{new BigInteger("42"), new BigDecimal("42.12"), new Float("42.14"), 42, 64, 42.14};
      String validOutput2 = "(42,42.12,42.14,42,64,42.14),";
      Object[] row3 = new Object[]{new BigInteger("42"), new BigDecimal("42"), new Float("42"), 42, 64, 42.0};
      String validOutput3 = "(42,42.00,42.0,42,64,42.0),";
      Object[] row4 = new Object[]{new BigInteger("42"), new BigDecimal("42.1234"), new Float("42.1434"), 42, 64, 42.1234};
      String validOutput4 = "(42,42.12,42.1434,42,64,42.1234),";
      Object[] row5 = new Object[]{BigDecimal.ZERO, BigDecimal.ZERO, new Float("0"), 0, 0, (double) 0};
      String validOutput5 = "(0,0.00,0.0,0,0,0.0),";
      //test nulls
      Object[] row6 = new Object[]{null, null, null, null, null, null};
      String validOutput6 = "(NULL,NULL,NULL,NULL,NULL,NULL);";
      Object[] values = new Object[]{row1, row2, row3, row4, row5, row6};
      String[] validOutput = new String[]{validOutput1, validOutput2, validOutput3, validOutput4, validOutput5, validOutput6};

      result.add(new Object[]{"testNumbers", null, columns, values, validOutput, false, false});
    }

    //testing strings
    {
      String[] columns = new String[]{"c1 VARCHAR(20)", "c2 VARCHAR(20)", "c3 VARCHAR(20)"};
      //test normal characters
      Object[] row1 = new Object[]{"123", "abc", "漢字"};
      String validOutput1 = "('123','abc','漢字'),";
      //test nulls
      Object[] row2 = new Object[]{"%", null, ""};
      String validOutput2 = "('%',NULL,''),";
      //test quotes and tabs
      Object[] row3 = new Object[]{"'test'", "\"test\"", "\t"};
      String validOutput3 = "('\\'test\\'','\"test\"','\\t'),";
      //test new line chars
      Object[] row4 = new Object[]{"\n", "\r", "\n\r"};
      String validOutput4 = "('\\n','\\r','\\n\\r');";

      Object[] values = new Object[]{row1, row2, row3, row4};
      String[] validOutput = new String[]{validOutput1, validOutput2, validOutput3, validOutput4};

      result.add(new Object[]{"testStrings", null, columns, values, validOutput, false, false});
    }

    //testing dates
    {
      String[] columns = new String[]{"c1 TIMESTAMP", "c2 TIMESTAMP"};
      // test standard dates
      Calendar c = Calendar.getInstance(TimeZone.getDefault());
      c.set(Calendar.YEAR, 2013);
      c.set(Calendar.MONTH, 5);
      c.set(Calendar.DAY_OF_MONTH, 6);
      c.set(Calendar.HOUR_OF_DAY, 11);
      c.set(Calendar.MINUTE, 10);
      c.set(Calendar.SECOND, 10);
      c.set(Calendar.MILLISECOND, 11);

      Calendar c2 = (Calendar) c.clone();
      c2.add(Calendar.DATE, -5000);

      Object[] row1 = new Object[]{c.getTime(), c2.getTime()};
      String validOutput1 = "('2013-06-06 11:10:10.011','1999-09-28 11:10:10.011'),";
      Object[] row2 = new Object[]{"2012-07-07 08:54:33", "1999-09-09 10:04:10"};
      String validOutput2 = "('2012-07-07 08:54:33.0','1999-09-09 10:04:10.0'),";
      Object[] row3 = new Object[]{null, null};
      String validOutput3 = "(NULL,NULL);";
      Object[] values = new Object[]{row1, row2, row3};
      String[] validOutput = new String[]{validOutput1, validOutput2, validOutput3};

      result.add(new Object[]{"testDates", null, columns, values, validOutput, false, false});
    }

    //testing CLOB
    {
      String[] columns = new String[]{"c1 CLOB"};
      Object[] row1 = new Object[]{"<clob value here>"};
      String validOutput1 = "('<clob value here>'),";
      Object[] row2 = new Object[]{null};
      String validOutput2 = "(NULL);";
      Object[] values = new Object[]{row1, row2};
      String[] validOutput = new String[]{validOutput1, validOutput2};

      result.add(new Object[]{"testClob", null, columns, values, validOutput, false, false});
    }

    //testing BLOB
    {
      String[] columns = new String[]{"c1 BLOB"};
      Object[] row1 = new Object[]{getTestImage()};
      Blob serialBlob = new SerialBlob(IOUtils.toByteArray(getTestImage()));
      String validOutput1 = "(" + Column.processBinaryData(serialBlob) + "),";
      Object[] row2 = new Object[]{null};
      String validOutput2 = "(NULL);";
      Object[] values = new Object[]{row1, row2};
      String[] validOutput = new String[]{validOutput1, validOutput2};

      result.add(new Object[]{"testBlob", null, columns, values, validOutput, false, false});
    }

    //testing skipping table
    {
      String[] columns = new String[]{"c1 VARCHAR(5)"};
      Object[] row1 = new Object[]{"123"};
      String validOutput1 = "";
      Object[] row2 = new Object[]{null};
      String validOutput2 = "(NULL);";
      Object[] values = new Object[]{row1, row2};
      String[] validOutput = new String[]{validOutput1, validOutput2};

      result.add(new Object[]{"testSkip", null, columns, values, validOutput, true, false});
    }

    //testing renaming table
    {
      String[] columns = new String[]{"c1 VARCHAR(5)"};
      Object[] row1 = new Object[]{"123"};
      String validOutput1 = "('123'),";
      Object[] row2 = new Object[]{null};
      String validOutput2 = "(NULL);";
      Object[] values = new Object[]{row1, row2};
      String[] validOutput = new String[]{validOutput1, validOutput2};

      result.add(new Object[]{"testRename", "testRenameNew", columns, values, validOutput, false, false});
    }

    //testing empty table
    {
      String[] columns = new String[]{"c1 VARCHAR(5)"};
      Object[] values = new Object[]{new Object[]{}};
      String[] validOutput = new String[]{};

      result.add(new Object[]{"testEmptyTable", null, columns, values, validOutput, true, false});
    }

    //testing truncate table
    {
      String[] columns = new String[]{"c1 VARCHAR(5)"};
      Object[] values = new Object[]{new Object[]{}};
      String[] validOutput = new String[]{};

      result.add(new Object[]{"testTruncateTable", null, columns, values, validOutput, true, true});
    }

    return result;
  }

  private static InputStream getTestImage() {
    return Thread.currentThread().getContextClassLoader().getResourceAsStream("Penguins.jpg");
  }

  @Test
  public void theDumpTest() throws Exception {
    // Create table
    StringBuilder createTableBuffer = new StringBuilder();
    createTableBuffer.append("CREATE TABLE ");
    createTableBuffer.append(Configuration.getConfiguration().getSchemaName());
    createTableBuffer.append(".");
    createTableBuffer.append(tableName);
    createTableBuffer.append(" (");

    StringBuilder insertBuffer = new StringBuilder();
    insertBuffer.append("INSERT INTO ");
    insertBuffer.append(RESOURCE_SCHEMA_NAME);
    insertBuffer.append(".");
    insertBuffer.append(tableName);
    insertBuffer.append(" VALUES (");

    for (String col : columns) {
      createTableBuffer.append(col.toUpperCase());
      //String[] c = col.split(" ");
      //insertBuffer.append(c[0].toUpperCase().trim());
      insertBuffer.append("?");
      if (!columns[columns.length - 1].equals(col)) {
        createTableBuffer.append(", ");
        insertBuffer.append(",");
      }
    }

    createTableBuffer.append(")");
    insertBuffer.append(")");


    config.setTableRewriteProperty("testSkip", "--exclude--");
    config.setTableRewriteProperty("testRename", "testRenameNew");
    config.setTruncateTables(truncate);

    Connection connection = db.createNewConnection();
    Statement statement = connection.createStatement();
    PreparedStatement ps = null;

    try {
      statement.execute(createTableBuffer.toString());
      connection.commit();
      //config.setTableRewriteProperty("TABLE2", "--exclude--");

      for (Object o : valuesToInsert) {
        Object[] vals = (Object[]) o;
        if (vals.length > 0) {
          ps = db.getConnection().prepareStatement(insertBuffer.toString());
          for (int i = 0; i < vals.length; i++) {
            if (vals[i] instanceof InputStream) {
              ps.setBinaryStream(i + 1, (InputStream) vals[i]);
            } else {
              ps.setObject(i + 1, vals[i]);
            }
          }
          ps.execute();
          connection.commit();
        }
      }

      StringWriter stringWriter = new StringWriter();
      OutputThread output = OutputThread.createInMemory(stringWriter);
      Thread writer = new Thread(output, "File_Writer");
      writer.start();

      new DatabaseReader(output);
      // Let the writer know that no more data is coming
      writer.interrupt();
      writer.join();

      // Now let's read the output and see what is in it
      List<String> lines = IOUtils.readLines(new StringReader(stringWriter.toString()));

      assertEquals("Missing foreign key operations", "SET FOREIGN_KEY_CHECKS = 0;", lines.get(0));
      assertEquals("Missing foreign key operations", "SET FOREIGN_KEY_CHECKS = 1;", lines.get(lines.size() - 1));

      if (!skipped) {
        assertTrue("LOCK missing", lines.contains("LOCK TABLES `" + outputTableName + "` WRITE;"));
        assertTrue("UNLOCK missing", lines.contains("UNLOCK TABLES;"));

        int index = lines.indexOf("LOCK TABLES `" + outputTableName + "` WRITE;");

        if (truncate) {
          assertTrue("TRUNCATE missing", lines.contains("TRUNCATE TABLE " + outputTableName + ";"));
          assertTrue("INSERT missing, got " + lines.get(index + 2), lines.get(index + 2).startsWith("INSERT INTO " + outputTableName));
        } else {
          assertTrue("INSERT missing, got " + lines.get(index + 1), lines.get(index + 1).startsWith("INSERT INTO " + outputTableName));
        }

        for (String s : validOutputs) {
          assertTrue("VALUES missing :" + s, lines.contains(s));
        }
      } else {
        assertFalse("LOCK missing", lines.contains("LOCK TABLES `" + outputTableName + "` WRITE;"));
      }
    } catch (Exception e) {
      e.printStackTrace();
      fail("failed to create test data" + e.getMessage());
    } finally {
      if (ps != null) {
        ps.close();
      }
      statement.close();
      connection.close();
    }
  }

  @After
  public void cleanUp() throws Exception {
    db.getConnection().close();
  }
}
