/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.extensions.internal.config;

import static org.mule.module.extensions.internal.capability.xml.schema.model.SchemaConstants.ATTRIBUTE_NAME_CONFIG;
import static org.mule.module.extensions.internal.config.XmlExtensionParserUtils.setNoRecurseOnDefinition;
import static org.mule.module.extensions.internal.config.XmlExtensionParserUtils.toElementDescriptorBeanDefinition;
import org.mule.config.spring.factories.PollingMessageSourceFactoryBean;
import org.mule.config.spring.util.SpringXMLUtils;
import org.mule.enricher.MessageEnricher;
import org.mule.extensions.introspection.Operation;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

final class OperationBeanDefinitionParser implements BeanDefinitionParser
{

    private final Operation operation;

    OperationBeanDefinitionParser(Operation operation)
    {
        this.operation = operation;
    }

    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext)
    {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(OperationFactoryBean.class);
        builder.setScope(BeanDefinition.SCOPE_PROTOTYPE);
        parseConfigRef(element, builder);
        builder.addConstructorArgValue(operation);
        builder.addConstructorArgValue(toElementDescriptorBeanDefinition(element));

        BeanDefinition definition = builder.getBeanDefinition();
        setNoRecurseOnDefinition(definition);
        attachProcessorDefinition(parserContext, definition);

        return definition;
    }

    private void parseConfigRef(Element element, BeanDefinitionBuilder builder)
    {
        String configRef = element.getAttribute(ATTRIBUTE_NAME_CONFIG);
        if (StringUtils.isBlank(configRef))
        {
            throw new IllegalArgumentException("All operations must provide a config-ref element");
        }

        builder.addConstructorArgValue(new RuntimeBeanReference(configRef));
    }


    private String generateChildBeanName(Element element)
    {
        String id = SpringXMLUtils.getNameOrId(element);
        if (StringUtils.isBlank(id))
        {
            String parentId = SpringXMLUtils.getNameOrId(((Element) element.getParentNode()));
            return String.format(".%s:%s", parentId, element.getLocalName());
        }
        else
        {
            return id;
        }
    }

    private BeanDefinition parseNestedProcessor(Element element, ParserContext parserContext, Class factory)
    {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(factory);
        BeanDefinition beanDefinition = builder.getBeanDefinition();
        parserContext.getRegistry().registerBeanDefinition(generateChildBeanName(element), beanDefinition);
        element.setAttribute("name", generateChildBeanName(element));
        builder.setSource(parserContext.extractSource(element));
        builder.setScope(BeanDefinition.SCOPE_SINGLETON);
        List list = parserContext.getDelegate().parseListElement(element, builder.getBeanDefinition());
        parserContext.getRegistry().removeBeanDefinition(generateChildBeanName(element));
        return beanDefinition;
    }

    private List parseNestedProcessorAsList(Element element, ParserContext parserContext, Class factory)
    {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(factory);
        BeanDefinition beanDefinition = builder.getBeanDefinition();
        parserContext.getRegistry().registerBeanDefinition(generateChildBeanName(element), beanDefinition);
        element.setAttribute("name", generateChildBeanName(element));
        builder.setSource(parserContext.extractSource(element));
        builder.setScope(BeanDefinition.SCOPE_SINGLETON);
        List list = parserContext.getDelegate().parseListElement(element, builder.getBeanDefinition());
        parserContext.getRegistry().removeBeanDefinition(generateChildBeanName(element));
        return list;
    }

    private void parseNestedProcessorAsListAndSetProperty(Element element,
                                                          ParserContext parserContext,
                                                          Class factory,
                                                          BeanDefinitionBuilder builder,
                                                          String propertyName)
    {
        builder.addPropertyValue(propertyName, parseNestedProcessorAsList(element, parserContext, factory));
    }

    private void parseNestedProcessorAndSetProperty(Element element,
                                                    ParserContext parserContext,
                                                    Class factory,
                                                    BeanDefinitionBuilder builder,
                                                    String propertyName)
    {
        builder.addPropertyValue(propertyName, parseNestedProcessor(element, parserContext, factory));
    }

    private void parseNestedProcessorAsListAndSetProperty(Element element,
                                                          String childElementName,
                                                          ParserContext parserContext,
                                                          Class factory,
                                                          BeanDefinitionBuilder builder,
                                                          String propertyName)
    {
        Element childDomElement = DomUtils.getChildElementByTagName(element, childElementName);
        if (childDomElement != null)
        {
            builder.addPropertyValue(propertyName,
                                     parseNestedProcessorAsList(childDomElement, parserContext, factory));
        }
    }

    private void parseNestedProcessorAndSetProperty(Element element,
                                                    String childElementName,
                                                    ParserContext parserContext,
                                                    Class factory,
                                                    BeanDefinitionBuilder builder,
                                                    String propertyName)
    {
        Element childDomElement = DomUtils.getChildElementByTagName(element, childElementName);
        if (childDomElement != null)
        {
            builder.addPropertyValue(propertyName,
                                     parseNestedProcessor(childDomElement, parserContext, factory));
        }
    }

    private void attachProcessorDefinition(ParserContext parserContext, BeanDefinition definition)
    {
        MutablePropertyValues propertyValues = parserContext.getContainingBeanDefinition()
                .getPropertyValues();
        if (parserContext.getContainingBeanDefinition()
                .getBeanClassName()
                .equals(PollingMessageSourceFactoryBean.class.getName()))
        {

            propertyValues.addPropertyValue("messageProcessor", definition);
        }
        else
        {
            if (parserContext.getContainingBeanDefinition()
                    .getBeanClassName()
                    .equals(MessageEnricher.class.getName()))
            {
                propertyValues.addPropertyValue("enrichmentMessageProcessor", definition);
            }
            else
            {
                PropertyValue messageProcessors = propertyValues.getPropertyValue("messageProcessors");
                if ((messageProcessors == null) || (messageProcessors.getValue() == null))
                {
                    propertyValues.addPropertyValue("messageProcessors", new ManagedList());
                }
                List listMessageProcessors = ((List) propertyValues.getPropertyValue("messageProcessors")
                        .getValue());
                listMessageProcessors.add(definition);
            }
        }
    }

    private void attachSourceDefinition(ParserContext parserContext, BeanDefinition definition)
    {
        MutablePropertyValues propertyValues = parserContext.getContainingBeanDefinition()
                .getPropertyValues();
        propertyValues.addPropertyValue("messageSource", definition);
    }
}