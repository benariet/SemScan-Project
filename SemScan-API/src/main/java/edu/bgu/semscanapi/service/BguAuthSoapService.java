package edu.bgu.semscanapi.service;

import edu.bgu.semscanapi.util.LoggerUtil;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.xml.sax.SAXException;

/**
 * SOAP client service for interacting with the BGU authentication web service.
 */
@Service
public class BguAuthSoapService {

    private static final Logger logger = LoggerUtil.getLogger(BguAuthSoapService.class);

    private static final String VALIDATE_USER_ACTION = "\"http://tempuri.org/validateUser\"";

    private final RestTemplate restTemplate;
    private final String soapEndpoint;

    public BguAuthSoapService(RestTemplateBuilder restTemplateBuilder,
                              @Value("${bgu.auth.soap.url}") String soapEndpoint) {
        this.restTemplate = restTemplateBuilder.build();
        this.soapEndpoint = soapEndpoint;
    }

    /**
     * Perform a validateUser SOAP call against the BGU authentication service.
     *
     * @param username BGU username
     * @param password BGU password
     * @return true if the credentials are valid, false otherwise
     */
    public boolean validateUser(String username, String password) {
        LoggerUtil.generateAndSetCorrelationId();
        logger.info("Invoking BGU validateUser SOAP call for username: {}", username);

        try {
            String payload = buildValidateUserPayload(username, password);
            String responseXml = sendSoapRequest(payload, VALIDATE_USER_ACTION);
            boolean result = parseBooleanResponse(responseXml, "validateUserResult");
            logger.info("BGU validateUser SOAP call completed for username: {}, result: {}", username, result);
            return result;
        } catch (Exception ex) {
            LoggerUtil.logError(logger, "Failed to validate user via BGU SOAP service", ex);
            return false;
        } finally {
            LoggerUtil.clearContext();
        }
    }

    private String buildValidateUserPayload(String username, String password) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
                " xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\"" +
                " xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "  <soap:Body>" +
                "    <validateUser xmlns=\"http://tempuri.org/\">" +
                "      <uname>" + escapeXml(username) + "</uname>" +
                "      <pwd>" + escapeXml(password) + "</pwd>" +
                "    </validateUser>" +
                "  </soap:Body>" +
                "</soap:Envelope>";
    }

    private String sendSoapRequest(String payload, String soapAction) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        headers.add("SOAPAction", soapAction);

        HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(soapEndpoint, requestEntity, String.class);
        return response.getBody();
    }

    private boolean parseBooleanResponse(String xml, String tagName) throws ParserConfigurationException, SAXException, java.io.IOException {
        if (xml == null || xml.isEmpty()) {
            throw new IllegalStateException("Empty SOAP response received from BGU service");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            throw new IllegalStateException("Unable to locate tag '" + tagName + "' in SOAP response");
        }

        String textContent = nodes.item(0).getTextContent();
        return Boolean.parseBoolean(textContent);
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}


