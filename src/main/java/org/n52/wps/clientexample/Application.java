package org.n52.wps.clientexample;

import net.opengis.gml.x32.AbstractFeatureCollectionType;
import net.opengis.gml.x32.AbstractRingPropertyType;
import net.opengis.gml.x32.CoordinatesType;
import net.opengis.gml.x32.FeatureCollectionDocument;
import net.opengis.gml.x32.FeaturePropertyType;
import net.opengis.gml.x32.LinearRingDocument;
import net.opengis.gml.x32.LinearRingType;
import net.opengis.gml.x32.LocationPropertyType;
import net.opengis.gml.x32.PolygonDocument;
import net.opengis.gml.x32.PolygonType;
import net.opengis.sampling.x20.SFSamplingFeatureDocument;
import net.opengis.sampling.x20.SFSamplingFeatureType;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.n52.geoprocessing.wps.client.ExecuteRequestBuilder;
import org.n52.geoprocessing.wps.client.WPSClientException;
import org.n52.geoprocessing.wps.client.WPSClientSession;
import org.n52.geoprocessing.wps.client.model.Process;
import org.n52.geoprocessing.wps.client.model.ResponseMode;
import org.n52.geoprocessing.wps.client.model.Result;
import org.n52.geoprocessing.wps.client.model.execution.ComplexData;
import org.n52.geoprocessing.wps.client.model.execution.Execute;
import org.n52.oxf.xmlbeans.parser.XMLHandlingException;
import org.n52.oxf.xmlbeans.tools.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Application {

    private static final Logger log = LoggerFactory.getLogger( Application.class );
    private static final String WPS_URL = "http://geoprocessing.demo.52north.org/javaps/service";

    public static void main(String[] args) throws WPSClientException, IOException, XMLHandlingException, XmlException {
        Application app = new Application();

        app.executeWithInlineResponse();

        /*
         * as the inline response output processing of the client lib
         * seems to have limitations (it does not produce real XML),
         * execute by reference is a good alternative
         */
        app.executeWithResponseByReference();
    }

    private void executeWithInlineResponse() throws WPSClientException, IOException, XMLHandlingException {
        XmlObject xml = this.createFeatureCollection();

        WPSClientSession session = new WPSClientSession();
        session.connect(WPS_URL, "2.0.0");

        Process desc = session.getProcessDescription(WPS_URL, "org.n52.javaps.test.EchoProcess", "2.0.0");

        Result response = prepareAndExecute(xml, session, desc);

        Object result = response.getOutputs().stream().map(o -> o.asComplexData().getValue()).findFirst().get();
        log.info("Result: {}", result);
    }

    private void executeWithResponseByReference() throws WPSClientException, IOException, XMLHandlingException, XmlException {
        XmlObject xml = this.createFeatureCollection();

        WPSClientSession session = new WPSClientSession();
        session.connect(WPS_URL, "2.0.0");

        Process desc = session.getProcessDescription(WPS_URL, "org.n52.javaps.test.EchoProcess", "2.0.0");

        Result response = prepareAndExecute(xml, session, desc, true);

        URL resultUrl = response.getOutputs().stream()
                .filter(o -> o instanceof ComplexData)
                .map(o -> (ComplexData) o)
                .filter(o -> o.isReference())
                .map(o -> o.getReference().getHref())
                .findFirst().get();

        log.info("Result By Reference: {}", resultUrl);

        // resolve the reference with HttpClient
        try (CloseableHttpResponse referencedOutput = HttpClientBuilder.create().build().execute(new HttpGet(resultUrl.toString()))) {
            String result = EntityUtils.toString(referencedOutput.getEntity());
            log.info("Result Resolved: {}", result);

            FeatureCollectionDocument resultXml = FeatureCollectionDocument.Factory.parse(result);
            log.info("Result Resolved and parsed: {}", XmlUtil.objectToString(resultXml, false, true));
        }

    }

    private Result prepareAndExecute(XmlObject input, WPSClientSession session, Process processDescription) throws WPSClientException, IOException {
        return prepareAndExecute(input, session, processDescription, false);
    }

    private Result prepareAndExecute(XmlObject input, WPSClientSession session, Process processDescription, boolean outputAsReference) throws WPSClientException, IOException {
        ExecuteRequestBuilder builder = new ExecuteRequestBuilder(processDescription);

        builder.addComplexData("complexInput", xmlToString(input), "http://schemas.opengis.net/gml/3.2.1/base/feature.xsd", null, "text/xml");
        builder.addOutput("complexOutput", "http://schemas.opengis.net/gml/3.2.1/base/feature.xsd", null, "text/xml");

        if (outputAsReference) {
            builder.setAsReference("complexOutput", true);
        }

        Execute executeRequest = builder.getExecute();
        executeRequest.setResponseMode(ResponseMode.DOCUMENT);

        return (Result) session.execute(WPS_URL, executeRequest, "2.0.0");
    }

    private String xmlToString(XmlObject xml) {
        XmlOptions opts = new XmlOptions();

        Map<String, String> prefixes = new HashMap<>();
        prefixes.put("http://www.opengis.net/sampling/2.0", "sf");
        prefixes.put("http://www.opengis.net/gml/3.2", "gml");

        opts.setSaveSuggestedPrefixes(prefixes);
        opts.setSaveAggressiveNamespaces();

        return xml.xmlText(opts);
    }

    private XmlObject createFeatureCollection() {
        FeatureCollectionDocument doc = FeatureCollectionDocument.Factory.newInstance();

        AbstractFeatureCollectionType coll = doc.addNewFeatureCollection();
        coll.setId("wps-test-collection");
        FeaturePropertyType feature = coll.addNewFeatureMember();

        SFSamplingFeatureDocument sampling = SFSamplingFeatureDocument.Factory.newInstance();
        SFSamplingFeatureType sf = sampling.addNewSFSamplingFeature();
        LocationPropertyType loc = sf.addNewLocation();

        PolygonDocument polyDoc = PolygonDocument.Factory.newInstance();
        PolygonType poly = polyDoc.addNewPolygon();
        AbstractRingPropertyType ext = poly.addNewExterior();

        LinearRingDocument ringDoc = LinearRingDocument.Factory.newInstance();
        LinearRingType ring = ringDoc.addNewLinearRing();
        CoordinatesType coords = ring.addNewCoordinates();
        coords.setStringValue("112.1484375,71.18775391813158 88.59374999999999,70.8446726342528 90,67.87554134672945 96.6796875,65.94647177615738 119.53125,68.9110048456202 112.1484375,71.18775391813158");

        // these steps are required as the schema is an working with substitution groups,
        // and the GML binding do not know all valid members for the groups. This is one
        // of the major limitations of XMLBeans
        ext.addNewAbstractRing().set(ring);
        XmlUtil.qualifySubstitutionGroup(ext.getAbstractRing(), LinearRingDocument.type.getDocumentElementName());

        loc.setAbstractGeometry(poly);
        XmlUtil.qualifySubstitutionGroup(loc.getAbstractGeometry(), PolygonDocument.type.getDocumentElementName());

        feature.setAbstractFeature(sf);
        XmlUtil.qualifySubstitutionGroup(feature.getAbstractFeature(), SFSamplingFeatureDocument.type.getDocumentElementName());

        return doc;
    }

}
