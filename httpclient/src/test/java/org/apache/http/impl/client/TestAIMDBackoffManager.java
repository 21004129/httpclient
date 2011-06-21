package org.apache.http.impl.client;

import static org.junit.Assert.*;

import java.util.Random;

import org.apache.http.HttpHost;
import org.apache.http.client.BackoffManager;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.routing.HttpRoute;
import org.junit.Before;
import org.junit.Test;


public class TestAIMDBackoffManager {

    private AIMDBackoffManager impl;
    private ConnPerRouteBean connPerRoute;
    private HttpRoute route;
    private MockClock clock;

    @Before
    public void setUp() {
        connPerRoute = new ConnPerRouteBean();
        route = new HttpRoute(new HttpHost("localhost:80"));
        clock = new MockClock();
        impl = new AIMDBackoffManager(connPerRoute, clock);
        impl.setPerHostConnectionCap(10);
    }
    
    @Test
    public void isABackoffManager() {
        assertTrue(impl instanceof BackoffManager);
    }
    
    @Test
    public void halvesConnectionsOnBackoff() {
        connPerRoute.setMaxForRoute(route, 4);
        impl.backOff(route);
        assertEquals(2, connPerRoute.getMaxForRoute(route));
    }
    
    @Test
    public void doesNotBackoffBelowOneConnection() {
        connPerRoute.setMaxForRoute(route, 1);
        impl.backOff(route);
        assertEquals(1, connPerRoute.getMaxForRoute(route));        
    }
    
    @Test
    public void increasesByOneOnProbe() {
        connPerRoute.setMaxForRoute(route, 2);
        impl.probe(route);
        assertEquals(3, connPerRoute.getMaxForRoute(route));        
    }
    
    @Test
    public void doesNotIncreaseBeyondPerHostMaxOnProbe() {
        connPerRoute.setDefaultMaxPerRoute(5);
        connPerRoute.setMaxForRoute(route, 5);
        impl.setPerHostConnectionCap(5);
        impl.probe(route);
        assertEquals(5, connPerRoute.getMaxForRoute(route));
    }
    
    @Test
    public void backoffDoesNotAdjustDuringCoolDownPeriod() {
        connPerRoute.setMaxForRoute(route, 4);
        long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        impl.backOff(route);
        long max = connPerRoute.getMaxForRoute(route);
        clock.setCurrentTime(now + 1);
        impl.backOff(route);
        assertEquals(max, connPerRoute.getMaxForRoute(route));
    }
    
    @Test
    public void backoffStillAdjustsAfterCoolDownPeriod() {
        connPerRoute.setMaxForRoute(route, 8);
        long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        impl.backOff(route);
        long max = connPerRoute.getMaxForRoute(route);
        clock.setCurrentTime(now + 10 * 1000L);
        impl.backOff(route);
        assertTrue(max == 1 || max > connPerRoute.getMaxForRoute(route));
    }
    
    @Test
    public void probeDoesNotAdjustDuringCooldownPeriod() {        
        connPerRoute.setMaxForRoute(route, 4);
        long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        impl.probe(route);
        long max = connPerRoute.getMaxForRoute(route);
        clock.setCurrentTime(now + 1);
        impl.probe(route);
        assertEquals(max, connPerRoute.getMaxForRoute(route));
    }

    @Test
    public void probeStillAdjustsAfterCoolDownPeriod() {
        connPerRoute.setMaxForRoute(route, 8);
        long now = System.currentTimeMillis();
        clock.setCurrentTime(now);
        impl.probe(route);
        long max = connPerRoute.getMaxForRoute(route);
        clock.setCurrentTime(now + 10 * 1000L);
        impl.probe(route);
        assertTrue(max < connPerRoute.getMaxForRoute(route));
    }
    
    @Test
    public void backOffFactorIsConfigurable() {
        connPerRoute.setMaxForRoute(route, 10);
        impl.setBackoffFactor(0.9);
        impl.backOff(route);
        assertEquals(9, connPerRoute.getMaxForRoute(route));
    }
    
    @Test
    public void coolDownPeriodIsConfigurable() {
        long cd = new Random().nextLong() / 2;
        if (cd < 0) cd *= -1;
        if (cd < 1) cd++;
        long now = System.currentTimeMillis();
        impl.setCooldownMillis(cd);
        clock.setCurrentTime(now);
        impl.probe(route);
        int max0 = connPerRoute.getMaxForRoute(route);
        clock.setCurrentTime(now);
        impl.probe(route);
        assertEquals(max0, connPerRoute.getMaxForRoute(route));
        clock.setCurrentTime(now + cd + 1);
        impl.probe(route);
        assertTrue(max0 < connPerRoute.getMaxForRoute(route));
    }
}
