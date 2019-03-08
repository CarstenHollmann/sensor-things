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

import static org.n52.sta.edm.provider.entities.AbstractSensorThingsEntityProvider.PROP_ID;
import static org.n52.sta.edm.provider.entities.AbstractSensorThingsEntityProvider.PROP_PROPERTIES;
import static org.n52.sta.edm.provider.entities.DatastreamEntityProvider.ES_DATASTREAMS_NAME;
import static org.n52.sta.edm.provider.entities.LocationEntityProvider.ES_LOCATIONS_NAME;
import static org.n52.sta.edm.provider.entities.ThingEntityProvider.ES_THINGS_NAME;
import static org.n52.sta.edm.provider.entities.ThingEntityProvider.ET_THING_FQN;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.olingo.commons.api.data.ComplexValue;
import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.server.api.ODataApplicationException;
import org.n52.series.db.beans.sta.DatastreamEntity;
import org.n52.series.db.beans.sta.LocationEntity;
import org.n52.series.db.beans.sta.ThingEntity;
import org.n52.sta.utils.EntityCreationHelper;
import org.n52.sta.utils.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import static org.n52.sta.edm.provider.entities.DatastreamEntityProvider.ET_DATASTREAM_NAME;
import static org.n52.sta.edm.provider.entities.HistoricalLocationEntityProvider.ET_HISTORICAL_LOCATION_NAME;
import static org.n52.sta.edm.provider.entities.LocationEntityProvider.ET_LOCATION_NAME;

/**
 *
 * @author <a href="mailto:s.drost@52north.org">Sebastian Drost</a>
 */
@Component
public class ThingMapper extends AbstractMapper<ThingEntity> {

    private static final Logger LOG = LoggerFactory.getLogger(ThingMapper.class);

    @Autowired
    private EntityCreationHelper entityCreationHelper;

    @Autowired
    private JsonHelper jsonHelper;

    @Autowired
    private LocationMapper locationMapper;

    @Autowired
    private DatastreamMapper datastreamMapper;

    @Override
    public Entity createEntity(ThingEntity thing) {
        Entity entity = new Entity();
        entity.addProperty(new Property(null, PROP_ID, ValueType.PRIMITIVE, thing.getId()));
        addDescription(entity, thing);
        addName(entity, thing);

        entity.addProperty(new Property(null, PROP_PROPERTIES, ValueType.COMPLEX, createJsonProperty(thing)));

        entity.setType(ET_THING_FQN.getFullQualifiedNameAsString());
        entity.setId(entityCreationHelper.createId(entity, ES_THINGS_NAME, PROP_ID));

        return entity;
    }

    @Override
    public ThingEntity createEntity(Entity entity) {
        ThingEntity thing = new ThingEntity();
        setId(thing, entity);
        setName(thing, entity);
        setDescription(thing, entity);
        if (entity.getProperty(PROP_PROPERTIES) != null) {
            if (entity.getProperty(PROP_PROPERTIES).getValue() instanceof ComplexValue) {
                thing.setProperties(getPropertyValue((ComplexValue) entity.getProperty(PROP_PROPERTIES).getValue(), PROP_PROPERTIES).toString());
            } else {
                thing.setProperties(entity.getProperty(PROP_PROPERTIES).getValue().toString());
            }
        }
        addLocations(thing, entity);
        addDatastreams(thing, entity);
        return thing;
    }

    @Override
    public ThingEntity merge(ThingEntity existing, ThingEntity toMerge) {
        mergeName(existing, toMerge);
        mergeDescription(existing, toMerge);
        if (toMerge.hasProperties()) {
            existing.setProperties(toMerge.getProperties());
        }
        return existing;
    }

    private void addLocations(ThingEntity thing, Entity entity) {
        if (checkNavigationLink(entity, ES_LOCATIONS_NAME)) {
            Set<LocationEntity> locations = new LinkedHashSet<>();
            Iterator<Entity> iterator = entity.getNavigationLink(ES_LOCATIONS_NAME).getInlineEntitySet().iterator();
            while (iterator.hasNext()) {
                locations.add(locationMapper.createEntity((Entity) iterator.next()));
            }
            thing.setLocationEntities(locations);
        }
    }

    private void addDatastreams(ThingEntity thing, Entity entity) {
        if (checkNavigationLink(entity, ES_DATASTREAMS_NAME)) {
            Set<DatastreamEntity> datastreams = new LinkedHashSet<>();
            Iterator<Entity> iterator = entity.getNavigationLink(ES_DATASTREAMS_NAME).getInlineEntitySet().iterator();
            while (iterator.hasNext()) {
                datastreams.add(datastreamMapper.createEntity((Entity) iterator.next()));
            }
            thing.setDatastreamEntities(datastreams);
        }
    }

    @SuppressWarnings("finally")
    private JsonNode createJsonProperty(ThingEntity thing) {
        JsonNode node = null;
        try {
            node = jsonHelper.readJsonString(thing.getProperties());
        } catch (IOException ex) {
            LOG.warn("Could not parse properties for ThingEntity: {}", thing.getId(), ex.getMessage());
            LOG.debug(ex.getMessage(), ex);
        } finally {
            if (node == null) {
                return jsonHelper.createEmptyObjectNode();
            } else {
                return node;
            }
        }
    }

    @Override
    public Entity checkEntity(Entity entity) throws ODataApplicationException {
        checkNameAndDescription(entity);
        if (checkNavigationLink(entity, ES_LOCATIONS_NAME)) {
            Iterator<Entity> iterator = entity.getNavigationLink(ES_LOCATIONS_NAME).getInlineEntitySet().iterator();
            while (iterator.hasNext()) {
                locationMapper.checkNavigationLink((Entity) iterator.next());
            }
        }
        if (checkNavigationLink(entity, ES_DATASTREAMS_NAME)) {
            Iterator<Entity> iterator = entity.getNavigationLink(ES_DATASTREAMS_NAME).getInlineEntitySet().iterator();
            while (iterator.hasNext()) {
                datastreamMapper.checkNavigationLink((Entity) iterator.next());
            }
        }
        return entity;
    }

    /* (non-Javadoc)
     * @see org.n52.sta.mapping.AbstractMapper#getRelatedCollections(java.lang.Object)
     */
    @Override
    public Map<String, Set<Long>> getRelatedCollections(Object rawObject) {
        Map<String, Set<Long>> collections = new HashMap<String, Set<Long>> ();
        Set<Long> set = new HashSet<Long>();
        ThingEntity entity = (ThingEntity) rawObject;

        try {
            entity.getLocationEntities().forEach((en)-> {
                set.add(en.getId());
            });
            collections.put(ET_LOCATION_NAME, new HashSet(set));
        } catch(NullPointerException e) {}
        set.clear();
        try {
            entity.getHistoricalLocationEntities().forEach((en) -> {
                set.add(en.getId());
            });
            collections.put(ET_HISTORICAL_LOCATION_NAME,  new HashSet(set));
        } catch(NullPointerException e) {}
        set.clear();
        try {
            entity.getDatastreamEntities().forEach((en) -> {
                set.add(en.getId());
            });
            collections.put(ET_DATASTREAM_NAME,  new HashSet(set));
        } catch(NullPointerException e) {}
        return collections;
    }
}
