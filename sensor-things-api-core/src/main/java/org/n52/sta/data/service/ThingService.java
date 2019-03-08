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
package org.n52.sta.data.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.n52.series.db.beans.ProcedureEntity;
import org.n52.series.db.beans.sta.QThingEntity;
import org.apache.olingo.commons.api.http.HttpMethod;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.joda.time.DateTime;
import org.n52.series.db.beans.sta.DatastreamEntity;
import org.n52.series.db.beans.sta.HistoricalLocationEntity;
import org.n52.series.db.beans.sta.LocationEntity;
import org.n52.series.db.beans.sta.ThingEntity;
import org.n52.sta.data.query.ThingQuerySpecifications;
import org.n52.sta.data.repositories.ThingRepository;
import org.n52.sta.data.service.EntityServiceRepository.EntityTypes;
import org.n52.sta.mapping.ThingMapper;
import org.n52.sta.service.query.FilterExpressionVisitor;
import org.n52.sta.service.query.QueryOptions;
import org.springframework.stereotype.Component;

import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;

/**
 *
 * @author <a href="mailto:s.drost@52north.org">Sebastian Drost</a>
 */
@Component
public class ThingService extends AbstractSensorThingsEntityService<ThingRepository, ThingEntity> {

    private ThingMapper mapper;

    private final static ThingQuerySpecifications tQS = new ThingQuerySpecifications();

    public ThingService(ThingRepository repository, ThingMapper mapper) {
        super(repository);
        this.mapper = mapper;
    }

    @Override
    public EntityTypes getType() {
        return EntityTypes.Thing;
    }

    @Override
    public EntityCollection getEntityCollection(QueryOptions queryOptions) throws ODataApplicationException {
        EntityCollection retEntitySet = new EntityCollection();
        Predicate filter = getFilterPredicate(ThingEntity.class, queryOptions);
        getRepository().findAll(filter, createPageableRequest(queryOptions)).forEach(t -> retEntitySet.getEntities().add(mapper.createEntity(t)));
        return retEntitySet;
    }

    @Override
    public Entity getEntity(Long id) {
        Optional<ThingEntity> entity = getRepository().findOne(tQS.withId(id));
        return entity.isPresent() ? mapper.createEntity(entity.get()) : null;
    }

    @Override
    public EntityCollection getRelatedEntityCollection(Long sourceId, EdmEntityType sourceEntityType, QueryOptions queryOptions) throws ODataApplicationException {
        BooleanExpression filter = tQS.withRelatedLocation(sourceId);

        filter = filter.and(getFilterPredicate(ThingEntity.class, queryOptions));
        Iterable<ThingEntity> things = getRepository().findAll(filter, createPageableRequest(queryOptions));

        EntityCollection retEntitySet = new EntityCollection();
        things.forEach(t -> retEntitySet.getEntities().add(mapper.createEntity(t)));
        return retEntitySet;
    }

    @Override
    public long getRelatedEntityCollectionCount(Long sourceId, EdmEntityType sourceEntityType) {
        return getRepository().count(tQS.withRelatedLocation(sourceId));
    }

    @Override
    public boolean existsEntity(Long id) {
        return getRepository().exists(tQS.withId(id));
    }

    @Override
    public boolean existsRelatedEntity(Long sourceId, EdmEntityType sourceEntityType) {
        return this.existsRelatedEntity(sourceId, sourceEntityType, null);
    }

    @Override
    public boolean existsRelatedEntity(Long sourceId, EdmEntityType sourceEntityType, Long targetId) {
        switch(sourceEntityType.getFullQualifiedName().getFullQualifiedNameAsString()) {
        case "iot.Location": {
            BooleanExpression filter = tQS.withRelatedLocation(sourceId);
            if (targetId != null) {
                filter = filter.and(tQS.withId(targetId));
            }
            return getRepository().exists(filter);
        }
        default: return false;
        }
    }

    @Override
    public OptionalLong getIdForRelatedEntity(Long sourceId, EdmEntityType sourceEntityType) {
        return this.getIdForRelatedEntity(sourceId, sourceEntityType, null);
    }

