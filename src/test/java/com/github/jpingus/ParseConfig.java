package com.github.jpingus;

import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class ParseConfig {
    @Test
    public void parse() throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document docConfig =
                builder.parse(ParseConfig.class.getResourceAsStream("/config.xml"));
        analyse(docConfig);
    }

    private void analyse(Document docConfig) {
        if(!"aggregator4j".equals(docConfig.getDocumentElement().getTagName()))
            throw new Error("config root must be aggregator4j");
    }
}
