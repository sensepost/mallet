package com.sensepost.mallet.handlers.iso8583;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.netty.util.CharsetUtil;

public class Iso8583Message {

	private String mti;
	private Map<Iso8583Field, String> fields;

	public Iso8583Message(String mti, Map<Iso8583Field, String> fields) {
		this.mti = mti;
		this.fields = fields;
	}

	public String get(Iso8583Field f) {
		return fields.get(f);
	}

	public void set(Iso8583Field f, String value) {
		// verify that the value is appropriate for the field
		f.encode(value);
		fields.put(f, value);
	}

	public byte[] encodeAsBytes() {
		return encodeAsString().getBytes(CharsetUtil.US_ASCII);
	}

	public String encodeAsString() {
		StringBuilder s = new StringBuilder(mti);
		fields.put(Iso8583Field.F1, null); // must always have the bitmap
		s.append(Iso8583Field.F1.encode(fieldsToBitmap(fields.keySet())));
		for (Entry<Iso8583Field, String> e : fields.entrySet()) {
			if (!Iso8583Field.F1.equals(e.getKey()))
				s.append(e.getKey().encode(e.getValue()));
		}
		return s.toString();
	}

	public String toString() {
		StringBuilder s = new StringBuilder(mti).append(" [");
		for (Entry<Iso8583Field, String> e : fields.entrySet()) {
			if (!Iso8583Field.F1.equals(e.getKey()))
				s.append(e.getKey().name()).append("=").append(e.getValue()).append(" ");
		}
		s.append("]");
		return s.toString();
	}

	private static String fieldsToBitmap(Set<Iso8583Field> fields) {
		char[] bitmap = new char[32];
		Iso8583Field[] isoFields = Iso8583Field.values();
		int pos=0;
		for (int i=0; i<isoFields.length; i+=4) {
			int c = 0;
			for (int j=0; j<4; j++)
				c += (fields.contains(isoFields[i+j]) ? 1 << (3-j) : 0);
			bitmap[pos] = Integer.toHexString(c).toUpperCase().charAt(0);
			pos++;
		}
		return new String(bitmap);
	}

	private static BitSet parseBitmap(String bitmap) {
		BitSet bs = new BitSet();
		int pos = 1;
		for (int i = 0; i < bitmap.length(); i++) {
			int v = Integer.parseInt(bitmap.substring(i, i + 1), 16);
			for (int b = 3; b >= 0; b--) {
				boolean set = (v & (1 << b)) != 0;
				bs.set(pos++, set);
			}
		}
		return bs;
	}

	public static Iso8583Message decode(String message) throws ParseException {
		System.out.println(message);
		String mti = message.substring(0, 4);
		int pos = 4;
		String value = Iso8583Field.F1.decode(message, pos);
		BitSet bitmap = parseBitmap(value);
		pos += Iso8583Field.F1.encode(value).length();
		Map<Iso8583Field, String> fields = new LinkedHashMap<>();
		int bit = 1;
		while ((bit = bitmap.nextSetBit(bit + 1)) > 0) {
			Iso8583Field f = Iso8583Field.valueOf("F" + bit);
			value = f.decode(message, pos);
			System.out.println(f + " = " + value);
			fields.put(f, value);
			pos += f.encode(value).length();
		}
		return new Iso8583Message(mti, fields);
	}

	public static void main(String[] args) throws Exception {
		System.out.println("Enter message: ");
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		String message = reader.readLine();
		System.out.println(Iso8583Message.decode(
				message).encodeAsString());
		System.err.println(message);
		reader.close();
	}
}
