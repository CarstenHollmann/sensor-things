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
package org.n52.sta.mapping;

import java.util.Collections;
import java.util.HashMap;
import static org.n52.sta.edm.provider.entities.AbstractSensorThingsEntityProvider.PROP_ID;
import static org.n52.sta.edm.provider.entities.AbstractSensorThingsEntityProvider.PROP_OBSERVATION_TYPE;
import static org.n52.sta.edm.provider.entities.AbstractSensorThingsEntityProvider.PROP_OBSERVED_AREA;
import static org.n52.sta.edm.provider.entities.AbstractSensorThingsEntityProvider.PROP_PHENOMENON_TIME;
import static org.n52.sta.edm.provider.entities.AbstractSensorThingsEntityProvider.PROP_RESULT_TIME;
import static org.n52.sta.edm.provider.entities.AbstractSensorThingsEntityProvider.PROP_UOM;
import static org.n52.sta.edm.provider.entities.DatastreamEntityProvider.ES_DATASTREAMS_NAME;
import static org.n52.sta.edm.provider.entities.DatastreamEntityProvider.ET_DATASTREAM_FQN;
import static org.n52.sta.edm.provider.entities.ObservationEntityProvider.ES_OBSERVATIONS_NAME;
import static org.n52.sta.edm.provider.entities.ObservedPropertyEntityProvider.ET_OBSERVED_PROPERTY_NAME;
import static org.n52.sta.edm.provider.entities.SensorEntityProvider.ET_SENSOR_NAME;
import static org.n52.sta.edm.provider.entities.ThingEntityProvider.ET_THING_NAME;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.olingo.commons.api.data.ComplexValue;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.geo.Geospatial;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.n52.series.db.beans.FormatEntity;
import org.n52.series.db.beans.UnitEntity;
import org.n52.series.db.beans.sta.DatastreamEntity;
import org.n52.series.db.beans.sta.StaDataEntity;
import org.n52.shetland.ogc.gml.time.Time;
import org.n52.shetland.ogc.gml.time.TimeInstant;
import org.n52.shetland.ogc.gml.time.TimePeriod;
import org.n52.shetland.util.DateTimeHelper;
import org.n52.sta.edm.provider.complextypes.UnitOfMeasurementComplexType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author <a href="mailto:s.drost@52north.org">Sebastian Drost</a>
 */
@Component
public class DatastreamMapper extends AbstractLocationGeometryMapper<DatastreamEntity> {

    @Autowired
    private ThingMapper thingMapper;

    @Autowired
    private ObservedPropertyMapper observedPropertyMapper;

    @Autowired
    private SensorMapper sensorMapper;

    @Autowired
    private ObservationMapper observationMapper;

    public Entity createEntity(DatastreamEntity datastream) {
        Entity entity = new Entity();
        entity.addProperty(new Property(null, PROP_ID, ValueType.PRIMITIVE, datastream.getId()));
        addDescription(entity, datastream);
        addName(entity, datastream);
        entity.addProperty(new Property(null,
                                        PROP_OBSERVATION_TYPE,
                                        ValueType.PRIMITIVE,
                                        datastream.getObservationType().getFormat()));

        entity.addProperty(new Property(null,
                                        PROP_PHENOMENON_TIME,
                                        ValueType.PRIMITIVE,
                                        DateTimeHelper.format(createTime(createDateTime(datastream.getSamplingTimeStart()),
                                                                         createDateTime(datastream.getSamplingTimeEnd())))));
        entity.addProperty(new Property(null,
                                        PROP_RESULT_TIME,
                                        ValueType.PRIMITIVE,
                                        DateTimeHelper.format(createTime(createDateTime(datastream.getResultTimeStart()),
                                                                         createDateTime(datastream.getResultTimeEnd())))));

        entity.addProperty(new Property(null,
                                        PROP_UOM,
                                        ValueType.COMPLEX,
                                        resolveUnitOfMeasurement(datastream.getUnitOfMeasurement())));
        addObservedArea(entity, datastream);

        entity.setType(ET_DATASTREAM_FQN.getFullQualifiedNameAsString());
        entity.setId(entityCreationHelper.createId(entity, ES_DATASTREAMS_NAME, PROP_ID));

        return entity;
    }

    public DatastreamEntity createEntity(Entity entity) {
        DatastreamEntity datastream = new DatastreamEntity();
        setId(datastream, entity);
        setName(datastream, entity);
        setDescription(datastream, entity);
        addFormat(datastream, entity);
        addObservedArea(datastream, entity);
        addUnitOfMeasurement(datastream, entity);
        addPhenomenonTime(datastream, entity);
        addResultTime(datastream, entity);
        addSensor(datastream, entity);
        addObservedProperty(datastream, entity);
        addThing(datastream, entity);
        addObservations(datastream, entity);
        return datastream;
    }

