package com.sensepost.mallet.util;

import java.io.IOException;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class XmlUtil {

	public static String elementAsString(Object o) {
		return elementAsString(o, 0);
	}

	public static String elementAsString(Object o, int indent) {
		if (o instanceof Node) {
			Node n = (Node) o;
			try {
				StringWriter buffer = new StringWriter();
				pretty(n, new StreamResult(buffer), indent);
				return buffer.toString();
			} catch (TransformerException e1) {
				e1.printStackTrace();
				return null;
			}
		} else {
			return String.valueOf(o);
		}
	}

	/**
	 * Returns a new DOM document for the given URI.
	 * 
	 * @param uri
	 *            URI to parse into the document.
	 * @return Returns a new DOM document for the given URI.
	 * @throws ParserConfigurationException 
	 * @throws IOException 
	 * @throws SAXException 
	 */
	public static Document loadDocument(String uri) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
				.newInstance();
		DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
		return docBuilder.parse(uri);
	}
	
	public static void pretty(Node node, Result result, int indent) throws TransformerException {
	    TransformerFactory transformerFactory = TransformerFactory.newInstance();
	    Transformer transformer = transformerFactory.newTransformer();
	    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
	    if (indent > 0) {
	        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", Integer.toString(indent));
	    }
	    Source source = new DOMSource(node);
	    transformer.transform(source, result);
	}
}
