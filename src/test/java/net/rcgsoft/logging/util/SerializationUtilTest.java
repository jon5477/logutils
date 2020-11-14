package net.rcgsoft.logging.util;

import static org.junit.Assert.assertEquals;

import java.io.StringWriter;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Test;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class SerializationUtilTest {
	static final class MyObject {
		public boolean bool;
		public char c;
		public byte b;
		public short s;
		public int i;
		public long l;
		public float f;
		public double d;
		public String str;
		public MyObject nested;

		@Override
		public String toString() {
			return "MyObject [bool=" + bool + ", c=" + c + ", b=" + b + ", s=" + s + ", i=" + i + ", f=" + f + ", d="
					+ d + ", str=" + str + ", nested=" + nested + "]";
		}
	}

	@Test
	public void testToJsonObject() throws Exception {
		MyObject mo = new MyObject();
		mo.bool = true;
		mo.c = '.';
		mo.b = 99;
		mo.s = 7;
		mo.i = 1337;
		mo.l = 13371337;
		mo.f = 183.5F;
		mo.d = 19392.14;
		mo.str = "Testing";
		mo.nested = new MyObject();
		JsonObject jObj = SerializationUtil.toJsonObject(mo);
		StringWriter sw = new StringWriter();
		jObj.writeTo(sw);
		String expectJson = "{\"bool\":true,\"c\":\".\",\"b\":99,\"s\":7,\"i\":1337,\"l\":13371337,\"f\":183.5,\"d\":19392.14,\"str\":\"Testing\",\"nested\":{\"bool\":false,\"c\":\"\\u0000\",\"b\":0,\"s\":0,\"i\":0,\"l\":0,\"f\":0,\"d\":0,\"str\":null,\"nested\":null}}";
		String json = sw.getBuffer().toString();
		assertEquals(expectJson, json);
	}

	@Test
	public void testCalendarToJsonValue() throws Exception {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
		cal.set(2020, 10, 12, 20, 42, 0);
		cal.set(Calendar.MILLISECOND, 0);
		JsonValue jsonDate = SerializationUtil.toJsonValue(cal.getClass(), cal);
		StringWriter sw = new StringWriter();
		jsonDate.writeTo(sw);
		String json = sw.getBuffer().toString();
		assertEquals("\"2020-11-12T20:42:00Z\"", json);
	}

	@Test
	public void testInstantToJsonValue() throws Exception {
		ZonedDateTime zdt = ZonedDateTime.of(2020, 11, 12, 20, 42, 0, 0, ZoneOffset.UTC);
		JsonValue jsonDate = SerializationUtil.toJsonValue(zdt.getClass(), zdt);
		StringWriter sw = new StringWriter();
		jsonDate.writeTo(sw);
		String json = sw.getBuffer().toString();
		assertEquals("\"2020-11-12T20:42:00Z\"", json);
	}
}