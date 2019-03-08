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
package org.n52.sta.service.query;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.commons.core.edm.primitivetype.EdmBoolean;
import org.apache.olingo.commons.core.edm.primitivetype.EdmDateTimeOffset;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeography;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeographyLineString;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeographyMultiLineString;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeographyMultiPoint;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeographyMultiPolygon;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeographyPoint;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeographyPolygon;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeometry;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeometryLineString;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeometryMultiLineString;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeometryMultiPoint;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeometryMultiPolygon;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeometryPoint;
import org.apache.olingo.commons.core.edm.primitivetype.EdmGeometryPolygon;
import org.apache.olingo.commons.core.edm.primitivetype.EdmString;
import org.apache.olingo.commons.core.edm.primitivetype.EdmTimespan;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty;
import org.apache.olingo.server.api.uri.queryoption.expression.BinaryOperatorKind;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor;
import org.apache.olingo.server.api.uri.queryoption.expression.Literal;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;
import org.apache.olingo.server.api.uri.queryoption.expression.MethodKind;
import org.apache.olingo.server.api.uri.queryoption.expression.UnaryOperatorKind;
import org.geolatte.geom.codec.Wkt;
import org.n52.sta.data.query.EntityQuerySpecifications;
import org.n52.sta.data.query.QuerySpecificationRepository;
import org.n52.sta.data.service.AbstractSensorThingsEntityService;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.DateTimeExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.spatial.GeometryExpression;
import com.querydsl.spatial.GeometryExpressions;
import com.querydsl.spatial.SpatialOps;

/**
 * @author <a href="mailto:j.speckamp@52north.org">Jan Speckamp</a>
 *
 */
public class FilterExpressionVisitor implements ExpressionVisitor<Object> {

    private Class< ? > sourceType;

    private String sourcePath;

    private EntityQuerySpecifications< ? > rootQS;

    private AbstractSensorThingsEntityService< ? , ? > service;

