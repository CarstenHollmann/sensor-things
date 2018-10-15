/*
 * Copyright (C) 2012-2018 52°North Initiative for Geospatial Open Source
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

import java.util.List;
import java.util.Locale;

import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.commons.core.edm.primitivetype.EdmBoolean;
import org.apache.olingo.commons.core.edm.primitivetype.EdmString;
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

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.PathBuilder;

/**
 * @author <a href="mailto:j.speckamp@52north.org">Jan Speckamp</a>
 *
 */
public class FilterExpressionVisitor<T> implements ExpressionVisitor<Object> {
    
    private Class<T> sourceType;
    
    public FilterExpressionVisitor(Class<T> sourceType) {
        this.sourceType = sourceType;
    }

    /* (non-Javadoc)
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitBinaryOperator(org.apache.olingo.server.api.uri.queryoption.expression.BinaryOperatorKind, java.lang.Object, java.lang.Object)
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
                throw new ODataApplicationException("Binary operation " + operator.name() + " is not implemented", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
//              return evaluateComparisonOperation(operator, left, right);
            } else if (operator == BinaryOperatorKind.AND
                || operator == BinaryOperatorKind.OR) {
              return evaluateBooleanOperation(operator, left, right);
              } else {
                throw new ODataApplicationException("Binary operation " + operator.name() + " is not implemented", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
              }
    }
    
    private Object evaluateBooleanOperation(BinaryOperatorKind operator, Object left, Object right)
            throws ODataApplicationException {
      
        // Check operands and get Operand Values
        BooleanExpression leftExpr = convertToBooleanExpression(left);
        BooleanExpression rightExpr = convertToBooleanExpression(right);
        
        BooleanBuilder builder = new BooleanBuilder();
        
        if (operator == BinaryOperatorKind.AND) {
            return builder.and(rightExpr).and(leftExpr);
        } else if (operator == BinaryOperatorKind.OR) {
            return builder.or(rightExpr).or(leftExpr);
        } else {
          throw new ODataApplicationException("Boolean operations valid operator.",
          HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
    }
    
    private BooleanExpression convertToBooleanExpression(Object expr) throws ODataApplicationException {
        BooleanExpression result;
        if(expr instanceof Boolean) {
            result = Expressions.asBoolean((Boolean)expr);
        } else if (expr instanceof BooleanExpression){
            result = (BooleanExpression) expr;
        } else {
          throw new ODataApplicationException("Boolean operations needs two boolean operands",
          HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
        return result;
    }
    
    private Object evaluateArithmeticOperation(BinaryOperatorKind operator, Object left, Object right)
            throws ODataApplicationException {
        
        // Check values
        com.querydsl.core.types.dsl.NumberExpression<Double> leftExpr = convertToArithmeticExpression(left);
        com.querydsl.core.types.dsl.NumberExpression<Double> rightExpr = convertToArithmeticExpression(right);
        
        // Check operands and get Operand Values
        switch(operator) {
        case ADD:
            return leftExpr.add(rightExpr);
        case DIV:
            return leftExpr.divide(rightExpr);
        case MOD:
            return leftExpr.mod(rightExpr);
        case MUL:
            return leftExpr.multiply(rightExpr);
        case SUB:
            return leftExpr.subtract(rightExpr);
        default:
            // this should never happen. Trying Arithmetics without Arithmetic operator
            return null;
         
        }
    }

    private com.querydsl.core.types.dsl.NumberExpression<Double> convertToArithmeticExpression(Object expr) throws ODataApplicationException {
        com.querydsl.core.types.dsl.NumberExpression<Double> result;
        if(expr instanceof Number) {
            // Raw Number
            result = Expressions.asNumber((double)expr);
        } else if (expr instanceof String) {
            // Reference to Property
            result = new PathBuilder<T>(sourceType, "entity").getNumber((String) expr, Double.class);   
        } else if (expr instanceof NumberExpression<?>) {
            // SubExpression
            result = (com.querydsl.core.types.dsl.NumberExpression<Double>) expr;
        } else {
          throw new ODataApplicationException("Boolean operations needs two boolean operands",
          HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
        return result;
    }

    /* (non-Javadoc)
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitUnaryOperator(org.apache.olingo.server.api.uri.queryoption.expression.UnaryOperatorKind, java.lang.Object)
     */
    @Override
    public Object visitUnaryOperator(UnaryOperatorKind operator, Object operand) throws ExpressionVisitException,
            ODataApplicationException {
        
        if(operator == UnaryOperatorKind.NOT && operand instanceof Boolean) {
            // 1.) boolean negation
            return !(Boolean) operand;
          } else if(operator == UnaryOperatorKind.MINUS && operand instanceof Integer){
            // 2.) arithmetic minus
            return -(Integer) operand;
          }

          // Operation not processed, throw an exception
          throw new ODataApplicationException("Invalid type for unary operator",
              HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
    }

    /* (non-Javadoc)
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitMethodCall(org.apache.olingo.server.api.uri.queryoption.expression.MethodKind, java.util.List)
     */
    @Override
    public Object visitMethodCall(MethodKind methodCall, List<Object> parameters) throws ExpressionVisitException,
            ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitLambdaExpression(java.lang.String, java.lang.String, org.apache.olingo.server.api.uri.queryoption.expression.Expression)
     */
    @Override
    public Object visitLambdaExpression(String lambdaFunction, String lambdaVariable, Expression expression)
            throws ExpressionVisitException,
            ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitLiteral(org.apache.olingo.server.api.uri.queryoption.expression.Literal)
     */
    @Override
    public Object visitLiteral(Literal literal) throws ExpressionVisitException, ODataApplicationException {
        // To keep this tutorial simple, our filter expression visitor supports only Edm.Int32 and Edm.String
        // In real world scenarios it can be difficult to guess the type of an literal.
        // We can be sure, that the literal is a valid OData literal because the URI Parser checks
        // the lexicographical structure

        // String literals start and end with an single quotation mark
        String literalAsString = literal.getText();
        if(literal.getType() instanceof EdmString) {
            String stringLiteral = "";
            if(literal.getText().length() > 2) {
                stringLiteral = literalAsString.substring(1, literalAsString.length() - 1);
            }

            return stringLiteral;
        } else if (literal.getType() instanceof EdmBoolean) {
            return Boolean.valueOf(literal.getText());
        }
        else {
            // Try to convert the literal into an Java Integer
            try {
                return Double.parseDouble(literalAsString);
            } catch(NumberFormatException e) {
                throw new ODataApplicationException("Only Edm.Int32 and Edm.String literals are implemented",
                    HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitMember(org.apache.olingo.server.api.uri.queryoption.expression.Member)
     */
    @Override
    public Object visitMember(Member member) throws ExpressionVisitException, ODataApplicationException {
        final List<UriResource> uriResourceParts = member.getResourcePath().getUriResourceParts();

        // Make sure that the resource path of the property contains only a single segment and a
        // primitive property has been addressed. We can be sure, that the property exists because  
        // the UriParser checks if the property has been defined in service metadata document.

        if(uriResourceParts.size() == 1 && uriResourceParts.get(0) instanceof UriResourcePrimitiveProperty) {
          UriResourcePrimitiveProperty uriResourceProperty = (UriResourcePrimitiveProperty) uriResourceParts.get(0);
          return new PathBuilder<T>(sourceType, "entity").get(uriResourceProperty.getProperty().getName());
        } else {
          // The OData specification allows in addition complex properties and navigation    
          // properties with a target cardinality 0..1 or 1.
          // This means any combination can occur e.g. Supplier/Address/City
          //  -> Navigation properties  Supplier
          //  -> Complex Property       Address
          //  -> Primitive Property     City
          // For such cases the resource path returns a list of UriResourceParts
          throw new ODataApplicationException("Only primitive properties are implemented in filter expressions", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitAlias(java.lang.String)
     */
    @Override
    public Object visitAlias(String aliasName) throws ExpressionVisitException, ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitTypeLiteral(org.apache.olingo.commons.api.edm.EdmType)
     */
    @Override
    public Object visitTypeLiteral(EdmType type) throws ExpressionVisitException, ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitLambdaReference(java.lang.String)
     */
    @Override
    public Object visitLambdaReference(String variableName) throws ExpressionVisitException, ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor#visitEnum(org.apache.olingo.commons.api.edm.EdmEnumType, java.util.List)
     */
    @Override
    public Object visitEnum(EdmEnumType type, List<String> enumValues) throws ExpressionVisitException,
            ODataApplicationException {
        // TODO Auto-generated method stub
        return null;
    }

}