    @Override
    public DatastreamEntity merge(DatastreamEntity existing, DatastreamEntity toMerge)
            throws ODataApplicationException {
        mergeName(existing, toMerge);
        mergeDescription(existing, toMerge);
        checkObservationType(existing, toMerge);
        // observedArea
        mergeGeometry(existing, toMerge);
        // unit
        if (toMerge.isSetUnit() && existing.getUnit().getSymbol().equals(toMerge.getUnit().getSymbol())) {
            existing.setUnit(toMerge.getUnit());
        }
        // phenTime
        mergeSamplingTime(existing, toMerge);

        // resultTime
        if (toMerge.hasResultTimeStart() && toMerge.hasResultTimeEnd()) {
            existing.setResultTimeStart(toMerge.getResultTimeStart());
            existing.setResultTimeEnd(toMerge.getResultTimeEnd());
        }
        // observationType
        if (existing.getDatasets() == null || existing.getDatasets().isEmpty() && toMerge.isSetObservationType()) {
            if (!existing.getObservationType().getFormat().equals(toMerge.getObservationType().getFormat())) {
                existing.setObservationType(toMerge.getObservationType());
            }
        }
        return existing;
    }

    @Override
    public Entity checkEntity(Entity entity) throws ODataApplicationException {
        checkNameAndDescription(entity);
        checkPropertyValidity(PROP_OBSERVATION_TYPE, entity);
        checkPropertyValidity(PROP_UOM, entity);
        checkUom(entity);
        if (checkNavigationLink(entity, ET_SENSOR_NAME)) {
            sensorMapper.checkNavigationLink(entity.getNavigationLink(ET_SENSOR_NAME).getInlineEntity());
        }
        if (checkNavigationLink(entity, ET_OBSERVED_PROPERTY_NAME)) {
            observedPropertyMapper
            .checkNavigationLink(entity.getNavigationLink(ET_OBSERVED_PROPERTY_NAME)
                                 .getInlineEntity());
        }
        if (checkNavigationLink(entity, ET_THING_NAME)) {
            thingMapper.checkNavigationLink(entity.getNavigationLink(ET_THING_NAME).getInlineEntity());
        }
        if (checkNavigationLink(entity, ES_OBSERVATIONS_NAME)) {
            Iterator<Entity> iterator = entity.getNavigationLink(ES_OBSERVATIONS_NAME).getInlineEntitySet().iterator();
            while (iterator.hasNext()) {
                observationMapper.checkNavigationLink((Entity) iterator.next());
            }
        }
        return entity;
    }

    private void checkObservationType(DatastreamEntity existing, DatastreamEntity toMerge)
            throws ODataApplicationException {
        if (toMerge.isSetObservationType() && !toMerge.getObservationType().getFormat().equalsIgnoreCase("unknown")
                && !existing.getObservationType().getFormat().equals(toMerge.getObservationType().getFormat())) {
            throw new ODataApplicationException(
                                                String.format(
                                                              "The updated observationType (%s) does not comply with the existing observationType (%s)",
                                                              toMerge.getObservationType().getFormat(),
                                                              existing.getObservationType().getFormat()),
                                                HttpStatusCode.CONFLICT.getStatusCode(),
                                                Locale.getDefault());
        }
    }

    private ComplexValue resolveUnitOfMeasurement(UnitEntity uom) {
        ComplexValue value = new ComplexValue();
        if (uom != null) {
            value.getValue().add(
                                 new Property(null,
                                              UnitOfMeasurementComplexType.PROP_NAME,
                                              ValueType.PRIMITIVE,
                                              uom.getName()));
            value.getValue().add(new Property(null,
                                              UnitOfMeasurementComplexType.PROP_SYMBOL,
                                              ValueType.PRIMITIVE,
                                              uom.getSymbol()));
            value.getValue().add(new Property(null,
                                              UnitOfMeasurementComplexType.PROP_DEFINITION,
                                              ValueType.PRIMITIVE,
                                              uom.getLink()));

        }
        return value;
    }

    private DatastreamEntity addFormat(DatastreamEntity datastream, Entity entity) {
        if (checkProperty(entity, PROP_OBSERVATION_TYPE)) {
            return datastream.setObservationType(
                                                 new FormatEntity().setFormat(getPropertyValue(entity,
                                                                                               PROP_OBSERVATION_TYPE).toString()));
        }
        return datastream.setObservationType(new FormatEntity().setFormat("unknown"));
    }

