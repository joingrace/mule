/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.mule.DefaultMuleEvent;
import org.mule.DefaultMuleMessage;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.MuleSession;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.service.Service;
import org.mule.tck.MuleTestUtils;
import org.mule.tck.junit4.AbstractMuleContextTestCase;
import org.mule.tck.testmodels.fruit.Apple;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class SimpleCollectionAggregatorTestCase extends AbstractMuleContextTestCase
{

    public SimpleCollectionAggregatorTestCase()
    {
        setStartContext(true);
    }

    @Test
    public void testMessageProcessor() throws Exception
    {
        MuleSession session1 = getTestSession(getTestService(), muleContext);
        session1.setProperty("key1", "value1");
        MuleSession session2 = getTestSession(getTestService(), muleContext);
        session1.setProperty("key1", "value1NEW");
        session1.setProperty("key2", "value2");
        MuleSession session3 = getTestSession(getTestService(), muleContext);
        session1.setProperty("key3", "value3");
        
        
        Service testService = getTestService("test", Apple.class);
        assertNotNull(testService);

        SimpleCollectionAggregator router = new SimpleCollectionAggregator();
        router.setMuleContext(muleContext);
        router.setFlowConstruct(testService);
        router.initialise();
        
        MuleMessage message1 = new DefaultMuleMessage("test event A", muleContext);
        MuleMessage message2 = new DefaultMuleMessage("test event B", muleContext);
        MuleMessage message3 = new DefaultMuleMessage("test event C", muleContext);
        message1.setCorrelationId(message1.getUniqueId());
        message2.setCorrelationId(message1.getUniqueId());
        message3.setCorrelationId(message1.getUniqueId());
        message1.setCorrelationGroupSize(3);

        InboundEndpoint endpoint = MuleTestUtils.getTestInboundEndpoint(MessageExchangePattern.ONE_WAY, muleContext);
        MuleEvent event1 = new DefaultMuleEvent(message1, endpoint, testService, session1);
        MuleEvent event2 = new DefaultMuleEvent(message2, endpoint, testService, session2);
        MuleEvent event3 = new DefaultMuleEvent(message3, endpoint, testService, session3);

        assertNull(router.process(event1));
        assertNull(router.process(event2));
        MuleEvent resultEvent = router.process(event3);
        assertNotNull(resultEvent);
        MuleMessage resultMessage = resultEvent.getMessage();
        assertNotNull(resultMessage);
        List<String> payload = (List<String>)resultMessage.getPayload();
        assertEquals(3, payload.size());
        String[] results=new String[3];
        results=payload.toArray(results);
        Arrays.sort(results);
        assertEquals("test event A", results[0]);
        assertEquals("test event B", results[1]);
        assertEquals("test event C", results[2]);
        
        // Assert that session was merged correctly
        assertEquals(3, resultEvent.getSession().getProperties().size());
        assertEquals("value1NEW", resultEvent.getSession().getProperty("key1"));
        assertEquals("value2", resultEvent.getSession().getProperty("key2"));
        assertEquals("value3", resultEvent.getSession().getProperty("key3"));
    }

}