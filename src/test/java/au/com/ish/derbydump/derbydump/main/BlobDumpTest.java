package au.com.ish.derbydump.derbydump.main;

import au.com.ish.derbydump.derbydump.config.Configuration;
import au.com.ish.derbydump.derbydump.config.DBConnectionManager;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.derby.impl.sql.execute.StdDevSAggregator;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.*;
import java.util.List;

public class BlobDumpTest {

  private Configuration config;
  private final File actualDump = new File("./build/outputs/actualDump.sql");
  private final File expectedDump = new File("./src/test/resources/expectedDump.sql");

  @BeforeEach
  public void setUp() throws Exception {
    if (actualDump.exists()) {
      actualDump.delete();
    }
    actualDump.getParentFile().mkdirs();

    config = Configuration.getConfiguration();
    config.setDerbyDbPath(DumpTest.RESOURCE_DATABASE_PATH);
    config.setDriverClassName(DumpTest.RESOURCE_DRIVER_NAME);
    config.setSchemaName(DumpTest.RESOURCE_SCHEMA_NAME);
    config.setBufferMaxSize(DumpTest.RESOURCE_MAX_BUFFER_SIZE);
    config.setOutputFilePath(actualDump.getCanonicalPath());
    config.setTruncateTables(false);

    System.out.println("db " + config.getDerbyUrl().replace("create=false", "create=true"));
  }

  @AfterEach
  public void tearDown() throws Exception {
    try {
      new DBConnectionManager("jdbc:derby:" + config.getDerbyDbPath() + ";drop=true");
    } catch (SQLNonTransientConnectionException e) {
      //the db was dropped
    }
  }

  @Test
  @SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
  public void theDumpTest() throws Exception {

    DBConnectionManager db = new DBConnectionManager(config.getDerbyUrl().replace("create=false", "create=true"));

    // Create table
    String createTable = "CREATE TABLE app.test (data BLOB)";

    String insertString = "INSERT INTO app.test (DATA) VALUES (?)";

    Connection connection = db.createNewConnection();
    Statement statement = connection.createStatement();
    PreparedStatement ps = null;
    try {

      statement.execute("SET SCHEMA app");
      connection.commit();

      statement.execute(createTable);
      connection.commit();

      ps = db.getConnection().prepareStatement(insertString);

      String imageMd5 = DigestUtils.md5Hex(IOUtils.toByteArray(Thread.currentThread().getContextClassLoader().getResourceAsStream("Penguins.jpg")));
      ps.setBinaryStream(1, Thread.currentThread().getContextClassLoader().getResourceAsStream("Penguins.jpg"));
      ps.execute();
      connection.commit();

      Configuration.getConfiguration().setTruncateTables(true);
      StringWriter stringWriter = new StringWriter();
      OutputThread output = OutputThread.createInMemory(stringWriter);
      Thread writer = new Thread(output, "File_Writer");
      writer.start();

      new DatabaseReader(output);
      // Let the writer know that no more data is coming
      writer.interrupt();
      writer.join();

      System.out.println("dump: " + stringWriter.toString());

      // now reimport the dump
      executeDumpFileAgainstDatabase(connection, new BufferedReader(new StringReader(stringWriter.toString())));

      statement = connection.createStatement();

      statement.execute("select * from app.test");
      ResultSet set = statement.getResultSet();
      set.next();
      Blob blob = set.getBlob(1);
      String importedBlobBinaryMd5 = DigestUtils.md5Hex(IOUtils.toByteArray(blob.getBinaryStream()));

      Assertions.assertEquals(imageMd5, importedBlobBinaryMd5);
    } catch (Exception e) {
      e.printStackTrace();
      Assertions.fail("failed to create test data" + e.getMessage());
    } finally {
      if (ps != null) {
        ps.close();
      }
      statement.close();
      connection.close();
    }
  }

  private void executeDumpFileAgainstDatabase(Connection connection, BufferedReader bufferedReader) throws Exception {
    StringBuilder sqlCommand = new StringBuilder();
    String line;
    while ((line = bufferedReader.readLine()) != null) {

      sqlCommand.append(line);
      if (!line.endsWith(";")) {
        continue;
      }

      try {
        // AUTOCOMMIT is an IJ command
        String finalSqlCommand = sqlCommand.toString();
        if (finalSqlCommand.startsWith("AUTOCOMMIT")) continue;
        Statement statement = connection.createStatement();
        statement.execute(finalSqlCommand.substring(0, finalSqlCommand.length() - 1)); // cut-off semicolon
        connection.commit();

        sqlCommand.delete(0, sqlCommand.length());
      } catch (SQLException e) {
        throw new RuntimeException("Could not execute line " + line, e);
      }
    }
  }
}