    @Override
    public OptionalLong getIdForRelatedEntity(Long sourceId, EdmEntityType sourceEntityType, Long targetId) {
        Optional<ThingEntity> thing = this.getRelatedEntityRaw(sourceId, sourceEntityType, targetId);
        if (thing.isPresent()) {
            return OptionalLong.of(thing.get().getId());
        } else {
            return OptionalLong.empty();
        }
    }

    @Override
    public Entity getRelatedEntity(Long sourceId, EdmEntityType sourceEntityType) {
        return this.getRelatedEntity(sourceId, sourceEntityType, null);
    }

    @Override
    public Entity getRelatedEntity(Long sourceId, EdmEntityType sourceEntityType, Long targetId) {
        Optional<ThingEntity> thing = this.getRelatedEntityRaw(sourceId, sourceEntityType, targetId);
        if (thing.isPresent()) {
            return mapper.createEntity(thing.get());
        } else {
            return null;
        }
    }

    /**
     * Retrieves Thing Entity with Relation to sourceEntity from Database.
     * Returns empty if Thing is not found or Entities are not related.
     *
     * @param sourceId Id of the Source Entity
     * @param sourceEntityType Type of the Source Entity
     * @param targetId Id of the Thing to be retrieved
     * @return Optional<ThingEntity> Requested Entity
     */
    private Optional<ThingEntity> getRelatedEntityRaw(Long sourceId, EdmEntityType sourceEntityType, Long targetId) {
        BooleanExpression filter;
        switch(sourceEntityType.getFullQualifiedName().getFullQualifiedNameAsString()) {
        case "iot.HistoricalLocation": {
            filter = tQS.withRelatedHistoricalLocation(sourceId);
            break;
        }
        case "iot.Datastream": {
            filter = tQS.withRelatedDatastream(sourceId);
            break;
        }
        case "iot.Location": {
            filter = tQS.withRelatedLocation(sourceId);
            break;
        }
        default: return Optional.empty();
        }

        if (targetId != null) {
            filter = filter.and(tQS.withId(targetId));
        }
        return getRepository().findOne(filter);
    }

    @Override
    public long getCount(QueryOptions queryOptions) throws ODataApplicationException {
        return getRepository().count(getFilterPredicate(ThingEntity.class, queryOptions));
    }

    @Override
    public ThingEntity create(ThingEntity thing) throws ODataApplicationException {
        if (!thing.isProcesssed()) {
            if (thing.getId() != null && !thing.isSetName()) {
                return getRepository().findOne(tQS.withId(thing.getId())).get();
            }
            if (getRepository().exists(tQS.withName(thing.getName()))) {
                Optional<ThingEntity> optional = getRepository().findOne(tQS.withName(thing.getName()));
                return optional.isPresent() ? optional.get() : null;
            }
            thing.setProcesssed(true);
            processLocations(thing);
            thing = getRepository().save(thing);
            processHistoricalLocations(thing);
            processDatastreams(thing);
            thing = getRepository().save(thing);
        }
        return thing;

    }

