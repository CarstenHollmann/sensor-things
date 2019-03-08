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

import java.util.Collection;

import org.apache.olingo.server.api.uri.queryoption.expression.BinaryOperatorKind;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.n52.series.db.beans.sta.DatastreamEntity;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;

/**
 * @author <a href="mailto:j.speckamp@52north.org">Jan Speckamp</a>
 *
 */
public class DatastreamQuerySpecifications extends EntityQuerySpecifications<DatastreamEntity> {

    public BooleanExpression withId(Long id) {
        return qdatastream.id.eq(id);
    }

    public BooleanExpression withName(String name) {
        return qdatastream.name.eq(name);
    }

    public BooleanExpression withObservedProperty(Long observedPropertyId) {
        return qdatastream.observableProperty.id.eq(observedPropertyId);
    }

    public BooleanExpression withObservedProperty(String name) {
        return qdatastream.observableProperty.name.eq(name);
    }

    public BooleanExpression withThing(Long thingId) {
        return qdatastream.thing.id.eq(thingId);
    }

    public BooleanExpression withThing(String name) {
        return qdatastream.thing.name.eq(name);
    }

    public BooleanExpression withSensor(Long sensorId) {
        return qdatastream.procedure.id.eq(sensorId);
    }

    public BooleanExpression withSensor(String name) {
        return qdatastream.procedure.name.eq(name);
    }

    public BooleanExpression withDataset(Long datasetId) {
        return qdatastream.datasets.any().id.eq(datasetId);
    }

    public BooleanExpression withDataset(Collection<Long> datasetIds) {
        return qdatastream.datasets.any().id.in(datasetIds);
    }

    public BooleanExpression withObservation(Long observationId) {
        return qdatastream.datasets.any().id.in(JPAExpressions
                                                              .selectFrom(qobservation)
                                                              .where(qobservation.id.eq(observationId))
                                                              .select(qobservation.dataset.id));
    }

    public JPQLQuery<Long> getIdSubqueryWithFilter(Expression<Boolean> filter) {
        return this.toSubquery(qdatastream, qdatastream.id, filter);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object getFilterForProperty(String propertyName,
                                       Object propertyValue,
                                       BinaryOperatorKind operator,
                                       boolean switched)
            throws ExpressionVisitException {
        if (propertyName.equals("Sensor") || propertyName.equals("ObservedProperty") || propertyName.equals("Thing") || propertyName.equals("Observations")) {
            return handleRelatedPropertyFilter(propertyName, (JPQLQuery<Long>) propertyValue, switched);
        } else if (propertyName.equals("id")) {
            return handleDirectNumberPropertyFilter(qdatastream.id, propertyValue, operator, switched);
        } else {
            return handleDirectPropertyFilter(propertyName, propertyValue, operator, switched);
        }
    }

    /**
     * Handles filtering of properties embedded in this Entity.
     *
     * @param propertyName
     *        Name of property
     * @param propertyValue
     *        Supposed value of Property
     * @param operator
     *        Comparison operator between propertyValue and actual Value
     * @return BooleanExpression evaluating to true if Entity is not filtered out
     * @throws ExpressionVisitException
     */
    private Object handleDirectPropertyFilter(String propertyName,
                                              Object propertyValue,
                                              BinaryOperatorKind operator,
                                              boolean switched)
            throws ExpressionVisitException {
        switch (propertyName) {
        case "name":
            return handleDirectStringPropertyFilter(qdatastream.name, propertyValue, operator, switched);
        case "description":
            return handleDirectStringPropertyFilter(qdatastream.description, propertyValue, operator, switched);
        case "observationType":
            return handleDirectStringPropertyFilter(qdatastream.observationType.format,
                                                    propertyValue,
                                                    operator,
                                                    switched);
        default:
            throw new ExpressionVisitException("Error getting filter for Property: \"" + propertyName
                    + "\". No such property in Entity.");
        }
    }

    /**
     * Handles filtering of properties in related Entities.
     *
     * @param propertyName
     *        Name of property
     * @param propertyValue
     *        Supposed value of Property
     * @param operator
     *        Comparison operator between propertyValue and actual Value
     * @return BooleanExpression evaluating to true if Entity is not filtered out
     */
    private BooleanExpression handleRelatedPropertyFilter(String propertyName,
                                                          JPQLQuery<Long> propertyValue,
                                                          boolean switched)
            throws ExpressionVisitException {

        // TODO: handle switched Parameter
        switch (propertyName) {
        case "Sensor": {
            return qdatastream.procedure.id.eqAny(propertyValue);
        }
        case "ObservedProperty": {
            return qdatastream.observableProperty.id.eqAny(propertyValue);
        }
        case "Thing": {
            return qdatastream.thing.id.eqAny(propertyValue);
        }
        case "Observations": {
            return qdatastream.datasets.any().id.in(JPAExpressions
                                                    .selectFrom(qobservation)
                                                    .where(qobservation.id.eq(propertyValue))
                                                    .select(qobservation.dataset.id));
        }
        default:
            throw new ExpressionVisitException("Error getting filter for Property: \"" + propertyName
                    + "\". No such related Entity.");
        }
    }
}
