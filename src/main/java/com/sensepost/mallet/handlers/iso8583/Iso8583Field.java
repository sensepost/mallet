package com.sensepost.mallet.handlers.iso8583;

import java.text.ParseException;

public enum Iso8583Field {

	F1(Iso8583FieldType.BITMAP, 32, "Bitmap"), F2(Iso8583FieldType.VAR, 2, "Primary account number (PAN)"),
	F3(Iso8583FieldType.FIXED, 6, "Processing code"), F4(Iso8583FieldType.FIXED, 12, "Amount, transaction"),
	F5(Iso8583FieldType.FIXED, 12, "Amount, settlement"), F6(Iso8583FieldType.FIXED, 12, "Amount, cardholder billing"),
	F7(Iso8583FieldType.FIXED, 10, "Transmission date & time"), F8(Iso8583FieldType.FIXED, 8, "Amount, cardholder billing fee"),
	F9(Iso8583FieldType.FIXED, 8, "Conversion rate, settlement"),
	F10(Iso8583FieldType.FIXED, 8, "Conversion rate, cardholder billing"),
	F11(Iso8583FieldType.FIXED, 6, "System trace audit number (STAN)"),
	F12(Iso8583FieldType.FIXED, 6, "Time, local transaction (hhmmss)"),
	F13(Iso8583FieldType.FIXED, 4, "Date, local transaction (MMDD)"), F14(Iso8583FieldType.FIXED, 4, "Date, expiration"),
	F15(Iso8583FieldType.FIXED, 4, "Date, settlement"), F16(Iso8583FieldType.FIXED, 4, "Date, conversion"),
	F17(Iso8583FieldType.FIXED, 4, "Date, capture"), F18(Iso8583FieldType.FIXED, 4, "Merchant type/Merchant Category Code"),
	F19(Iso8583FieldType.FIXED, 3, "Acquiring institution country code"),
	F20(Iso8583FieldType.FIXED, 3, "PAN extended, country code"),
	F21(Iso8583FieldType.FIXED, 3, "Forwarding institution. country code"),
	F22(Iso8583FieldType.FIXED, 3, "Point of service entry mode"), F23(Iso8583FieldType.FIXED, 3, "Application PAN sequence number"),
	F24(Iso8583FieldType.FIXED, 3, "Function code (ISO 8583:1993)/Network International identifier (NII)"),
	F25(Iso8583FieldType.FIXED, 2, "Point of service condition code"),
	F26(Iso8583FieldType.FIXED, 2, "Point of service capture code"),
	F27(Iso8583FieldType.FIXED, 1, "Authorizing identification response length"),
	F28(Iso8583FieldType.FIXED, 9, "Amount, transaction fee"), F29(Iso8583FieldType.FIXED, 9, "Amount, settlement fee"),
	F30(Iso8583FieldType.FIXED, 9, "Amount, transaction processing fee"),
	F31(Iso8583FieldType.FIXED, 9, "Amount, settlement processing fee"),
	F32(Iso8583FieldType.VAR, 2, "Acquiring institution identification code"),
	F33(Iso8583FieldType.VAR, 2, "Forwarding institution identification code"),
	F34(Iso8583FieldType.VAR, 2, "Primary account number, extended"), F35(Iso8583FieldType.VAR, 2, "Track 2 data"),
	F36(Iso8583FieldType.VAR, 3, "Track 3 data"), F37(Iso8583FieldType.FIXED, 12, "Retrieval reference number"),
	F38(Iso8583FieldType.FIXED, 6, "Authorization identification response"), F39(Iso8583FieldType.FIXED, 2, "Response code"),
	F40(Iso8583FieldType.FIXED, 3, "Service restriction code"),
	F41(Iso8583FieldType.FIXED, 8, "Card acceptor terminal identification"),
	F42(Iso8583FieldType.FIXED, 15, "Card acceptor identification code"),
	F43(Iso8583FieldType.FIXED, 40, "Card acceptor name/location"), F44(Iso8583FieldType.VAR, 2, "Additional response data"),
	F45(Iso8583FieldType.VAR, 2, "Track 1 data"), F46(Iso8583FieldType.VAR, 3, "Additional data - ISO"),
	F47(Iso8583FieldType.VAR, 3, "Additional data - national"), F48(Iso8583FieldType.VAR, 3, "Additional data - private"),
	F49(Iso8583FieldType.FIXED, 3, "Currency code, transaction"), F50(Iso8583FieldType.FIXED, 3, "Currency code, settlement"),
	F51(Iso8583FieldType.FIXED, 3, "Currency code, cardholder billing"),
	F52(Iso8583FieldType.FIXED, 16, "Personal identification number data"),
	F53(Iso8583FieldType.FIXED, 16, "Security related control information"), F54(Iso8583FieldType.VAR, 3, "Additional amounts"),
	F55(Iso8583FieldType.VAR, 3, "ICC Data - EMV having multiple tags"), F56(Iso8583FieldType.VAR, 3, "Reserved ISO"),
	F57(Iso8583FieldType.VAR, 3, "Reserved national"), F58(Iso8583FieldType.VAR, 3, "Reserved national"),
	F59(Iso8583FieldType.VAR, 3, "Reserved national"), F60(Iso8583FieldType.VAR, 3, "Reserved national"),
	F61(Iso8583FieldType.VAR, 3, "Reserved private (Ex=CVV2/Service Code)"),
	F62(Iso8583FieldType.VAR, 3, "Reserved private (Invoice Number, TPK Key"), F63(Iso8583FieldType.VAR, 3, "Reserved private"),
	F64(Iso8583FieldType.FIXED, 16, "Message authentication code (MAC)"), F65(Iso8583FieldType.FIXED, 16, "Bitmap, extended"),
	F66(Iso8583FieldType.FIXED, 1, "Settlement code"), F67(Iso8583FieldType.FIXED, 2, "Extended payment code"),
	F68(Iso8583FieldType.FIXED, 3, "Receiving institution country code"),
	F69(Iso8583FieldType.FIXED, 3, "Settlement institution country code"),
	F70(Iso8583FieldType.FIXED, 3, "Network management information code"), F71(Iso8583FieldType.FIXED, 4, "Message number"),
	F72(Iso8583FieldType.FIXED, 4, "Message number, last"), F73(Iso8583FieldType.FIXED, 6, "Date, action (YYMMDD)"),
	F74(Iso8583FieldType.FIXED, 10, "Credits, number"), F75(Iso8583FieldType.FIXED, 10, "Credits, reversal number"),
	F76(Iso8583FieldType.FIXED, 10, "Debits, number"), F77(Iso8583FieldType.FIXED, 10, "Debits, reversal number"),
	F78(Iso8583FieldType.FIXED, 10, "Transfer number"), F79(Iso8583FieldType.FIXED, 10, "Transfer, reversal number"),
	F80(Iso8583FieldType.FIXED, 10, "Inquiries number"), F81(Iso8583FieldType.FIXED, 10, "Authorizations, number"),
	F82(Iso8583FieldType.FIXED, 12, "Credits, processing fee amount"),
	F83(Iso8583FieldType.FIXED, 12, "Credits, transaction fee amount"),
	F84(Iso8583FieldType.FIXED, 12, "Debits, processing fee amount"),
	F85(Iso8583FieldType.FIXED, 12, "Debits, transaction fee amount"), F86(Iso8583FieldType.FIXED, 16, "Credits, amount"),
	F87(Iso8583FieldType.FIXED, 16, "Credits, reversal amount"), F88(Iso8583FieldType.FIXED, 16, "Debits, amount"),
	F89(Iso8583FieldType.FIXED, 16, "Debits, reversal amount"), F90(Iso8583FieldType.FIXED, 42, "Original data elements"),
	F91(Iso8583FieldType.FIXED, 1, "File update code"), F92(Iso8583FieldType.FIXED, 2, "File security code"),
	F93(Iso8583FieldType.FIXED, 5, "Response indicator"), F94(Iso8583FieldType.FIXED, 7, "Service indicator"),
	F95(Iso8583FieldType.FIXED, 42, "Replacement amounts"), F96(Iso8583FieldType.FIXED, 16, "Message security code"),
	F97(Iso8583FieldType.FIXED, 17, "Amount, net settlement"), F98(Iso8583FieldType.FIXED, 25, "Payee"),
	F99(Iso8583FieldType.VAR, 2, "Settlement institution identification code"),
	F100(Iso8583FieldType.VAR, 2, "Receiving institution identification code"), F101(Iso8583FieldType.VAR, 2, "File name"),
	F102(Iso8583FieldType.VAR, 2, "Account identification 1"), F103(Iso8583FieldType.VAR, 2, "Account identification 2"),
	F104(Iso8583FieldType.VAR, 3, "Transaction description"), F105(Iso8583FieldType.VAR, 3, "Reserved for ISO use"),
	F106(Iso8583FieldType.VAR, 3, "Reserved for ISO use"), F107(Iso8583FieldType.VAR, 3, "Reserved for ISO use"),
	F108(Iso8583FieldType.VAR, 3, "Reserved for ISO use"), F109(Iso8583FieldType.VAR, 3, "Reserved for ISO use"),
	F110(Iso8583FieldType.VAR, 3, "Reserved for ISO use"), F111(Iso8583FieldType.VAR, 3, "Reserved for ISO use"),
	F112(Iso8583FieldType.VAR, 3, "Reserved for national use"), F113(Iso8583FieldType.VAR, 3, "Reserved for national use"),
	F114(Iso8583FieldType.VAR, 3, "Reserved for national use"), F115(Iso8583FieldType.VAR, 3, "Reserved for national use"),
	F116(Iso8583FieldType.VAR, 3, "Reserved for national use"), F117(Iso8583FieldType.VAR, 3, "Reserved for national use"),
	F118(Iso8583FieldType.VAR, 3, "Reserved for national use"), F119(Iso8583FieldType.VAR, 3, "Reserved for national use"),
	F120(Iso8583FieldType.VAR, 3, "Reserved for private use"), F121(Iso8583FieldType.VAR, 3, "Reserved for private use"),
	F122(Iso8583FieldType.VAR, 3, "Reserved for private use"), F123(Iso8583FieldType.VAR, 3, "Reserved for private use"),
	F124(Iso8583FieldType.VAR, 3, "Reserved for private use"), F125(Iso8583FieldType.VAR, 3, "Reserved for private use"),
	F126(Iso8583FieldType.VAR, 3, "Reserved for private use"), F127(Iso8583FieldType.VAR, 6, "Reserved for private use"),
	F128(Iso8583FieldType.FIXED, 16, "Message authentication code");