    public FilterExpressionVisitor(Class< ? > sourceType, AbstractSensorThingsEntityService< ? , ? > service)
            throws ODataApplicationException {
        this.sourceType = sourceType;
        this.service = service;
        this.rootQS = QuerySpecificationRepository.getSpecification(sourceType.getSimpleName());

        // TODO: Replace fragile simpleName (with lowercase first letter) with better alternative (e.g.
        // <QType>.getRoot())
        this.sourcePath = Character.toLowerCase(sourceType.getSimpleName().charAt(0))
                + sourceType.getSimpleName().substring(1);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitBinaryOperator(org.
     * apache.olingo.server.api.uri.queryoption.expression.BinaryOperatorKind, java.lang.Object,
     * java.lang.Object)
     */
    @Override
    public Object visitBinaryOperator(BinaryOperatorKind operator, Object left, Object right)
            throws ExpressionVisitException,
            ODataApplicationException {
        if (operator == BinaryOperatorKind.ADD
                || operator == BinaryOperatorKind.MOD
                || operator == BinaryOperatorKind.MUL
                || operator == BinaryOperatorKind.DIV
                || operator == BinaryOperatorKind.SUB) {
            return evaluateArithmeticOperation(operator, left, right);
        } else if (operator == BinaryOperatorKind.EQ
                || operator == BinaryOperatorKind.NE
                || operator == BinaryOperatorKind.GE
                || operator == BinaryOperatorKind.GT
                || operator == BinaryOperatorKind.LE
                || operator == BinaryOperatorKind.LT) {
            return evaluateComparisonOperation(operator, left, right);
        } else if (operator == BinaryOperatorKind.AND
                || operator == BinaryOperatorKind.OR) {
            return evaluateBooleanOperation(operator, left, right);
        } else {
            throw new ODataApplicationException("Binary operation " + operator.name() + " is not implemented",
                                                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(),
                                                Locale.ENGLISH);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitUnaryOperator(org.apache
     * .olingo.server.api.uri.queryoption.expression.UnaryOperatorKind, java.lang.Object)
     */
    @Override
    public Object visitUnaryOperator(UnaryOperatorKind operator, Object operand) throws ExpressionVisitException,
    ODataApplicationException {

        if (operator == UnaryOperatorKind.NOT && operand instanceof BooleanExpression) {
            // 1.) boolean negation
            return ((BooleanExpression) operand).not();
        } else if (operator == UnaryOperatorKind.MINUS && operand instanceof Number) {
            // 2.) arithmetic minus
            return -(Double) operand;
        } else if (operator == UnaryOperatorKind.MINUS && operand instanceof NumberExpression) {
            // 2.) arithmetic minus
            return ((NumberExpression< ? >) operand).negate();
        }

        // Operation not processed, throw an exception
        throw new ODataApplicationException("Invalid type for unary operator",
                                            HttpStatusCode.BAD_REQUEST.getStatusCode(),
                                            Locale.ENGLISH);
    }

    private Object evaluateArithmeticOperation(BinaryOperatorKind operator, Object left, Object right)
            throws ExpressionVisitException,
            ODataApplicationException {
        NumberExpression< ? > leftExpr = convertToArithmeticExpression(left);
        NumberExpression< ? > rightExpr = convertToArithmeticExpression(right);

        return rootQS.handleNumberFilter(leftExpr, rightExpr, operator, false);
    }

    /**
     * Evaluates Comparison operation for various Types. Comparison is attempted in the following order:
     *
     * Number Comparison > String Comparison > Date Comparison > Timespan Comparison.
     *
     * If parameters can not be converted into comparable Datatypes or all Comparisons fail an error is
     * thrown.
     *
     * @param operator
     *        Operator to be used for comparison
     * @param left
     *        left operand
     * @param right
     *        right operand
     * @return BooleanExpression evaluating to true if comparison evaluated to true
     * @throws ODataApplicationException
     *         if invalid operator was encountered or Expression is not comparable
     * @throws ExpressionVisitException
     *         if invalid operator was encountered
     */
    private Object evaluateComparisonOperation(BinaryOperatorKind operator, Object left, Object right)
            throws ODataApplicationException,
            ExpressionVisitException {

        // Let QuerySpecifications handle Expressions with properties
        // as they can ensure proper Datatypes etc.
        if (left instanceof String || right instanceof String) {
            if (left instanceof String) {
                return rootQS.getFilterForProperty((String) left, right, operator, false);
            } else {
                return rootQS.getFilterForProperty((String) right, left, operator, true);
            }
        } else {
            // Handle Literals + Combined Expressions
            // Assume Numbers are compared
            try {

                NumberExpression< ? extends Comparable< ? >> leftExpr = convertToArithmeticExpression(left);
                NumberExpression< ? extends Comparable< ? >> rightExpr = convertToArithmeticExpression(right);
                return rootQS.handleNumberFilter(leftExpr, rightExpr, operator, false);
            } catch (ODataApplicationException e) {
            }

            // Fallback to String comparison
            try {
                // Handle literal values + inherent properties
                if (! (left instanceof List< ? > || right instanceof List< ? >)) {

                    // Fallback to String comparison
                    StringExpression leftExpr = convertToStringExpression(left);
                    StringExpression rightExpr = convertToStringExpression(right);
                    return rootQS.handleStringFilter(leftExpr, rightExpr, operator, false);
                } else {
                    // Handle foreign properties
                    if (left instanceof List< ? > && right instanceof List< ? >) {
                        // TODO: implement
                        throw new ODataApplicationException("Comparison of two foreign properties is currently not implemented",
                                                            HttpStatusCode.BAD_REQUEST.getStatusCode(),
                                                            Locale.ENGLISH);
                    } else if (left instanceof List< ? >) {
                        return convertToForeignExpression(left, right, operator);
                    } else {
                        return convertToForeignExpression(right, left, operator);
                    }

                }
            } catch (ODataApplicationException f) {
            }

            // Fallback to Date comparison
            try {
                DateTimeExpression<Date> leftExpr = convertToDateTimeExpression(left);
                DateTimeExpression<Date> rightExpr = convertToDateTimeExpression(right);

                return rootQS.handleDateFilter(leftExpr, rightExpr, operator, false);
            } catch (ODataApplicationException e) {
            }

            // Fallback to Timespan comparison
            try {
                DateTimeExpression<Date>[] leftExpr = convertToTimespanExpression(left);
                DateTimeExpression<Date>[] rightExpr = convertToTimespanExpression(right);

                switch (operator) {
                case EQ: {
                    return leftExpr[0].eq(rightExpr[0]).and(leftExpr[1].eq(rightExpr[1]));
                }
                case NE: {
                    return leftExpr[0].ne(rightExpr[0]).or(leftExpr[1].ne(rightExpr[1]));
                }
                default: {
                    throw new ODataApplicationException("Comparison of Timespans is currently implemented for EQ and NE operators.",
                                                        HttpStatusCode.BAD_REQUEST.getStatusCode(),
                                                        Locale.ENGLISH);
                }
                }
            } catch (ODataApplicationException e) {
            }

            // Fallback to Error
            throw new ODataApplicationException("Could not parse Parameters to Filter Expression.",
                                                HttpStatusCode.BAD_REQUEST.getStatusCode(),
                                                Locale.ENGLISH);
        }
    }

    private Object evaluateBooleanOperation(BinaryOperatorKind operator, Object left, Object right)
            throws ODataApplicationException {
        BooleanExpression leftExpr = (BooleanExpression) left;
        BooleanExpression rightExpr = (BooleanExpression) right;

        BooleanBuilder builder = new BooleanBuilder();
        if (operator == BinaryOperatorKind.AND) {
            return builder.and(rightExpr).and(leftExpr).getValue();
        } else if (operator == BinaryOperatorKind.OR) {
            return builder.or(rightExpr).or(leftExpr).getValue();
        } else {
            throw new ODataApplicationException("Could not convert " + operator.toString() + " to BooleanOperation",
                                                HttpStatusCode.BAD_REQUEST.getStatusCode(),
                                                Locale.ENGLISH);
        }
    }

    @SuppressWarnings("unchecked")
    private DateTimeExpression<Date> convertToDateTimeExpression(Object expr) throws ODataApplicationException {
        if (expr instanceof DateTimeExpression< ? >) {
            // Literal
            return ((DateTimeExpression<Date>) expr);
        } else if (expr instanceof Date) {
            // Literal
            return Expressions.asDateTime((Date) expr);
        } else if (expr instanceof String) {
            // Property
            return new PathBuilder(sourceType, sourcePath).getDateTime((String) expr, Date.class);
        } else
            throw new ODataApplicationException("Could not convert " + expr.toString() + "to BooleanExpression",
                                                HttpStatusCode.BAD_REQUEST.getStatusCode(),
                                                Locale.ENGLISH);
    }

    private DateTimeExpression<Date>[] convertToTimespanExpression(Object expr) throws ODataApplicationException {
        @SuppressWarnings("unchecked")
        DateTimeExpression<Date>[] result = new DateTimeExpression[2];

        if (expr instanceof Date[]) {
            // Literal
            result[0] = Expressions.asDateTime(((Date[])expr)[0]);
            result[1] = Expressions.asDateTime(((Date[])expr)[1]);
            return result;
        } else
            throw new ODataApplicationException("Could not convert " + expr.toString() + "to DateTimeExpression",
                                                HttpStatusCode.BAD_REQUEST.getStatusCode(),
                                                Locale.ENGLISH);
    }

    /**
     *
     * Constructs a Subquery based on given Path to property of related Entity to evaluate Filters on those
     * properties. Returned Expression evaluates to true if Entity should be included. TODO: Expand to support
     * deeper nested properties
     *
     * @param resources
     *        Path to foreign property
     * @param value
     *        supposed value of foreign property
     * @param operator
     *        operator to be used to compare value and actual value
     * @return BooleanExpression evaluating to true if filter on related entity was successful.
     * @throws ExpressionVisitException
     *         If the subquery could not be build.
     * @throws ODataApplicationException
     *         If no QuerySpecification for given related Entity was found.
     */
    private BooleanExpression convertToForeignExpression(Object resources,
                                                         Object value,
                                                         BinaryOperatorKind operator)
                                                                 throws ExpressionVisitException,
                                                                 ODataApplicationException {
        JPQLQuery<Long> idQuery = null;
        @SuppressWarnings("unchecked")
        List<UriResource> uriResources = (List<UriResource>) resources;
        int uriLength = uriResources.size();
        String lastResource = uriResources.get(uriLength-2).toString();

        // Get filter on Entity
        EntityQuerySpecifications< ? > stepQS = QuerySpecificationRepository.getSpecification(lastResource);
        Object filter = stepQS.getFilterForProperty(uriResources.get(uriLength-1).toString(), value, operator, false);
        idQuery = stepQS.getIdSubqueryWithFilter((com.querydsl.core.types.Expression<Boolean>)filter);

        for (int i = uriLength-3; i > 0; i-- ) {
            // Get QuerySpecifications for subQuery
            stepQS = QuerySpecificationRepository.getSpecification(uriResources.get(i).toString());
            // Get new IdQuery based on Filter
            BooleanExpression expr = (BooleanExpression) stepQS.getFilterForProperty(uriResources.get(i+1).toString(), idQuery, null, false);
            idQuery = stepQS.getIdSubqueryWithFilter(expr);
        }
        //
        // // Filter by Id on main Query
        //TODO check if this cast is legit
        return  (BooleanExpression) rootQS.getFilterForProperty(uriResources.get(0).toString(), idQuery, null, false);
    }

    /**
     * Converts an Object into a computable StringExpression. Throws an Exception if Conversion fails.
     *
     * @param expr
     *        Object to be coerced into StringExpression
     * @return StringExpression equivalent to expr
     * @throws ODataApplicationException
     *         if Object cannot be converted to StringExpression
     */
    private StringExpression convertToStringExpression(Object expr) throws ODataApplicationException {
        StringExpression result;
        if (expr instanceof StringExpression) {
            // SubExpression
            result = (StringExpression) expr;
        } else {
            throw new ODataApplicationException("Could not convert " + expr.toString() + " to StringExpression",
                                                HttpStatusCode.BAD_REQUEST.getStatusCode(),
                                                Locale.ENGLISH);
        }
        return result;
    }

    /**
     * Converts an Object into a computable NumberExpression. Throws an Exception if Conversion fails.
     *
     * @param expr
     *        Object to be coerced into NumberExpression
     * @return NumberExpression equivalent to expr
     * @throws ODataApplicationException
     *         if Object cannot be converted to NumberExpression
     */
    private NumberExpression< ? extends Comparable< ? >> convertToArithmeticExpression(Object expr)
            throws ODataApplicationException {
        if (expr instanceof Number) {
            // Raw Number
            return Expressions.asNumber((double) expr);
        } else if (expr instanceof NumberExpression< ? >) {
            // SubExpression
            return (NumberExpression< ? >) expr;
        } else {
            throw new ODataApplicationException("Could not convert " + expr.toString() + " to NumberExpression",
                                                HttpStatusCode.BAD_REQUEST.getStatusCode(),
                                                Locale.ENGLISH);
        }
    }

    /**
     * Converts an Object into a GeometryExpression. Throws an Exception if Conversion fails.
     *
     * @param expr
     *        Object to be coerced into GeometryExpression
     * @return GeometryExpression equivalent to expr
     * @throws ODataApplicationException
     *         if Object cannot be converted to GeometryExpression
     */
    private GeometryExpression< ? > convertToGeometryExpression(Object expr) throws ODataApplicationException {
        GeometryExpression< ? > result = null;
        if (expr instanceof GeometryExpression< ? >) {
            // SubExpression
            result = (GeometryExpression< ? >) expr;
        } else {
            throw new ODataApplicationException("Could not convert " + expr.toString() + " to StringExpression",
                                                HttpStatusCode.BAD_REQUEST.getStatusCode(),
                                                Locale.ENGLISH);
        }
        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitMethodCall(org.apache.
     * olingo.server.api.uri.queryoption.expression.MethodKind, java.util.List)
     */
    @Override
    public Object visitMethodCall(MethodKind methodCall, List<Object> parameters) throws ExpressionVisitException,
    ODataApplicationException {
        Object arg1 = parameters.get(0);
        Object arg2 = parameters.get(1);

        switch (methodCall) {
        // String Functions
        case CONTAINS:
        case SUBSTRINGOF:
            return convertToStringExpression(arg1).contains(convertToStringExpression(arg2));
        case ENDSWITH:
            return convertToStringExpression(arg1).endsWith(convertToStringExpression(arg2));
        case STARTSWITH:
            return convertToStringExpression(arg1).startsWith(convertToStringExpression(arg2));
        case LENGTH:
            return convertToStringExpression(arg1).length();
        case INDEXOF:
            return convertToStringExpression(arg1).indexOf(convertToStringExpression(arg2));
        case SUBSTRING:
            StringExpression string = convertToStringExpression(arg1);
            NumberExpression<Integer> len = convertToArithmeticExpression(arg2).intValue();
            return Expressions.stringOperation(Ops.SUBSTR_1ARG, string, len);
        case TOLOWER:
            return convertToStringExpression(arg1).toLowerCase();
        case TOUPPER:
            return convertToStringExpression(arg1).toUpperCase();
        case TRIM:
            convertToStringExpression(arg1).trim();
        case CONCAT:
            return convertToStringExpression(arg1).concat(convertToStringExpression(arg2));

            // Math Functions
        case ROUND:
            return convertToArithmeticExpression(arg1).round();
        case FLOOR:
            return convertToArithmeticExpression(arg1).floor();
        case CEILING:
            return convertToArithmeticExpression(arg1).ceil();

            // Date Functions
        case YEAR:
            return convertToDateTimeExpression(arg1).year();
        case MONTH:
            return convertToDateTimeExpression(arg1).month();
        case DAY:
            return convertToDateTimeExpression(arg1).dayOfMonth();
        case HOUR:
            return convertToDateTimeExpression(arg1).hour();
        case MINUTE:
            return convertToDateTimeExpression(arg1).minute();
        case SECOND:
            return convertToDateTimeExpression(arg1).second();
        case FRACTIONALSECONDS:
            return convertToDateTimeExpression(arg1).milliSecond();
        case NOW:
            return Expressions.asDate(new Date());
        case MINDATETIME:
            return Expressions.asDate(Date.from(Instant.MIN));
        case MAXDATETIME:
            return Expressions.asDate(Date.from(Instant.MAX));
        case DATE:
            // TODO: Implement
            break;
        case TIME:
            // TODO: Implement
            break;
        case TOTALOFFSETMINUTES:
            // TODO: Implement
            break;

            // Geospatial Functions
        case GEODISTANCE:
            return convertToGeometryExpression(arg1).distance(convertToGeometryExpression(arg2));
        case GEOLENGTH:
            // TODO: Implement
            break;
        case GEOINTERSECTS:
            return convertToGeometryExpression(arg1).intersects(convertToGeometryExpression(arg2));

            // Spatial Relationship Functions
        case ST_CONTAINS:
            return convertToGeometryExpression(arg1).contains(convertToGeometryExpression(arg2));
        case ST_CROSSES:
            return convertToGeometryExpression(arg1).crosses(convertToGeometryExpression(arg2));
        case ST_DISJOINT:
            return convertToGeometryExpression(arg1).disjoint(convertToGeometryExpression(arg2));
        case ST_EQUALS:
            return Expressions.booleanOperation(SpatialOps.EQUALS,
                                                convertToGeometryExpression(arg1),
                                                convertToGeometryExpression(arg1));
        case ST_INTERSECTS:
            return convertToGeometryExpression(arg1).intersects(convertToGeometryExpression(arg2));
        case ST_OVERLAPS:
            return convertToGeometryExpression(arg1).overlaps(convertToGeometryExpression(arg2));
        case ST_RELATE:
            return convertToGeometryExpression(arg1).relate(convertToGeometryExpression(arg2),
                                                            parameters.get(2).toString());
        case ST_TOUCHES:
            return convertToGeometryExpression(arg1).touches(convertToGeometryExpression(arg2));
        case ST_WITHIN:
            return convertToGeometryExpression(arg1).within(convertToGeometryExpression(arg2));
        default:
            break;
        }
        // Fallback to Error in case of ODATA-conform but not STA-conform Method or unimplemented method
        throw new ODataApplicationException("Invalid Method: " + methodCall.name()
        + " is not included in STA Specification.",
        HttpStatusCode.BAD_REQUEST.getStatusCode(),
        Locale.ENGLISH);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitLambdaExpression(java.
     * lang.String, java.lang.String, org.apache.olingo.server.api.uri.queryoption.expression.Expression)
     */
    @Override
    public Object visitLambdaExpression(String lambdaFunction, String lambdaVariable, Expression expression)
            throws ExpressionVisitException,
            ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitLiteral(org.apache.
     * olingo.server.api.uri.queryoption.expression.Literal)
     */
    @Override
    public Object visitLiteral(Literal literal) throws ExpressionVisitException, ODataApplicationException {
        // String literals start and end with an single quotation mark
        EdmType type = literal.getType();
        String literalAsString = literal.getText();
        if (type instanceof EdmString) {
            String stringLiteral = "";
            if (literal.getText().length() > 2) {
                stringLiteral = literalAsString.substring(1, literalAsString.length() - 1);
            }
            return Expressions.asString(stringLiteral);
        } else if (type instanceof EdmBoolean) {
            // TODO: Check if boolean literals are actually supported by STA Spec
            // return (Boolean.valueOf(literal.getText()))? Expressions.TRUE: Expressions.FALSE;
            throw new ODataApplicationException("Boolean Literals are currently not implemented",
                                                HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(),
                                                Locale.ENGLISH);
        } else if (type instanceof EdmDateTimeOffset) {
            return Expressions.asDateTime(Date.from(OffsetDateTime.parse(literal.getText()).toInstant()));
        } else if (type instanceof EdmGeography || type instanceof EdmGeometry
                || type instanceof EdmGeographyPoint || type instanceof EdmGeometryPoint
                || type instanceof EdmGeographyMultiPoint || type instanceof EdmGeometryMultiPoint
                || type instanceof EdmGeographyLineString || type instanceof EdmGeometryLineString
                || type instanceof EdmGeographyMultiLineString || type instanceof EdmGeometryMultiLineString
                || type instanceof EdmGeographyPolygon || type instanceof EdmGeometryPolygon
                || type instanceof EdmGeographyMultiPolygon || type instanceof EdmGeometryMultiPolygon) {
            String wkt = literalAsString.substring(literalAsString.indexOf("\'") + 1, literalAsString.length() - 1);
            if (!wkt.startsWith("SRID")) {
                wkt = "SRID=4326;" + wkt;
            }
            return GeometryExpressions.asGeometry(Wkt.fromWkt(wkt));
        } else if (type instanceof EdmTimespan) {
            Date[] timespan = new Date[2];

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
            String[] split = literalAsString.split("/");
            try {
                timespan[0] = format.parse(split[0]);
                timespan[1] = format.parse(split[1]);
            } catch (ParseException e) {
                throw new ODataApplicationException("Could not parse Date. Error was: "
                        + e.getMessage(),
                        HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ENGLISH);
            }
            return timespan;
        } else {
            // Coerce literal numbers into Double
            try {
                return Expressions.asNumber(Double.parseDouble(literalAsString));
            } catch (NumberFormatException e) {
                throw new ODataApplicationException("Could not parse literal Numeric Value to Double. Error was: "
                        + e.getMessage(),
                        HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ENGLISH);
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitMember(org.apache.olingo
     * .server.api.uri.queryoption.expression.Member)
     */
    @Override
    public Object visitMember(Member member) throws ExpressionVisitException, ODataApplicationException {
        final List<UriResource> uriResourceParts = member.getResourcePath().getUriResourceParts();
        if (uriResourceParts.size() == 1 && uriResourceParts.get(0) instanceof UriResourcePrimitiveProperty) {
            UriResourcePrimitiveProperty uriResourceProperty = (UriResourcePrimitiveProperty) uriResourceParts.get(0);

            String name = uriResourceProperty.getProperty().getName();
            // Workaround for Properties that can not be ordered but filtered by
            if (name.equals("encodingType") || name.equals("metadata")) {
                return name;
            } else {
                return service.checkPropertyName(name);
            }
        } else {
            return uriResourceParts;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitAlias(java.lang.String)
     */
    @Override
    public Object visitAlias(String aliasName) throws ExpressionVisitException, ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitTypeLiteral(org.apache.
     * olingo.commons.api.edm.EdmType)
     */
    @Override
    public Object visitTypeLiteral(EdmType type) throws ExpressionVisitException, ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitLambdaReference(java.
     * lang.String)
     */
    @Override
    public Object visitLambdaReference(String variableName) throws ExpressionVisitException, ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitEnum(org.apache.olingo.
     * commons.api.edm.EdmEnumType, java.util.List)
     */
    @Override
    public Object visitEnum(EdmEnumType type, List<String> enumValues) throws ExpressionVisitException,
    ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

}
