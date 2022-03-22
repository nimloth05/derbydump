package au.com.ish.derbydump.derbydump.metadata;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;
import java.io.InputStream;
import java.sql.Clob;


public class ColumnTest {

	@Test
	public void testProcessBinaryData() throws Exception {
		InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("Penguins.jpg");
		byte[] inputData = IOUtils.toByteArray(is);

		Assertions.assertEquals(5569, inputData.length);

		String result = Column.processBinaryData(new SerialBlob(inputData));

		Assertions.assertEquals(11140, result.length());

		Assertions.assertEquals("0x61", Column.processBinaryData(new SerialBlob(new byte[]{'a'})));
		Assertions.assertEquals("0x0A", Column.processBinaryData(new SerialBlob(new byte[]{'\n'})));
	}

	@Test
	public void testProcessNullBinaryData() throws Exception {
		Assertions.assertEquals("NULL", Column.processBinaryData(null));
		Assertions.assertEquals("NULL", Column.processBinaryData(new SerialBlob(new byte[]{})));
	}

	@Test
	public void testProcessClobData() throws Exception {
		String oneSimpleClob = "one simple clob";
		Clob inputClob = new SerialClob(oneSimpleClob.toCharArray());

		String processedString = Column.processClobData(inputClob);

		Assertions.assertEquals("'"+oneSimpleClob+"'", processedString);
	}

	@Test
	public void testProcessNullClobData() throws Exception {
		Assertions.assertEquals("NULL", Column.processClobData(null));
		Assertions.assertEquals("''", Column.processClobData(new SerialClob("".toCharArray())));
	}


	@Test
	public void testEscapeQuotes(){
		String test1 = "'Single quotes'";
		Assertions.assertEquals("\\'Single quotes\\'", Column.escapeQuotes(test1), "Single quote");

		String test2 = "''Single quotes twice''";
		Assertions.assertEquals("\\'\\'Single quotes twice\\'\\'", Column.escapeQuotes(test2), "Single quotes twice");

		String test3 = "Tab\t";
		Assertions.assertEquals("Tab\\t", Column.escapeQuotes(test3), "Tab");

		String test4 = "Single backslash\\";
		Assertions.assertEquals("Single backslash\\\\", Column.escapeQuotes(test4), "Backslash");

		String test5 = "Newline\n and carriage return\r";
		Assertions.assertEquals("Newline\\n and carriage return\\r", Column.escapeQuotes(test5), "Newline");


	}
}
