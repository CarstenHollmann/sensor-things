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
package org.n52.sta.data.query;

import org.apache.olingo.server.api.uri.queryoption.expression.BinaryOperatorKind;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.n52.series.db.beans.sta.ThingEntity;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPQLQuery;

/**
 * @author <a href="mailto:j.speckamp@52north.org">Jan Speckamp</a>
 *
 */
public class ThingQuerySpecifications extends EntityQuerySpecifications<ThingEntity> {

    public BooleanExpression withRelatedLocation(Long locationId) {
        return qthing.locationEntities.any().id.eq(locationId);
    }

    public BooleanExpression withRelatedHistoricalLocation(Long historicalId) {
        return qthing.historicalLocationEntities.any().id.eq(historicalId);
    }

    public BooleanExpression withRelatedDatastream(Long datastreamId) {
        return qthing.datastreamEntities.any().id.eq(datastreamId);
    }

    public BooleanExpression withId(Long id) {
        return qthing.id.eq(id);
    }

    public BooleanExpression withName(String name) {
        return qthing.name.eq(name);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.n52.sta.data.query.EntityQuerySpecifications#getIdSubqueryWithFilter(com.querydsl.core.types.dsl.
     * BooleanExpression)
     */
    @Override
    public JPQLQuery<Long> getIdSubqueryWithFilter(Expression<Boolean> filter) {
        return this.toSubquery(qthing, qthing.id, filter);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.n52.sta.data.query.EntityQuerySpecifications#getFilterForProperty(java.lang.String,
     * java.lang.Object, org.apache.olingo.server.api.uri.queryoption.expression.BinaryOperatorKind)
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object getFilterForProperty(String propertyName,
                                       Object propertyValue,
                                       BinaryOperatorKind operator,
                                       boolean switched)
            throws ExpressionVisitException {
        if (propertyName.equals("Datastreams") || propertyName.equals("Locations")
                || propertyName.equals("HistoricalLocations")) {
            return handleRelatedPropertyFilter(propertyName, (JPQLQuery<Long>) propertyValue);
        } else if (propertyName.equals("id")) {
            return handleDirectNumberPropertyFilter(qthing.id, propertyValue, operator, switched);
        } else {
            return handleDirectPropertyFilter(propertyName, propertyValue, operator, switched);
        }
    }

    private BooleanExpression handleRelatedPropertyFilter(String propertyName, JPQLQuery<Long> propertyValue)
            throws ExpressionVisitException {
        switch(propertyName) {
        case "Datastreams": {
            return qthing.datastreamEntities.any().id.eqAny(propertyValue);
        }
        case "Locations": {
            return qthing.locationEntities.any().id.eqAny(propertyValue);
        }
        case "HistoricalLocations": {
            return qthing.historicalLocationEntities.any().id.eqAny(propertyValue);
        }
        default:
            throw new ExpressionVisitException("Filtering by Related Properties with cardinality >1 is currently not supported!");
        }
    }

    private Object handleDirectPropertyFilter(String propertyName,
                                              Object propertyValue,
                                              BinaryOperatorKind operator,
                                              boolean switched)
            throws ExpressionVisitException {
        switch (propertyName) {
        case "name":
            return handleDirectStringPropertyFilter(qthing.name, propertyValue, operator, switched);
        case "description":
            return handleDirectStringPropertyFilter(qthing.description, propertyValue, operator, switched);
        case "properties":
            return handleDirectStringPropertyFilter(qthing.properties, propertyValue, operator, switched);
        default:
            throw new ExpressionVisitException("Error getting filter for Property: \"" + propertyName
                    + "\". No such property in Entity.");
        }
    }
}
