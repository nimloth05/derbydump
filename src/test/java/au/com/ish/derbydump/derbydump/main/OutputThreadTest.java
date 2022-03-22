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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.*;

import static junit.framework.Assert.assertEquals;

public class OutputThreadTest {

  private static final Logger LOGGER = LogManager.getLogger(OutputThreadTest.class);

  private static final String RESOURCE_DUMP_LOCATION = "./build/tmp/writer_test.out";

  @BeforeClass
  public static void setUp() throws Exception {
    Configuration config = Configuration.getConfiguration();
    File output = new File(RESOURCE_DUMP_LOCATION);
    if (output.exists()) {
      output.delete();
    }
    output.getParentFile().mkdirs();
    config.setOutputFilePath(output.getCanonicalPath());

  }

  @Test
  public void testAdd() throws Exception {
    StringWriter stringWriter = new StringWriter();
    OutputThread output = OutputThread.createInMemory(stringWriter);
    Thread writer = new Thread(output, "writer test");
    writer.start();

    output.add("Some text");
    writer.interrupt();
    writer.join();

    // Now let's read the output and see what is in it
    BufferedReader in = new BufferedReader(new StringReader(stringWriter.toString()));
    String line = in.readLine();
    in.close();

    assertEquals("File writer didn't write correct text.", line, "Some text");
  }

  @Test
  public void testAddChinese() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    OutputThread output = OutputThread.createFromOutputStream(out);
    Thread writer = new Thread(output, "writer test");
    writer.start();

    output.add("漢字");
    writer.interrupt();
    writer.join();

    // Now let's read the output and see what is in it
    BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(out.toByteArray())));
    String line = in.readLine();
    in.close();

    assertEquals("File writer didn't write correct UTF.", line, "漢字");
  }
}
