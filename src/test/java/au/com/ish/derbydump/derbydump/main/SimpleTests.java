package au.com.ish.derbydump.derbydump.main;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/**
 * Aggregation of simple elementary tests
 */
public class SimpleTests {

	@Test
	public void testHexUtils(){
		Hex hexEncoder = new Hex(CharEncoding.UTF_8);

		byte[] test1 = "Hex String ==;90%$#@^ Byte Array".getBytes(StandardCharsets.UTF_8);
		byte[] test1_expected = "48657820537472696e67203d3d3b3930252423405e2042797465204172726179".getBytes(StandardCharsets.UTF_8);

		byte[] test1_output = hexEncoder.encode(test1);
		Assertions.assertEquals(new String(test1_expected).toUpperCase(), new String(test1_output).toUpperCase(), "failure In converting byte to HEX");

		byte[] test2 = "中國全國人大、政協「兩會」綜合報導 Read more:".getBytes(StandardCharsets.UTF_8);
		byte[] test2_expected = "E4B8ADE59C8BE585A8E59C8BE4BABAE5A4A7E38081E694BFE58D94E3808CE585A9E69C83E3808DE7B69CE59088E5A0B1E5B08E2052656164206D6F72653A".getBytes(StandardCharsets.UTF_8);

		byte[] test2_output = hexEncoder.encode(test2);
		Assertions.assertEquals(new String(test2_expected).toUpperCase(), new String(test2_output).toUpperCase(), "failure In converting byte to HEX For Chinese");

	}
}
