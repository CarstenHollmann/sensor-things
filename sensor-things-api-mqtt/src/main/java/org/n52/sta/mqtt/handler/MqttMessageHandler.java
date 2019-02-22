/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.n52.sta.mqtt.handler;

import org.apache.olingo.commons.api.edm.provider.CsdlAbstractEdmProvider;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.uri.UriInfo;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.core.uri.parser.Parser;
import org.apache.olingo.server.core.uri.parser.UriParserException;
import org.apache.olingo.server.core.uri.validator.UriValidationException;
import org.n52.sta.mqtt.MqttHandlerException;
import org.n52.sta.mqtt.core.AbstractMqttSubscription;
import org.n52.sta.mqtt.core.MqttEventHandler;
import org.n52.sta.service.handler.AbstractEntityCollectionRequestHandler;
import org.n52.sta.service.query.URIQueryOptions;
import org.n52.sta.service.response.EntityResponse;
import org.n52.sta.utils.CrudHelper;
import org.n52.sta.utils.UriResourceNavigationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.moquette.interception.messages.InterceptPublishMessage;
import io.moquette.interception.messages.InterceptSubscribeMessage;
import io.moquette.interception.messages.InterceptUnsubscribeMessage;
import io.netty.buffer.ByteBufInputStream;
import org.apache.olingo.server.api.uri.UriResourcePartTyped;
import org.n52.sta.mqtt.core.MqttEntityCollectionSubscription;
import org.n52.sta.mqtt.core.MqttEntitySubscription;
import org.n52.sta.mqtt.request.SensorThingsMqttRequest;
import org.n52.sta.service.handler.AbstractEntityRequestHandler;

/**
 *
 * @author <a href="mailto:s.drost@52north.org">Sebastian Drost</a>
 */
@Component
public class MqttMessageHandler {

    final Logger LOGGER = LoggerFactory.getLogger(MqttMessageHandler.class);

    private static final String BASE_URL = "";

    @Autowired
    private Parser parser;

    @Autowired
    AbstractEntityCollectionRequestHandler<SensorThingsMqttRequest, MqttEntityCollectionSubscription> entityCollcetionRequestHandler;
    
    @Autowired
    AbstractEntityRequestHandler<SensorThingsMqttRequest, MqttEntitySubscription> mqttEntitySubscHandler;

    @Autowired
    private CrudHelper crudHelper;

    @Autowired
    private MqttEventHandler localClient;

    @SuppressWarnings("unchecked")
    public void processPublishMessage(InterceptPublishMessage msg) throws UriParserException, UriValidationException, ODataApplicationException, DeserializerException {
        UriInfo uriInfo = parser.parseUri(msg.getTopicName(), null, null, "");
        EntityResponse entityResponse = new EntityResponse();
        DeserializerResult deserializeRequestBody = crudHelper.deserializeRequestBody(new ByteBufInputStream(msg.getPayload()), uriInfo);
        if (deserializeRequestBody.getEntity() != null) {
            entityResponse = crudHelper.getCrudEntityHanlder(uriInfo)
                    .handleCreateEntityRequest(deserializeRequestBody.getEntity(), uriInfo.getUriResourceParts());
            LOGGER.info("Creation of Entity {} was succesful", entityResponse.getEntity());
        }
    }

    public void processSubscribeMessage(InterceptSubscribeMessage msg) throws MqttHandlerException {
        localClient.addSubscription(createMqttSubscription(msg.getTopicFilter()), msg.getClientID());
    }

    public void processUnsubscribeMessage(InterceptUnsubscribeMessage msg) throws MqttHandlerException {
        localClient.removeSubscription(createMqttSubscription(msg.getTopicFilter()), msg.getClientID());
    }

    private AbstractMqttSubscription createMqttSubscription(String topic) throws MqttHandlerException {
        AbstractMqttSubscription subscription = validateTopicPattern(topic, "");
        return subscription;
    }

    private AbstractMqttSubscription validateTopicPattern(String topic, String baseUri) throws MqttHandlerException {
        try {
            // Validate that Topic is valid URI
            UriInfo uriInfo = parser.parseUri(topic, null, null, baseUri);
            AbstractMqttSubscription subscription = null;
            switch (uriInfo.getKind()) {
                case resource:
                case entityId:
                    subscription = validateResource(topic, uriInfo);
                    break;
                default:
                    throw new MqttHandlerException("Unsupported MQTT topic pattern.");
            }
            return subscription;
        } catch (UriParserException | UriValidationException ex) {
            throw new MqttHandlerException("Error while parsing MQTT topic.", ex);
        }

    }

    private AbstractMqttSubscription validateResource(String topic, UriInfo uriInfo) throws MqttHandlerException {
        try {
            final int lastPathSegmentIndex = uriInfo.getUriResourceParts().size() - 1;
            final UriResource lastPathSegment = uriInfo.getUriResourceParts().get(lastPathSegmentIndex);
            AbstractMqttSubscription subscription = null;

            switch (lastPathSegment.getKind()) {

                case entitySet:
                case navigationProperty:
                    if (((UriResourcePartTyped) lastPathSegment).isCollection()) {
                        subscription = entityCollcetionRequestHandler
                                .handleEntityCollectionRequest(new SensorThingsMqttRequest(topic,
                                        uriInfo.getUriResourceParts(),
                                        new URIQueryOptions(uriInfo, BASE_URL)));
                    } else {
                        subscription = mqttEntitySubscHandler
                                .handleEntityRequest(new SensorThingsMqttRequest(topic,
                                        uriInfo.getUriResourceParts(),
                                        new URIQueryOptions(uriInfo, BASE_URL)));
                    }
                    break;

                case primitiveProperty:
                    // TODO create MqttPropertySubscription with
                    // not yet existing MqttPropertySubscriptionHandler
                    break;

                case complexProperty:
                    // TODO create MqttPropertySubscription with
                    // not yet existing MqttPropertySubscriptionHandler
                    break;

                default:
                    throw new MqttHandlerException("Unsupported MQTT topic pattern.");
            }
            return subscription;
        } catch (ODataApplicationException ex) {
            throw new MqttHandlerException("Error while resolving MQTT subscription topic.", ex);
        }
    }
}