    @Override
    public ThingEntity update(ThingEntity entity, HttpMethod method) throws ODataApplicationException {
        checkUpdate(entity);
        if (HttpMethod.PATCH.equals(method)) {
            Optional<ThingEntity> existing = getRepository().findOne(tQS.withId(entity.getId()));
            if (existing.isPresent()) {
                ThingEntity merged = mapper.merge(existing.get(), entity);
                if (entity.hasLocationEntities()) {
                    merged.setLocationEntities(entity.getLocationEntities());
                    processLocations(merged);
                    merged = getRepository().save(merged);
                    processHistoricalLocations(merged);
                }
                return getRepository().save(merged);
            }
            throw new ODataApplicationException("Entity not found.",
                    HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        } else if (HttpMethod.PUT.equals(method)) {
            throw new ODataApplicationException("Http PUT is not yet supported!",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.getDefault());
        }
        throw new ODataApplicationException("Invalid http method for updating entity!",
                HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.getDefault());
    }

    private void checkUpdate(ThingEntity thing) throws ODataApplicationException {
        if (thing.hasLocationEntities()) {
            for (LocationEntity location : thing.getLocationEntities()) {
                checkInlineLocation(location);
            }
        }
        if (thing.hasDatastreamEntities()) {
            for (DatastreamEntity datastream : thing.getDatastreamEntities()) {
                checkInlineDatastream(datastream);
            }
        }
    }

    @Override
    public ThingEntity update(ThingEntity entity) throws ODataApplicationException {
        return getRepository().save(entity);
    }

    @Override
    public void delete(Long id) throws ODataApplicationException {
        if (getRepository().existsById(id)) {
            ThingEntity thing = getRepository().getOne(id);
            // delete datastreams
            thing.getDatastreamEntities().forEach(d -> {
                try {
                    getDatastreamService().delete(d.getId());
                } catch (ODataApplicationException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });
            // delete historicalLocation
            thing.getHistoricalLocationEntities().forEach(hl -> {
                try {
                    getHistoricalLocationService().delete(hl);
                } catch (ODataApplicationException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            });
            getRepository().deleteById(id);
        } else {
        throw new ODataApplicationException("Entity not found.",
                HttpStatusCode.NOT_FOUND.getStatusCode(), Locale.ROOT);
        }
    }

    @Override
    public void delete(ThingEntity entity) throws ODataApplicationException {
        getRepository().deleteById(entity.getId());
    }

    private void processDatastreams(ThingEntity thing) throws ODataApplicationException {
       if (thing.hasDatastreamEntities()) {
           Set<DatastreamEntity> datastreams = new LinkedHashSet<>();
           for (DatastreamEntity datastream : thing.getDatastreamEntities()) {
               datastream.setThing(thing);
               DatastreamEntity optionalDatastream = getDatastreamService().create(datastream);
               datastreams.add(optionalDatastream != null ? optionalDatastream : datastream);
           }
           thing.setDatastreamEntities(datastreams);
       }
    }

    private void processLocations(ThingEntity thing) throws ODataApplicationException {
        if (thing.hasLocationEntities()) {
            Set<LocationEntity> locations = new LinkedHashSet<>();
            for (LocationEntity location : thing.getLocationEntities()) {
                LocationEntity optionalLocation = getLocationService().create(location);
                locations.add(optionalLocation != null ? optionalLocation : location);
            }
            thing.setLocationEntities(locations);
        }
    }

    private void processHistoricalLocations(ThingEntity thing) throws ODataApplicationException {
        if (thing != null && thing.hasLocationEntities()) {
            Set<HistoricalLocationEntity> historicalLocations = thing.hasHistoricalLocationEntities()
                    ? new LinkedHashSet<>(thing.getHistoricalLocationEntities())
                    : new LinkedHashSet<>();
            HistoricalLocationEntity historicalLocation = new HistoricalLocationEntity();
            historicalLocation.setThingEntity(thing);
            historicalLocation.setTime(DateTime.now().toDate());
            historicalLocation.setProcesssed(true);
            HistoricalLocationEntity createdHistoricalLocation =
                    getHistoricalLocationService().createOrUpdate(historicalLocation);
            if (createdHistoricalLocation != null) {
                historicalLocations.add(createdHistoricalLocation);
            }
            for (LocationEntity location : thing.getLocationEntities()) {
                location.setHistoricalLocationEntities(historicalLocations);
                getLocationService().createOrUpdate(location);
            }
            thing.setHistoricalLocationEntities(historicalLocations);
        }
    }

    private AbstractSensorThingsEntityService<?, LocationEntity> getLocationService() {
        return (AbstractSensorThingsEntityService<?, LocationEntity>) getEntityService(EntityTypes.Location);
    }

    private AbstractSensorThingsEntityService<?, HistoricalLocationEntity> getHistoricalLocationService() {
        return (AbstractSensorThingsEntityService<?, HistoricalLocationEntity>) getEntityService(
                EntityTypes.HistoricalLocation);
    }

    private AbstractSensorThingsEntityService<?, DatastreamEntity> getDatastreamService() {
        return (AbstractSensorThingsEntityService<?, DatastreamEntity>) getEntityService(
                EntityTypes.Datastream);
    }

}