    private void addObservedArea(DatastreamEntity datastream, Entity entity) {
        Property observedArea = entity.getProperty(PROP_OBSERVED_AREA);
        if (observedArea != null) {
            if (observedArea.getValueType().equals(ValueType.PRIMITIVE)
                    && observedArea.getValue() instanceof Geospatial) {
                datastream.setGeometryEntity(parseGeometry((Geospatial) observedArea.getValue()));
            }
        }
    }

    private void addUnitOfMeasurement(DatastreamEntity datastream, Entity entity) {
        UnitEntity unit = new UnitEntity();
        Object uom = getPropertyValue(entity, PROP_UOM);
        if (uom instanceof ComplexValue) {
            ComplexValue uomComplexValue = (ComplexValue) uom;
            unit.setSymbol(getPropertyValue(uomComplexValue, UnitOfMeasurementComplexType.PROP_SYMBOL).toString());
            unit.setName(getPropertyValue(uomComplexValue, UnitOfMeasurementComplexType.PROP_NAME).toString());
            unit.setLink(getPropertyValue(uomComplexValue, UnitOfMeasurementComplexType.PROP_DEFINITION).toString());
            datastream.setUnit(unit);
        }
    }

    private void addResultTime(DatastreamEntity datastream, Entity entity) {
        if (checkProperty(entity, PROP_RESULT_TIME)) {
            Time time = parseTime(getPropertyValue(entity, PROP_RESULT_TIME));
            if (time instanceof TimeInstant) {
                datastream.setResultTimeStart(((TimeInstant) time).getValue().toDate());
                datastream.setResultTimeEnd(((TimeInstant) time).getValue().toDate());
            } else if (time instanceof TimePeriod) {
                datastream.setResultTimeStart(((TimePeriod) time).getStart().toDate());
                datastream.setResultTimeEnd(((TimePeriod) time).getEnd().toDate());
            }
        }
    }

    private void addSensor(DatastreamEntity datastream, Entity entity) {
        if (checkNavigationLink(entity, ET_SENSOR_NAME)) {
            datastream.setProcedure(
                                    sensorMapper.createEntity(entity.getNavigationLink(ET_SENSOR_NAME)
                                                              .getInlineEntity()));
        }
    }

    private void addObservedProperty(DatastreamEntity datastream, Entity entity) {
        if (checkNavigationLink(entity, ET_OBSERVED_PROPERTY_NAME)) {
            datastream.setObservableProperty(observedPropertyMapper
                                             .createEntity(entity.getNavigationLink(ET_OBSERVED_PROPERTY_NAME)
                                                           .getInlineEntity()));
        }
    }

    private void addThing(DatastreamEntity datastream, Entity entity) {
        if (checkNavigationLink(entity, ET_THING_NAME)) {
            datastream.setThing(thingMapper.createEntity(entity.getNavigationLink(ET_THING_NAME).getInlineEntity()));
        }
    }

    private void addObservations(DatastreamEntity datastream, Entity entity) {
        if (checkNavigationLink(entity, ES_OBSERVATIONS_NAME)) {
            Set<StaDataEntity> observations = new LinkedHashSet<>();
            Iterator<Entity> iterator = entity.getNavigationLink(ES_OBSERVATIONS_NAME).getInlineEntitySet().iterator();
            while (iterator.hasNext()) {
                StaDataEntity createObservation = observationMapper.createEntity((Entity) iterator.next());
                createObservation.setDatastream(datastream);
                observations.add(createObservation);
            }
            datastream.setObservations(observations);
        }
    }

    private void checkUom(Entity entity) throws ODataApplicationException {
        checkPropertyValidity(PROP_UOM, entity);
        Object value = getPropertyValue(entity, PROP_UOM);
        if (value != null && value instanceof ComplexValue) {
            ComplexValue uomComplexValue = (ComplexValue) value;
            checkProperty(uomComplexValue, UnitOfMeasurementComplexType.PROP_SYMBOL);
            checkProperty(uomComplexValue, UnitOfMeasurementComplexType.PROP_NAME);
            checkProperty(uomComplexValue, UnitOfMeasurementComplexType.PROP_DEFINITION);
        }
    }

    @Override
    public Map<String, Set<Long>> getRelatedCollections(Object rawObject) {
        Map<String, Set<Long>> collections = new HashMap<String, Set<Long>> ();
        DatastreamEntity entity = (DatastreamEntity) rawObject;
        try {
            collections.put(ET_THING_NAME, Collections.singleton(entity.getThing().getId()));
        } catch(NullPointerException e) {}
        try {
            collections.put(ET_SENSOR_NAME, Collections.singleton(entity.getProcedure().getId()));
        } catch(NullPointerException e) {}
        try {
            collections.put(ET_OBSERVED_PROPERTY_NAME, Collections.singleton(entity.getObservableProperty().getId()));
        } catch(NullPointerException e) {}
        return collections;
    }
}
