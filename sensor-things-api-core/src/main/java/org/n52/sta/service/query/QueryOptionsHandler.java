/*
 * Copyright (C) 2018-2019 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 as published
 * by the Free Software Foundation.
 *
 * If the program is linked with libraries which are licensed under one of
 * the following licenses, the combination of the program with the linked
 * library is not considered a "derivative work" of the program:
 *
 *     - Apache License, version 2.0
 *     - Apache Software License, version 1.0
 *     - GNU Lesser General Public License, version 3
 *     - Mozilla Public License, versions 1.0, 1.1 and 2.0
 *     - Common Development and Distribution License (CDDL), version 1.0
 *
 * Therefore the distribution of the program linked with libraries licensed
 * under the aforementioned licenses, is permitted by the copyright holders
 * if the distribution is compliant with both the GNU General Public
 * License version 2 and the aforementioned licenses.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 */
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.n52.sta.service.query;

import static org.n52.sta.edm.provider.entities.AbstractSensorThingsEntityProvider.PROP_ID;

import java.util.List;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.data.Link;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.uri.UriHelper;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceNavigation;
import org.apache.olingo.server.api.uri.queryoption.ExpandItem;
import org.apache.olingo.server.api.uri.queryoption.ExpandOption;
import org.apache.olingo.server.api.uri.queryoption.SelectOption;
import org.n52.sta.data.service.AbstractSensorThingsEntityService;
import org.n52.sta.data.service.EntityServiceRepository;
import org.n52.sta.utils.EntityAnnotator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Class to handle query options
 *
 * @author <a href="mailto:s.drost@52north.org">Sebastian Drost</a>
 */
@Component
@SuppressWarnings("checkstyle:avoidstaticimport")
public class QueryOptionsHandler {

    protected UriHelper uriHelper;

    @Autowired
    private EntityServiceRepository serviceRepository;

    @Autowired
    private EntityAnnotator entityAnnotator;

    public UriHelper getUriHelper() {
        return uriHelper;
    }

    public void setUriHelper(UriHelper uriHelper) {
        this.uriHelper = uriHelper;
    }

    /**
     * @param edmEntityType
     *        the {@link EdmEntityType} the select list option is
     * @param expandOption
     *        the {@link ExpandOption} to get the expand items from
     * @param selectOption
     *        the {@link SelectOption} to get the select list from referred to
     * @return the select list
     * @throws SerializerException if list could not be extracted
     */
    public String getSelectListFromSelectOption(EdmEntityType edmEntityType,
                                                ExpandOption expandOption,
                                                SelectOption selectOption)
                                                        throws SerializerException {
        String selectList = uriHelper.buildContextURLSelectList(edmEntityType,
                                                                expandOption,
                                                                selectOption);

        return selectList;
    }

    /**
     * Handles the $expand Query Parameter
     *
     * @param entity
     *        The entity to handle expand parameter for
     * @param expandOption
     *        Options for expand Parameter
     * @param sourceId
     *        Id of the source Entity
     * @param sourceEdmEntityType
     *        EntityType of the source Entity
     * @param baseURI
     *        baseURI of the Request
     */
    @SuppressWarnings("checkstyle:emptycatchblock")
    public void handleExpandOption(Entity entity,
                                   ExpandOption expandOption,
                                   Long sourceId,
                                   EdmEntityType sourceEdmEntityType,
                                   String baseURI) {
        List<ExpandItem> minimized = expandOption.getExpandItems();
        minimized.forEach(expandItem -> {
            EdmNavigationProperty edmNavigationProperty = null;
            EdmEntityType targetEdmEntityType = null;
            String targetTitle = null;

            UriResource uriResource = expandItem.getResourcePath().getUriResourceParts().get(0);

            if (uriResource instanceof UriResourceNavigation) {
                edmNavigationProperty = ((UriResourceNavigation) uriResource).getProperty();
            }

            // Get Target Type and Name
            if (edmNavigationProperty != null) {
                targetEdmEntityType = edmNavigationProperty.getType();
                targetTitle = edmNavigationProperty.getName();
            }

            Link link = entity.getNavigationLink(targetTitle);

            // Either add inline Collection or add single inline Entity
            if (sourceEdmEntityType.getNavigationProperty(targetTitle).isCollection()) {
                try {
                    link.setInlineEntitySet(
                                        getInlineEntityCollection(sourceId,
                                                                  sourceEdmEntityType,
                                                                  targetEdmEntityType,
                                                                  new ExpandItemQueryOptions(expandItem, baseURI)));
                } catch (ODataApplicationException e) { }
            } else {
                link.setInlineEntity(getInlineEntity(sourceId,
                                                     sourceEdmEntityType,
                                                     targetEdmEntityType,
                                                     new ExpandItemQueryOptions(expandItem, baseURI)));
            }
        });
    }

    private Entity getInlineEntity(Long sourceId,
                                   EdmEntityType sourceType,
                                   EdmEntityType targetType,
                                   QueryOptions queryOptions) {
        AbstractSensorThingsEntityService< ? , ? > responseService = serviceRepository
                .getEntityService(targetType.getName());
        Entity entity = responseService.getRelatedEntity(sourceId, sourceType);

        if (queryOptions.hasExpandOption()) {
            entityAnnotator.annotateEntity(entity,
                                           targetType,
                                           queryOptions.getBaseURI(),
                                           queryOptions.getSelectOption());
            String id = entity.getProperty(PROP_ID).getValue().toString();
            handleExpandOption(entity,
                               queryOptions.getExpandOption(),
                               Long.parseLong(id),
                               targetType,
                               queryOptions.getBaseURI());
        } else {
            entityAnnotator.annotateEntity(entity,
                                           targetType,
                                           queryOptions.getBaseURI(),
                                           queryOptions.getSelectOption());
        }

        return entity;
    }

    private EntityCollection getInlineEntityCollection(Long sourceId,
                                                       EdmEntityType sourceType,
                                                       EdmEntityType targetType,
                                                       QueryOptions queryOptions)
                                                               throws ODataApplicationException {
        AbstractSensorThingsEntityService< ? , ? > responseService = serviceRepository
                .getEntityService(targetType.getName());
        EntityCollection entityCollection = responseService.getRelatedEntityCollection(sourceId,
                                                                                       sourceType,
                                                                                       queryOptions);

        if (queryOptions.hasCountOption()) {
            long count = responseService.getRelatedEntityCollectionCount(sourceId, sourceType);
            entityCollection.setCount(Long.valueOf(count).intValue());
        }

        if (queryOptions.hasExpandOption()) {
            entityCollection.forEach(entity -> {
                entityAnnotator.annotateEntity(entity,
                                               targetType,
                                               queryOptions.getBaseURI(),
                                               queryOptions.getSelectOption());
                String id = entity.getProperty(PROP_ID).getValue().toString();
                handleExpandOption(entity,
                                   queryOptions.getExpandOption(),
                                   Long.parseLong(id),
                                   targetType,
                                   queryOptions.getBaseURI());
            });
        } else {
            entityCollection.forEach(entity -> {
                entityAnnotator.annotateEntity(entity,
                                               targetType,
                                               queryOptions.getBaseURI(),
                                               queryOptions.getSelectOption());
            });
        }

        return entityCollection;
    }

}
