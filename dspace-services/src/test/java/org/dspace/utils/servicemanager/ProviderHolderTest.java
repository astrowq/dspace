/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.utils.servicemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Tests the ability of a provider holder to release a reference correctly
 *
 * @author Aaron Zeckoski (azeckoski @ gmail.com)
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class ProviderHolderTest {

    public class Thing {
        public String stuff;
    }

    @Test
    public void testHolder() {
        ProviderHolder<Thing> holder = new ProviderHolder<>();
        assertNull(holder.getProvider());

        Thing t = new Thing();
        holder.setProvider(t);

        Thing t2 = holder.getProvider();
        assertNotNull(t2);
        assertEquals(t, t2);

        //rubbish the references
        t = t2 = null;
    }

    @Test
    public void testHolderRelease() {
        ProviderHolder<Thing> holder = new ProviderHolder<>();
        Thing t = new Thing();
        holder.setProvider(t);

        Thing t2 = holder.getProvider();
        assertNotNull(t2);
        assertEquals(t, t2);

        // trash the references
        t = null;
        t2 = null;

        System.gc(); // yuck but it works

        Thing t3 = holder.getProvider();
        assertNull(t3);

        t3 = null;
    }

    @Test
    public void testHolderException() {
        ProviderHolder<Thing> holder = new ProviderHolder<Thing>();
        try {
            holder.getProviderOrFail();
            fail("Should have died");
        } catch (ProviderNotFoundException e) {
            assertNotNull(e.getMessage());
        }

        Thing t = new Thing();
        holder.setProvider(t);

        Thing t2 = holder.getProviderOrFail();
        assertNotNull(t2);
        assertEquals(t, t2);

        //trash the references
        t = t2 = null;
    }

    @Test
    public void testHolderHashEqualsString() {
        ProviderHolder<Thing> holder = new ProviderHolder<Thing>();
        holder.hashCode();
        assertFalse(holder.equals(null));
        assertNotNull(holder.toString());

        holder.setProvider(new Thing());
        holder.hashCode();
        assertFalse(holder.equals(null));
        assertNotNull(holder.toString());

        holder = null;
    }

}
