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

import org.n52.series.db.beans.QDatasetEntity;

import com.querydsl.core.types.dsl.BooleanExpression;

public class DatasetQuerySpecifications {

    /**
     * Matches datasets having offering with given ids.
     *
     * @param id
     *        the id to match
     * @return a boolean expression
     */
    public BooleanExpression matchOfferings(final String id) {
        return QDatasetEntity.datasetEntity.offering.id.eq(Long.valueOf(id));
    }

    /**
     * Matches datasets having feature with given id.
     *
     * @param id
     *        the id to match
     * @return a boolean expression
     */
    public BooleanExpression matchFeatures(final String id) {
        return QDatasetEntity.datasetEntity.feature.id.eq(Long.valueOf(id));
    }

    /**
     * Matches datasets having procedures with given id.
     *
     * @param id
     *        the id to match
     * @return a boolean expression
     */
    public BooleanExpression matchProcedures(final String id) {
        return QDatasetEntity.datasetEntity.procedure.id.eq(Long.valueOf(id));
    }

    /**
     * Matches datasets having phenomena with given id.
     *
     * @param id
     *        the id to match
     * @return a boolean expression
     */
    public BooleanExpression matchPhenomena(final String id) {
        return QDatasetEntity.datasetEntity.phenomenon.id.eq(Long.valueOf(id));
    }
}