	private final Iso8583FieldType type;
	private final int length;
	private String fieldName;

	Iso8583Field(Iso8583FieldType type, int length, String fieldName) {
		this.type = type;
		this.length = length;
		this.fieldName = fieldName;
	}

	public String getFieldName() {
		return fieldName;
	}

	public String toString() {
		return name() + " (" + getFieldName() + ")";
	}

	public String decode(String src, int offset) throws ParseException {
		switch (type) {
		case BITMAP: {
//			int l = length;
//			if (Integer.parseInt(src.substring(offset,offset+1), 16) > 7) {
//				l = l * 2;
//			}
			return src.substring(offset, offset + length);
		}
		case FIXED:
			return src.substring(offset, offset + length);
		case VAR: {
			String l = src.substring(offset, offset + length);
			try {
				int len = Integer.parseInt(l);
				return src.substring(offset + length, offset + length + len);
			} catch (Exception e) {
				throw new RuntimeException("Error parsing length for field " + this + " at offset " + offset + ", original message was \n" + src, e);
			}
		}
		}
		throw new RuntimeException("Unsupported type: " + type);
	}

	public String encode(String value) {
		switch (type) {
		case BITMAP: {
			if (value.length() != length)
				throw new RuntimeException("Invalid length bitmap, expected " + length + ", got " + value.length());
			return value;
		}
		case FIXED:
			if (value.length() != length) {
				throw new RuntimeException(
						"Value wrong size for fixed field(" + length + "): " + value.length() + " '" + value + "'");
			} else {
				return String.format("%" + length + "s", value);
			}
		case VAR:
			if (value.length() > Math.pow(10, length) - 1) {
				throw new RuntimeException(
						"Value too large for var field(" + length + "): " + value.length() + " '" + value + "'");
			} else {
				return String.format("%0" + length + "d%s", value.length(), value);
			}
		}
		throw new RuntimeException("Unsupported type: " + type);
	}

}
