/*
 * plist - An open source library to parse and generate property lists
 * Copyright (C) 2011 Daniel Dreibrodt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.dd.plist;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

/**
 * Parses XML property lists
 * @author Daniel Dreibrodt
 * @author David A. Solin
 */
public class XMLPropertyListParser {
    private static final DocumentBuilderFactory FACTORY = DocumentBuilderFactory.newInstance();
    static {
	try {
            FACTORY.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            Float v1_7 = new Float("1.7");
            Float javaVersion = new Float(System.getProperty("java.specification.version"));
            if (javaVersion.compareTo(v1_7) >= 0) {
                FACTORY.setFeature("http://xml.org/sax/features/external-general-entities", false);
                FACTORY.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            }
            FACTORY.setXIncludeAware(false);
            FACTORY.setExpandEntityReferences(false);
            FACTORY.setNamespaceAware(false);
            FACTORY.setIgnoringComments(true);
	    FACTORY.setCoalescing(true);
	    FACTORY.setValidating(false);
	} catch (ParserConfigurationException e) {
	    throw new RuntimeException(e);
	}
    }

    /**
     * Resolves only the Apple PLIST DTD.
     */
    static class PlistResolver implements EntityResolver {
	private static final String PLIST_SYSTEMID_1 = "-//Apple Computer//DTD PLIST 1.0//EN";
	private static final String PLIST_SYSTEMID_2 = "-//Apple//DTD PLIST 1.0//EN";

        PlistResolver() {
        }

        // Implement EntityResolver

        public InputSource resolveEntity(String publicId, String systemId) {
            if (PLIST_SYSTEMID_1.equals(publicId) || PLIST_SYSTEMID_2.equals(publicId)) {
                return new InputSource(XMLPropertyListParser.class.getResourceAsStream("PropertyList-1.0.dtd"));
            }
            return null;
        }
    }

    /**
     * Gets a DocumentBuilder to parse a XML property list.
     * As DocumentBuilders are not thread-safe a new DocBuilder is generated for each request.
     * @return A new DocBuilder that can parse property lists w/o an internet connection.
     */
    public static synchronized DocumentBuilder getDocBuilder() throws ParserConfigurationException {
        DocumentBuilder builder = FACTORY.newDocumentBuilder();
	builder.setEntityResolver(new PlistResolver());
        return builder;
    }

    /**
     * Parses a XML property list file.
     * @param f The XML plist file.
     * @return The root object of the property list.
     * @throws Exception
     */
    public static NSObject parse(File f) throws Exception {
        return parseDocument(getDocBuilder().parse(f));
    }

    /**
     * Parses a XML property list from a byte array.
     * @param bytes The byte array
     * @return The root object of the property list
     * @throws Exception
     */
    public static NSObject parse(final byte[] bytes) throws Exception {
        return parse(new ByteArrayInputStream(bytes));
    }

    /**
     * Parses a XML property list from an input stream.
     * @param is The input stream.
     * @return The root object of the property list
     * @throws Exception
     */
    public static NSObject parse(InputStream is) throws Exception {
        return parseDocument(getDocBuilder().parse(is));
    }

    private static NSObject parseDocument(Document doc) throws Exception {
        if (!doc.getDoctype().getName().equals("plist")) {
            throw new UnsupportedOperationException("The given XML document is not a property list.");
        }

        //Skip all #TEXT nodes and take the first element node we find as root
        List<Node> rootNodes = filterElementNodes(doc.getDocumentElement().getChildNodes());
        if (rootNodes.size() > 0) {
            return parseObject(rootNodes.get(0));
        } else {
            throw new Exception("No root node found!");
	}
    }

    /**
     * Parses a node in the XML structure and returns the corresponding NSObject
     * @param n The XML node
     * @return The corresponding NSObject
     * @throws Exception
     */
    private static NSObject parseObject(Node n) throws Exception {
        String type = n.getNodeName();
        if (type.equals("dict")) {
            NSDictionary dict = new NSDictionary();
            List<Node> children = filterElementNodes(n.getChildNodes());
	    for (int i = 0; i < children.size(); i += 2) {
		Node key = children.get(i);
		Node val = children.get(i+1);
		if (key.getChildNodes().getLength() == 0) {
		    dict.put("", parseObject(val)); // <key></key> case
		} else {
		    dict.put(key.getChildNodes().item(0).getNodeValue(), parseObject(val));
		}
            }
            return dict;
        } else if (type.equals("array")) {
            List<Node> children = filterElementNodes(n.getChildNodes());
	    NSArray array = new NSArray(children.size());
	    for (int i = 0; i < children.size(); i++) {
		array.setValue(i, parseObject(children.get(i)));
	    }
	    return array;
        } else if (type.equals("true")) {
            return new NSNumber(true);
        } else if (type.equals("false")) {
            return new NSNumber(false);
        } else if (type.equals("integer")) {
            return new NSNumber(n.getChildNodes().item(0).getNodeValue());
        } else if (type.equals("real")) {
            return new NSNumber(n.getChildNodes().item(0).getNodeValue());
        } else if (type.equals("string")) {
            NodeList children = n.getChildNodes();
            if (children.getLength() == 0) {
                return new NSString(""); //Empty string
            } else {
                return new NSString(children.item(0).getNodeValue());
            }
        } else if (type.equals("data")) {
            return new NSData(n.getChildNodes().item(0).getNodeValue());
        } else if (type.equals("date")) {
            return new NSDate(n.getChildNodes().item(0).getNodeValue());
        }
        return null;
    }

    /**
     * Returns all element nodes that are contained in a list of nodes.
     * @param list The list of nodes to search
     * @return The sublist of nodes which have an element type.
     */
    private static List<Node> filterElementNodes(NodeList list) {
	List<Node> result = new ArrayList<Node>(list.getLength());
	for (int i=0; i<list.getLength(); i++) {
	    if (list.item(i).getNodeType()==Node.ELEMENT_NODE) {
		result.add(list.item(i));
	    }
	}
	return result;
    }
}
