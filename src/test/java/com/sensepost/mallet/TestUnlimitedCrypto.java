package com.sensepost.mallet;

import javax.crypto.Cipher;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestUnlimitedCrypto {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws Exception {
		Assert.assertEquals("Unlimited crypto policy files not installed", Integer.MAX_VALUE, Cipher.getMaxAllowedKeyLength("AES"));
	}

}
